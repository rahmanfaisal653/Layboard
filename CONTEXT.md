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
_Avoid_: Bonding, registering

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
The left and right on-screen buttons that click on the Host. They are separate from the Mouse.
_Avoid_: Tap, mouse buttons

## Screens

**Input Screen**:
The screen holding the Keyboard, Mouse and Click Buttons. It is shown only while a Connection is active.
_Avoid_: Main screen, remote screen

**Status Screen**:
The separate screen that explains why the Input Screen is not available (missing permission, Bluetooth off, Connection lost, unsupported Phone) and offers one action to fix it.
_Avoid_: Error dialog, banner, popup
