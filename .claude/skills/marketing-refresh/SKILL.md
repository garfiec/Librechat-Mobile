---
name: marketing-refresh
description: >
  Refresh Switchboard's marketing images and copy end to end: the README screenshots and
  recordings, the F-Droid/IzzyOnDroid store screenshots in fastlane/, and the Google Play
  listing (captioned screenshots, feature graphic, listing text). Indexes what the app can do,
  ranks it by what a newcomer would feel, picks a running joke for a demo account, seeds that
  account with real model replies, drives the emulator and simulators through framed captures
  and recordings, composes the store images, and stops for the user's review before anything is
  committed. Use when the screenshots predate a visible feature or a theme change, before a store
  launch, or when asked to refresh the README images, the store listing or the Play graphics.
allowed-tools: Bash, Read, Write, Edit, Glob, Grep, Agent, SendMessage, WebSearch, WebFetch, AskUserQuestion, Artifact
argument-hint: "[readme | store | play | all] [--since <tag|date>] (defaults to all, since the last refresh)"
---

# Marketing Refresh

Produces, from one demo account and one capture session:

| Output | Lives in | Who reads it |
|---|---|---|
| Store screenshots (plain framed, half size) | `fastlane/metadata/android/en-US/images/{phone,tenInch}Screenshots/` | F-Droid, IzzyOnDroid, the README |
| Feature graphic | `fastlane/metadata/android/en-US/images/featureGraphic.png` | F-Droid banner, uploaded to Play by hand |
| README recordings and README-only stills | `docs/screenshots/` | the README |
| README Screenshots section, Why and Features copy | `README.md` | GitHub |
| Play screenshots (captioned canvases) | built by `scripts/screenshots/play.py set <dir>`, never committed | Play Console, by hand |
| Play listing text | handed to the user | Play Console, by hand |

The tools already exist: `scripts/demo-seed/` (the account) and `scripts/screenshots/` (frames,
captures, recordings, Play canvases, contact sheets, README check). Read both READMEs before
starting. This skill is the judgment around them: what to show, in what order, with what story,
and how to get it captured without a person holding the device.

Three reference files go with it:
- `reference/capture-agent.md`: the brief for the agent that drives the devices, the per-shot
  checklist, and every device gotcha found so far.
- `reference/store-rules.md`: Play, F-Droid and Apple rules, sizes and limits.
- `reference/2026-10-refresh.md`: the last refresh as a worked example, from the feature index
  to the captions, including what was cut and why. Read it before Phase 2; it is the precedent.

## Invariants

1. **Real app, real replies.** Every message on screen is a real model reply from a real server,
   through the API the app uses. Never write or edit a reply, never retouch a screenshot. The
   frame, the canvas and the caption are the only things added. A weak reply is regenerated
   (`seed.py --only X --redo X`), not fixed.
2. **Never ship a shot of something broken.** If a feature renders wrong during capture, leave it
   out of the lineup, tell the user, and record the bug. (2026-10: inline math rendered as
   separate blocks, so there is no math shot.)
3. **Don't oversell table stakes.** A store slot goes to something a newcomer would feel and the
   website or other clients don't give them. "Tables fit the screen" looked fine and was cut as
   overselling a basic feature.
4. **A demo account only.** No real names, emails, servers or personal data on screen. The persona
   name is the user's choice. The status bar is in demo mode on every capture.
5. **Platforms don't share an image.** Never an Apple device beside an Android one (Apple's
   marketing guidelines). The hero shows one platform in both looks. iOS frames from the
   Simulator's chrome are for the GitHub README only; App Store images use Apple's product bezels
   and are outside this skill.
6. **One copy of each image** (`fastlane/README.md`). The README links straight into fastlane/;
   `docs/screenshots/` holds only what the README shows and the store doesn't. Play canvases are
   generated from fastlane/ and never committed.
7. **The store reads the tag.** F-Droid reads fastlane/ from the tagged release commit, so the
   refresh must merge before the release is cut. Play takes images and text only by hand.
8. **Store text is per channel.** fastlane/ text describes the open-source build; Play text is
   written for the Play build's defaults (`ChannelPolicy`, see `fastlane/CLAUDE.md`). Never paste
   one into the other unchanged.
9. **Every server or device change made for capture is temporary.** Keep a list as you go and undo
   all of it in Phase 10.
10. **The user approves before anything image-related is committed.** Device work in this skill is
    the user's request for device work; it does not extend to filing bugs, pushing, or uploading.

## Phase 0: Scope and baseline

```bash
git log -1 --format='%h %cs %s' -- fastlane/metadata/android/en-US/images/phoneScreenshots
scripts/screenshots/sheet.py /tmp/before.jpg fastlane/metadata/android/en-US/images/*Screenshots/*.png
```

Look at the current images and the README Screenshots section. Note what is visibly stale: an old
theme, a removed screen, a clipped clock, a feature that has since changed. If the argument didn't
say which outputs are in scope, ask once, with the stale list as the reason.

## Phase 1: Index what the app can do

