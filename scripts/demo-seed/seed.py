#!/usr/bin/env python3
"""Seed a LibreChat account with demo conversations for screenshots and recordings.

The conversations are real: every prompt is sent to a real model through the same API the app
uses, and nothing in a reply is written by hand. What to send lives in scenario.toml, written
against model *roles* (smart, fast, reasoning, ...). A per-server profile maps each role to a
real endpoint and model, so one scenario seeds any server. See README.md.

    scripts/demo-seed/seed.py --self-test
    scripts/demo-seed/seed.py --profile my-server.toml --dry-run
    DEMO_SEED_EMAIL=… DEMO_SEED_PASSWORD=… \\
        scripts/demo-seed/seed.py --server https://chat.example.com --profile my-server.toml
    … --only car-wash --redo car-wash        # regenerate one chat until the joke lands
    … --reset                                # delete everything this seeder created

Run it against a throwaway account. The stock server soft-bans a client on its first
non-browser User-Agent and rate-limits message bursts, and a ban deletes the account's sessions.
Every request goes through Client._request, which always sends the app's browser UA; sends are
paced (--pace).
"""

from __future__ import annotations

import argparse
import http.client
import json
import mimetypes
import os
import struct
import sys
import time
import tomllib
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass, field
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_SCENARIO = HERE / "scenario.toml"
STATE_DIR = Path(os.environ.get("DEMO_SEED_STATE_DIR") or HERE / ".state")

# Must match applyBrowserDefaults in core/network/.../client/LibreChatHttpClient.kt. The server's
# ua-parser middleware soft-bans the first non-browser UA it sees. Never send Origin or
# Sec-Fetch-Site: from v0.8.8-rc3 the login route rejects a request carrying either.
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
)
NO_PARENT = "00000000-0000-0000-0000-000000000000"
BUILT_IN_ENDPOINTS = {
    "openAI", "azureOpenAI", "google", "anthropic", "bedrock",
    "assistants", "azureAssistants", "agents",
}
IMAGE_TOOL_PLACEHOLDER = "@image"
KNOWN_REQUIREMENTS = {"image_gen", "web_search", "artifacts", "attachments", "compare"}


class SeedError(Exception):
    pass


class Refusal(SeedError):
    """The server answered a send with a non-JSON body: a rate limit, ban or moderation page.
    Continuing would only dig deeper, so the whole run stops."""


class Paused(SeedError):
    """The run stopped for a clarifying question or a tool approval. Seeded chats must not use
    agents that can pause; those agents are for live capture only."""


# ----------------------------------------------------------------------------- scenario + profile

@dataclass
class Role:
    name: str
    endpoint: str
    model: str
    params: dict = field(default_factory=dict)
    endpoint_type: str | None = None


@dataclass
class Profile:
    roles: dict[str, Role]
    image_tool: str | None

    @staticmethod
    def load(path: Path) -> "Profile":
        data = tomllib.loads(path.read_text())
        roles = {}
        for name, r in data.get("roles", {}).items():
            if "endpoint" not in r or "model" not in r:
                raise SeedError(f"profile role '{name}' needs endpoint and model")
            roles[name] = Role(name, r["endpoint"], r["model"], dict(r.get("params", {})),
                               r.get("endpoint_type"))
        return Profile(roles, data.get("image", {}).get("tool"))


@dataclass
class Turn:
    text: str
    attachments: list[str] = field(default_factory=list)


@dataclass
class Chat:
    key: str
    title: str | None
    role: str | None
    agent: str | None
    compare_with: str | None
    turns: list[Turn]
    requires: list[str]
    web_search: bool
    artifacts: bool
    tags: list[str]
    project: str | None
    pinned: bool
    note: str | None


@dataclass
class AgentDef:
    key: str
    name: str
    description: str
    instructions: str
    role: str
    tools: list[str]
    artifacts: bool
    seed: bool
    capture_prompt: str | None


