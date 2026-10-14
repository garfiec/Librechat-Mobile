# Switchboard privacy policy: store forms and open questions

Internal companion to [PRIVACY.md](PRIVACY.md). Not part of the published policy. It maps the
policy onto Google Play's Data safety form and Apple's privacy label, and lists what must be
decided or filled in before the policy is published.

## A. Suggested Google Play Data safety declaration (`com.garfiec.librechat.play`)

Derived from this policy. Google's form covers data the app's own code and libraries send off the
device; it has no explicit rule for data sent to a server the user chooses. The conservative
reading, recommended here, is to declare that traffic as collected (not shared) because Play review
is mechanical and under-declaring is the costlier error. Android vitals is collected by Google
Play, not by the app, and is not declared. See section C, item 2, for the decision.

| Form question | Suggested answer |
|---|---|
| Does your app collect or share any of the required user data types? | Yes |
| Is all collected data encrypted in transit? | Yes (TLS when the user's server is `https://`; note the user-chosen `http://` case in the policy) |
| Do you provide a way for users to request deletion? | Yes: in-app account deletion (Settings → Account → Delete account) plus sign-out/uninstall; external URL = this policy, section 11 |

| Data type | Collected | Shared | Optional | Purpose | Notes |
|---|---|---|---|---|---|
| Personal info → Email address, Name, User IDs | Yes | No | Required for sign-in | App functionality, Account management | Sent to the user's own server to sign in |
| Personal info → Other (authentication tokens, 2FA codes) | Ephemeral / not stored by us | No | Required | App functionality | Declared under Account management if the form insists on a row |
| Messages → Other in-app messages | Yes | No | Required for the app's purpose | App functionality | Chat content sent to the user's server |
| Photos and videos → Photos, Videos | Yes | No | Optional | App functionality | Attachments the user picks |
| Files and docs | Yes | No | Optional | App functionality | Attachments the user picks |
| Audio → Voice or sound recordings | Yes | No | Optional | App functionality | Only with the "External" STT engine (uploaded to the user's server). Device-engine audio goes to Google's speech service by the OS, not by the app, so not declared here |
| Web browsing → Web browsing history | No | No | | | Search is performed by the server; favicon hostnames are a per-result lookup, not a history. Disclosed in the policy instead |
| App activity → App interactions, Other user-generated content | Yes (user-generated content: prompts, agents, presets, memories) | No | Optional | App functionality | Stored on the user's server |
| App info and performance → Crash logs, Diagnostics | **No** | No | | | The app sends none. Android vitals is Google's own collection and is outside the form's scope (platform-collected). Re-check if a crash SDK is ever added |
| Device or other IDs | No | No | | | None read or sent |
| Location, Contacts, Calendar, Health, Financial | No | No | | | Not accessed |

"Shared" is No throughout because the only recipient is a server the user selected and operates,
which is a user-initiated transfer, and the favicon/GitHub requests carry no user data types.

## B. Suggested Apple privacy nutrition label (App Store build)

Apple defines "collect" as transmitting data off the device in a way that lets the developer or
their partners access it beyond servicing the request. Data sent to a server the user chooses is
accessible to neither, and Apple says the developer is not responsible for disclosing data Apple
collects itself (crash reports under the user's analytics-sharing setting). On that reading the
honest label is **"Data Not Collected"**, which is also the position taken by comparable
self-hosted-client apps. If App Review pushes back, the fallback declaration is:

| Data type | Linked to identity | Used for tracking | Purpose |
|---|---|---|---|
| Contact Info → Email Address, Name | Yes (on the user's server) | No | App Functionality |
| User Content → Other User Content (messages), Photos or Videos, Audio Data | Yes | No | App Functionality |
| Identifiers → User ID | Yes | No | App Functionality |
| Diagnostics → Crash Data | **Not declared** | No | Collected by Apple under the user's setting, not by the app |

Tracking: **No**. The app includes no SDK that links data across apps or companies.

Required-reasons API privacy manifest (`PrivacyInfo.xcprivacy`): the project does not currently
ship one, and App Store Connect may require it for UserDefaults and file-timestamp APIs used by
DataStore and the log writer. See section C.

## C. Open questions for the developer

1. **Policy URL.** The policy names its own URL as the rendered GitHub page,
   `https://github.com/garfiec/Librechat-Mobile/blob/develop/docs/PRIVACY.md`. Play needs a public,
   non-PDF, non-geofenced URL, which that satisfies. If it moves (for example to garfiec.com),
   update section 16 and the Play Console entry together.
2. **Data safety stance for user-server traffic.** Section A declares messages, files and
   sign-in data as "collected, not shared". The alternative, treating a user-chosen server like a
   user's own Dropbox (Google's closest analogy) and declaring "no data collected", is arguable but
   riskier in review. Decide which to file; the policy text supports both.
3. **"Play vitals" interpretation (confirmed: Android vitals).** This policy assumes *Android vitals*: operating-system-level
   crash, ANR and performance reporting that Google Play collects for Play-installed apps from users
   who share usage and diagnostics, with no SDK in the app and only aggregated results and crash
   clusters visible to the developer. If instead a crash-reporting SDK (Firebase Crashlytics, the
   Play Core/Integrity libraries, Sentry) is added to the `play` flavor, section 3, section 7,
   section A (Crash logs and Diagnostics become "Collected") and the F-Droid metadata all change,
   and the open-source build must stay free of it.
4. **In-app privacy-policy link.** Both Google Play and App Store guideline 5.1.1(i) require the
   policy to be linked from inside the app. Settings → About has no such row today.
5. **Account-deletion URL.** Play's account-deletion requirement needs a web URL in Play Console.
   Section 11 of the policy can serve:
   `https://github.com/garfiec/Librechat-Mobile/blob/develop/docs/PRIVACY.md#11-retention-and-deletion`.
6. **Verify the Apple paragraph in section 7 against current Apple documentation** before
   publishing: the exact setting name ("Share with App Developers"), what a forwarded crash report
   contains, the "not linked to Apple Account" statement, and the claim that sideloaded builds are
   not reported on (Organizer and App Store Connect cover App Store and TestFlight distribution).
   The Apple pages fetched while drafting were partial.
6a. **Device speech audio in the Data safety form.** section A does not declare Audio for the
   "Browser" engine on the reasoning that the OS recognizer, not the app, transmits it (as with
   system voice typing). The app does deliberately invoke the network recognizer as a fallback,
   so this is a judgement call to make alongside item 2.
7. **`PrivacyInfo.xcprivacy`.** None exists in `iosApp/`. Apple requires required-reason API
   declarations for App Store submission; add one before the first upload.
8. **Code-side items that the policy currently discloses rather than fixes** (consider fixing,
   then simplifying the text): remote images inside artifact previews load without a prompt;
   artifact previews have no navigation guard on either platform (no `shouldOverrideUrlLoading` /
   `decidePolicyForNavigationAction`); cached agents and presets are not purged on sign-out;
   explicit favicon URLs from the search payload bypass the "Google only" framing; gateway headers
   are stored unencrypted; iOS does not exclude app data from device backups; iOS Keychain items
   outlive an uninstall; the on-device speech preference falls back to the network recognizer
   silently; the Play and App Store builds default website icons to on.
