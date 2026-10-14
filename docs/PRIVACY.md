# Switchboard Privacy Policy

**Effective date:** October 13, 2026
**Applies to:** Switchboard for Android and iOS, in every distribution channel listed in section 3.
**Developer:** Garfiec Apps ("we", "us"). Contact: appsupport@garfiec.com.

Switchboard is a native mobile client for [LibreChat](https://www.librechat.ai/), an open-source
AI chat server. Switchboard does not come with a server. You connect it to a LibreChat server that
you, your employer, your school or another operator runs, and everything you do in the app is done
against that server.

This policy describes what the app does with your data on your device, what it sends and to whom,
and what we as the developer can and cannot see. It is written to be read alongside the privacy
policy of the LibreChat server you connect to, which governs what happens to your data once it
arrives there.

## 1. The short version

- **Switchboard does not collect your data.** We run no servers and receive nothing from the app
  automatically. What you send goes to the LibreChat server you chose, not to us. Switchboard has no
  analytics, advertising, crash-reporting or telemetry SDK in any channel. The only ways data about
  you can reach us are (a) aggregate crash and performance statistics that Google Play or Apple
  collect on their own and show to developers, which depend on your device settings and your
  distribution channel, and (b) things you choose to send us yourself, such as a diagnostic log
  attached to a bug report.
- **Your LibreChat server receives your content.** Messages, attachments, voice recordings you
  choose to transcribe server-side, your account details and your settings on that server go to the
  server you configured. The server's operator, not us, controls that data.
- **Two hard-coded third-party services exist, both optional.** Google's favicon service (for
  website icons next to web-search results) and GitHub's API (to check for new versions and show
  release notes, open-source channel only). Section 6 says exactly what each receives and how to
  turn it off.
- **Content can point the app at other hosts.** Images that your server or the AI model reference by
  full URL, and images inside AI-generated "artifacts", are fetched from wherever they are hosted.
- **Everything the app stores on your device is deletable** by signing out, clearing the cache, or
  uninstalling. One exception on iOS: Keychain items (your server address, and the sign-in tokens of
  any account you did not sign out of) survive uninstalling the app (section 5). You can delete your
  account on your server from inside the app.

## 2. Who is who

| Party | What they hold | Whose policy applies |
|---|---|---|
| **You, on your device** | Cached conversations, sign-in tokens, preferences, a local diagnostic log | This policy (section 5) |
| **Your LibreChat server operator** | Your account, conversations, files, memories, shared links, API keys, usage records; also whatever their AI providers and plugins receive | The operator's own privacy policy and terms |
| **AI model providers and tools behind your server** (OpenAI, Anthropic, Google, local models, web-search providers, MCP servers, etc.) | Whatever your server forwards to them | Their policies, as configured by your operator |
| **Google** (favicon service; Android speech services; Google Play) | See section 6 | Google's privacy policy |
| **Apple** (iOS speech services; App Store) | See section 6 | Apple's privacy policy |
| **GitHub** (release check, open-source channel only) | See section 6 | GitHub's privacy statement |
| **Us, the developer** | Nothing automatic. Aggregate platform statistics where enabled (section 7); anything you send us (section 8) | This policy |

## 3. Distribution channels

The app is built in two kinds: an **open-source build** and a **store build**. The differences are
limited to the items in this table; everything else in this policy applies equally to all channels.

| | Android, open source | Android, Google Play | iOS, App Store | iOS, sideloaded |
|---|---|---|---|---|
| Where you get it | GitHub Releases, Obtainium, F-Droid | Google Play | App Store | IPA from GitHub Releases |
| Package / bundle ID | `com.garfiec.librechat` | `com.garfiec.librechat.play` | `com.garfiec.librechat.ios` | `com.garfiec.librechat.ios` |
| Update check against GitHub (section 6.2) | Off by default; opt-in daily check | Never | Never | Never (no self-update on iOS) |
| Release notes fetched from GitHub on tap (section 6.2) | Yes, only when you open them | No | No | Yes, only when you open them |
| Website icons via Google (section 6.1) | Off until you answer a one-time prompt | On by default; can be turned off | On by default; can be turned off | Off until you answer a one-time prompt |
| Platform crash and performance statistics reaching us (section 7) | None. Google only reports on installs made through Play. | Android vitals, if your device shares usage and diagnostics with Google | Apple crash and analytics reports, if you have enabled sharing with app developers | None. Apple forwards reports only for App Store and TestFlight builds. |
| Analytics, ads or crash SDKs inside the app | None | None | None | None |

