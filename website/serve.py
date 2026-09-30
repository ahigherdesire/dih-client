"""Serve the DIH Client site locally:  python website/serve.py  [port]  ->  http://localhost:8080/

Static files only. /dl/<channel>/<build> redirects to GitHub like the live Worker (worker/index.js) but
counts nothing, and there is no /api, so the download count stays hidden. `npx wrangler dev` runs the
real Worker with a local D1."""
import http.server
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))


def jar_url(channel, build):
    text = open(os.path.join(ROOT, "assets", "release.js"), encoding="utf-8").read()
    release = json.loads(text[text.index("{", text.index("DIH_RELEASE")):text.rindex("}") + 1])
    b = release.get("channels", {}).get(channel, {}).get("builds", {}).get(build)
    if b:
        return release["repo"] + "/releases/download/" + release["channels"][channel]["tag"] + "/" + b["file"]
    return None


class SiteHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def send_head(self):
        m = re.fullmatch(r"/dl/([a-z0-9]+)/([a-z0-9.]+-[a-z]+)/?", self.path.split("?")[0])
        url = m and jar_url(m.group(1), m.group(2))
        if url:
            self.send_response(302)
            self.send_header("Location", url)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return None
        return super().send_head()

    def send_error(self, code, message=None, explain=None):
        page = os.path.join(ROOT, "404.html")
        if code == 404 and os.path.isfile(page):
            body = open(page, "rb").read()
            self.send_response(404)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(body)
            return
        super().send_error(code, message, explain)


port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
with http.server.ThreadingHTTPServer(("", port), SiteHandler) as httpd:
    print(f"DIH Client site on http://localhost:{port}/  (Ctrl+C to stop)")
    httpd.serve_forever()