@dataclass
class Scenario:
    name: str
    avatar: str | None
    starred_roles: list[str]
    starred_agents: list[str]
    agents: dict[str, AgentDef]
    chats: list[Chat]

    @staticmethod
    def load(path: Path) -> "Scenario":
        data = tomllib.loads(path.read_text())
        acct = data.get("account", {})
        agents = {}
        for a in data.get("agents", []):
            agents[a["key"]] = AgentDef(
                key=a["key"], name=a["name"], description=a.get("description", ""),
                instructions=a.get("instructions", ""), role=a["role"],
                tools=list(a.get("tools", [])), artifacts=a.get("artifacts", False),
                seed=a.get("seed", True), capture_prompt=a.get("capture_prompt"),
            )
        chats = []
        for c in data.get("chats", []):
            turns = [Turn(t["text"], list(t.get("attachments", []))) for t in c.get("turns", [])]
            chats.append(Chat(
                key=c["key"], title=c.get("title"), role=c.get("role"), agent=c.get("agent"),
                compare_with=c.get("compare_with"), turns=turns,
                requires=list(c.get("requires", [])), web_search=c.get("web_search", False),
                artifacts=c.get("artifacts", False), tags=list(c.get("tags", [])),
                project=c.get("project"), pinned=c.get("pinned", False), note=c.get("note"),
            ))
        return Scenario(acct.get("name", "Demo"), acct.get("avatar"),
                        list(acct.get("starred_roles", [])), list(acct.get("starred_agents", [])),
                        agents, chats)

    def validate(self, profile: Profile | None) -> list[str]:
        """Every problem found, so one run reports them all."""
        problems = []
        keys = [c.key for c in self.chats]
        for dup in {k for k in keys if keys.count(k) > 1}:
            problems.append(f"duplicate chat key '{dup}'")
        roles = profile.roles if profile else None
        for a in self.agents.values():
            if roles is not None and a.role not in roles:
                problems.append(f"agent '{a.key}': role '{a.role}' is not in the profile")
            if IMAGE_TOOL_PLACEHOLDER in a.tools and profile and not profile.image_tool:
                problems.append(f"agent '{a.key}' uses {IMAGE_TOOL_PLACEHOLDER} but the profile "
                                "has no [image] tool")
        for c in self.chats:
            where = f"chat '{c.key}'"
            if bool(c.role) == bool(c.agent):
                problems.append(f"{where}: set exactly one of role or agent")
            if c.agent:
                agent = self.agents.get(c.agent)
                if agent is None:
                    problems.append(f"{where}: unknown agent '{c.agent}'")
                elif not agent.seed:
                    problems.append(f"{where}: agent '{c.agent}' is capture-only (seed = false)")
                elif "ask_user_question" in agent.tools:
                    problems.append(f"{where}: agent '{c.agent}' can pause to ask a question, "
                                    "which would stall the seed")
            for r in (c.role, c.compare_with):
                if r and roles is not None and r not in roles:
                    problems.append(f"{where}: role '{r}' is not in the profile")
            if not c.turns:
                problems.append(f"{where}: no turns")
            for req in c.requires:
                if req not in KNOWN_REQUIREMENTS:
                    problems.append(f"{where}: unknown requirement '{req}'")
            if c.compare_with and len(c.turns) != 1:
                problems.append(f"{where}: comparison chats take exactly one turn")
        for r in self.starred_roles:
            if roles is not None and r not in roles:
                problems.append(f"starred role '{r}' is not in the profile")
        for a in self.starred_agents:
            if a not in self.agents:
                problems.append(f"starred agent '{a}' is not defined")
        return problems


# ----------------------------------------------------------------------------- payloads

def endpoint_type_for(endpoint: str, endpoints_config: dict, override: str | None = None) -> str:
    if override:
        return override
    declared = (endpoints_config.get(endpoint) or {}).get("type")
    if declared:
        return declared
    return endpoint if endpoint in BUILT_IN_ENDPOINTS else "custom"


def chat_body(
    *, text: str, role: Role | None, agent_id: str | None, endpoints_config: dict,
    conversation_id: str | None, parent_message_id: str | None, files: list[dict],
    web_search: bool, artifacts: bool, compare: Role | None, timezone: str,
    message_id: str | None = None, client_request_id: str | None = None,
) -> tuple[str, dict]:
    """The (endpoint, body) for POST /api/agents/chat/{endpoint}, shaped as ChatPayloadBuilder
    builds it in core/data."""
    if agent_id:
        endpoint = "agents"
        body: dict = {"endpoint": "agents", "endpointType": "agents", "agent_id": agent_id}
    else:
        assert role is not None
        endpoint = role.endpoint
        cfg = endpoints_config.get(endpoint) or {}
        body = {
            "endpoint": endpoint,
            "endpointType": endpoint_type_for(endpoint, endpoints_config, role.endpoint_type),
            "model": role.model,
            "modelDisplayLabel": cfg.get("modelDisplayLabel") or endpoint,
        }
        body.update(role.params)
        ephemeral = {}
        if web_search:
            ephemeral["web_search"] = True
            body["web_search"] = True
        if artifacts:
            ephemeral["artifacts"] = "default"
        if ephemeral:
            body["ephemeralAgent"] = ephemeral
    body.update({
        "text": text,
        "parentMessageId": parent_message_id or NO_PARENT,
        "messageId": message_id or str(uuid.uuid4()),
        "clientRequestId": client_request_id or str(uuid.uuid4()),
        "timezone": timezone,
    })
    if conversation_id:
        body["conversationId"] = conversation_id
    if files:
        body["files"] = files
    if compare:
        ccfg = endpoints_config.get(compare.endpoint) or {}
        body["addedConvo"] = {
            "conversationId": conversation_id,
            "parentMessageId": parent_message_id or NO_PARENT,
            "endpoint": compare.endpoint,
            "endpointType": endpoint_type_for(compare.endpoint, endpoints_config,
                                              compare.endpoint_type),
            "modelDisplayLabel": ccfg.get("modelDisplayLabel") or compare.endpoint,
            "model": compare.model,
            **compare.params,
        }
    return endpoint, body


