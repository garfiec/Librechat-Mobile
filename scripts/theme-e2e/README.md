# Server theme end-to-end check

Checks on an Android device that a LibreChat server's `interface.theme` reaches the app's pixels:
the served theme is fetched, cached, resolved and painted where the web paints those roles. Unit
tests cover the parser, the resolver and the role → token map; this covers everything after them.

- `run.py`: the run. Python 3.11+, standard library only.
- `fixtures/*.toml`: one theme each, with the colours the run expects (`expect`) or must not see
  (`differ`) per mode.
  - `clickhouse`: the bundled theme.
  - `acme`: an inline theme that names every sampled role, plus square corners and a serif face.
  - `none`: no theme; the app must drop the last one.

For each fixture the run writes the theme into the server's `librechat.yaml`, restarts the server,
waits for `/api/config` to serve it, then cold-starts the app on a conversation in light and in
dark. It samples four places and compares them with the fixture by ΔE (CIE76, pass at 3 or under):

| Target | Sampled at | Paints the web role |
| --- | --- | --- |
| `canvas` | the screen's left edge beside the code block (a phone layout, no rail) | `surface-canvas` |
| `user_message` | inside the user's bubble, left of its text | `surface-user-message` |
| `composer` | inside the composer box (test tag `composer`) | `surface-composer` |
| `code_body` | a code block's bottom-left, inside its corner | `surface-code-body` |

With `--api-stop` and `--api-start`, the first fixture with expectations is also checked with the
server down: the theme cached from the last fetch must paint the first frame.

## Setup

1. A LibreChat v0.8.9-rc1 or later server you can restart, with these two lines under `interface:`
   in its `librechat.yaml`. The run writes the theme between them and puts the file back afterwards.

   ```yaml
   interface:
     # theme-e2e:begin
     # theme-e2e:end
   ```

2. The foss debug build installed and signed in to that server. In Settings › Appearance: theme
   System default, style Material (Liquid Glass tints the composer), the server's colours chosen,
   and under Chat appearance, Show bubbles on (without it the user's message has no fill). The UI
   in English: the code block is found by its description.
3. A conversation in that account whose user message and a code block both fit on one screen, for
   example the reply to "Reply with only a 5-line Python code block." Note its id (the web shows it
   in the address bar) and a few words of the message.

## Run

```bash
scripts/theme-e2e/run.py --serial emulator-5554 \
    --server http://localhost:3090 \
    --config ~/rigs/theme/librechat.yaml \
    --restart "docker compose -p lctheme restart api" \
    --api-stop "docker compose -p lctheme stop api" \
    --api-start "docker compose -p lctheme start api" \
    --conversation <id> --user-message "5-line Python"
```

On a device with two displays (a Fold), pass `--display` with the lit display's id
(`adb shell dumpsys SurfaceFlinger --display-id`). Screenshots, `results.json` and an `index.html`
contact sheet go to `.screenshots/theme-e2e/<time>/`; corners and fonts are checked by eye there.
The exit code is non-zero when any check fails.

Not covered: Liquid Glass, switching between the server's colours and Switchboard's in Settings,
and switching accounts between two servers.
