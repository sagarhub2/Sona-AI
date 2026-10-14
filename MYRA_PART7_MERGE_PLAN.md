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

- [x] **Stage 0 — Archive inventory (initial pass):** ZIP inventory counted 7 project snapshots and 323 Kotlin / 324 XML / 21 Gradle Kotlin files across the archive; Part 7 alone contains 78 Kotlin files. APK ZIP inventory confirms `classes.dex`, `AndroidManifest.xml`, `resources.arsc`, Vosk native libraries for multiple ABIs, English Vosk model assets, speaker-model assets, HUD/orb HTML, and overlay toy assets. **Remaining:** decode binary manifest/resources and inspect activity/service/permission declarations before Stage 0 is fully closed. Evidence: inventory run on 2026-10-14.
- [x] **License review (initial pass):** `Playlist-main/LICENSE` and `Part 7/LICENSE` specify Tech GPTx attribution requirements for substantial reuse and creator media; preserve license and attribution if adapting code. Confirm exact compliance requirements before publishing derivative content.
- [ ] **Stage 0b — Deep archive inspection:** decode APK manifest/resources, list APK activities/services/permissions, and review source/license constraints in detail.
- [ ] **Stage 1 — Gap matrix:** map every discovered screen/feature to the existing MYRA implementation; label `already present`, `partial`, `missing`, or `not compatible`.

### Initial verified reference inventory (2026-10-14)

`Playlist-main.zip` contains seven successive Android source snapshots. Part 7 includes source files for Vosk model management, owner voice management, headset audio routing, wake-word/background services (from earlier parts), accessibility command execution, contacts/phone/WhatsApp/YouTube automation, Room-based memory, agent task engine and task overlay, screen capture/UI grounding/visual task orchestration, voice enrollment, privacy shield, permission/settings flows, location/weather, and update UI. These are **reference capabilities found in source filenames**, not proof that every feature builds or works. They must be compared against MYRA and permission/API constraints before integration.

`Part 7.apk` includes Vosk native libraries and bundled voice-model assets plus orb/HUD/overlay HTML. This is an initial container inventory only; APK binary manifest has not yet been decoded and DEX code has not been fully decompiled. Do not infer full runtime behavior from strings alone.

Initial integration priorities after the gap matrix: (1) check owner voice/Vosk/wake phrase against MYRA's existing `LiveVoiceSession.kt`; (2) check screen-capture/agent task assistance against the existing accessibility service and permissions; (3) check YouTube/WhatsApp/contact launchers against existing intent-based shortcuts. Avoid merging duplicate project snapshots wholesale.
- [ ] **Stage 2 — Voice gaps:** add only verified, compatible missing voice features; keep microphone use explicit and visible.
- [ ] **Stage 3 — Phone/app/media gaps:** use supported Android intents and permissions; no claims of unrestricted control.
- [ ] **Stage 4 — Screen/vision gaps:** add only features supported by the app's permissions and platform APIs.
- [ ] **Stage 5 — Memory/productivity gaps:** reuse existing local storage and screens where possible; avoid duplicate data stores.
- [ ] **Stage 6 — Profile/privacy/settings gaps:** extend existing settings instead of adding duplicate destinations.
- [ ] **Stage 7 — Regression/build:** build, inspect workflow logs/artifact, and report device-only behaviors as unverified until tested.

## Completion evidence

For every stage, record the exact files changed, behavior added, duplicate check, build result, and any device-side test still required. Do not claim all reference features are integrated until the matrix is fully audited and every supported feature has implementation evidence.