The Play build has a different package name on purpose: it is a separate app, so a device that
installed the open-source build can never be silently updated onto the store build. If a future
store build ever includes a crash-reporting or analytics SDK, this policy and the store's data
declarations will be updated before that build ships, and the open-source build will not carry it.

## 4. Data sent to your LibreChat server

Switchboard is a client. Apart from the specific exceptions in sections 6 and 7, every network
request the app makes goes to the server address you typed at setup. That server receives:

- **Account and sign-in data**: your email address or username and password when you sign in or
  register; two-factor codes; or, for single sign-on, the session your identity provider hands back.
  Sign-in and token refresh go over the server's normal API.
- **Your content**: the messages you send, the files, photos and camera captures you attach, text or
  files shared into the app from other apps, and edits, feedback (thumbs up/down with a reason) and
  regenerations. The server stores these and forwards what it needs to the AI providers and tools
  the operator configured.
- **Voice**: if you pick the "External" speech-to-text engine, the audio recording is uploaded to
  your server for transcription. If you pick a server text-to-speech voice, the text of the message
  is sent to your server, which returns audio. (Device engines are described in section 6.4.)
- **Server-side settings and objects you manage from the app**: presets, prompts, agents, memories,
  MCP server configuration, API keys, shared links, tags, projects, schedules, and the opt-out for
  memories. These live on the server; the app only edits them.
- **Gateway credentials, if you configure them**: if your server sits behind an access gateway such
  as Cloudflare Access, you can enter static request headers (for example a service-token ID and
  secret). The app adds them to requests to that server only, and only when the scheme, host and
  port match exactly, so they are never sent anywhere else.
- **Technical data inherent to any connection**: your IP address, and a browser-style
  `User-Agent` string. The app presents itself as a Chrome browser because the stock LibreChat
  server rejects and bans non-browser clients; it does not include your real device model.
- **Optional background refresh**: if you turn on background prefetch (off by default), the app
  periodically asks your server for recent conversations, and optionally their attachments, so they
  are available offline. It can be limited to unmetered networks.

What the server does with all of this is up to its operator. If you do not know who operates your
server or how they handle data, ask them before using the app.

**Unencrypted connections.** The app allows plain `http://` server addresses because people run
LibreChat on home networks. If you enter one, the app warns you that the connection is unencrypted
and asks you to confirm. Everything in this section, including your password, then travels in the
clear on that network.

**Single sign-on.** If your server uses an external identity provider, sign-in happens in a web view
inside the app that loads the provider's pages. The provider sees what any browser login would show
it. Cookies from previous logins are cleared before each sign-in (Android) or kept in a
non-persistent store that is discarded afterwards (iOS).

## 5. Data stored on your device

| Data | Where | Protection | Removed when |
|---|---|---|---|
| Sign-in tokens (access and refresh) for each account | Android: `EncryptedSharedPreferences` (AES-256, key in the Android Keystore). iOS: Keychain, accessible after first unlock, not synced to iCloud Keychain | Encrypted at rest by the OS | You sign out (that account) or remove the account |
| Server address and account roster | Preferences store. On iOS the server address is also mirrored to the Keychain so it survives a reinstall | App-private storage | You clear the server; iOS Keychain copy persists across uninstall until cleared |
| Gateway headers (section 4) | Local database, per server | App-private storage; **stored unencrypted** by design, so a broken device keystore cannot lock you out of your server | You delete them in Settings. Not removed on sign-out, because they are needed to sign back in |
| Conversations, messages, tags, drafts | Local database (`librechat.db`), a cache of what the server holds | App-private storage | Sign out (rows for that account are deleted), remove account, or uninstall |
| Cached agent and preset listings | Local database | App-private storage | Uninstall (not purged on sign-out; overwritten on the next fetch) |
| Pinned artifact snapshots (Android home-screen shortcuts) | Local database and your launcher | App-private storage | You remove the shortcut or uninstall |
| Images, downloaded files, camera captures, shared-in files, temporary speech audio | Cache directory (image cache up to 250 MB on Android) | App-private storage | Settings → Data → Clear cache; on account switch (images); OS cache eviction; uninstall |
| Preferences (theme, chat layout, speech settings, update-check state, per-model usage counts used for home-screen shortcuts, "not now" snoozes) | Preferences store | App-private storage | Uninstall; individual settings reset in Settings |
| Diagnostic log (section 8) | App-private files, max 4 MiB, entries older than 7 days dropped | Redacted before it is written | Settings → Data → Clear logs; uninstall |
| Most-used models | Published as launcher / quick-action shortcuts whose labels are model names | Visible on your home screen's long-press menu | Sign out (shortcuts are cleared) |

