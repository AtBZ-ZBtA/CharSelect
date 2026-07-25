# Contributing

Bug reports, ideas and pull requests are all welcome. You do not need permission to start.

## Reporting a bug

Open an issue using the **Bug report** template. The two things that make a report
actionable are:

1. **The full log or crash report**, not a screenshot of it. `logs/latest.log` for odd
   behaviour, or the file from `crash-reports/` if the game closed. Paste it into a
   [gist](https://gist.github.com) and link it if it is long.
2. **What you were doing**, in enough detail to try it. "Crashed opening the character list
   with a Sophisticated Backpack in my inventory" is a fixable report. "It crashed" is not.

Also say which modpack you are on, if any. Most interesting bugs in this mod come from
interactions with other mods rather than from the mod alone.

## Building it

You need **JDK 21**. Nothing else — the Gradle wrapper fetches its own Gradle.

```bash
./gradlew build
```

The jar lands in `build/libs/`. The first build takes a while because NeoForge downloads and
decompiles Minecraft; later ones are quick.

To run a dev client:

```bash
./gradlew runClient
```

On Windows you can double-click `Run Client.bat` instead, which does the same thing and
works around a NeoForge splash-screen bug on some setups.

`runClientAlt` starts a second client under a different account name, which is how you test
two characters against one Essential-hosted world.

## Working on the code

`README.md` explains how the pieces fit together — worth reading first, particularly the
section on how player data is split between the character and the world, since that is the
part most likely to surprise you.

A few things worth knowing before you change anything:

- **Mixins are applied lazily.** A broken injection point in a screen nobody has opened stays
  silent until someone opens it. If you add or change a mixin, load the screen it targets
  before assuming it works.
- **`PlayerDataSplitter` hardcodes vanilla NBT key names.** Get one wrong and nothing
  crashes — that data just quietly stops following the character. Check against the game's
  own save code rather than guessing.
- **Anything drawn on the character screen runs outside a world.** Modded items can throw
  while resolving their model there, which is why item rendering goes through
  `SafeItemRenderer`. Keep new third-party rendering behind the same kind of guard.
- **Other mods' data** should not need special handling — see the mod developer section of
  the README for the extension API rather than adding cases to the splitter.

## Pull requests

- Branch off `main` and keep one change per PR.
- Match the surrounding style. There is no formatter config; just read the file you are in.
- Say what you actually tested. "Compiles" is useful to know; so is "compiles, untested
  in game" — that is honest and still mergeable.
- CI builds every PR. A red build usually means a compile error, not a review problem.

## Licence

This mod is released under [CC0 1.0 Universal](LICENSE) — it is in the public domain. By
contributing you agree your contribution is released the same way, with no rights reserved.
