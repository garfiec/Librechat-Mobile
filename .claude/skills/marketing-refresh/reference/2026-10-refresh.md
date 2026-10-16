# The 2026-10 refresh, as a worked example

The first refresh run end to end with these tools, after message search (#514) merged. Use it as
the precedent for the judgment calls; the numbers will change, the reasoning mostly won't.

## What was stale

Every shot was dark, Android, purple, Material, and predated theming and Liquid Glass. The
tables shot predated the table rework, the settings shot predated the Appearance page, and the
clock was clipped by the frame's corner. The real account name was on screen.

## Feature index, top of the list

From the index, these were the features a newcomer would feel:

| Feature | What a user feels | Shows as | Differentiator |
|---|---|---|---|
| Material / Liquid Glass, any accent, light/dark | "It looks the way I want" | still, both looks side by side | yes, the web app has one look |
| Message search, offline | "I can find that thing I asked last month" | still + recording | yes, the web needs the server's search |
| Chat media page | "Every picture from a chat in one place" | still | yes, the web app has no equivalent |
| Image generation | "I asked for a picture and got one" | still | no, but it's what people try first |
| Two-sided bubbles, chat layout | "It feels like a messaging app" | still | partly |
| Model comparison | "Two answers side by side" | still | no, but visual |
| Artifacts, pinned to the home screen | "It built me a little app" | still | pinning is Android-only |
| Photo viewer, drag to dismiss | "It feels like Google Photos" | recording | yes |
| Agent questions / tool approval | "The agent checks with me" | still (live) | no, but new |
| Sidebar, projects | "Everything's a swipe away" | recording | partly |
| Predictive back | "Feels native" | recording | yes, the website can't |
| Offline reading, multi-account, speed | real, but not visual | copy only | yes |

## Lineup

**README:** a full-width hero (the same chat on the unfolded Fold, Material beside Liquid Glass),
then: Make It Yours · Find Anything · All Your Media · Create Images · Chat Your Way · Compare
Models · Interactive Artifacts (iPhone and iPad) · Photo Viewer · Answer Your Agents ·
Everything in Reach · Answers That Fit · Swipe Back.

**Phone store slots:** hero pair · search · media · image generation · bubbles · compare ·
agent question · artifact pinned to the home screen.

**Tablet store slots:** hero pair · accent colors · search · media · image generation ·
chat layout · compare · agent result.

**Looks per row** (varied so the set shows the theming): hero blue/light in both styles; search
amber/light; images orange/light; bubbles green/dark/Liquid Glass; compare lavender/dark;
agent pink/dark; rich answers blue/light; artifacts teal/light.

## Cut, and why

- **Math:** inline formulas rendered as separate blocks. A broken render is never shipped
  (invariant 2), and the bug was recorded.
- **Tables as a store slot:** looked fine, but oversold a basic feature (invariant 3). Cut on
  review and kept in the README only. The slot went to an artifact pinned to the home screen:
  two overlapping phones, the home screen with its pinned icons behind and the app opened from
  one in front. The other order (the app in the chat, then the home screen) read less clearly.
- **Tool approval:** the live agent finished without calling the tool, so the policy never fired.
  The agent row shows the questions and the result instead.
- **Phone swipe-back recording:** the app stopped responding under a host load of about 48, twice.
  The row kept only the tablet recording, rather than the old purple one.
- **Server Connect, Home, Chat Options, Model Selection, Code, Mermaid, LaTeX, Thinking,
  Settings** rows: dropped as setup screens or table stakes.
- **iPhone Duo:** no simulator runtime in the installed Xcode. The iPhone 17 Pro and iPad Pro
  (iOS 26) stood in.

## Story

"An AI agent with a Dubai chocolate habit." The agent buys 40 chocolate bars, apologizes,
negotiates to keep one "for morale", and is told it can't eat chocolate. Around it:
- "chocolate" in titles, replies and reasoning across several chats (26 hits), for the search
  shot;
- Mayor Whiskers, a cat mayor, in a 14-image album for the media page and image rows, with one
  photo cropped for the persona's avatar;
- a melted bar photo ("Is my chocolate supposed to look like this?");
- a viral desserts ranking (the table);
- a robot vacuum's annual performance review (bubbles, looks);
- "walk or drive to a car wash 50 m away": Opus said drive, Haiku said walk (comparison);
- an allowance tracker React app (artifacts);
- a Chore Agent that asks about budget and whether to add Dubai chocolate (agent panels).

Persona: "Garfie" (the user's choice). Models: Opus for the smart and artifact roles, Haiku as
the fast model and the rival.

## Captions (Play)

| Slot | Title | Subtitle |
|---|---|---|
| hero | Two looks, one app | Material or Liquid Glass, your pick |
| search | Find anything you've asked | Search every chat, even offline |
| media | Every image in one place | Pictures, files and links from each chat |
| images | Create images as you chat | Ask for a picture and it appears |
| bubbles | Make it yours | Any color, light or dark, bubbles or threads |
| compare | Compare models | One question, two answers |
| agent | Agents that check with you | Answer their questions right in the chat |
| home screen | From chat to home screen | Pin the apps it builds, open them in a tap |

Three first drafts wrapped to two lines and were shortened ("All your images in one place",
"Answers that fit your screen", "Turn what it builds into an app"). A subtitle that repeated the title's first word was reworded.

## Decisions the user made

- Marketing voice: felt benefits first, no spec sheet.
- Hero: Android only, the same chat in both looks.
- Two iOS frame sets: Simulator chrome for the README, Apple's bezels for the App Store.
- Demo mode on both platforms for every capture.
- Phone Play images on a 1080×1920 canvas with a caption; tablet on 1920×1080.
- US spelling in captions and the Play text (the F-Droid text keeps its own).
- Short description: "Switchboard, LibreChat on Android. One app to chat with all your AI
  models." A list of features ("search, offline, multi-account") read as random, not as one
  idea; and no other products' names ("ChatGPT-like"), per Play's metadata policy.
- One PR, with separate commits.

## Bugs found while capturing

Recorded for the user, not fixed in the refresh: inline math as blocks; comparison columns
labelling the user differently; agent cards showing a raw author id (since fixed); the table
expand icon covering a header; an unfolded comparison staying tabbed while the sidebar is open;
search snippets showing raw markdown; an ANR on the back gesture under load; and on iOS, a
cold-launch crash from the prefetch default, blank Mermaid and a see-through artifact sheet.
Capture is a good bug hunt: it walks every headline feature with real data.
