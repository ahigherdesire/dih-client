"""Serve the DIH Client site locally:  python website/serve.py  [port]  ->  http://localhost:8080/"""
import functools
import http.server
import os
import sys

port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
root = os.path.dirname(os.path.abspath(__file__))
handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root)
with http.server.ThreadingHTTPServer(("", port), handler) as httpd:
    print(f"DIH Client site on http://localhost:{port}/  (Ctrl+C to stop)")
    httpd.serve_forever()
