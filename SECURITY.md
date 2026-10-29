# Security

Switchboard connects to a LibreChat server you choose and holds your sign-in for it. This page
lists what the app guarantees about where your requests and credentials go, and the rule each
guarantee places on code changes. A change that would break one of them is a bug, not a trade-off.

What the app sends and stores, and to whom, is covered in the [privacy policy](docs/PRIVACY.md).

## Guarantees

### 1. You sign in only to a verified LibreChat server

Before a server address is saved, the app fetches `/api/config` from it and checks that the answer
looks like LibreChat's config. A host that answers with anything else, such as a web page, a
login portal or another API, is rejected: the address isn't saved and you can't sign in to it. The
check runs the same way when you add a second account.

**Rule:** a request that talks to the user's server, or carries their credentials, goes only to an
address that passed this check. It takes the base URL from the request's identity snapshot, not
from user input or response content.

### 2. Insecure connections are flagged

An `http://` server address shows a warning that the connection is not encrypted, and the app goes
no further until you accept it. Plain HTTP is allowed because LibreChat is often self-hosted on a
home network, but it is never used silently: an address without a scheme becomes `https://`.

On Android, certificates you or your device manager installed are trusted alongside the system's,
so a server behind a private certificate authority works. When one of those certificates vouched
for your server, sign-in and Settings → General say so.

**Rule:** never downgrade a connection to `http://` on the user's behalf, and keep both warnings
in place.

### 3. Credentials never cross between servers or accounts

Every request is bound to one server, one account and that account's token when it starts, and
keeps them for its whole life. Switching accounts mid-request can't attach one account's token to
another account's server, and a reconnecting stream stops rather than resuming under a different
account.

- The session token is attached only to requests for the server's host.
- Gateway headers you configure for a server (for example Cloudflare Access tokens) are attached
  only when scheme, host and port all match that server.
- Requests to anyone else carry none of your credentials. GitHub requests use a separate client.
  Images, including site icons, are loaded through the app's main client, which attaches the
  token, the image cookie and gateway headers only for your server.
- Tokens are stored encrypted, in EncryptedSharedPreferences on Android and the Keychain on iOS.

**Rule:** read the server, account and token from the request's identity snapshot, never from the
live active account. Code that runs during an account switch or add-account flow must leave the
active account's state alone.

### 4. Nothing outside your server is contacted without your say (open-source builds)

On the open-source builds, the app contacts third parties only for features you switch on. Site
icons for web-search results are off until the app asks you once, and the daily update check is
off by default. Release notes are fetched only when you open them. The store builds never contact
GitHub; their site icons are on by default and can be switched off in Settings.

The app has no analytics, advertising, crash-reporting or telemetry SDK in any build.

**Rule:** a new request to a host other than the user's server is opt-in on the open-source
builds, and gets an entry in section 6 of the [privacy policy](docs/PRIVACY.md). Adding an
analytics or telemetry SDK is not accepted.

### 5. The app never downloads libraries to run

All the JavaScript libraries the artifact, diagram and math previews use (such as KaTeX, mermaid,
marked, highlight.js, Tailwind, Babel and React) ship inside the app. CI checks them against a sha256 lock.

An artifact's own code comes from the chat and runs only inside its preview. Preview pages may load
remote `https:` images, but never remote scripts, styles or frames. HTML, React and document
previews can't navigate to another website.

**Rule:** a new WebView dependency is vendored and registered in `scripts/web-assets.json` in the
same change. Repin through `/update-web-assets`, never by editing a vendored file.

### 6. Logs are kept free of sensitive data

The diagnostic log you can attach to a bug report is meant to hold no messages, credentials or
server responses. Code doesn't log them, and everything written to the log also passes through a
redactor first. The redactor recognises common token formats, email addresses, URLs and IDs and
replaces them with short hashes, removes JSON response bodies, and drops any field it doesn't know
to be safe. It works by pattern, so it can't catch everything.

**Rule:** don't log sensitive data in the first place. The redactor is a backstop, not permission
to log a token and rely on it being caught. A new log field is dropped until its key is added to
the redactor's safe list on purpose.

### 7. Links into the app are checked

On Android, every `librechat://` link goes through one resolver. It accepts only the link kinds the
app declares, checks IDs against a strict pattern and passes other values on as plain text, so a
link from another app can't open an arbitrary screen or address. iOS doesn't handle these links
yet.

**Rule:** a new deep link is a branch in `DeepLinks.resolve`, and values taken from a link are
treated as untrusted input.

## Known exceptions

These fall short of the guarantees above today:

- **Images referenced by your content.** An image the model returns as a URL, an avatar from a
  social-login provider, or an `<img>` inside an artifact preview is loaded from the host in its
  URL when it is shown, without asking. That host sees your IP address. No credentials are sent
  with it.
- **Same-host token scoping.** The session token is matched on the host alone, not the scheme or
  port, so a request to another port on your server's host, or to its `http://` address, would
  carry it.
- **Leaving a preview.** A link in a math block in a chat reply (KaTeX `\href`) opens the linked
  website inside the math view, on both platforms. On Android, a form that posts can also send a
  Markdown or SVG preview to another website.

## Verifying a build

The [README](README.md#verifying-the-signing-key) shows how to check a release's signing key,
checksum and build provenance.

## Reporting a vulnerability

Most security problems are fine to report as a normal
[issue](https://github.com/garfiec/Librechat-Mobile/issues).

Report privately instead if someone else could use the problem against people who haven't
updated yet: for example, if opening a link, viewing a chat, or connecting to a server could
expose a password, a sign-in, or someone's chats. If you're not sure, report privately. Use
[Report a vulnerability](https://github.com/garfiec/Librechat-Mobile/security/advisories/new) on
the Security tab, or email appsupport@garfiec.com.

Problems in the LibreChat server itself belong with the
[LibreChat project](https://github.com/LibreChat-AI/LibreChat/security).
