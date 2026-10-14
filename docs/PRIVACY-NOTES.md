# Switchboard privacy policy: store declarations

Internal companion to [PRIVACY.md](PRIVACY.md), not part of the published policy. It records how
the policy maps onto Google Play's Data safety form and Apple's privacy label, and what to keep in
step with it.

## A. Google Play Data safety (`com.garfiec.librechat.play`)

The app declares **no data collected** and **no data shared**. Google's form covers data the app's
own code and libraries send off the device to the developer or to third parties; Switchboard sends
user data only to the LibreChat server the user chose, which is the same reading section B takes
for Apple. Android vitals is collected by Google Play, not by the app, so it is not declared.

| Form question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | No |

With "No", the form skips the encryption, deletion and per-type questions. Play Console still asks
for an account-deletion URL separately, because the app can create accounts (section C, item 4).

If review asks for a fuller declaration, the traffic to the user's server can be declared as
collected, not shared. The policy text supports either.

| Form question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all collected data encrypted in transit? | Yes (TLS when the user's server is `https://`; note the user-chosen `http://` case in the policy) |
| Do you provide a way for users to request deletion? | Yes: in-app account deletion (Settings → Account → Delete account) plus sign-out/uninstall; external URL = this policy, section 11 |

| Data type | Collected | Shared | Optional | Purpose | Notes |
|---|---|---|---|---|---|
| Personal info → Email address, Name, User IDs | Yes | No | Required for sign-in | App functionality, Account management | Sent to the user's own server to sign in |
| Personal info → Other (authentication tokens, 2FA codes) | Ephemeral / not stored by us | No | Required | App functionality | Declared under Account management if the form needs a row |
| Messages → Other in-app messages | Yes | No | Required for the app's purpose | App functionality | Chat content sent to the user's server |
| Photos and videos → Photos, Videos | Yes | No | Optional | App functionality | Attachments the user picks |
| Files and docs | Yes | No | Optional | App functionality | Attachments the user picks |
| Audio → Voice or sound recordings | Yes | No | Optional | App functionality | Only with the "External" speech engine (uploaded to the user's server). With the device engine, the OS recognizer sends the audio, as with system voice typing, so it is not declared |
| Web browsing → Web browsing history | No | No | | | Search is performed by the server; favicon hostnames are a per-result lookup, not a history. Disclosed in the policy instead |
| App activity → App interactions, Other user-generated content | Yes (user-generated content: prompts, agents, presets, memories) | No | Optional | App functionality | Stored on the user's server |
| App info and performance → Crash logs, Diagnostics | No | No | | | The app sends none; Android vitals is collected by the platform. Revisit if a crash SDK is ever added |
| Device or other IDs | No | No | | | None read or sent |
| Location, Contacts, Calendar, Health, Financial | No | No | | | Not accessed |

"Shared" is No throughout: the only recipient is a server the user selected, and the favicon and
GitHub requests carry no user data types.

## B. Apple privacy label (App Store build)

Apple defines "collect" as transmitting data off the device in a way that lets the developer or
their partners access it beyond servicing the request. Data sent to a server the user chooses is
accessible to neither, and the developer does not disclose data Apple collects itself (crash
reports under the user's analytics-sharing setting). The label is **Data Not Collected**. If App
Review asks for more, the fuller declaration is:

| Data type | Linked to identity | Used for tracking | Purpose |
|---|---|---|---|
| Contact Info → Email Address, Name | Yes (on the user's server) | No | App Functionality |
| User Content → Other User Content (messages), Photos or Videos, Audio Data | Yes | No | App Functionality |
| Identifiers → User ID | Yes | No | App Functionality |
| Diagnostics → Crash Data | Not declared | No | Collected by Apple under the user's setting, not by the app |

Tracking: No. The app includes no SDK that links data across apps or companies.

## C. Keeping the policy in step

1. **Policy URL.** The policy is published as the rendered GitHub page,
   `https://github.com/garfiec/Librechat-Mobile/blob/develop/docs/PRIVACY.md`, which meets Play's
   requirement for a public, non-PDF, non-geofenced URL. If it moves (for example to garfiec.com),
   update section 16 and the Play Console entry together.
2. **Crash reporting.** The policy describes Android vitals: crash, ANR and performance reporting
   that Google Play collects for Play installs from users who share usage and diagnostics, with no
   SDK in the app and only aggregates and crash clusters visible to the developer. Adding a
   crash-reporting SDK (Firebase Crashlytics, the Play Core/Integrity libraries, Sentry) to the
   `play` flavor changes sections 3 and 7 of the policy, section A here (Crash logs and
   Diagnostics become collected) and the F-Droid metadata, and the open-source build must stay free
   of it.
3. **In-app privacy-policy link.** Both Google Play and App Store guideline 5.1.1(i) require the
   policy to be linked from inside the app: Settings → General → About → Privacy policy, which opens
   `PRIVACY_POLICY_URL` in `GeneralSettingsScreen.kt`. If the policy moves (item 1), update that
   constant too and leave a stub at the old path, since installed builds (F-Droid ones lag a
   release or more) keep linking to it.
4. **Account-deletion URL.** Play Console asks for a web URL for account deletion. Section 11 of
   the policy serves:
   `https://github.com/garfiec/Librechat-Mobile/blob/develop/docs/PRIVACY.md#11-retention-and-deletion`.
5. **Apple crash-reporting paragraph.** Check section 7 of the policy against Apple's current
   documentation before the first App Store release: the setting name ("Share with App
   Developers"), what a forwarded crash report contains, the "not linked to Apple Account"
   statement, and that sideloaded builds are not reported on (Organizer and App Store Connect cover
   App Store and TestFlight distribution).
6. **`PrivacyInfo.xcprivacy`.** None exists in `iosApp/`. Apple requires required-reason API
   declarations (UserDefaults and file-timestamp APIs used by DataStore and the log writer) for App
   Store submission; add one before the first upload.
7. **Behaviour the policy discloses that could be tightened instead:** remote images inside
   artifact previews load without a prompt; artifact previews have no navigation guard on either
   platform (no `shouldOverrideUrlLoading` / `decidePolicyForNavigationAction`); cached agents and
   presets are not purged on sign-out; favicon URLs carried in the search payload bypass the
   Google favicon service; gateway headers are stored unencrypted; iOS does not exclude app data
   from device backups; iOS Keychain items outlive an uninstall; on-device speech recognition falls
   back to the network recognizer without telling the user; the Play and App Store builds show
   website icons by default. Fixing any of these lets the policy text get simpler.
