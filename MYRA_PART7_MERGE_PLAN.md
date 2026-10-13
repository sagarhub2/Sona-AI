# MYRA AI + Part 7 APK + Playlist ZIP integration tracker

## Project constraints

- Keep the existing native Kotlin + Jetpack Compose app and current MYRA UI; do not start a new project.
- Add reference features one at a time, only after checking for an existing equivalent.
- Do not copy a compiled APK's decompiled code blindly or add placeholder screens that pretend a feature works.
- Respect Android runtime permissions, accessibility policy, user consent, privacy, and platform limits.
- Preserve the current five primary destinations: Home, Chat, Voice, Trigger, Setting.

## Reference inputs confirmed

Both reference files are now present in the user's Library and have been materialized for inspection:

- `Part 7.apk` — 99,002,975 bytes.
- `Playlist-main.zip` — 278,977,140 bytes.

The previous statement that these inputs were pending is superseded. Their existence is confirmed, but a full file-by-file archive/APK inspection still needs to complete before claiming every contained feature has been audited. Do not mark a reference feature complete solely because it appears in a previous summary.

## Current MYRA implementation already found

The current `MainActivity.kt` already contains implementations or entry points for:

- Home and holographic orb.
- Chat sessions, categories, search, and conversation history.
- Live voice screen and Boss Mode controls.
- Profile and Settings, including Gemini key, voice/TTS preferences, theme, and personalization.
- Smart Memory, tasks, focus mode, daily planner, and history.
- Feature menu, triggers/alarms, files, camera/image analysis, contacts/media launchers, web search, device settings, and permission dashboard.
- Existing service classes for accessibility actions, floating orb, motion/security, and notification listening.
- Gemini Live WebSocket integration in `LiveVoiceSession.kt`.

These are duplicate-check anchors, not proof that every path is fully functional on-device.

## Reference feature inventory to verify against the archives

Check each item against both reference artifacts and the current implementation before deciding to reuse, adapt, or omit it:

1. **Voice:** offline speech/Vosk, wake phrase, voice manager, voice enrollment/identity verification, hands-free listening, audio routing and mute states.
2. **Phone & communication:** contacts, call/dial intents, WhatsApp message handoff, phone shortcuts, notification actions.
3. **Apps & media:** app/browser/Chrome launch, YouTube/music controls, media intents.
4. **Screen & vision:** screen capture, UI recognition, visual task assistance, camera/gallery analysis, screen signatures.
5. **Memory & productivity:** memory categories/manage flow, notes/voice notes, tasks, reminders, planner, AI agent/task overlay.
6. **Personalization & safety:** profile/avatar/personality, location/weather, privacy controls, permission dashboard, identity checks.
7. **Maintenance:** updates, API configuration, connection/error states, accessibility explanation and opt-in.

## One-by-one integration sequence

- [ ] **Stage 0 — Archive inspection:** list ZIP contents and inspect APK manifest, activities, services, permissions, resources and packaged libraries. Record source/license constraints and evidence.
- [ ] **Stage 1 — Gap matrix:** map every discovered screen/feature to the existing MYRA implementation; label `already present`, `partial`, `missing`, or `not compatible`.
- [ ] **Stage 2 — Voice gaps:** add only verified, compatible missing voice features; keep microphone use explicit and visible.
- [ ] **Stage 3 — Phone/app/media gaps:** use supported Android intents and permissions; no claims of unrestricted control.
- [ ] **Stage 4 — Screen/vision gaps:** add only features supported by the app's permissions and platform APIs.
- [ ] **Stage 5 — Memory/productivity gaps:** reuse existing local storage and screens where possible; avoid duplicate data stores.
- [ ] **Stage 6 — Profile/privacy/settings gaps:** extend existing settings instead of adding duplicate destinations.
- [ ] **Stage 7 — Regression/build:** build, inspect workflow logs/artifact, and report device-only behaviors as unverified until tested.

## Completion evidence

For every stage, record the exact files changed, behavior added, duplicate check, build result, and any device-side test still required. Do not claim all reference features are integrated until the matrix is fully audited and every supported feature has implementation evidence.