def agent_body(agent: AgentDef, role: Role, image_tool: str | None) -> dict:
    tools = [image_tool if t == IMAGE_TOOL_PLACEHOLDER else t for t in agent.tools]
    body = {
        "name": agent.name,
        "description": agent.description,
        "instructions": agent.instructions,
        "provider": role.endpoint,
        "model": role.model,
        "tools": tools,
        "model_parameters": dict(role.params),
    }
    if agent.artifacts:
        body["artifacts"] = "default"
    return body


def file_ref(upload: dict) -> dict:
    keys = ("file_id", "filename", "filepath", "type", "width", "height")
    return {k: upload[k] for k in keys if upload.get(k) is not None}


# ----------------------------------------------------------------------------- small parsers

def image_size(data: bytes) -> tuple[int, int] | None:
    """(width, height) of a PNG or JPEG, or None. The upload route wants both for images."""
    if data[:8] == b"\x89PNG\r\n\x1a\n" and len(data) >= 24:
        return struct.unpack(">II", data[16:24])
    if data[:2] == b"\xff\xd8":
        i = 2
        while i + 9 < len(data):
            if data[i] != 0xFF:
                i += 1
                continue
            marker = data[i + 1]
            if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
                i += 2
                continue
            length = struct.unpack(">H", data[i + 2:i + 4])[0]
            if marker in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                          0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                h, w = struct.unpack(">HH", data[i + 5:i + 9])
                return w, h
            i += 2 + length
    return None


def sse_events(lines):
    """Yield each SSE frame's (event, data) from an iterable of byte lines."""
    event, data = "message", []
    for raw in lines:
        line = raw.decode("utf-8", "replace").rstrip("\r\n")
        if not line:
            if data:
                yield event, "\n".join(data)
            event, data = "message", []
        elif line.startswith("event:"):
            event = line[6:].strip()
        elif line.startswith("data:"):
            data.append(line[5:].lstrip(" "))
    if data:
        yield event, "\n".join(data)


def multipart(fields: dict[str, str], file_field: str, filename: str, content: bytes,
              content_type: str) -> tuple[bytes, str]:
    boundary = uuid.uuid4().hex
    out = bytearray()
    for name, value in fields.items():
        out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n"
                f"{value}\r\n").encode()
    out += (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{file_field}\"; "
            f"filename=\"{filename}\"\r\nContent-Type: {content_type}\r\n\r\n").encode()
    out += content + f"\r\n--{boundary}--\r\n".encode()
    return bytes(out), f"multipart/form-data; boundary={boundary}"


# ----------------------------------------------------------------------------- HTTP

