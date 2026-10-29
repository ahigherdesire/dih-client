"""Build docs.html from COMMANDS.md:  python website/build_docs.py [path/to/docs.html]

With no path it updates website/docs.html here. The live site is the ahigherdesire/dih-client-site repo,
so pass that checkout's docs.html to update it (its assets/docs.css and docs.js are copies of the ones here).

COMMANDS.md is the source. Its shape is fixed: `##` is a system (Basics, Client commands, Pathfinder & AI
commands), `###` a group, `####` one command named in plain text, optional `Aliases:` and `Beta:` lines under it, then
a one-line summary and any tables, code blocks or `#####` sub-headings. Rerun this after editing COMMANDS.md
and commit both files."""
import html
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(ROOT, "..", "COMMANDS.md")
TARGET = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs.html")
TEMPLATE_START = "<!-- docs:start -->"
TEMPLATE_END = "<!-- docs:end -->"


def slug(text):
    text = re.sub(r"[^a-z0-9]+", "-", text.lower())
    return text.strip("-")


def command_id(name):
    if name.startswith("."):
        return "dot-" + slug(name[1:])
    if name.startswith("#"):
        return "hash-" + (slug(name[1:]) or "help-alias")
    return slug(name)


def parse(lines):
    """Return a list of systems: {title, id, intro, groups:[{title, id, intro, entries:[...]}]}."""
    systems, system, group, entry = [], None, None, None
    blocks = None  # where the next block goes
    i = 0
    while i < len(lines):
        line = lines[i]
        m = re.match(r"^(#{1,5}) (.+)$", line)
        if m and not line.startswith("#!"):
            level, text = len(m.group(1)), m.group(2).strip()
            if level == 1:
                blocks = []  # page intro, unused on the site
            elif level == 2:
                system = {"title": text, "id": slug(text), "intro": [], "groups": []}
                systems.append(system)
                group = entry = None
                blocks = system["intro"]
            elif level == 3:
                group = {"title": text, "id": system["id"] + "-" + slug(text), "intro": [], "entries": []}
                system["groups"].append(group)
                entry = None
                blocks = group["intro"]
            elif level == 4:
                entry = {"name": text, "id": command_id(text), "aliases": [], "beta": "", "blocks": []}
                group["entries"].append(entry)
                blocks = entry["blocks"]
            else:
                blocks.append(("h5", text))
            i += 1
            continue
        if line.startswith("```"):
            body = []
            i += 1
            while i < len(lines) and not lines[i].startswith("```"):
                body.append(lines[i])
                i += 1
            blocks.append(("pre", "\n".join(body)))
            i += 1
            continue
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append(split_row(lines[i]))
                i += 1
            blocks.append(("table", rows[0], rows[2:]))
            continue
        if not line.strip():
            i += 1
            continue
        item = re.match(r"^(- |\d+\. )", line)
        if item:
            # a "- " or "1. " list; an indented line continues the item above it
            ordered = item.group(1)[0].isdigit()
            items = []
            while i < len(lines) and (re.match(r"^(- |\d+\. )", lines[i]) or (lines[i].startswith("  ") and items)):
                m = re.match(r"^(- |\d+\. )(.*)$", lines[i])
                if m:
                    items.append(m.group(2).strip())
                else:
                    items[-1] += " " + lines[i].strip()
                i += 1
            blocks.append(("ol" if ordered else "ul", items))
            continue
        para = []
        while i < len(lines) and lines[i].strip() and not re.match(r"^(#{1,5} |```|\||- |\d+\. )", lines[i]):
            para.append(lines[i].strip())
            i += 1
        text = " ".join(para)
        if entry is not None and text.startswith("Aliases:") and not entry["blocks"]:
            entry["aliases"] = re.findall(r"`([^`]+)`", text)
        elif entry is not None and text.startswith("Beta:") and not entry["blocks"]:
            entry["beta"] = text[len("Beta:"):].strip()
        else:
            blocks.append(("p", text))
    return systems


def split_row(row):
    cells, cur, in_code, i = [], "", False, 0
    row = row.strip()[1:-1]
    while i < len(row):
        c = row[i]
        if c == "\\" and row[i + 1:i + 2] == "|":
            cur += "|"
            i += 2
            continue
        if c == "`":
            in_code = not in_code
        if c == "|" and not in_code:
            cells.append(cur.strip())
            cur = ""
        else:
            cur += c
        i += 1
    cells.append(cur.strip())
    return cells


