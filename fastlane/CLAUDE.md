# Store metadata

**The only store listings built from this directory are F-Droid and IzzyOnDroid. It does not
feed Google Play or the App Store.** (The repo `README.md` also embeds its screenshots.)
Nothing uploads it there: `play.yml` sends only the bundle and the R8 mapping, and `appstore.yml`
only the archive. Each store listing is a separate copy kept by hand in Play
Console or App Store Connect.

- Text here describes the **open-source build** (`foss` flavor). Store builds differ: see
  `ChannelPolicy` (`:core:common`) and the per-channel defaults in `AppPreferences`
  (`:core:data`). Site icons are the known case: the open-source build asks before loading them,
  and store builds load them by default. A sentence that is true here can be false on Play.
- Editing a file here never changes the Play listing. If the change should reach Play too, say
  so and edit the Console copy separately, reworded for the Play build's defaults.
- Never copy this text into a store listing unchanged. Check every claim about what the app does
  by default against that store's channel.
- The store copies already differ from these files (for example the Play short description).
  Don't sync either side to match the other without asking.

Format rules, character limits, the CI-written changelogs and the screenshot policy are in
`README.md`. Run `scripts/check-fdroid-metadata.py` after any edit.
