# Layboard

A lightweight Android app that turns a phone into a Bluetooth keyboard and mouse for a laptop or other computer, with no software needed on the computer.

## Language

**Layboard**:
The Android app itself.
_Avoid_: Blek (a competing app), remote, controller app

**Host**:
The computer being controlled: a Windows, Linux or macOS laptop or desktop.
_Avoid_: Laptop, PC, target, device, receiver

**Phone**:
The Android device running Layboard and acting as the input device.
_Avoid_: Client, controller, remote

**Pairing**:
The one-time trust relationship between the Phone and a Host, remembered by Bluetooth across sessions.
_Avoid_: Bonding

**Registration**:
The Phone announcing itself to Android's Bluetooth as a keyboard and mouse. It lasts only while Layboard is in the foreground, and a Connection can only exist while it does.
_Avoid_: HID registration, advertising, pairing (a Pairing outlives a Registration)

**Connection**:
The live link between the Phone and a Host, which exists only while Layboard is open in the foreground.
_Avoid_: Session, pairing (a Pairing outlives a Connection)

## Input

**Keyboard**:
The Phone's standard Android on-screen keyboard, whose typing is forwarded to the Host, plus a small row of special keys (Esc, Tab, Ctrl, Alt, Win/Cmd, arrows).
_Avoid_: IME, keypad

**Mouse**:
The on-screen area that only moves the Host's pointer. It never clicks and never scrolls.
_Avoid_: Touchpad, trackpad, pointer pad

**Click Buttons**:
The left and right on-screen buttons that click on the Host. They are separate from the Mouse; a button held down while the Mouse moves drags.
_Avoid_: Tap, mouse buttons

**Armed modifier**:
A Ctrl, Alt or Win key that was tapped and is waiting, shown lit, to be applied to the next Keyboard key and then cleared. Clicking does not use or clear it, and a Connection ending does.
_Avoid_: Sticky key, toggled key

## Screens

**Hosts Screen**:
The starting screen while no Connection exists. It lists the paired Hosts, offers "Pair new host", holds the Pointer Speed slider, and shows a one-line notice such as "Disconnected" when a Connection ends.
_Avoid_: Home, device list, main screen

**Input Screen**:
The screen holding the Keyboard, Mouse and Click Buttons. It is shown only while a Connection is active.
_Avoid_: Main screen, remote screen

**Status Screen**:
The separate screen that explains a problem blocking everything else (missing permission, Bluetooth off, unsupported Phone) and offers one action to fix it. A lost Connection is not one of them: it returns to the Hosts Screen.
_Avoid_: Error dialog, banner, popup

**Pointer Speed**:
How far the Host's pointer moves per finger travel on the Mouse, set by one slider on the Hosts Screen and remembered.
_Avoid_: Sensitivity, DPI
