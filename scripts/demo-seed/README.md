# Demo account seeder

Fills a LibreChat account with conversations for README and store screenshots and recordings.
Every prompt goes to a real model through the API the app uses; no reply is written or edited
by hand, so the screenshots show what the app actually does.

- `scenario.toml`: the account, its agents and its chats, written against model roles.
- `profile.example.toml`: maps those roles to one server's endpoints and models. Copy it per
  server (`profile.<name>.toml` is gitignored).
- `seed.py`: Python 3.11+, standard library only.
- `smoke.py`: end-to-end check of the seeder against a fake server.
- `make-assets.py`: writes the document asset, so only photos need supplying by hand.
- `review.py`: prints what the server saved for each seeded chat. Read it before capturing:
  artifact directives must be well-formed, React artifacts may import only `react` (the app
  bundles nothing else), and no reply should apologise for a broken tool.

## Run it

```bash
scripts/demo-seed/seed.py --self-test                       # offline checks
scripts/demo-seed/seed.py --profile profile.mine.toml --dry-run

export DEMO_SEED_SERVER=https://chat.example.com
export DEMO_SEED_EMAIL=demo@example.com DEMO_SEED_PASSWORD=…
scripts/demo-seed/seed.py --profile scripts/demo-seed/profile.mine.toml
```

The account is registered on first run if it doesn't exist. Its display name is set at
registration (`[account] name`, or `DEMO_SEED_NAME`) and the server has no rename route, so
decide the persona before the first run.

Re-running skips chats already seeded, and resumes a chat that failed part-way from its next turn
(the photo album is 14 paid image generations). To iterate on one:

```bash
seed.py --profile … --only car-wash --redo car-wash   # delete it and send it again
seed.py --profile … --reset                          # delete everything the seeder created
```

What was created is recorded in `.state/<server>__<email>.json` (gitignored); `--reset` and
`--redo` need it. `--reset` doesn't remove images a tool generated, because the server created
those, not the seeder; delete them in the app's file manager, or start a fresh account.

## Before the first run

- **Use a throwaway account, ideally on a test server.** The seeder refuses an account that has
  conversations it didn't create (`--allow-existing` overrides). The stock server soft-bans a
  non-browser User-Agent and rate-limits bursts, and a ban deletes the account's sessions. The
  seeder always sends the app's browser UA and paces chats 20 s apart (`--pace`); a refused send
  stops the run.
- **Assets** go in `scripts/demo-seed/assets/` (gitignored, so photos stay out of the repo).
  Chats whose asset is missing are skipped:
  - `avatar.png`: the account picture
  - `melted-bar.jpg`: a photo of a melted chocolate bar
  - `town-snack-policy.pdf`: a one-page "policy" document. `make-assets.py` writes it.
- **Image generation** needs an image tool with server keys. Set `[image] tool` in the profile to
  one that `GET /api/agents/tools` reports `authenticated: true`. Without it the image agent and
  the photo album are skipped.
- **Web search** and **artifacts** chats are skipped when the server doesn't enable them.

## What can't be seeded

The panels for an agent's clarifying questions and tool approval have to be on screen while
recording, so they are sent live. The seeder creates the agent (`seed = false` in the scenario)
and prints the prompt to send at the end of the run.

Tool approval is server configuration, not something the account can switch on. For the approval
panel to appear, the server's `librechat.yaml` needs a policy that asks. Turn it on **after**
seeding: the policy applies to every chat, so a seeded chat that calls the same tool would stop
and wait for approval. For example:

```yaml
endpoints:
  agents:
    toolApproval:
      enabled: true
      ask: ["web_search"]
```

Timestamps all read as the day the seeder ran. Spreading chats across "Yesterday" and "Last week"
in the sidebar would mean seeding on different days.

## Changing the scenario

A chat sets exactly one of `role` or `agent`. `requires` lists what it needs (`image_gen`,
`web_search`, `artifacts`, `attachments`, `compare`), and a chat whose requirements the server
can't meet is skipped with the reason. Seeded chats can't use an agent that has
`ask_user_question`: a model may ask unprompted, and the run would wait forever.
`--self-test` checks the scenario against the example profile, so run it after every edit.

`smoke.py` runs the seeder end to end against a fake server: a full seed, a re-run, a rate-limited
send (the run must stop), a turn that fails part-way (the next run must resume the same
conversation), the clean-account guard and `--reset`. Run it after changing `seed.py`.
