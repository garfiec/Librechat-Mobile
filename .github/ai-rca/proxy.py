#!/usr/bin/env python3
"""Loopback proxy to the Anthropic Messages API that holds the API key.

  proxy.py PORT MODEL[,MODEL…]        (env: ANTHROPIC_API_KEY)

The agent runs as another user with ANTHROPIC_BASE_URL pointing here and no key of its own, so
nothing it runs can read or print the key. Only POST /v1/messages and its count_tokens are forwarded,
only for the listed models, and never with server-side tools, MCP servers or URL sources (the API
would fetch those).
"""
import http.client, json, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

KEY = os.environ.pop("ANTHROPIC_API_KEY")
UPSTREAM = "api.anthropic.com"
MODELS = set(sys.argv[2].split(","))
DROP = {"host", "x-api-key", "authorization", "connection", "content-length", "accept-encoding"}


def no_duplicates(pairs):
    keys = [k for k, _ in pairs]
    if len(keys) != len(set(keys)):
        raise ValueError("duplicate key")
    return dict(pairs)


def fetches_url(node):
    """True if any content block asks the API to fetch a URL (image/document sources)."""
    if isinstance(node, dict):
        source = node.get("source")
        if node.get("type") in ("image", "document") and not (
                isinstance(source, dict) and source.get("type") in ("base64", "text")):
            return True
        return any(fetches_url(v) for v in node.values())
    if isinstance(node, list):
        return any(fetches_url(v) for v in node)
    return False


class Proxy(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_POST(self):
        if self.path.split("?")[0] not in ("/v1/messages", "/v1/messages/count_tokens"):
            print(f"refused path {self.path[:80]!r}", file=sys.stderr, flush=True)
            self.send_error(403)
            return
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0))
        try:
            req = json.loads(body, object_pairs_hook=no_duplicates)
        except ValueError:
            req = None
        if not isinstance(req, dict) or req.get("model") not in MODELS or "mcp_servers" in req or any(
                isinstance(t, dict) and "type" in t and not t["type"].startswith("custom")
                for t in req.get("tools") or []) or fetches_url(req.get("messages")) or fetches_url(req.get("system")):
            model = req.get("model") if isinstance(req, dict) else None
            print(f"refused body model={str(model)[:60]!r}", file=sys.stderr, flush=True)
            self.send_error(403)
            return
        headers = {k: v for k, v in self.headers.items() if k.lower() not in DROP}
        headers["x-api-key"] = KEY
        upstream = http.client.HTTPSConnection(UPSTREAM, timeout=600)
        upstream.request("POST", self.path, body=body, headers=headers)
        resp = upstream.getresponse()
        self.send_response(resp.status)
        for k, v in resp.getheaders():
            if k.lower() not in ("transfer-encoding", "connection", "content-length"):
                self.send_header(k, v)
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        while chunk := resp.read1(65536):
            self.wfile.write(b"%x\r\n%s\r\n" % (len(chunk), chunk))
            self.wfile.flush()
        self.wfile.write(b"0\r\n\r\n")
        upstream.close()

    def do_GET(self):
        self.send_error(403)

    def log_message(self, *args):
        pass


ThreadingHTTPServer(("127.0.0.1", int(sys.argv[1])), Proxy).serve_forever()
