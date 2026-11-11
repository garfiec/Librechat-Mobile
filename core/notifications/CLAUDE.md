# core:notifications

System notifications and in-app snackbars for replies the user wasn't watching and for scheduled
chats' runs, the permission flow, and the watchers that notice them.

## Rules
- **Every announcement goes through `ReplyNotifications.announce`**, which applies `deliveryOf`'s
  account, open-chat and foreground rules. Don't post to `PlatformNotifier` directly.
- **The reply watcher reads only `GET /chat/active`, never `/chat/status`**: on servers with the legacy
  parked-steer store a status read of an ended run deletes the user's parked steers.
- **Schedule checks are armed only from the server's `nextRunAt`**, never a time computed with
  `ScheduleCadenceRules`: the server applies timezone and cadence, and a local one drifts.
- **A notification's link carries its account** (`conversationLink`, `schedulesLink`), and `:shared`'s
  `placeDeepLink` drops it under any other account. Keep them and their branches of
  `DeepLinks.resolve` in step.
- **Nothing asks for permission at launch.** A feature offers it at its moment through
  `NotificationAsk` (one answer per `NotificationAskTrigger`, stored by `key`: never rename one).
- **Strings resolve outside composition.** Android sets the process locale from the stored app
  language before building them (`AndroidNotifier.applyAppLanguage`); a notification string never
  comes from a composable.
- **Android has one channel per kind, created by the code that posts to it** (`ensureChannel` before
  each post). Don't create a channel nothing posts to: it shows as a dead switch in system settings.
- **Android watches with plain WorkManager jobs, no foreground service or exact alarm** (no Play
  foreground-service or exact-alarm declaration).
