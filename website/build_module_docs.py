"""Generate MODULES.md and BETA-CHANGES.md from the client source:
python website/build_module_docs.py <beta source> [<stable source>]

Each source is a folder holding src/main/java/dihclient, such as `git archive origin/beta src | tar -x -C dir`.
Modules missing from the stable source are marked Beta. Then run build_docs.py, which adds both files to /docs.
Settings are read from the code as written, so a setting built in a loop (a colour per ore) can't be listed; the
note for that module says so."""
import glob
import os
import re
import sys

CATS = {"COMBAT": "Combat", "MOVEMENT": "Movement", "PLAYER": "Player", "MISC": "Misc", "RENDER": "Render"}
ROOT = os.path.dirname(os.path.abspath(__file__))
# ids that are constants defined in another file
IDS = {"PanicMode": "hide", "AnchorAura": "anchor-aura"}
BASE = "SprintResetModule.java"  # WTap and STap share its settings
SUBS = ("WTapModule.java", "STapModule.java")
SKIP_NOTE = "A few of its settings are made per item in the code and aren't listed here: {}. The module menu shows them."

# What the settings table can't say, from the release notes. Keep these short and factual.
NOTES = {
    "AnchorAura": "**Speed** is **Instant** by default: it places, charges and detonates an anchor in one tick, with "
                  "**Step Delay** in ticks. **Smooth** keeps human-like turning and millisecond delays. Instant flags "
                  "anticheats.",
    "ArrowDamage": "Bow arrows fly at sniper speed and hit far harder. As you let go, the server is told you're moving "
                   "fast along your aim, and the arrow takes that speed. Shots fly almost straight, so aim right at "
                   "the target; Trajectories and BowAimAssist show and aim the boosted shot. Crossbows aren't "
                   "affected. It flags anticheats and only shows its real effect on a server.",
    "AutoMace": "**Nearby** aim smashes a target below you that's in reach and in sight, without turning your view. "
                "**Crosshair** only hits what you look at. Every drop counts from its own top, so it smashes again "
                "on the way back down after a Wind Burst launch.",
    "AutoTrade": "See the full guide under Guides: `AutoTrade`.",
    "BedESP": "Colours the defence around each nearby bed by layer. **Fastest Path** highlights the quickest way in, "
              "counting walking and mining time with the best tool in your hotbar (enchantments and effects aren't "
              "counted). **HUD Summary** shows the nearest bed above your hotbar, and each bed gets a floating label "
              "with the team, break time, distance and what the defence is made of.",
    "Clutch": "Places blocks back under you when you're knocked toward the void, using any full block in your hotbar. "
              "**Clutch With: Water** MLGs a water bucket; **Auto** uses water when it will catch you and blocks "
              "otherwise, and blocks in the Nether, where water would vanish. It turns your camera and can flag "
              "anticheats.",
    "Criticals": "**Packet** crits every hit at once but anticheats flag it. **Jump** really jumps and hits on the way "
                 "down, which is plain movement but slower.",
    "CrystalAura": "**Speed** is **Instant** by default: it looks at its spot and clicks in the same tick, with **Place "
                   "Delay** and **Break Delay** in ticks. **Smooth** keeps human-like turning and millisecond delays. "
                   "Instant flags anticheats.",
    "Danger": "Warns about arrows, fireballs and thrown items that will hit you: time to impact and direction under "
              "the crosshair, a ping, and the path drawn in the world. **Deflect Fireballs** is off by default; with "
              "**Return To Sender** on, it turns to the fireball so it flies back. Deflecting turns your camera and "
              "can flag anticheats.",
    "GIF": "Shows a GIF, PNG or JPEG on your HUD. **Image Path** is a full path or one inside `.minecraft`. **Cape** "
           "and **Shield** turn the image into an animated cape and shield on your own player. Leave **Cape Image** "
           "and **Shield Image** blank to use Image Path. Only you see them.",
    "KillAura": "It leaves your own tamed pets alone by default (**Ignore Pets: Yours**). Set that to Off for the old "
                "behaviour. **PerfectHit** times hits for when damage immunity ends.",
    "MaceKill": "Turns every mace hit into a smash by telling the server you fell first, up to 20 blocks and less "
                "under a roof. A smash the server turns down leaves you that fall, so by default it skips targets "
                "still flashing from a hit. It flags anticheats.",
    "PearlCharge": "Throws an ender pearl and pops a wind charge together to launch you, straight up or toward where "
                   "you look. Bind a key and tap to fire. It needs an ender pearl and a wind charge on your hotbar.",
    "Reach": "Raises entity reach up to 6 and block reach up to 5.5. A vanilla server allows a little over the normal "
             "range; anticheats check tighter.",
    "Schematics": "See the full guide under `.schem`.",
    "SpearKill": "**Poke**, the default, gives huge charge hits while you stand still or walk: each tick a target is in "
                 "the spear's reach, the server is told you just charged at it. **Rush** flies you at the target. "
                 "Both flag anticheats.",
    "Teams": "This is the friends list `.friend` manages. It can also group players by name colour or armour dye.",
    "Viewmodel": "**Z (Depth)** moves the item forward and back. **Size** scales it.",
    "Xray": "OreSim, its ore predictor, needs a world seed; `.oresim` explains what it's doing and `#seedinput` shares "
            "the seed with `#seedmap`.",
}


