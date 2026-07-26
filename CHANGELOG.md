# Changelog

## 1.0.1

### Fixes

- **Hardcore worlds work correctly again.** Dying in a hardcore world is supposed to lock
  you to spectator mode for good, vanilla's own permadeath mechanic. A guard in 1.0.0 meant
  to stop survival characters cheating into creative/spectator was silently blocking that
  transition too, so dying in a hardcore world just... didn't do anything. Fixed — hardcore
  worlds and hardcore characters are independent and both work now.

### New

- **Curses are tracked.** A character caught wearing a permanent curse item — currently
  Enigmatic Legacy's Ring of Seven Curses — is marked **Cursed** in the character list, for
  good, the same way hardcore is tracked.
- **Convert a survival character to creative.** Editing a survival character now offers
  **To Creative**, which makes a full copy of it — inventory, progress, everything — as a
  brand new creative character. The original is left exactly as it was.
- **Cross-world maps say so.** A map carried into a world that never generated it has
  nothing to draw — vanilla already shows this as a blank map rather than crashing, but now
  the tooltip explains why instead of just looking broken.
- The character list opens with your first character already selected.
- A row's detail text (last played / worlds / playtime) scrolls into view on hover if it
  runs past the column instead of just being cut off.
- If FancyMenu is installed and hasn't been configured to reskin the character screens, a
  small note says so in the corner (toggle: `general.showFancyMenuHint`).

### For modpack authors

The README now lists the exact class names for `CharacterSelectScreen` and
`CharacterCreationScreen`, for pointing FancyMenu or similar reskinning tools at them
directly.

## 1.0.0

Initial release.