**Backups.** On Android the app opts out of system backups (`allowBackup="false"`), so none of the
above is included in Google or device-to-device backups. On iOS the app has not excluded its data
from device backups, so the local database, preferences and diagnostic log can be included in an
iCloud or computer backup of your device, protected by that backup's own encryption. iOS Keychain
items follow Apple's rules: they persist if you delete the app without signing out first, and are
restored only to the same device from an encrypted backup.

## 6. Third parties the app itself contacts

This section lists every destination other than your server that the app's own code can connect
to. There are no others.

### 6.1 Google favicon service (website icons)

When a reply includes web-search results, the app can show each source's site icon. To do that it
requests `https://www.google.com/s2/favicons?domain=<hostname>` for each result, or, when the search
payload already names an icon image, that icon's own URL on whatever host serves it.

- **What is sent**: the hostname of each result site, your IP address, and a browser-style user
  agent. No sign-in tokens or gateway headers are ever attached to these requests.
- **What it reveals**: a list of the sites your searches surfaced, which can hint at what you asked.
- **Control**: on the open-source channels the feature is off until the app asks you once, in the
  chat, whether to enable it. On the store builds it is on by default. Either way it is one switch
  in Settings and takes effect immediately; while it is off the app shows a generic globe icon and
  makes no request.

### 6.2 GitHub (new-version check and release notes)

Open-source channels only. The store builds never contact GitHub.

- **Daily update check** (Android open-source build only): off by default. If you turn it on in
  Settings, once a day when you open the app it requests the release list at
  `https://api.github.com/repos/garfiec/Librechat-Mobile/releases`.
- **Release notes**: when you open "Release notes" in Settings → About, the app requests the notes
  for your installed version from the same API. Nothing is fetched until you tap.
- **What is sent**: your IP address and the user agent `Switchboard/<version>`. These requests go
  through a separate HTTP client that carries none of your server's tokens, gateway headers or
  browser identity.
- **Nothing is installed automatically.** The app tells you a version exists and links to it.

### 6.3 Hosts referenced by your content

Some images are not served by your server but referenced by full URL: an image the model returned
as a URL, an avatar from a social-login provider, or an `<img>` tag inside an AI-generated artifact
(an HTML, React, SVG or Markdown preview rendered in a web view). The app fetches those from the
host in the URL when the message or artifact is displayed, which reveals your IP address to that
host. Your server's sign-in token is only ever attached to requests to your server's own host.

Artifact previews run entirely from code bundled in the app (no scripts are downloaded), and the
preview page itself may load only images (`https:`), never remote scripts, styles or frames. An
artifact can, however, navigate the preview to another website, which then behaves like an
ordinary browser page under that site's policy. The app does not currently ask before loading a
remote image inside an artifact, and does not block such navigation.

### 6.4 Device speech services (Google on Android, Apple on iOS)

**Speech-to-text.** The default "Browser" engine uses your device's speech recognizer. The app
prefers on-device recognition (a setting that is on by default), which keeps audio on the phone.
Audio does leave the device in these cases:

