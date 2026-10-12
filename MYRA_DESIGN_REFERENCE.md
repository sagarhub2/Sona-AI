# MYRA AI — Locked Design Reference

This file records the user's uploaded MYRA AI multi-screen concept image (2026-10-12). Treat it as the canonical visual and feature reference for future implementation. Do not ask the user to upload or describe it again in this project thread.

## Visual direction
- Premium native Android UI; clean white/light blush surfaces, soft pink/red holographic waves, red accent buttons, subtle gold/yellow outer highlights from the reference.
- Central black circular MYRA emblem/orb with red neon glow; refined micro-animations and voice waveform.
- Rounded cards, thin blush borders, restrained shadows, compact typography, consistent spacing and status bars.
- Bottom navigation: Home, Chat, central Voice orb, Triggers, Settings. Keep it consistent across primary screens.
- Do not use generic starter UI. Maintain accessibility, legible contrast, system back behavior, and loading/empty/error states.

## Screens in the reference
1. Splash/loading: MYRA logo/orb, tagline and initialization state.
2. Home: MYRA AI identity, greeting, message input, Quick Access cards for Voice Mode, Neural Lens, Smart Memory, Tasks & Reminders.
3. Chat: Conversations/Recent tabs, search/new chat, categories such as General Chat, Study Help, Image Generation, Code Assistant, Travel Plans, Health & Fitness, Creative Ideas.
4. Voice Mode: animated orb/waveform, listening state, tap-to-stop, Boss Mode, mic control, audio input/output diagnostics.
5. Neural Lens: camera preview, Scan/Live/Detect modes, gallery and flash controls; explain and request camera permission.
6. Smart Memory: memory status/count, Personal Info, Work & Study, Interests, Conversations, Files & Documents, Manage Memory and editable detail screens.
7. Tasks & Reminders: All/Tasks/Reminders filters, task status, date/time, completion toggles, add task.
8. Files & Documents: All/PDF/Images/Docs filters and file list with type/size metadata.
9. Triggers: App Control, Phone Settings, Media Control, Calling & Contacts, Wallpaper & Themes, Device Settings.
10. Settings: MYRA profile, API Key, Voice & Language, Personality, Permissions, Appearance, About.
11. API Key: secure Gemini API key entry, show/hide, save/update feedback, brief explanation.
12. Permissions: clear status and user-controlled permission actions for Microphone, Camera, Storage/Photos, Phone, Contacts, Location, Notifications, plus Accessibility where needed.
13. History: Conversations, Files, Tasks.
14. Memory Details: categories and editable saved information.
15. Neural Lens secondary image/preview mode.

## Behavior and safety requirements
- Preserve existing native Kotlin/Jetpack Compose implementation and working features; no fake buttons or demo-only screens.
- Audit existing code before adding a feature; avoid duplicate screens, command handlers, services, or permission flows.
- Request Android permissions in context and let users enable special access themselves in system settings.
- For calls/messages, open the system dialer or a prefilled draft and require the user to confirm; never silently send messages or place calls.
- Accessibility is opt-in, explain exactly which navigation actions are supported, and do not claim screen reading if it is disabled in service configuration.
- API keys must not be hardcoded or logged. Show clear setup and error states.
- Validate builds through GitHub Actions and report the actual result; do not claim device-tested behavior without a device test.

## Implementation order
1. Bring Home, bottom navigation, theme, orb and cards in line with reference.
2. Finish Chat and Voice Mode experience, including real voice start/stop/error states.
3. Complete Neural Lens, Smart Memory, Tasks and Files screens with working actions.
4. Complete Triggers, Settings, API Key, Permissions, Profile, Appearance and History.
5. Run a screen-by-screen feature audit, resolve build issues, and validate on Android 8+ and the user's target phone.
