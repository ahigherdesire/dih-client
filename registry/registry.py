#!/usr/bin/env python3
"""DIH addon registry tool. Standard library only.

  python registry/registry.py check                   # validate every entry (what CI runs)
  python registry/registry.py build                   # validate, then write website/addons.json
  python registry/registry.py add --jar build/libs/my-addon-1.0.0.jar \
      --source https://github.com/you/my-addon \
      --jar-url https://github.com/you/my-addon/releases/download/v1.0.0/my-addon-1.0.0.jar \
      --summary "One line shown under the name"

Each addon is one file in registry/addons/<id>.json, so contributions never conflict. An addon can ship one jar per
Minecraft version: run `add` once per jar (same addon version) and the files are merged into one listing.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
ENTRIES = ROOT / "addons"
OUTPUT = ROOT.parent / "website" / "addons.json"

ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{1,39}$")
VERSION_RE = re.compile(r"^\d+\.\d+(?:\.\d+)?(?:[-+][0-9A-Za-z.-]+)?$")
SHA_RE = re.compile(r"^[0-9a-f]{64}$")
REPO_RE = re.compile(r"^https://github\.com/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+)/?$")
MC_RE = re.compile(r"^\d+\.\d+(?:\.\d+)?$")
MAX_JAR_BYTES = 64 * 1024 * 1024
CATEGORIES = {"hud", "utility", "automation", "combat", "movement", "render", "world", "macro", "library", "fun",
              "basehunting", "seed"}
FIELDS = {"id", "name", "version", "author", "summary", "description", "apiVersion", "license", "categories",
          "source", "image", "files"}
REQUIRED = FIELDS - {"categories", "summary", "image"}
FILE_FIELDS = {"minecraft", "jar", "size", "sha256"}


class RegistryError(Exception):
    pass


def text_ok(value, limit: int) -> bool:
    return isinstance(value, str) and bool(value.strip()) and len(value) <= limit


def validate(entry: dict, filename: str) -> list[str]:
    """Return a list of problems for one entry (empty = valid)."""
    errs: list[str] = []
    unknown = set(entry) - FIELDS
    if unknown:
        errs.append("unknown field(s): " + ", ".join(sorted(unknown)))
    for key in sorted(REQUIRED - set(entry)):
        errs.append("missing field: " + key)
    if errs:
        return errs

    if not ID_RE.match(entry["id"]):
        errs.append("id must be 2-40 chars of a-z 0-9 _ - and start with a letter or digit")
    if entry["id"] == "dih" or entry["id"].startswith(("dih-", "dih_")):
        errs.append("ids starting with dih- are reserved for addons built into DIH")
    if entry["id"] + ".json" != filename:
        errs.append("file must be named " + entry["id"] + ".json")
    if not VERSION_RE.match(entry["version"]):
        errs.append("version must look like 1.2.3")
    if not isinstance(entry["apiVersion"], int) or entry["apiVersion"] < 1:
        errs.append("apiVersion must be a positive integer (see ApiVersion.CURRENT)")
    for key, limit in (("name", 40), ("author", 60), ("description", 300), ("license", 40)):
        if not text_ok(entry[key], limit):
            errs.append(key + " must be a non-empty string up to " + str(limit) + " chars")
    if "summary" in entry and not text_ok(entry["summary"], 70):
        errs.append("summary must be a non-empty string up to 70 chars")
    for cat in entry.get("categories", []):
        if cat not in CATEGORIES:
            errs.append("unknown category " + repr(cat) + " (allowed: " + ", ".join(sorted(CATEGORIES)) + ")")

    repo = REPO_RE.match(entry["source"])
    prefix = None
    if not repo:
        errs.append("source must be a public GitHub repo URL: https://github.com/owner/repo")
    else:
        prefix = "https://github.com/" + repo.group(1) + "/" + repo.group(2) + "/releases/download/"

    if "image" in entry:
        img = entry["image"]
        ok_type = isinstance(img, str) and img.lower().endswith((".png", ".jpg", ".jpeg"))
        ok_host = repo is not None and isinstance(img, str) and (
            img.startswith("https://raw.githubusercontent.com/" + repo.group(1) + "/" + repo.group(2) + "/")
            or img.startswith("https://github.com/" + repo.group(1) + "/" + repo.group(2) + "/releases/download/"))
        if not (ok_type and ok_host) or ".." in img:
            errs.append("image must be a .png/.jpg in the source repo (raw.githubusercontent.com/<owner>/<repo>/...) or its releases")

    files = entry["files"]
    if not isinstance(files, list) or not files:
        return errs + ["files must be a non-empty list (one jar per Minecraft version)"]
    seen = set()
    for i, f in enumerate(files):
        where = "files[" + str(i) + "]: "
        if not isinstance(f, dict) or set(f) != FILE_FIELDS:
            errs.append(where + "needs exactly " + ", ".join(sorted(FILE_FIELDS)))
            continue
        if not MC_RE.match(f["minecraft"]):
            errs.append(where + "minecraft must look like 26.2")
        if f["minecraft"] in seen:
            errs.append(where + "second file for Minecraft " + f["minecraft"])
        seen.add(f["minecraft"])
        if not isinstance(f["size"], int) or not 0 < f["size"] <= MAX_JAR_BYTES:
            errs.append(where + "size must be the jar size in bytes (max 64 MB)")
        if not SHA_RE.match(f["sha256"]):
            errs.append(where + "sha256 must be 64 lowercase hex characters")
        if prefix and (not f["jar"].startswith(prefix) or not f["jar"].endswith(".jar") or ".." in f["jar"]):
            errs.append(where + "jar must be a release asset of the source repo (" + prefix + "<tag>/<file>.jar)")
    return errs


def load_entries() -> list[dict]:
    entries: list[dict] = []
    problems: list[str] = []
    seen: dict[str, str] = {}
    for path in sorted(ENTRIES.glob("*.json")):
        try:
            entry = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            problems.append(path.name + ": invalid JSON (" + str(exc) + ")")
            continue
        if not isinstance(entry, dict):
            problems.append(path.name + ": must be a JSON object")
            continue
        for err in validate(entry, path.name):
            problems.append(path.name + ": " + err)
        if entry.get("id") in seen:
            problems.append(path.name + ": id already used by " + seen[entry["id"]])
        seen[entry.get("id", path.name)] = path.name
        entries.append(entry)
    if problems:
        raise RegistryError("\n".join(problems))
    return entries


def cmd_check(_args) -> None:
    entries = load_entries()
    print("OK: " + str(len(entries)) + " addon(s) valid")


def cmd_build(_args) -> None:
    entries = load_entries()
    entries.sort(key=lambda e: e["name"].lower())
    OUTPUT.write_text(json.dumps({"addons": entries}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("Wrote " + str(OUTPUT) + " (" + str(len(entries)) + " addon(s))")


def read_mod_json(jar: Path) -> dict:
    try:
        with zipfile.ZipFile(jar) as zf:
            return json.loads(zf.read("fabric.mod.json").decode("utf-8"))
    except (KeyError, zipfile.BadZipFile):
        raise RegistryError("not a Fabric mod jar (no fabric.mod.json)")


def cmd_add(args) -> None:
    jar = Path(args.jar)
    if not jar.is_file():
        raise RegistryError("jar not found: " + str(jar))
    meta = read_mod_json(jar)
    if "dih" not in meta.get("entrypoints", {}):
        raise RegistryError("fabric.mod.json has no 'dih' entrypoint, so DIH will not load it as an addon")
    if "dih" not in meta.get("depends", {}):
        raise RegistryError("fabric.mod.json must depend on 'dih' (the templates already do)")

    minecraft = args.minecraft or re.sub(r"[^0-9.]", "", str(meta.get("depends", {}).get("minecraft", "")))
    if not MC_RE.match(minecraft):
        raise RegistryError("could not read the Minecraft version from the jar; pass --minecraft 26.2")
    file_entry = {
        "minecraft": minecraft,
        "jar": args.jar_url,
        "size": jar.stat().st_size,
        "sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
    }

    target = ENTRIES / (meta["id"] + ".json")
    old = json.loads(target.read_text(encoding="utf-8")) if target.exists() else None
    files = [file_entry]
    if old and old.get("version") == meta["version"]:
        files = [f for f in old.get("files", []) if f.get("minecraft") != minecraft] + [file_entry]
        files.sort(key=lambda f: [int(p) for p in f["minecraft"].split(".")])

    authors = meta.get("authors") or ["unknown"]
    author = authors[0] if isinstance(authors[0], str) else authors[0].get("name", "unknown")
    entry = {
        "id": meta["id"],
        "name": meta.get("name", meta["id"]),
        "version": meta["version"],
        "author": args.author or author,
        "summary": args.summary or (old or {}).get("summary") or meta.get("description", "")[:70],
        "description": meta.get("description", ""),
        "apiVersion": args.api_version,
        "license": args.license or meta.get("license") or (old or {}).get("license") or "",
        "categories": args.category or (old or {}).get("categories", []),
        "source": args.source,
        "files": files,
    }
    image = args.image or (old or {}).get("image")
    if image:
        entry["image"] = image
    problems = validate(entry, entry["id"] + ".json")
    if problems:
        raise RegistryError("entry is not valid yet:\n  " + "\n  ".join(problems))
    target.write_text(json.dumps(entry, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    versions = ", ".join(f["minecraft"] for f in files)
    print("Wrote " + str(target) + " (Minecraft " + versions + ")")
    print("Next: python registry/registry.py build, then open a pull request with both files.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="cmd", required=True)
    sub.add_parser("check").set_defaults(fn=cmd_check)
    sub.add_parser("build").set_defaults(fn=cmd_build)
    add = sub.add_parser("add", help="create or extend a registry entry from a built jar")
    add.add_argument("--jar", required=True)
    add.add_argument("--source", required=True, help="GitHub repo URL")
    add.add_argument("--jar-url", required=True, help="GitHub release asset URL of the jar")
    add.add_argument("--summary", help="one line (max 70 chars) shown under the name")
    add.add_argument("--image", help="16:9 PNG/JPG card image URL in your repo, e.g. raw.githubusercontent.com/you/repo/main/card.png")
    add.add_argument("--author", help="override the author shown")
    add.add_argument("--license", help="defaults to the jar's fabric.mod.json license")
    add.add_argument("--api-version", type=int, default=3, help="DIH ApiVersion the addon was built against")
    add.add_argument("--minecraft", help="override the Minecraft version, e.g. 26.2")
    add.add_argument("--category", action="append", choices=sorted(CATEGORIES))
    add.set_defaults(fn=cmd_add)
    args = parser.parse_args()
    try:
        args.fn(args)
    except RegistryError as exc:
        print("[FAIL] " + str(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