class Client:
    def __init__(self, server: str, email: str, password: str, name: str):
        self.base = server.rstrip("/")
        self.email, self.password, self.name = email, password, name
        self.token: str | None = None
        self.user_id: str | None = None

    def _request(self, method: str, path: str, *, body=None, raw: bytes | None = None,
                 content_type: str | None = None, auth=True, stream=False, retry_auth=True,
                 timeout=120):
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
        data = raw
        if body is not None:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
        elif content_type:
            headers["Content-Type"] = content_type
        if stream:
            headers["Accept"] = "text/event-stream"
        if auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            resp = urllib.request.urlopen(req, timeout=timeout)
        except urllib.error.HTTPError as e:
            detail = e.read().decode("utf-8", "replace")[:400]
            if e.code == 429:
                raise Refusal(f"{method} {path} was rate-limited: {detail}") from None
            # Refresh is cookie-only and a seed outlives a 15-minute access token, so a 401 is
            # answered by signing in again. A sign-in that fails here stops the run: every
            # failed sign-in counts toward the server's violation score.
            if e.code == 401 and auth and retry_auth:
                try:
                    self.login()
                except SeedError as login_error:
                    raise Refusal(f"signing in again failed: {login_error}") from None
                return self._request(method, path, body=body, raw=raw,
                                     content_type=content_type, auth=auth, stream=stream,
                                     retry_auth=False, timeout=timeout)
            raise SeedError(f"{method} {path} → {e.code}: {detail}") from None
        except (OSError, http.client.HTTPException) as e:
            raise SeedError(f"{method} {path} failed: {e}") from None
        if stream:
            return resp
        text = resp.read().decode("utf-8", "replace")
        if not text:
            return None
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return text

    def json(self, method, path, **kw):
        result = self._request(method, path, **kw)
        if isinstance(result, str):
            raise SeedError(f"{method} {path} answered non-JSON: {result[:200]}")
        return result

    def login(self):
        result = self._request("POST", "/api/auth/login", auth=False, retry_auth=False,
                               body={"email": self.email, "password": self.password})
        if not isinstance(result, dict) or "token" not in result:
            if isinstance(result, dict) and result.get("twoFAPending"):
                raise SeedError("the demo account has 2FA on; turn it off for seeding")
            raise SeedError(f"login failed: {result}")
        self.token = result["token"]
        self.user_id = result.get("user", {}).get("_id") or result.get("user", {}).get("id")

    def login_or_register(self):
        try:
            self.login()
            return False
        except SeedError as first:
            if "→ 404" not in str(first) and "→ 401" not in str(first) \
                    and "→ 422" not in str(first):
                raise
        self._request("POST", "/api/auth/register", auth=False, retry_auth=False, body={
            "name": self.name, "email": self.email, "username": "",
            "password": self.password, "confirm_password": self.password,
        })
        try:
            self.login()
        except SeedError as e:
            raise SeedError(f"sign-in fails even after registering ({e}). Either the account "
                            "already exists with a different password, or the server sends "
                            "verification email: verify the address or set "
                            "ALLOW_UNVERIFIED_EMAIL_LOGIN=true") from None
        return True

    def upload(self, path: Path, *, endpoint: str, as_text: bool) -> dict:
        content = path.read_bytes()
        ctype = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        fields = {"file_id": str(uuid.uuid4()), "endpoint": endpoint, "message_file": "true"}
        size = image_size(content) if ctype.startswith("image/") else None
        if size:
            fields["width"], fields["height"] = str(size[0]), str(size[1])
        if as_text:
            fields["tool_resource"] = "context"
        raw, mp_type = multipart(fields, "file", path.name, content, ctype)
        return self.json("POST", "/api/files", raw=raw, content_type=mp_type)

    def upload_avatar(self, path: Path):
        content = path.read_bytes()
        ctype = mimetypes.guess_type(path.name)[0] or "image/png"
        raw, mp_type = multipart({"manual": "true"}, "file", path.name, content, ctype)
        return self.json("POST", "/api/files/images/avatar", raw=raw, content_type=mp_type)

    def start(self, endpoint: str, body: dict) -> dict:
        for attempt in range(6):
            try:
                result = self._request("POST", f"/api/agents/chat/{endpoint}", body=body)
            except Refusal:
                raise
            except SeedError as e:
                # 409: the parent is still being saved. 503 SERVER_NOT_READY: a startup race.
                if ("→ 409" in str(e) or "→ 503" in str(e)) and attempt < 5:
                    time.sleep(3)
                    continue
                # A 403 on a send is a ban or a moderation block, except an MCP server
                # rejecting its credentials, which only concerns this chat.
                if "→ 403" in str(e) and "MCP_" not in str(e):
                    raise Refusal(f"the server refused the send: {e}") from None
                raise
            if not isinstance(result, dict):
                raise Refusal(f"the server refused the send: {str(result)[:300]}")
            return result
        raise SeedError("unreachable")

    def wait_for_turn(self, start: dict) -> dict:
        """The finished turn as {conversationId, responseMessageId, title}."""
        cid = start.get("conversationId")
        stream_id = start.get("streamId")
        if stream_id:
            resp = self._request("GET", f"/api/agents/chat/stream/{stream_id}", stream=True,
                                 timeout=600)
            try:
                with resp:
                    final = self._read_final(resp, cid)
            except (OSError, http.client.HTTPException) as e:
                raise SeedError(f"the stream for {cid} broke: {e}") from None
            if final:
                return final
        # No stream, or it closed without a final frame: wait for the job, then read what was
        # saved.
        return self._saved_turn(cid)

    @staticmethod
    def _read_final(resp, cid) -> dict | None:
        for event, data in sse_events(resp):
            try:
                payload = json.loads(data)
            except json.JSONDecodeError:
                continue
            if not isinstance(payload, dict):
                continue
            if event == "error" or ("error" in payload and not payload.get("final")):
                raise SeedError(f"stream error: {data[:400]}")
            if payload.get("event") == "on_pending_action":
                raise Paused(f"conversation {cid} paused for input")
            if payload.get("final"):
                response = payload.get("responseMessage") or {}
                if response.get("unfinished"):
                    raise SeedError(f"conversation {cid} finished unfinished")
                conversation = payload.get("conversation") or {}
                return {
                    "conversationId": conversation.get("conversationId") or cid,
                    "responseMessageId": response.get("messageId"),
                    "title": payload.get("title") or conversation.get("title"),
                }
        return None

    def _saved_turn(self, cid: str | None) -> dict:
        if not cid:
            raise SeedError("the send was accepted without a conversation id")
        for _ in range(240):
            status = self._request("GET", f"/api/agents/chat/status/{cid}")
            if isinstance(status, dict) and status.get("pendingAction"):
                raise Paused(f"conversation {cid} paused for input")
            if not (isinstance(status, dict) and status.get("active")):
                break
            time.sleep(2.5)
        else:
            raise SeedError(f"conversation {cid} was still generating after 10 minutes")
        messages = self.json("GET", f"/api/messages/{cid}")
        replies = [m for m in messages if not m.get("isCreatedByUser")]
        if not replies:
            raise SeedError(f"conversation {cid} saved no reply")
        convo = self.json("GET", f"/api/convos/{cid}")
        return {"conversationId": cid, "responseMessageId": replies[-1]["messageId"],
                "title": convo.get("title")}


# ----------------------------------------------------------------------------- state