def strip_comments(t):
    res, i, n, q = [], 0, len(t), None
    while i < n:
        c = t[i]
        if q:
            res.append(c)
            if c == "\\":
                res.append(t[i + 1])
                i += 2
                continue
            if c == q:
                q = None
            i += 1
        elif c in "\"'":
            q = c
            res.append(c)
            i += 1
        elif t.startswith("//", i):
            while i < n and t[i] != "\n":
                i += 1
        elif t.startswith("/*", i):
            i = t.index("*/", i) + 2
        else:
            res.append(c)
            i += 1
    return "".join(res)


def close(t, i):
    """t[i] opens a bracket; returns the index of its partner."""
    depth, q = 0, None
    while i < len(t):
        c = t[i]
        if q:
            if c == "\\":
                i += 1
            elif c == q:
                q = None
        elif c in "\"'":
            q = c
        elif c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def split_args(s):
    args, cur, depth, q, i = [], "", 0, None, 0
    while i < len(s):
        c = s[i]
        if q:
            cur += c
            if c == "\\":
                cur += s[i + 1]
                i += 1
            elif c == q:
                q = None
        elif c in "\"'":
            q = c
            cur += c
        elif c in "([{":
            depth += 1
            cur += c
        elif c in ")]}":
            depth -= 1
            cur += c
        elif c == "," and depth == 0:
            args.append(cur.strip())
            cur = ""
        else:
            cur += c
        i += 1
    if cur.strip():
        args.append(cur.strip())
    return args


STRING = r'"(?:[^"\\]|\\.)*"'


def literal(a):
    """The text a Java expression spells, joining "a" + "b"; None if it isn't all literals."""
    parts = re.findall(STRING, a)
    if not parts or re.sub(STRING, "", a).replace("+", "").strip():
        return None
    text = "".join(p[1:-1] for p in parts)
    return (text.replace('\\"', '"').replace("\\\\", "\\").replace("\\n", " ")
            .replace("\\u00b0", "°").replace("\\u2013", "–").replace("\\u00d7", "×"))


def num(a):
    a = a.strip().rstrip("fFdDL")
    return a if re.fullmatch(r"-?\d+(\.\d+)?", a) else None


