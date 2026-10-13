# MYRA AI + Part 7 APK visual merge

## Current repository audit (2026-10-13)

- Repository: `sagarhub2/Sona-AI`, default branch: `main`.
- Native implementation: Kotlin + Jetpack Compose in `app/src/main/java/com/sona/ai/MainActivity.kt`.
- Existing voice integration: `LiveVoiceSession.kt`.
- Existing Android services include accessibility, motion/security, and notification-listener services.
- Canonical MYRA visual specification: `MYRA_DESIGN_REFERENCE.md`.
- Existing visual direction: deep navy/near-black surfaces, neon red/pink accents, subtle cyan outlines, central glowing orb, and bottom navigation Home / Chat / Voice / Triggers / Settings.

## Merge policy

Do not blindly copy APK assets or decompiled code into the native project. First inspect the supplied APK and ZIP, identify their screens and resources, and compare them with the current MYRA UI. Reuse only compatible, legally usable assets and behaviours; preserve the existing Kotlin/Compose architecture and working voice/permission flows.

## Screen-by-screen implementation order

1. Home and shared navigation.
2. Chat list, conversation screen, composer, empty/loading/error states.
3. Voice mode, orb/waveform, listening/muted/stopped/error states.
4. Profile and Settings.
5. Permissions and Triggers.
6. Regression build and screen audit.

## Required evidence before declaring completion

- APK and ZIP are actually accessible and inspected.
- Each proposed reference element is mapped to a specific MYRA screen/component.
- Source changes are committed and the Android workflow result is checked.
- Any item not testable in the current environment is marked as unverified, not complete.

## Pending inputs

- `Part 7.apk`: not yet confirmed accessible in this repository audit.
- `Playlist-main.zip`: not yet confirmed accessible in this repository audit.
- Reference screenshots: use the canonical specification in `MYRA_DESIGN_REFERENCE.md`; compare actual images when they are available in the working context.
