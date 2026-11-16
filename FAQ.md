# FAQ

## Why not just add the web app to my home screen?

You can — LibreChat ships a PWA manifest with `display: standalone`, so an "Add to Home Screen" install gets you an icon and a chrome-less window. That covers the cosmetics. What it doesn't cover:

| | Web PWA | This app |
|---|---|---|
| **Receive shares from other apps** | No — the manifest declares no `share_target` | Text, images, arbitrary files, and multi-select, from any app *(Android)* |
| **Home-screen shortcuts** | One icon; the manifest declares no `shortcuts` | Per-model launcher shortcuts / quick actions on both platforms, plus artifacts pinned as their own icons *(Android)* |
| **Reading old chats offline** | No — the service worker precaches app assets, not conversation data | List and opened threads render from a local database |
| **Multiple accounts / servers at once** | One session per browser profile | Switch instantly; storage scoped per account |
| **On-device dictation** | Web Speech API is missing or vendor-hosted on mobile browsers | Platform on-device recognizers, with server STT as fallback |
| **System back gesture** | Navigates browser history | Navigates the app, with predictive-back previews |

See [Why use this instead of the web app?](README.md#why-use-this-instead-of-the-web-app) for the longer version.

## Can I use a self-signed TLS certificate?

Yes, on both platforms, once the CA that signed it is installed on the device and the certificate meets the requirements below.

Both platforms enforce TLS certificate requirements that ordinary browsers do not. A certificate that works in Chrome or Safari may still be rejected by the mobile app.

### Common requirements (iOS and Android)

| Requirement | Details |
|---|---|
| **Subject Alternative Name** | DNS name must appear in the SAN extension. The CommonName fallback was removed in iOS 13 and in apps targeting Android 9+ (API 28). |
| **Signature algorithm** | SHA-256 or stronger. SHA-1 is rejected by iOS 13+ and by Android 10+ (API 29). |
| **Extended Key Usage** | The leaf certificate must include `id-kp-serverAuth` (TLS Web Server Authentication). Required by Apple for certs issued after July 1, 2019. |
| **Basic Constraints** | The issuing CA certificate must have `CA:TRUE`. |
| **Key size** | RSA ≥ 2048 bits, or any modern ECDSA curve (e.g. P-256). Much smaller keys may fail handshake via JSSE / Conscrypt algorithm constraints. |

[`mkcert`](https://github.com/FiloSottile/mkcert) generates certificates that meet all of the above automatically.

### iOS

iOS adds one platform-specific requirement on top of the table above:

| Requirement | Details |
|---|---|
| **Validity period** | ≤ 825 days for the leaf certificate (Apple's cap for certs issued after July 1, 2019). |

> The widely-cited **398-day cap does not apply here** — that stricter limit only governs certificates chaining to the Root CAs that Apple preinstalls on the device. A CA you install and trust yourself is exempt from the 398-day rule, but the older 825-day cap still applies.

**Setup:**

1. Generate a compliant certificate signed by your own CA.
2. On your iPhone: **Settings → General → VPN & Device Management** — install your CA certificate.
3. Go to **Settings → General → About → Certificate Trust Settings** — enable full trust for your CA.

Once the CA is trusted on the device, you only need to re-install it if you create a new CA. The server certificate itself (which you replace when it expires) does not need to be installed separately.

Plain HTTP works on iOS only to a local address: the server's local IP, a `.local` name, or a single-label name such as `http://nas`. iOS App Transport Security refuses plain HTTP to a domain name (`http://chat.example.com`) and to public IP addresses, and the app tells you to use `https://` instead.

### Android

Android imposes no equivalent validity-period cap — multi-year self-signed certificates are accepted as long as the requirements in the common table above are met.

Since Android 7.0 (API 24), apps trust only the system CA store unless they opt in to user-installed CAs. Switchboard opts in, so a CA you install yourself is trusted alongside the system's.

**Setup:**

1. Generate a compliant certificate signed by your own CA.
2. On the device, install the CA certificate: **Settings → Security → Encryption & credentials → Install a certificate → CA certificate** (the path varies by manufacturer; search Settings for "CA certificate").
3. Connect as usual. The sign-in screen notes that the server is trusted through a certificate authority installed on the device, and **Settings → General → About** shows the same under the server address.

Without the CA installed, the server check rejects the certificate and suggests installing the server's private CA.

If you can't install a CA on the device (a managed phone, for example), use a publicly trusted certificate instead (Let's Encrypt, ZeroSSL), or put the server behind a reverse proxy such as Caddy or Cloudflare Tunnel that terminates TLS with one. On a trusted local network you can also connect over plain HTTP and accept the in-app warning.

> **Looking ahead:** apps that target API 37+ (Android 17) get Certificate Transparency enabled by default for TLS connections. When Switchboard's `targetSdk` reaches 37, self-signed leaf certificates without SCTs will need `<certificateTransparency enabled="false"/>` in the Network Security Config.

## Can I use Switchboard behind Authelia?

Yes. When your server sits behind an [Authelia](https://www.authelia.com/) forward-auth gate, Switchboard recognises the gate when you connect and offers **Sign in with Authelia**. You sign in on Authelia's own pages in the system browser (password, two-factor, passkeys and password managers all work), and the app gets a token that lets it through the gate. Your LibreChat sign-in comes next, as usual.

This needs a one-time setup by the admin, because the app signs in as an OAuth client of Authelia. If that isn't set up, the app offers to sign in on Authelia's page inside the app instead. That keeps Authelia's session cookie, which expires on Authelia's schedule, and passkeys don't work there.

### Authelia setup (admins)

Authelia 4.38 or later. Register a public client for the app. `client_id` can be anything (users enter a name other than `switchboard` under **Advanced**); the redirect URI must be exactly `http://127.0.0.1/callback` (the app listens on a free port, and Authelia ignores the port for loopback addresses):

```yaml
identity_providers:
  oidc:
    clients:
      - client_id: 'switchboard'
        client_name: 'Switchboard'
        public: true
        authorization_policy: 'two_factor'
        require_pkce: true
        pkce_challenge_method: 'S256'
        require_pushed_authorization_requests: true
        redirect_uris:
          - 'http://127.0.0.1/callback'
        scopes: ['offline_access', 'authelia.bearer.authz']
        audience: ['https://chat.example.com']
        grant_types: ['authorization_code', 'refresh_token']
        response_types: ['code']
        response_modes: ['form_post']
        consent_mode: 'explicit'
        token_endpoint_auth_method: 'none'
```

LibreChat already uses the `Authorization` header for its own sign-in, so the app sends Authelia's token as `Proxy-Authorization`. Let the authz endpoint read it, and keep the cookie strategy for browsers:

```yaml
server:
  endpoints:
    authz:
      forward-auth:
        implementation: 'ForwardAuth'
        authn_strategies:
          - name: 'HeaderProxyAuthorization'
            schemes: ['Bearer']
          - name: 'CookieSession'
```

The reverse proxy must pass `Proxy-Authorization` on to Authelia. **Caddy** drops it unless told otherwise:

```
forward_auth authelia:9091 {
	uri /api/authz/forward-auth
	header_up Proxy-Authorization {http.request.header.Proxy-Authorization}
	copy_headers Remote-User Remote-Groups Remote-Email Remote-Name
}
```

Refresh tokens last 90 minutes by default, so a phone left alone longer has to sign in to Authelia again. To keep users signed in longer, give the client longer token lifespans (`identity_providers.oidc.lifespans.custom`, then `lifespan:` on the client), for example 30 days for refresh tokens.
