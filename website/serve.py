"""Serve the DIH Client site locally:  python website/serve.py  [port]  ->  http://localhost:8080/"""
import http.server
import os
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))


class SiteHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

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