- On Android 8 to 11, the system recognizer (normally Google's) is used and may process audio in
  the cloud.
- On Android 12 and later, if on-device recognition is unavailable for your device or language, or
  if the on-device recognizer fails mid-session, the app falls back to the network recognizer and
  audio is processed by that provider (normally Google).
- On iOS, if on-device recognition is unsupported for your language or fails, the app falls back to
  Apple's server-side speech recognition.
- If you turn the on-device preference off, the network recognizer is used directly.

Those providers process audio under their own policies. The alternative "External" engine sends
the recording to your LibreChat server instead (Android only; on iOS "External" currently behaves
like "Browser").

**Text-to-speech.** The default engine is the system voice: Apple's on-device synthesizer on iOS,
and on Android whichever text-to-speech engine your device has (Google's may use network voices
depending on its own settings). Choosing a server voice sends the message text to your server, as
described in section 4.

### 6.5 Your identity provider

Only during single sign-on, as described in section 4. The app does not contact it otherwise.

### 6.6 Operating-system features you trigger

Sharing a message, exporting a conversation or saving a diagnostic log hands the data to the system
share sheet or file picker, and from there to the app or location you choose. Opening a link opens
your browser. These are your actions, and the receiving app's policy applies.

## 7. Crash and performance statistics collected by the platforms

Switchboard contains no crash-reporting code of its own. Google and Apple, however, collect crash
and performance information at the operating-system level for apps installed through their stores,
and show developers aggregated results. That data never passes through the app, and we cannot
switch it on or off.

**Google Play build (Android vitals).** If your Android device is set to share usage and
diagnostics with Google (Settings → Google → Usage & diagnostics), Android reports crashes,
"application not responding" events, start-up times, rendering and battery metrics for Play-installed
apps to Google. We see this in Play Console as percentages and counts broken down by app version,
Android version, device model and country, plus clusters of crash stack traces. We do not see device
identifiers or accounts. Google states that Play Console retains this for 90 days, and its reporting
API for up to three years. A stack trace can incidentally contain text from an error message; the
app does not log message content, but an unexpected network error could include your server's
hostname. **The open-source Android build is not reported on at all**, because Google excludes
installs not made through Play.

**App Store build (Apple crash reports and App Analytics).** If you have enabled Settings →
Privacy & Security → Analytics & Improvements → "Share with App Developers", Apple forwards crash
logs and aggregated usage statistics for the app to us through App Store Connect and Xcode. Per
Apple, this data is not linked to your Apple Account; it includes device model, OS version and the
crash stack trace. **The sideloaded iOS build is not covered**, because Apple forwards reports only
for App Store and TestFlight distribution.

We use this information only to find and fix bugs. We do not combine it with anything else and do
not share it further.

## 8. The local diagnostic log, and what you can choose to send us

The app keeps a small, rolling diagnostic log on your device to help diagnose problems. It records
events such as "request to the server failed with status 502", app start-up details (app version,
OS version, a coarse device model such as "Google Pixel 8"), and crash stack traces. Before anything
is written, a redaction pass runs over every entry: message and conversation content and server
response bodies are dropped entirely; email addresses, tokens, hostnames and IDs are replaced with
short one-way hashes so that repeated occurrences can be correlated but the originals cannot be
recovered. The log is capped at 4 MiB and entries older than seven days are discarded.

**The log never leaves your device on its own.** Settings → Data lets you view its size, save or
share it, and clear it. If you choose to send it to us (for example attached to a bug report or
email), we receive what the file contains and your email address or GitHub identity. We keep it
only as long as needed to investigate the issue, and no longer than 90 days, and do not share it.

## 9. Permissions

| Permission | Why | When it is used |
|---|---|---|
| Network / Internet | Talking to your server and the destinations in section 6 | Always available; no prompt |
| Network state | To show the offline banner and honour the "unmetered only" prefetch setting | Always available; no prompt |
| Microphone (Android `RECORD_AUDIO`, iOS Microphone) | Voice input | Only when you tap the microphone; you can deny it and type instead |
| Speech recognition (iOS) | On-device or Apple speech-to-text | Only when you use voice input |
| Camera | Taking a photo to attach | Only when you choose "Camera" in the attachment menu |
| Photos (iOS) / file picker | Attaching images and files | Through the system picker; the app sees only what you select |
| Storage, Android 9 and older only | Saving exported files on old Android versions | Only when exporting |
| Wake lock, boot-completed, foreground service (Android) | Added by the Android WorkManager library so an optional background prefetch job can run and survive a reboot | Only if you turn background prefetch on |
| Background fetch / processing (iOS) | The same optional prefetch job | Only if you turn background prefetch on |

The app requests no location, contacts, calendar, notification, Bluetooth, advertising-ID or
device-identifier access, and sends no push notifications.

## 10. How we use data

We, the developer, use the only data we can receive (section 7 platform statistics and anything you
send under section 8) to fix bugs and improve the app. We do not sell, rent or trade any data, do
not use it for advertising, and do not build profiles. The app does not use your data for anything
other than providing the features you invoke.

The LibreChat web client can load Google Tag Manager when a server operator configures it;
Switchboard ignores that server setting and loads nothing.

## 11. Retention and deletion

**On your device.** Section 5 lists what is kept and how to remove each item. In summary:

- *Sign out* deletes that account's tokens and its cached conversations, messages, drafts and tags,
  and clears the model shortcuts.
- *Settings → Data → Clear cache* empties images, downloads and temporary files.
- *Settings → Data → Clear logs* deletes the diagnostic log.
- *Uninstalling* removes everything, except that on iOS Keychain items (tokens, if you did not sign
  out, and the server address) persist in the system Keychain until overwritten or cleared.

**On your server.** Your account and content are retained by your server operator under their
policy. Switchboard gives you the server's own controls in Settings: delete individual
conversations, clear or archive all conversations, delete memories, revoke shared links, delete API
keys, and **Settings → Account → Delete account**, which asks your server to delete your account and
its data (after two-factor confirmation if enabled) and then signs you out. We have no access to
your server and cannot delete, export or recover anything there; requests about server-held data go
to the operator.

**With us.** We hold no data about you unless you sent us a diagnostic log or other correspondence.
To have that deleted, email appsupport@garfiec.com. Platform statistics (section 7) are held by Google
and Apple under their retention rules; we cannot delete individual records from them because they
are not attributable to individuals.

## 12. Withdrawing consent

Every optional data flow has a switch:

| Flow | Where to turn it off |
|---|---|
| Website icons via Google | Settings → Chat → Show website icons (or decline the first-use prompt) |
| Daily update check | Settings → General → Check for updates (Android open-source only; off by default) |
| Background prefetch | Settings → Data → Background prefetch |
| Network speech recognition | Settings → Chat → Speech: keep "Prefer on-device" on, or switch to the "External" engine to use your own server, or do not use voice input |
| Server-side speech | Choose the device engine for speech-to-text and text-to-speech |
| Android vitals | Android Settings → Google → Usage & diagnostics |
| Apple crash and analytics sharing | iOS Settings → Privacy & Security → Analytics & Improvements |
| Microphone / camera | Deny or revoke the permission in system settings |
| Everything | Sign out and uninstall |

## 13. Security

Tokens are stored in the platform's encrypted credential store (section 5). Tokens and gateway
headers are attached only to requests whose destination matches your configured server. Requests
to third parties use a separate client with no credentials. Connections use TLS whenever your
server address is `https://`; the app warns before accepting an `http://` address. All code that
runs inside the artifact web views is bundled with the app and pinned by checksum; no scripts are
downloaded. The app is open source, and you can inspect or build it yourself.

No app can guarantee security. Keep your device locked, keep your server up to date, and prefer
`https://` server addresses.

## 14. Children

Switchboard is not directed at children. It is a tool for connecting to an AI chat server that
someone else operates, and the minimum age is whatever that operator and the applicable law set,
and never below 13 (or 16 where local law requires). We do not knowingly collect data from
children; because the app sends us nothing automatically, there is nothing to delete on our side.
Parents or operators with concerns should contact the server operator and, if needed, us.

## 15. Legal basis and international users

Where data protection law such as the GDPR or UK GDPR applies to us, the limited processing we
perform rests on our legitimate interest in maintaining a working, secure app (platform crash
statistics) and on your consent or request (anything you send us). Your server operator is the
controller for data on the server, and Google and Apple are independent controllers for the
statistics they collect under their own settings. Depending on where you live you may have rights
to access, correct, delete or object; exercise them with the party that holds the data (section 2),
and with us at appsupport@garfiec.com for anything we hold.

## 16. Changes to this policy

When the app's data handling changes, this policy changes with it. The current version lives at
https://github.com/garfiec/Librechat-Mobile/blob/develop/docs/PRIVACY.md. The effective date at
the top tells you when it last changed, and the changelog for each release notes privacy-relevant changes. Material changes
to a store build's data collection will be reflected in the store's data declarations before that
build ships.

## 17. Contact

Garfiec Apps: appsupport@garfiec.com. Bug reports and questions about the app can also be opened
at https://github.com/garfiec/Librechat-Mobile/issues (GitHub's privacy statement applies there).
Questions about data held on a LibreChat server go to that server's operator.

