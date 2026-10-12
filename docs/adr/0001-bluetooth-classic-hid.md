# Bluetooth Classic HID via BluetoothHidDevice, minSdk 28

Layboard presents the Phone as a Bluetooth Classic HID device using Android's public `BluetoothHidDevice` API, which exists from Android 9 (API 28) and needs no root. BLE HID (HOGP) was rejected because Android restricts the HID GATT service to privileged callers, so ordinary apps cannot use it. Phones released since 2020 ship Android 9 or newer, so `minSdk = 28` still covers the target audience.

## Consequences

- Android 8 and older Phones are unsupported.
- Some vendor Bluetooth stacks omit the HID Device service; Layboard detects this at launch and shows the Status Screen.
- Only one app can be registered as an HID device at a time, and registration is dropped when Layboard leaves the foreground, so a Connection exists only while the app is open.