def extract(src):
    root = os.path.join(src, "src", "main", "java", "dihclient")
    modules, dynamic = [], {}
    for f in sorted(glob.glob(os.path.join(root, "**", "*.java"), recursive=True)):
        rel = os.path.relpath(f, root)
        if rel.startswith(("api" + os.sep, "gui" + os.sep)):
            continue
        name = os.path.basename(f)
        t = strip_comments(open(f, encoding="utf-8").read())
        if "Setting(" not in t and "ModuleCategory." not in t and name not in SUBS:
            continue
        marks = []
        for m in re.finditer(r"super\(\s*(\"|[A-Za-z_.]+\s*,)", t):
            i = t.index("(", m.start())
            args = split_args(t[i + 1:close(t, i)])
            cat = next((CATS[c.split(".")[-1]] for c in args if "ModuleCategory" in c and c.split(".")[-1] in CATS), None)
            if name in SUBS and len(args) >= 3:
                mod = {"id": literal(args[0]), "name": literal(args[1]), "category": "Combat", "desc": literal(args[2]),
                       "settings": [], "file": name}
            elif cat and len(args) >= 3 and literal(args[1]):
                label = literal(args[1])
                const = re.search(r"\b" + re.escape(args[0]) + r"\s*=\s*(" + STRING + ")", t)
                ident = literal(args[0]) or (literal(const.group(1)) if const else None) or IDS.get(label, label.lower())
                mod = {"id": ident, "name": label, "category": cat,
                       "desc": (literal(args[3]) if len(args) > 3 else "") or "", "settings": [], "file": name}
            else:
                continue
            marks.append((m.start(), mod))
            modules.append(mod)
        if name == BASE:
            mod = {"id": "", "name": BASE, "category": "Combat", "desc": "", "settings": [], "file": BASE}
            marks.append((0, mod))
            modules.append(mod)
        starts = [c.start() for c in re.finditer(r"\bclass\s+\w+", t)]
        pattern = r"new\s+(Bool|Int|Double|Choice|Color|String|Range|Action|Keybind|StringList|RegistryList|PacketList)Setting\s*\("
        for m in re.finditer(pattern, t):
            i = t.index("(", m.start())
            e = close(t, i)
            args = split_args(t[i + 1:e])
            # settings may be fields above the constructor, so go by the enclosing class
            here = max([c for c in starts if c < m.start()], default=0)
            after = min([c for c in starts if c > m.start()], default=len(t))
            same = [k for k in marks if here <= k[0] < after]
            before = [k for k in marks if k[0] < m.start()]
            if not same and not before:
                continue
            mod = (same or before)[-1 if not same else 0][1]
            j, chain = e + 1, ""
            while True:
                c = re.match(r"\s*\.(\w+)\s*\(", t[j:])
                if not c:
                    break
                q = close(t, j + c.end() - 1)
                chain += t[j:q + 1]
                j = q + 1
                if c.group(1) == "build":
                    break
            kind = m.group(1)
            key, label = (literal(args[0]), literal(args[1])) if len(args) > 1 else (None, None)
            if key is None or label is None:
                dynamic.setdefault(mod["name"], 0)
                dynamic[mod["name"]] += 1
                continue
            group = re.search(r"\.group\(\s*(" + STRING + ")", chain)
            desc = re.search(r"\.description\(\s*((?:" + STRING + r"\s*\+?\s*)+)\)", chain)
            unit = re.search(r"\.unit\(\s*(" + STRING + ")", chain)
            s = {"kind": kind, "key": key, "label": label, "group": literal(group.group(1)) if group else "General",
                 "desc": literal(desc.group(1)) if desc else "", "default": "", "range": "", "choices": [],
                 "unit": literal(unit.group(1)) if unit else ""}
            if kind == "Bool" and len(args) > 2:
                s["default"] = {"true": "on", "false": "off"}.get(args[2], "")
            elif kind in ("Int", "Double"):
                values = [num(a) for a in args[2:5]]
                s["default"] = values[0] or "" if values else ""
                if len(values) > 2 and values[1] and values[2]:
                    s["range"] = f"{values[1]} to {values[2]}"
            elif kind == "Choice" and len(args) > 2:
                s["default"] = literal(args[2]) or ""
                s["choices"] = [literal(a) for a in args[3:] if literal(a)]
            elif kind == "String" and len(args) > 2:
                s["default"] = literal(args[2]) or ""
            mod["settings"].append(s)
    shared = next((m for m in modules if m["name"] == BASE), None)
    if shared:
        modules.remove(shared)
        for m in modules:
            if m["file"] in SUBS:
                m["settings"] = shared["settings"] + m["settings"]
    unique = {}
    for m in modules:
        if m["name"] not in unique:
            unique[m["name"]] = m
    return list(unique.values()), dynamic


def cell(text):
    return (text or "").replace("|", "\\|").replace("\n", " ").strip()