You can't rank features you haven't listed. Enumerate them from sources, not from memory:

- `README.md` Why and Features.
- Every PR merged since the last refresh:
  `gh pr list --state merged --base develop --search "merged:>=<date>" --limit 300 --json number,title`.
  Read the bodies of the `feat` ones; a title is a claim, the body says what a user sees.
- Release notes since then (`gh release list`, the Highlights sections).
- `ls feature/`, each `feature/*/CLAUDE.md`, and every Settings page: features hide in settings.
- Channel differences: `ChannelPolicy` and the `perChannel(…)` defaults in `AppPreferences`.
- What the official web app doesn't do. That is where the differentiators are.

Write one table:

| Feature | What a user feels (one line, their words) | New since last refresh | Platforms | Shows as | Differentiator | Demo content needed | Ships in the next release |
|---|---|---|---|---|---|---|---|

"Shows as" is one of: a still, motion (a gesture or animation, so a README recording), or not
visual (offline, speed, privacy: these belong in copy, not images). Mark anything that needs server
configuration (search, image generation, tool approval, web search) or isn't merged yet. A feature
that won't be in the build the listing describes doesn't go in the lineup.

## Phase 2: Rank and lay out the lineup

Rank by, in order:
1. **Felt benefit to someone who has never heard of LibreChat**: how it looks, finding things,
   working offline, images, help from agents.
2. **Difference from the website and other clients.**
3. **Reads in one still.** Store slots are stills; gestures go to README recordings.
4. **New since the last refresh**, since that's what the current images are missing.
5. **Not table stakes** (invariant 3).

Then lay out three lists:
- **README:** a hero (one platform, both looks, the same chat) plus about 12 rows. Each row has a
  benefit title, a phone cell, a tablet cell, its look, the seeded chat it uses, and whether each
  cell is a still or a recording.
- **Phone store slots:** 8, by impact. Slot 1 is the hero. Play shows about three before the
  viewer scrolls, so the first three carry the listing.
- **Tablet store slots:** 8 on the unfolded Fold.

Vary the look across rows (accent, light or dark, Material or Liquid Glass) so the set itself
shows the theming. Present the three lists to the user and get approval before seeding; the
lineup decides which chats exist.

## Phase 3: Pick the story

The demo account needs a running joke that ties the chats together and survives a skim.
Propose two or three themes, one line each, and let the user pick. A good theme:

- **Is current when the refresh happens.** Search for what is trending in AI and in food,
  internet culture, etc. that month. The 2026-10 theme was "an AI agent with a Dubai chocolate
  habit": agents doing chores crossed with a viral dessert. Rejected: pizza (dated), the World Cup
  (ages fast).
- **Gives search a hook.** One word ("chocolate") recurs in titles, replies and reasoning across
  several chats, so a search for it lights up.