class State:
    def __init__(self, path: Path):
        self.path = path
        self.data = {"agents": {}, "chats": {}, "projects": {}, "tags": [], "files": []}
        if path.exists():
            self.data.update(json.loads(path.read_text()))

    def save(self):
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, indent=2) + "\n")


def state_path(server: str, email: str) -> Path:
    host = urllib.parse.urlparse(server).netloc or server
    safe = "".join(ch if ch.isalnum() or ch in "-." else "_" for ch in f"{host}__{email}")
    return STATE_DIR / f"{safe}.json"


# ----------------------------------------------------------------------------- the run

def plan_lines(scenario: Scenario, profile: Profile, only: set[str] | None) -> list[str]:
    lines = [f"account: {scenario.name}"]
    for a in scenario.agents.values():
        role = profile.roles[a.role]
        tools = [profile.image_tool or "?" if t == IMAGE_TOOL_PLACEHOLDER else t for t in a.tools]
        kind = "seeded" if a.seed else "capture-only"
        lines.append(f"agent {a.key} ({kind}): {a.name} on {role.endpoint}/{role.model}, "
                     f"tools {tools}")
    for c in scenario.chats:
        if only and c.key not in only:
            continue
        who = (f"agent {c.agent}" if c.agent else
               f"{profile.roles[c.role].endpoint}/{profile.roles[c.role].model}")
        if c.compare_with:
            r = profile.roles[c.compare_with]
            who += f" vs {r.endpoint}/{r.model}"
        extras = [x for x, on in (("web search", c.web_search), ("artifacts", c.artifacts),
                                  (f"tags {c.tags}", bool(c.tags)),
                                  (f"project {c.project}", bool(c.project)),
                                  ("pinned", c.pinned)) if on]
        lines.append(f"chat {c.key}: {len(c.turns)} turn(s) on {who}"
                     + (f" [{', '.join(extras)}]" if extras else ""))
    return lines


def server_capabilities(client: Client) -> tuple[dict, set[str], set[str]]:
    endpoints = client.json("GET", "/api/endpoints")
    agent_caps = set((endpoints.get("agents") or {}).get("capabilities") or [])
    config = client._request("GET", "/api/config")
    interface = (config or {}).get("interface") or {} if isinstance(config, dict) else {}
    if interface.get("multiConvo", True) is not False:
        agent_caps.add("compare")
    # The capability is on by default, but without a search provider configured the tool only
    # errors, and the model apologizes on camera.
    if not (isinstance(config, dict) and config.get("webSearch")):
        agent_caps.discard("web_search")
    tools = client._request("GET", "/api/agents/tools")
    authenticated = {t.get("pluginKey") for t in tools or []
                     if isinstance(t, dict) and t.get("authenticated")}
    return endpoints, agent_caps, authenticated


def unmet(chat: Chat, scenario: Scenario, agent_caps: set[str], tools: set[str],
          profile: Profile, assets: Path) -> list[str]:
    missing = []
    if "web_search" in chat.requires and "web_search" not in agent_caps:
        missing.append("web search is not enabled on the server")
    if "artifacts" in chat.requires and "artifacts" not in agent_caps:
        missing.append("artifacts are not enabled on the server")
    if "compare" in chat.requires and "compare" not in agent_caps:
        missing.append("model comparison is turned off on the server")
    if "image_gen" in chat.requires and (not profile.image_tool
                                         or profile.image_tool not in tools):
        missing.append(f"image tool '{profile.image_tool}' has no server keys")
    for turn in chat.turns:
        for name in turn.attachments:
            if not (assets / name).exists():
                missing.append(f"asset '{name}' is missing from {assets}")
    return missing