def module_markdown(mods, dynamic, stable):
    out = ["# DIH Client modules", "",
           "Every module in 5.1 beta 7g.3 and every setting it has. Generated from the source by "
           "`website/build_module_docs.py`.", "", "## Module reference", "",
           f"All {len(mods)} modules, with their settings. Open the menu with Right Shift, or use `.toggle <module>` and "
           "`.setting <module> <key> <value>` with the ids below. Search finds settings too.", ""]
    for cat in CATS.values():
        out += [f"### {cat}", ""]
        for m in sorted((m for m in mods if m["category"] == cat), key=lambda x: x["name"].lower()):
            out += [f"#### {m['name']}", ""]
            if stable is not None and m["name"] not in stable:
                out += ["Beta: 5.1 beta only, not in 5.0.1 stable.", ""]
            out += [(m["desc"] or f"The {m['name']} module.").rstrip(".") + ".", ""]
            note = NOTES.get(m["name"])
            if note:
                out += [note, ""]
            out += [f"`.toggle {m['id']}`", ""]
            if m["name"] in dynamic:
                what = {"Xray": "a colour per ore", "StorageESP": "a filter per structure",
                        "SpawnerESP": "a colour per target"}.get(m["name"], "per-item options")
                out += [SKIP_NOTE.format(what), ""]
            if not m["settings"]:
                continue
            out += [f"##### Settings ({len(m['settings'])})", ""]
            groups = {}
            for s in m["settings"]:
                groups.setdefault(s["group"], []).append(s)
            for group, rows in groups.items():
                if len(groups) > 1:
                    out += [f"**{group}**", ""]
                out += ["| Setting | Default | What it does |", "|---|---|---|"]
                for s in rows:
                    default = s["default"]
                    if s["kind"] in ("Color", "Keybind", "Action", "StringList", "RegistryList", "PacketList", "Range") or default == "":
                        default = {"Color": "colour", "Keybind": "unbound", "Action": "button"}.get(s["kind"], "-")
                    elif s["unit"] and s["kind"] in ("Int", "Double"):
                        default += " " + s["unit"]
                    desc = s["desc"]
                    extra = []
                    if s["range"]:
                        extra.append(s["range"] + (" " + s["unit"] if s["unit"] else ""))
                    if s["choices"]:
                        extra.append("Choices: " + ", ".join(s["choices"]))
                    if extra:
                        desc = (desc.rstrip(".") + ". " if desc else "") + ". ".join(extra) + "."
                    out.append(f"| {cell(s['label'])} `{s['key']}` | {cell(default)} | {cell(desc)} |")
                out.append("")
    return "\n".join(out)


def changes_markdown(src):
    notes = os.path.join(src, ".github", "release-notes")
    order = ["7g.3", "7g.2", "7g.1", "7g", "7f", "7e", "7d", "7c", "7a", "5b", "5a", "3", "2"]
    out = ["# DIH Client beta changes", "", "## What's new in the beta", "",
           "The 5.1 betas with release notes, newest first. Recent betas are Fabric only, on Minecraft 26.2 and 26.3; "
           "NeoForge and Forge stay on beta 3.", ""]
    for v in order:
        path = os.path.join(notes, f"5.1-beta.{v}.md")
        if not os.path.isfile(path):
            continue
        lines = open(path, encoding="utf-8").read().replace("\r\n", "\n").strip().split("\n")
        out += [f"### {lines[0]}", ""]
        for line in lines[1:]:
            if line.startswith("SHA-256"):
                break
            if line.startswith(("Fabric only", "Still a pre-release", "Still to come", "Not play-tested", "ArrowDamage, SpearKill Poke",
                                "real effect on a server", "release.", "Clutch and Danger's deflect")):
                continue
            out.append("- " + line[4:] if line.startswith("  - ") else line)
        out.append("")
    return "\n".join(re.sub(r"\n{3,}", "\n\n", "\n".join(out)).split("\n"))


def main():
    beta = sys.argv[1]
    mods, dynamic = extract(beta)
    stable = {m["name"] for m in extract(sys.argv[2])[0]} if len(sys.argv) > 2 else None
    top = os.path.join(ROOT, "..")
    open(os.path.join(top, "MODULES.md"), "w", encoding="utf-8", newline="\n").write(module_markdown(mods, dynamic, stable))
    open(os.path.join(top, "BETA-CHANGES.md"), "w", encoding="utf-8", newline="\n").write(changes_markdown(beta))
    print(len(mods), "modules,", sum(len(m["settings"]) for m in mods), "settings,",
          sum(1 for m in mods if stable is not None and m["name"] not in stable), "beta-only")


if __name__ == "__main__":
    main()
