# Layboard

A tiny Android app that turns your phone into a Bluetooth keyboard and mouse for a laptop or desktop. No software is needed on the computer: it pairs like any other Bluetooth keyboard.

- Free, open source (MIT), no ads, no analytics, no internet access.
- Light on purpose: no runtime dependencies beyond the Android framework, aimed at a download under 5 MB and low-end phones.
- Needs Android 9 (API 28) or newer.

## Features

- **Keyboard**: your phone's normal keyboard, plus Esc, Tab, Ctrl, Alt, Win and arrow keys. These keys work like a real keyboard's: hold Ctrl, Alt or Win while tapping another key or clicking to send shortcuts such as Ctrl+C, Alt+Tab or Ctrl+click, and hold an arrow to repeat it. A button left of **Keyboard** hides or shows these keys; they are shown again on every new connection. Layout is US QWERTY; the computer's own layout decides which character appears.
- **Mouse**: slide one finger to move the pointer, or two fingers to scroll up, down, left and right like a touchpad (fingers up scroll the content up). Scrolling is smooth where the computer supports it, keeps gliding after a quick flick and stops when you touch the Mouse again. **Scroll Speed** is a slider under Pointer Speed on the Hosts Screen. Tapping never clicks.
- **Click Buttons**: separate left and right buttons.

Tested hosts: _Linux — yes (Ubuntu); Windows — not yet; macOS — not yet._

## Using it

1. Install the APK from [Releases](https://github.com/rahmanfaisal653/Layboard/releases).
2. Open Layboard and allow Bluetooth access.
3. Tap **Pair new host**, then pick your phone in the computer's Bluetooth settings.
4. Tap the computer in the list to connect. Layboard reconnects to the last host on launch.

The connection only exists while Layboard is open on screen; this is how Android's Bluetooth HID API works. The pairing itself stays on both devices.

**Updating from before 0.2.0:** scrolling adds a wheel to the mouse the computer sees, so an already paired computer has to forget the phone in its Bluetooth settings and pair again once (**Pair new host**). Until then it keeps the old mouse and scrolling does nothing.

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