def run(args, scenario: Scenario, profile: Profile) -> int:
    email = os.environ.get("DEMO_SEED_EMAIL")
    password = os.environ.get("DEMO_SEED_PASSWORD")
    if not (args.server and email and password):
        print("needs --server (or DEMO_SEED_SERVER), DEMO_SEED_EMAIL and DEMO_SEED_PASSWORD",
              file=sys.stderr)
        return 2
    assets = Path(args.assets)
    client = Client(args.server, email, password, os.environ.get("DEMO_SEED_NAME",
                                                                 scenario.name))
    state = State(state_path(args.server, email))
    registered = client.login_or_register()
    print(("registered and signed in" if registered else "signed in") + f" as {email}")

    if args.reset:
        return reset(client, state)

    seeded_ids = {c.get("conversationId") for c in state.data["chats"].values()}
    existing = client.json("GET", "/api/convos?limit=100").get("conversations", [])
    strangers = [c for c in existing if c.get("conversationId") not in seeded_ids]
    if strangers and not args.allow_existing:
        print(f"refusing: the account has {len(strangers)} conversation(s) this seeder didn't "
              "create. Use a clean demo account, or pass --allow-existing.", file=sys.stderr)
        return 2

    endpoints, agent_caps, tools = server_capabilities(client)
    timezone = args.timezone

    for a in scenario.agents.values():
        if a.key in state.data["agents"]:
            continue
        if IMAGE_TOOL_PLACEHOLDER in a.tools and profile.image_tool not in tools:
            print(f"skip agent {a.key}: image tool '{profile.image_tool}' has no server keys")
            continue
        created = client.json("POST", "/api/agents",
                              body=agent_body(a, profile.roles[a.role], profile.image_tool))
        state.data["agents"][a.key] = created["id"]
        state.save()
        print(f"created agent {a.key} → {created['id']}")

    if scenario.avatar and not state.data.get("avatar"):
        avatar = assets / scenario.avatar
        if avatar.exists():
            client.upload_avatar(avatar)
            state.data["avatar"] = True
            state.save()
            print("set the avatar")
        else:
            print(f"skip avatar: {avatar} is missing")

    favorites = [{"endpoint": profile.roles[r].endpoint, "model": profile.roles[r].model}
                 for r in scenario.starred_roles]
    favorites += [{"agentId": state.data["agents"][a]} for a in scenario.starred_agents
                  if a in state.data["agents"]]
    if favorites:
        client.json("POST", "/api/user/settings/favorites", body={"favorites": favorites})

    only = set(args.only) if args.only else None
    redo = set(args.redo or [])
    first_send = True
    summary = []
    for chat in scenario.chats:
        if only and chat.key not in only:
            continue
        record = state.data["chats"].get(chat.key)
        if record and record.get("complete", True) and chat.key not in redo:
            summary.append((chat.key, record, "kept"))
            continue
        if record and chat.key in redo:
            if record["conversationId"]:
                delete_conversation(client, record["conversationId"])
            del state.data["chats"][chat.key]
            state.save()
        missing = unmet(chat, scenario, agent_caps, tools, profile, assets)
        if chat.agent and chat.agent not in state.data["agents"]:
            missing.append(f"agent '{chat.agent}' was not created")
        if missing:
            print(f"skip {chat.key}: " + "; ".join(missing))
            continue
        if not first_send:
            time.sleep(args.pace)
        first_send = False
        resuming = chat.key in state.data["chats"]
        print(f"{'resuming' if resuming else 'seeding'} {chat.key} …", flush=True)
        try:
            record = seed_chat(client, chat, profile, state, endpoints, timezone, assets,
                               args.pace)
        except Refusal as e:
            print(f"stopping: {e}", file=sys.stderr)
            return 1
        except SeedError as e:
            print(f"  failed: {e}. Run again to resume it.", file=sys.stderr)
            continue
        summary.append((chat.key, record, "new"))

    print("\nseeded chats:")
    for key, record, how in summary:
        print(f"  {key:<24} {record['conversationId']}  {record.get('title') or ''}"
              + ("" if how == "new" else "  (kept)"))
    for a in scenario.agents.values():
        if not a.seed and a.capture_prompt:
            print(f"\ncapture: open agent '{a.name}' and send:\n  {a.capture_prompt}")
    return 0


def seed_chat(client: Client, chat: Chat, profile: Profile, state: State, endpoints: dict,
              timezone: str, assets: Path, pace: float) -> dict:
    """Send the chat's turns, resuming after the last one that finished. The record is saved
    as soon as the server names the conversation, so a failure part-way leaves something
    --reset and --redo can find, and a long chat isn't paid for twice."""
    role = profile.roles[chat.role] if chat.role else None
    agent_id = state.data["agents"][chat.agent] if chat.agent else None
    compare = profile.roles[chat.compare_with] if chat.compare_with else None
    record = state.data["chats"].setdefault(
        chat.key, {"conversationId": None, "parent": None, "turnsDone": 0, "complete": False})
    conversation_id, parent = record["conversationId"], record["parent"]
    title = record.get("title")
    for i, turn in enumerate(chat.turns):
        if i < record["turnsDone"]:
            continue
        if i > record["turnsDone"]:
            time.sleep(min(pace, 5))
        files = []
        for name in turn.attachments:
            path = assets / name
            ctype = mimetypes.guess_type(name)[0] or ""
            upload_endpoint = "agents" if agent_id else role.endpoint
            uploaded = client.upload(path, endpoint=upload_endpoint,
                                     as_text=not ctype.startswith("image/"))
            state.data["files"].append({"file_id": uploaded["file_id"],
                                        "filepath": uploaded.get("filepath", "")})
            state.save()
            files.append(file_ref(uploaded))
        endpoint, body = chat_body(
            text=turn.text, role=role, agent_id=agent_id, endpoints_config=endpoints,
            conversation_id=conversation_id, parent_message_id=parent, files=files,
            web_search=chat.web_search, artifacts=chat.artifacts, compare=compare,
            timezone=timezone,
        )
        started = client.start(endpoint, body)
        if not record["conversationId"] and started.get("conversationId"):
            record["conversationId"] = started["conversationId"]
            state.save()
        result = client.wait_for_turn(started)
        conversation_id = result["conversationId"]
        parent = result["responseMessageId"]
        title = result.get("title") or title
        record.update(conversationId=conversation_id, parent=parent, turnsDone=i + 1,
                      title=title)
        state.save()
        print(f"  turn {i + 1}/{len(chat.turns)} done")
    if chat.title:
        client.json("POST", "/api/convos/update",
                    body={"arg": {"conversationId": conversation_id, "title": chat.title}})
        title = chat.title
    if chat.tags:
        client._request("PUT", f"/api/tags/convo/{conversation_id}", body={"tags": chat.tags})
        state.data["tags"] = sorted(set(state.data["tags"]) | set(chat.tags))
    if chat.project:
        project_id = state.data["projects"].get(chat.project)
        if not project_id:
            project_id = client.json("POST", "/api/projects", body={"name": chat.project})["_id"]
            state.data["projects"][chat.project] = project_id
        client._request("PUT", f"/api/projects/conversations/{conversation_id}",
                        body={"projectId": project_id})
    if chat.pinned:
        client._request("POST", "/api/convos/pin",
                        body={"arg": {"conversationId": conversation_id, "pinned": True}})
    record.update(title=title, complete=True)
    state.save()
    return record