class Renderer:
    def __init__(self, systems):
        self.links = {}
        for system in systems:
            for group in system["groups"]:
                for entry in group["entries"]:
                    for name in [entry["name"]] + entry["aliases"]:
                        self.links.setdefault(name, entry["id"])
        self.current = None

    def code(self, text):
        target = self.links.get(text)
        inner = html.escape(text)
        if target and target != self.current:
            return f'<a class="ref" href="#{target}"><code>{inner}</code></a>'
        return f"<code>{inner}</code>"

    def inline(self, text):
        codes = []

        def stash(m):
            codes.append(self.code(m.group(1)))
            return f"\x00{len(codes) - 1}\x00"

        out = re.sub(r"`([^`]+)`", stash, text)
        out = html.escape(out, quote=False)
        out = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", out)
        out = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', out)
        return re.sub(r"\x00(\d+)\x00", lambda m: codes[int(m.group(1))], out)

    def blocks(self, blocks):
        out = []
        for block in blocks:
            kind = block[0]
            if kind == "p":
                out.append(f"<p>{self.inline(block[1])}</p>")
            elif kind == "h5":
                out.append(f"<h5>{self.inline(block[1])}</h5>")
            elif kind == "pre":
                out.append(f'<pre><code>{html.escape(block[1])}</code></pre>')
            elif kind in ("ul", "ol"):
                items = "".join(f"<li>{self.inline(text)}</li>" for text in block[1])
                out.append(f"<{kind}>{items}</{kind}>")
            elif kind == "table":
                head = "".join(f"<th scope=\"col\">{self.inline(c)}</th>" for c in block[1])
                rows = "".join(
                    "<tr>" + "".join(f"<td>{self.inline(c)}</td>" for c in row) + "</tr>" for row in block[2])
                out.append(f'<div class="table"><table><thead><tr>{head}</tr></thead><tbody>{rows}</tbody></table></div>')
        return "\n".join(out)

    def entry(self, entry):
        self.current = entry["id"]
        name = entry["name"]
        prefix, rest = (name[0], name[1:]) if name[0] in ".#" else ("", name)
        blocks = list(entry["blocks"])
        summary = ""
        if blocks and blocks[0][0] == "p":
            summary = blocks.pop(0)[1]
        aliases = ""
        if entry["aliases"]:
            aliases = ('<p class="aka"><span>Also</span> '
                       + " ".join(f"<code>{html.escape(a)}</code>" for a in entry["aliases"]) + "</p>")
        if entry["beta"]:
            aliases = (f'<p class="aka"><span class="beta-tag">Beta</span> {html.escape(entry["beta"])}</p>\n' + aliases)
        attrs = (f'id="{entry["id"]}" data-name="{html.escape(name)}" '
                 f'data-aliases="{html.escape(" ".join(entry["aliases"]))}"'
                 + (' data-beta="1"' if entry["beta"] else ""))
        out = (f'<article class="cmd" {attrs}>\n'
               f'<h4><a class="self" href="#{entry["id"]}"><span class="pfx">{html.escape(prefix)}</span>'
               f'{html.escape(rest)}</a></h4>\n{aliases}\n'
               f'<p class="sum">{self.inline(summary)}</p>\n{self.blocks(blocks)}\n</article>')
        self.current = None
        return out

    def page(self, systems):
        body, nav = [], []
        for system in systems:
            mark = {"client-commands": ".", "pathfinder-ai-commands": "#"}.get(system["id"], "")
            badge = f'<span class="sys-mark" aria-hidden="true">{mark}</span>' if mark else ""
            body.append(f'<section class="sys" id="{system["id"]}" aria-labelledby="{system["id"]}-h">')
            body.append(f'<h2 id="{system["id"]}-h">{badge}{html.escape(system["title"])}</h2>')
            body.append(self.blocks(system["intro"]))
            nav.append(f'<div class="toc-sys"><a class="toc-head" href="#{system["id"]}">{badge}{html.escape(system["title"])}</a>')
            for group in system["groups"]:
                body.append(f'<section class="grp" id="{group["id"]}">')
                body.append(f'<h3>{html.escape(group["title"])}</h3>')
                body.append(self.blocks(group["intro"]))
                if group["entries"]:
                    nav.append(f'<p class="toc-grp">{html.escape(group["title"])}</p><ul>')
                    for entry in group["entries"]:
                        body.append(self.entry(entry))
                        n = entry["name"]
                        pfx, rest = (n[0], n[1:]) if n[0] in ".#" else ("", n)
                        nav.append(f'<li><a href="#{entry["id"]}" class="mono{" beta-tag" if entry["beta"] else ""}"><span class="pfx">{html.escape(pfx)}</span>'
                                   f'{html.escape(rest)}</a></li>')
                    nav.append("</ul>")
                else:
                    nav.append(f'<a class="toc-topic" href="#{group["id"]}">{html.escape(group["title"])}</a>')
                body.append("</section>")
            nav.append("</div>")
            body.append("</section>")
        # module guides (AutoTrade) aren't commands, so only . and # entries count
        count = sum(1 for s in systems for g in s["groups"] for e in g["entries"] if e["name"][0] in ".#")
        return "\n".join(body), "\n".join(nav), count


def main():
    lines = open(SOURCE, encoding="utf-8").read().splitlines()
    systems = parse(lines)
    body, nav, count = Renderer(systems).page(systems)
    page = open(TARGET, encoding="utf-8").read()
    start, end = page.index(TEMPLATE_START), page.index(TEMPLATE_END)
    page = page[:start + len(TEMPLATE_START)] + "\n" + body + "\n" + page[end:]
    nstart, nend = page.index("<!-- toc:start -->"), page.index("<!-- toc:end -->")
    page = page[:nstart + len("<!-- toc:start -->")] + "\n" + nav + "\n" + page[nend:]
    page = re.sub(r'<b id="cmd-count">\d*</b>', f'<b id="cmd-count">{count}</b>', page)
    open(TARGET, "w", encoding="utf-8", newline="\n").write(page)
    print(f"docs.html: {count} commands")


if __name__ == "__main__":
    main()
