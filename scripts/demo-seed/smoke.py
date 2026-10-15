#!/usr/bin/env python3
"""End-to-end check of seed.py against a fake LibreChat server, so a change to the seeder is
exercised before it meets a real account.

    scripts/demo-seed/smoke.py

The fake answers the routes the seeder calls, asserts the browser-header rules on every request,
and can be told to fail: rate-limit the Nth send, or end a turn's stream with an error once.
The checks are the ones that protect a real account: a refused send stops the run, and a chat
that fails part-way resumes in the same conversation instead of leaving one behind.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from seed import Scenario  # noqa: E402

scenario_keys = [c.key for c in Scenario.load(HERE / "scenario.toml").chats]
UA_PREFIX = "Mozilla/5.0 (Linux; Android 14; Pixel 8)"


class Fake:
    def __init__(self):
        self.users: dict[str, str] = {}
        self.convos: dict[str, list[str]] = {}   # conversationId -> parentMessageIds sent
        self.log: list[tuple[str, str, object]] = []
        self.sends = 0
        self.rate_limit_on_send: int | None = None
        self.stream_error_on_send: int | None = None
        self.header_violations: list[str] = []

    def reset_log(self):
        self.log.clear()

    def chat_sends(self):
        return [entry for entry in self.log
                if entry[0] == "POST" and entry[1].startswith("/api/agents/chat/")]


def make_handler(fake: Fake):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def _body(self):
            n = int(self.headers.get("Content-Length") or 0)
            raw = self.rfile.read(n) if n else b""
            if self.headers.get("Content-Type", "").startswith("application/json"):
                return json.loads(raw)
            return raw

        def _send(self, code, obj):
            data = json.dumps(obj).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def _stream(self, frames):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            for event, payload in frames:
                self.wfile.write(f"event: {event}\ndata: {json.dumps(payload)}\n\n".encode())

        def _handle(self, method):
            body = self._body()
            path = self.path.split("?")[0]
            fake.log.append((method, path, body if isinstance(body, dict) else None))
            if not self.headers.get("User-Agent", "").startswith(UA_PREFIX):
                fake.header_violations.append(f"{path}: UA {self.headers.get('User-Agent')}")
            for banned in ("Origin", "Sec-Fetch-Site"):
                if banned in self.headers:
                    fake.header_violations.append(f"{path}: sent {banned}")

            if path == "/api/auth/login":
                if fake.users.get(body["email"]) != body["password"]:
                    return self._send(404, {"message": "Email does not exist"})
                return self._send(200, {"token": "t", "user": {"_id": "u1"}})
            if path == "/api/auth/register":
                fake.users[body["email"]] = body["password"]
                return self._send(200, {"message": "ok"})
            if not self.headers.get("Authorization"):
                return self._send(401, {"message": "Unauthorized"})
            if path == "/api/convos" and method == "GET":
                return self._send(200, {"conversations": [{"conversationId": c}
                                                          for c in fake.convos]})
            if path == "/api/convos" and method == "DELETE":
                fake.convos.pop(body["arg"]["conversationId"], None)
                return self._send(200, {})
            if path == "/api/endpoints":
                return self._send(200, {"anthropic": {}, "openAI": {}, "agents": {
                    "capabilities": ["artifacts", "web_search"]}})
            if path == "/api/config":
                return self._send(200, {"interface": {}, "webSearch": {"provider": "x"}})
            if path == "/api/agents/tools":
                return self._send(200, [{"pluginKey": "image_gen_oai", "authenticated": True}])
            if path == "/api/agents" and method == "POST":
                return self._send(201, {"id": "agent_" + uuid.uuid4().hex[:6]})
            if path.startswith("/api/agents/chat/stream/"):
                cid = path.rsplit("/", 1)[1]
                if fake.stream_error_on_send == fake.sends:
                    fake.stream_error_on_send = None
                    return self._stream([("error", {"error": "provider fell over"})])
                return self._stream([
                    ("message", {"created": {}}),
                    ("message", {"final": True, "conversation": {"conversationId": cid},
                                 "title": "Auto title",
                                 "responseMessage": {"messageId": "r-" + uuid.uuid4().hex}}),
                ])
            if path.startswith("/api/agents/chat/") and method == "POST":
                fake.sends += 1
                if fake.rate_limit_on_send == fake.sends:
                    return self._send(429, {"message": "Too many messages"})
                cid = body.get("conversationId") or uuid.uuid4().hex
                fake.convos.setdefault(cid, []).append(body["parentMessageId"])
                return self._send(200, {"streamId": cid, "conversationId": cid,
                                        "status": "started"})
            if path == "/api/files":
                return self._send(200, {"file_id": uuid.uuid4().hex, "filename": "x",
                                        "filepath": "/f/x", "type": "image/jpeg"})
            if path == "/api/projects" and method == "POST":
                return self._send(201, {"_id": "proj1"})
            return self._send(200, {})

        def do_GET(self):
            self._handle("GET")

        def do_POST(self):
            self._handle("POST")

        def do_PUT(self):
            self._handle("PUT")

        def do_DELETE(self):
            self._handle("DELETE")

    return Handler


def main() -> int:
    fake = Fake()
    server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(fake))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    failures: list[str] = []

    def check(name, cond):
        print(f"{'ok  ' if cond else 'FAIL'} {name}")
        if not cond:
            failures.append(name)

    with tempfile.TemporaryDirectory() as tmp:
        assets = Path(tmp) / "assets"
        assets.mkdir()
        (assets / "melted-bar.jpg").write_bytes(
            b"\xff\xd8\xff\xc0\x00\x0b\x08\x01\x2c\x01\x90\x03\x00\x00\x00")
        env = {**os.environ,
               "DEMO_SEED_SERVER": f"http://127.0.0.1:{server.server_port}",
               "DEMO_SEED_EMAIL": "demo@example.com", "DEMO_SEED_PASSWORD": "pw-123456",
               "DEMO_SEED_STATE_DIR": str(Path(tmp) / "state")}

        def seed(*extra):
            return subprocess.run(
                [sys.executable, str(HERE / "seed.py"), "--profile",
                 str(HERE / "profile.example.toml"), "--assets", str(assets), "--pace", "0",
                 *extra], env=env, capture_output=True, text=True)

        def state():
            files = list((Path(tmp) / "state").glob("*.json"))
            return json.loads(files[0].read_text()) if files else None

        fake.rate_limit_on_send = 2
        run = seed()
        check("a rate-limited send stops the run", run.returncode == 1
              and "rate-limited" in run.stderr and len(fake.chat_sends()) == 2)
        fake.rate_limit_on_send = None

        run = seed("--only", "mayor-whiskers")
        whiskers_cid = state()["chats"]["mayor-whiskers"]["conversationId"]
        whiskers_sends = len(fake.convos[whiskers_cid])
        check("the album seeds all 14 turns", whiskers_sends == 14
              and state()["chats"]["mayor-whiskers"]["complete"])
        fake.stream_error_on_send = fake.sends + 3
        fake.reset_log()
        run = seed("--only", "mayor-whiskers", "--redo", "mayor-whiskers")
        partial = state()["chats"]["mayor-whiskers"]
        check("a failed turn leaves a resumable record",
              "Run again to resume" in run.stderr and partial["turnsDone"] == 2
              and not partial["complete"] and partial["conversationId"])
        run = seed("--only", "mayor-whiskers")
        resumed = state()["chats"]["mayor-whiskers"]
        parents = fake.convos[resumed["conversationId"]]
        check("the next run resumes the same conversation", "resuming mayor-whiskers" in run.stdout
              and resumed["conversationId"] == partial["conversationId"]
              and resumed["complete"] and resumed["turnsDone"] == 14)
        check("the retried turn continues from the last good reply",
              parents[3] == parents[2] == partial["parent"])

        run = seed()
        check("a full run succeeds", run.returncode == 0)
        seeded = state()["chats"]
        check("chats with missing assets are skipped",
              "whiskers-charter" not in seeded and "skip whiskers-charter" in run.stdout)
        check("every other chat is seeded", all(r["complete"] for r in seeded.values())
              and len(seeded) == len(scenario_keys) - 1)
        fake.reset_log()
        run = seed()
        check("a re-run keeps everything", run.returncode == 0 and not fake.chat_sends())

        fake.convos["stranger"] = [""]
        run = seed()
        check("an account with other conversations is refused",
              run.returncode == 2 and "refusing" in run.stderr)
        del fake.convos["stranger"]

        run = seed("--reset")
        check("reset removes every seeded conversation", run.returncode == 0
              and not fake.convos and state() is None)

    server.shutdown()
    check("every request sent the browser UA and no Origin or Sec-Fetch-Site",
          not fake.header_violations)
    print(f"smoke: {'ok' if not failures else f'{len(failures)} failure(s)'}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
