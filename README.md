# Layboard

A tiny Android app that turns your phone into a Bluetooth keyboard and mouse for a laptop or desktop. No software is needed on the computer: it pairs like any other Bluetooth keyboard.

- Free, open source (MIT), no ads, no analytics, no internet access.
- Light on purpose: no runtime dependencies beyond the Android framework, aimed at a download under 5 MB and low-end phones.
- Needs Android 9 (API 28) or newer.

## Features

- **Keyboard**: your phone's normal keyboard, plus Esc, Tab, Ctrl, Alt, Win and arrow keys. Tap Ctrl, Alt or Win, then the next key, to send shortcuts such as Ctrl+C or Alt+Tab. Long-press Win to send it on its own. Layout is US QWERTY; the computer's own layout decides which character appears.
- **Mouse**: slide a finger to move the pointer. It only moves; it never clicks or scrolls.
- **Click Buttons**: separate left and right buttons.

Tested hosts: _Linux — yes (Ubuntu); Windows — not yet; macOS — not yet._

## Using it

1. Install the APK from [Releases](https://github.com/rahmanfaisal653/Layboard/releases).
2. Open Layboard and allow Bluetooth access.
3. Tap **Pair new host**, then pick your phone in the computer's Bluetooth settings.
4. Tap the computer in the list to connect. Layboard reconnects to the last host on launch.

The connection only exists while Layboard is open on screen; this is how Android's Bluetooth HID API works. The pairing itself stays on both devices.

Some phones do not ship the Bluetooth HID Device service. Layboard tells you when that is the case.

## Building

CI builds every push (`.github/workflows/build.yml`). To build locally you need JDK 17 and the Android SDK (platform 35):

```
./gradlew testDebugUnitTest assembleRelease
```

Pushing a tag such as `v0.1.0` builds a signed APK and attaches it to a GitHub Release. Signing uses the repository secrets `LAYBOARD_KEYSTORE_BASE64`, `LAYBOARD_KEYSTORE_PASSWORD`, `LAYBOARD_KEY_ALIAS` and `LAYBOARD_KEY_PASSWORD`.

## Project notes

- Domain vocabulary: [CONTEXT.md](CONTEXT.md)
- Decisions: [docs/adr](docs/adr)
