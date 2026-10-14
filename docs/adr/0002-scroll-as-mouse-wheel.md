# Two-finger scroll is sent as a mouse wheel, not as a touchpad

The Mouse scrolls with two fingers like a touchpad, but the Host never sees a touchpad: Layboard adds a vertical wheel and a horizontal pan to the mouse in its HID descriptor and turns two-finger travel into wheel reports, working out the glide after the fingers lift on the Phone. A precision-touchpad (digitizer) descriptor would feel closest to a laptop, but it is much larger and its support over Bluetooth on Windows, Linux and macOS is uncertain; sending arrow keys needs no descriptor change but moves the text cursor instead of scrolling. High-resolution scrolling is used where the Host accepts it, with plain wheel steps otherwise.

## Consequences

- Changing the descriptor changes what a Host remembers from Pairing, so an already paired Host may have to forget the Phone and pair again once; every later descriptor change carries the same cost.
- The Host's own touchpad settings (natural scrolling, inertia) do not apply; direction and glide are decided on the Phone.