def delete_conversation(client: Client, conversation_id: str):
    client._request("DELETE", "/api/convos", body={"arg": {"conversationId": conversation_id}})


def reset(client: Client, state: State) -> int:
    """Delete what this seeder recorded creating. Images a tool generated are files the server
    created, not the seeder; delete those from the app's file manager, or start a new account."""
    d = state.data
    for key, record in list(d["chats"].items()):
        if record.get("conversationId"):
            delete_conversation(client, record["conversationId"])
            print(f"deleted chat {key}")
    for key, agent_id in list(d["agents"].items()):
        client._request("DELETE", f"/api/agents/{agent_id}")
        print(f"deleted agent {key}")
    for name, project_id in list(d["projects"].items()):
        client._request("DELETE", f"/api/projects/{project_id}")
        print(f"deleted project {name}")
    for tag in d["tags"]:
        client._request("DELETE", f"/api/tags/{urllib.parse.quote(tag, safe='')}")
    if d["files"]:
        client._request("DELETE", "/api/files", body={"files": d["files"]})
        print(f"deleted {len(d['files'])} uploaded file(s)")
    state.path.unlink(missing_ok=True)
    print("reset done")
    return 0


# ----------------------------------------------------------------------------- self-test

def self_test() -> int:
    failures = []

    def check(name, cond):
        if not cond:
            failures.append(name)

    scenario = Scenario.load(DEFAULT_SCENARIO)
    profile = Profile.load(HERE / "profile.example.toml")
    problems = scenario.validate(profile)
    check("scenario validates against the example profile: " + "; ".join(problems),
          not problems)
    check("scenario has chats", len(scenario.chats) > 0)

    endpoints = {"anthropic": {"modelDisplayLabel": "Claude"}, "OpenRouter": {"type": "custom"}}
    smart = Role("smart", "anthropic", "claude-x", {"thinking": True})
    custom = Role("cheap", "OpenRouter", "m")
    ids = {"message_id": "m1", "client_request_id": "r1", "timezone": "UTC"}

    ep, body = chat_body(text="hi", role=smart, agent_id=None, endpoints_config=endpoints,
                         conversation_id=None, parent_message_id=None, files=[],
                         web_search=False, artifacts=False, compare=None, **ids)
    check("plain chat body", ep == "anthropic" and body == {
        "endpoint": "anthropic", "endpointType": "anthropic", "model": "claude-x",
        "modelDisplayLabel": "Claude", "thinking": True, "text": "hi",
        "parentMessageId": NO_PARENT, "messageId": "m1", "clientRequestId": "r1",
        "timezone": "UTC"})

    ep, body = chat_body(text="next", role=None, agent_id="agent_1", endpoints_config=endpoints,
                         conversation_id="c1", parent_message_id="p1", files=[],
                         web_search=False, artifacts=False, compare=None, **ids)
    check("agent follow-up body", ep == "agents" and body == {
        "endpoint": "agents", "endpointType": "agents", "agent_id": "agent_1", "text": "next",
        "parentMessageId": "p1", "messageId": "m1", "clientRequestId": "r1", "timezone": "UTC",
        "conversationId": "c1"})
    check("agent body sends no model", "model" not in body)

    _, body = chat_body(text="t", role=custom, agent_id=None, endpoints_config=endpoints,
                        conversation_id=None, parent_message_id=None,
                        files=[{"file_id": "f"}], web_search=True, artifacts=True,
                        compare=smart, **ids)
    check("custom endpointType", body["endpointType"] == "custom")
    check("web search is sent in both places",
          body["web_search"] is True and body["ephemeralAgent"]["web_search"] is True)
    check("artifacts ride the ephemeral agent", body["ephemeralAgent"]["artifacts"] == "default")
    check("files are attached", body["files"] == [{"file_id": "f"}])
    check("comparison adds a second model", body["addedConvo"]["model"] == "claude-x"
          and body["addedConvo"]["parentMessageId"] == NO_PARENT
          and body["addedConvo"]["thinking"] is True)

    agent = AgentDef("a", "Artist", "d", "i", "smart", ["@image", "web_search"], True, True, None)
    ab = agent_body(agent, smart, "image_gen_oai")
    check("agent body resolves the image tool", ab["tools"] == ["image_gen_oai", "web_search"])
    check("agent body carries provider and model",
          ab["provider"] == "anthropic" and ab["model"] == "claude-x"
          and ab["artifacts"] == "default")

    check("file ref keeps only what the chat needs",
          file_ref({"file_id": "f", "filename": "a.png", "filepath": "/p", "type": "image/png",
                    "width": 2, "height": 3, "bytes": 9, "source": "local"})
          == {"file_id": "f", "filename": "a.png", "filepath": "/p", "type": "image/png",
              "width": 2, "height": 3})

    png = b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\rIHDR" + struct.pack(">II", 640, 480) + b"\0" * 8
    check("PNG size", image_size(png) == (640, 480))
    jpeg = (b"\xff\xd8" + b"\xff\xe0" + struct.pack(">H", 4) + b"\0\0"
            + b"\xff\xc0" + struct.pack(">H", 11) + b"\x08" + struct.pack(">HH", 300, 400)
            + b"\x03\0\0\0")
    check("JPEG size", image_size(jpeg) == (400, 300))
    check("unknown image", image_size(b"GIF89a....") is None)

    stream = [b"event: message\n", b'data: {"created":{}}\n', b"\n",
              b'data: {"final":true,\n', b'data: "x":1}\n', b"\n", b"event: error\n",
              b'data: {"error":"boom"}\n']
    events = list(sse_events(stream))
    check("SSE frames", events == [("message", '{"created":{}}'),
                                   ("message", '{"final":true,\n"x":1}'),
                                   ("error", '{"error":"boom"}')])

    raw, ctype = multipart({"a": "1"}, "file", "x.txt", b"hello", "text/plain")
    boundary = ctype.split("boundary=")[1]
    check("multipart framing", raw.startswith(f"--{boundary}\r\n".encode())
          and raw.endswith(f"--{boundary}--\r\n".encode()) and b"hello" in raw)

    bad = Scenario("x", None, ["nope"], [], {
        "asker": AgentDef("asker", "A", "", "", "smart", ["ask_user_question"], False, True,
                          None)}, [
        Chat("k", None, "smart", "asker", None, [], ["teleport"], False, False, [], None,
             False, None),
        Chat("k", None, None, None, "ghost", [Turn("a"), Turn("b")], [], False, False, [],
             None, False, None)])
    issues = "\n".join(bad.validate(profile))
    for expected in ("duplicate chat key", "exactly one of role or agent", "can pause",
                     "no turns", "unknown requirement", "exactly one turn", "starred role",
                     "role 'ghost'"):
        check(f"validation reports '{expected}'", expected in issues)

    check("state path is filesystem-safe",
          state_path("https://chat.example.com:3080", "a+b@x.io").name
          == "chat.example.com_3080__a_b_x.io.json")

    for f in failures:
        print(f"FAIL {f}")
    print(f"self-test: {'ok' if not failures else f'{len(failures)} failure(s)'}")
    return 1 if failures else 0