- **Has a recurring character for images**, kept consistent across an album of 12 or more, so the
  media page fills (Mayor Whiskers, a cat who is the town's mayor).
- **Has a prompt two models disagree on**, for the comparison row. Asked whether to walk or drive
  50 m to a car wash, one model said drive and the other said walk.
- **Has filler chats with good titles**, so the sidebar looks lived in.
- **Avoids** real people, brands, news events that will age, and anything a store reviewer could
  read as harmful.

Ask the user for the persona's display name before the first seed: the server can't rename an
account afterwards.

## Phase 4: Seed the demo account

Follow `scripts/demo-seed/README.md`. Edit `scenario.toml` so every lineup row that needs content
has a chat, with `requires` naming what the server must offer. Then:

```bash
scripts/demo-seed/seed.py --self-test && scripts/demo-seed/smoke.py
scripts/demo-seed/seed.py --env-file scripts/demo-seed/.state/<server>.env --profile scripts/demo-seed/profile.<server>.toml
scripts/demo-seed/review.py …        # read every reply before capturing
```

Lessons from the last run:
- React artifacts may import only `react` and use Tailwind. The app bundles nothing else, so
  `lucide-react` or `recharts` render as a missing-package error.
- Seed artifact chats on the strongest model; a smaller one wrote a malformed `:::artifact` tag.
- A web search chat needs a search provider on the server, or the model apologizes. The seeder
  skips it unless `/api/config` reports `webSearch`.
- Image chats need a tool that `GET /api/agents/tools` reports `authenticated: true`.
- Photo assets are generated once (the OpenAI image API, with the same key) and the avatar is a
  crop of an album image. They stay out of the repo (`assets/` is gitignored).
- Never print an API key. Pipe it from the user's secrets file into the server's env file.
- The agent panels (clarifying questions, tool approval) can't be seeded. They're sent live while
  capturing, and tool approval needs a server policy that is switched on only after seeding.

## Phase 5: Server and device prep

**Server.** Make the changes the lineup needs, and write each one down for Phase 10:
- `SEARCH=true` (Meilisearch running, master key set), or the search screen shows a
  "server search is off" note.
- The image tool's key (for example `IMAGE_GEN_OAI_API_KEY`).
- The tool approval policy in `librechat.yaml` (keep a backup of the file), only after seeding.
- Restart the container after each change and confirm it through the API, not by assumption.

**Android** (Pixel 10 Pro Fold emulator): install the foss debug build, clear its data if a newer
database is installed, sign in as the persona (the emulator reaches the host at `10.0.2.2`), turn
on background prefetch so every chat is cached, and launch by component. Details are in
`reference/capture-agent.md`.

**iOS** (only if the README has iOS rows): build Debug for the newest simulators that have a
runtime, install, and sign in. Maestro drives the login. Shots are reached by tapping; iOS deep
links don't navigate.

## Phase 6: Capture

Hand the capture to a background agent, in batches, with the brief in
`reference/capture-agent.md`. Driving the device takes hundreds of tool calls: the 2026-10 run
needed three batches because the agent hit its turn limit. Keep each batch to one look or one
posture where possible, since theme switches and fold changes are the slow part.

While a batch runs, don't touch the device yourself. When it reports:
1. Build a contact sheet of the new files (`scripts/screenshots/sheet.py`, with `--frames 6` for
   GIFs) and look at every tile. Check: demo clock, the right theme for the row, no keyboard, no
   toast or error banner, no "isn't responding" dialog, only the persona's name, nothing broken.
2. Send the retakes back to the same agent (`SendMessage`), with what was wrong.

Every capture comes out framed. Android uses the Pixel 10 Pro Fold skin that Android Studio's
"screenshot with frame" uses (`~/Library/Android/sdk/skins/pixel_10_pro_fold/`). Recordings get the
same skin on every frame (ffmpeg overlays the screen video between the skin's back and its corner
mask), and come out as GIFs with transparent corners. iOS uses the Simulator's own device chrome.
`scripts/screenshots/frame.py` does both.

Ask for `--raw` copies of every store still. They let a canvas be rebuilt at full resolution,
though half-size frames upscaled to a Play canvas were indistinguishable in testing.

## Phase 7: Compose

- **Store screenshots:** copy the framed shots into fastlane/ in slot order, replacing in place.
  Delete old files that no longer have a slot, and renumber the prefixes. The hero is two framed
  shots joined by `play.py pair material.png glass.png 01-hero.png`.
- **README:** a full-width hero (the tablet pair), then a table of Feature | Phone (`width="280"`)
  | Tablet / Foldable (`width="380"`). A 2×2 grid in one cell uses `width="186"`. Recordings and
  README-only stills go in `docs/screenshots/`. Every image gets alt text. Run
  `scripts/screenshots/check-readme.py`.
- **Play:** edit the captions in `scripts/screenshots/play-set.toml`, then
  `scripts/screenshots/play.py set <dir>`. It frames, captions and checks every image against
  Play's rules, and copies the feature graphic in use (fastlane/'s `featureGraphic.png`, the
  same on Play and F-Droid). `play.py feature` draws the wordmark alternative in `design/`.

**Captions** are marketing copy:
- Name the benefit, not the feature.
- Keep the title to one line: about 26 characters at the phone size.
- Keep the subtitle under ten words, and don't repeat the title's words in it.
- Use one spelling convention across captions and listing text.

## Phase 8: Copy

- **README Why and Features:** lead with what a user feels (looks, speed, finding things, offline),
  not with implementation detail. A feature new to the lineup gets a Why bullet if it's a reason to
  install, and a Features bullet either way.
- **Play listing:** app name (30 characters), short description (80), full description (4000).
  Write it for the Play build: check every claim about defaults against `ChannelPolicy`.
- **F-Droid text** (`fastlane/…/*.txt`) is a separate copy. If it changes, run
  `scripts/check-fdroid-metadata.py`.

## Phase 9: Review

Nothing image-related is committed until the user has seen it. Show contact sheets for the store
screenshots, the Play canvases and the README recordings.

If the user may be away from the terminal, publish one private review page with the Artifact
tool. Put the decisions you need at the top, then:
- the Play screenshots as sideways-scrolling strips in listing order, tap to enlarge;
- the listing text with live character counts and copy buttons;
- the README section as GitHub will lay it out, at its real widths, with the recordings playing;
- the F-Droid order;
- notes on what was left out and the bugs found.

Ship the images as supporting files (downsized JPEG and WebP), not data URIs, so the page stays
small.

## Phase 10: Ship

1. Run the checks: `frame.py --self-test`, `play.py --self-test`, `check-readme.py`,
   `scripts/check-fdroid-metadata.py`, `seed.py --self-test`.
2. Commit in separate commits: seeder and scenario changes; capture-script changes; README copy;
   images plus the README table; Play tooling and captions.
3. Undo the Phase 5 list: the server policy, the backup files, demo mode, and any app settings
   changed for a shot.
4. List the bugs found during capture for the user. File them only if asked.
5. Open one PR with `/create-pr`. Say which devices and OS versions were used, what wasn't
   captured and why, and that the images must land before the next release is cut.
6. Give the user the Play set: the `play.py set` output directory and the listing text, ready to
   paste. The directory is generated, so say how to rebuild it.
