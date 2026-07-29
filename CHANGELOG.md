# Changelog

## 1.1 — Multiplayer

Character Select now works on servers. Your characters are yours, they travel with you, and
the server keeps its own honest copy of what happened while you were on it.

### Multiplayer

- **Characters on servers.** Joining a server running the mod asks which character you want
  to play before you spawn, exactly like the singleplayer picker. Your inventory, progress,
  and identity come with you.
- **`/gamerule itemsTransfer`** decides whether items may come with a character from
  singleplayer onto the server. The first player to join a fresh world is asked once and
  their answer becomes the world's setting. Off means only identity travels — nickname,
  skin, and gamemode — and the character starts with a clean, server-local inventory. The
  server enforces this itself rather than trusting the client.
- **`/character reconnect`** leaves the world so you can pick a different character, then
  reconnects you automatically. Servers only — in singleplayer just return to the character
  select screen.
- **Creative characters need operator permission.** Bringing a creative character onto a
  server is effectively a permanent creative-mode grant, so it is gated behind the same
  permission you would need to give yourself creative in the first place. Non-operators are
  turned away at the door with an explanation, before they spawn. Singleplayer is unaffected.
- **Your local copy stays current.** The server hands your character back while you are
  still connected: on every world autosave, and again the moment you quit. Nothing is
  stranded server-side waiting for a disconnect that has already happened.

### Characters left behind

- **`/gamerule charactersStayBehind`** (on by default) leaves a character standing in the
  world when you switch away from it or disconnect, wearing its own name and skin. It is a
  real entity — punchable, killable, and it persists across a server restart.
- **Reclaiming one** puts you back exactly where it was standing.
- **Killing one has consequences.** The stand-in is replaced by a corpse that lies where it
  fell and stays there. The next time that character loads, it spawns on that spot and dies
  for real — an ordinary death with ordinary drops. A hardcore character stays gone for
  good, exactly as it always would; any other character just dies once and carries on.

### Origins

- **Origins integration.** A character's chosen origin travels with it. With
  `itemsTransfer` off, the origin still follows; with it on, unlocked powers do too.

### Other

- Character data now saves on the world's autosave, not only when you leave, so a crash
  costs a few minutes at most instead of a whole session.
- Gamemode locking no longer applies to server players, so operators can change anyone's
  gamemode normally.

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