# ----------------------------------------------------------------------------- main

def main() -> int:
    p = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    p.add_argument("--env-file", help="read DEMO_SEED_* settings from a KEY=VALUE file")
    p.add_argument("--server")
    p.add_argument("--profile", help="TOML mapping roles to this server's models")
    p.add_argument("--scenario", default=str(DEFAULT_SCENARIO))
    p.add_argument("--assets", default=str(HERE / "assets"))
    p.add_argument("--only", nargs="+", metavar="KEY", help="seed only these chats")
    p.add_argument("--redo", nargs="+", metavar="KEY", help="delete and reseed these chats")
    p.add_argument("--pace", type=float, default=20.0,
                   help="seconds between chats (default 20), to stay under rate limits")
    p.add_argument("--timezone", default="America/New_York")
    p.add_argument("--allow-existing", action="store_true",
                   help="seed an account that already has other conversations")
    p.add_argument("--reset", action="store_true",
                   help="delete everything this seeder created on the account")
    p.add_argument("--dry-run", action="store_true", help="print the plan; contact nothing")
    p.add_argument("--self-test", action="store_true")
    args = p.parse_args()

    if args.self_test:
        return self_test()
    if args.env_file:
        for line in Path(args.env_file).read_text().splitlines():
            line = line.strip().removeprefix("export ").strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                os.environ.setdefault(key.strip(), value.strip().strip("'\""))
    args.server = args.server or os.environ.get("DEMO_SEED_SERVER")
    if not args.profile:
        p.error("--profile is required (copy profile.example.toml)")
    scenario = Scenario.load(Path(args.scenario))
    profile = Profile.load(Path(args.profile))
    problems = scenario.validate(profile)
    if problems:
        for problem in problems:
            print(f"scenario: {problem}", file=sys.stderr)
        return 2
    if args.dry_run:
        print("\n".join(plan_lines(scenario, profile, set(args.only) if args.only else None)))
        return 0
    try:
        return run(args, scenario, profile)
    except SeedError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
