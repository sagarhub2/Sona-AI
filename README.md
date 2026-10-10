# Myra AI

Myra AI — Native Android personal assistant with a holographic, voice-first interface.

Built with Kotlin and Jetpack Compose. Features depend on device permissions and the configured Gemini API key; device controls use Android intents and do not provide unrestricted system control.


## 🔒 Final UI Design Lock — 10 October 2026

The user-approved reference image defines the **locked visual direction** for Myra AI. Do not redesign or replace this visual direction unless the user explicitly unlocks it.

### Locked visual specification
- Deep midnight/navy background.
- Holographic neon cyan and violet accents, thin glowing outlines, rounded cards.
- Central animated AI orb and premium female AI avatar treatment where appropriate.
- Consistent typography and spacing; high-contrast, readable labels.
- Home screen: greeting, AI orb, quick actions, voice button.
- Voice conversation: listening/response state, microphone and end-session controls.
- Feature menu: Phone & Calls, Apps & Media, Camera & Vision, Files & Documents, Notes & Tasks, Reminders, Device Settings, Web Search, Smart Memory.
- Settings: Myra AI/personalization, voice & language, Accessibility, API Key, Appearance, Privacy & Security, About.
- Supporting screens: Accessibility Service, Notes & Voice Notes, Tasks & Reminders, Camera & Vision, App & Media Control, Live AI Assistant.

### Implementation rule
This is a **visual reference and product scope**, not proof that every depicted feature already works. Keep the design stable while implementing, building and testing features incrementally. Use native Jetpack Compose components and respect Android permissions and platform limits. Do not claim unrestricted control over Android, automatic background listening, real-time vision, or media control unless each capability is implemented and tested.

### Unlock policy
Only change the locked visual design if the user explicitly asks to unlock or revise the design. Feature implementation, bug fixes, accessibility fixes and build fixes may continue without changing the visual direction.
