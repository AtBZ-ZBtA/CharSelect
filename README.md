# Character Select

Terraria-style character slots for Minecraft. Your character — its nickname, skin, gamemode
and everything it is carrying — is stored separately from the worlds you play it in.

**NeoForge 1.21.1** · [Report a bug](https://github.com/AtBZ-ZBtA/CharSelect/issues/new?template=bug_report.yml) · [Contributing](CONTRIBUTING.md) · [Public domain (CC0)](LICENSE)

Safe to add to a pack you already play — see
[Adding it to a pack you already play](#adding-it-to-a-pack-you-already-play).

## What it does

Pressing **Singleplayer** now opens a character list instead of the world list. You pick a
character first, and only then a world. The character you picked decides which worlds you
are even shown.

- **Character slots.** Create as many as you like (32 by default, configurable). Each has a
  nickname, a skin and a gamemode, and the list shows what it is carrying, how long it has
  been played and whether it is hardcore.
- **Skins.** Type a Minecraft username and the skin is fetched from Mojang, upload a PNG
  from disk, or stay as Steve/Alex. A rotating paper doll shows the result as you pick —
  drag it to spin it.
- **Gamemode is permanent.** Chosen at creation, never editable. Survival characters can
  never reach creative or spectator, cheats or no cheats. Which worlds each character may
  enter is set by `worldSeparation` in the config, strict Terraria-style by default.
- **Your stuff follows you.** By default a character carries its inventory, ender chest,
  health, hunger, XP, effects, advancements, stats, recipes and attributes into any world.
- **Cheat history is tracked.** A survival character that has entered a world with commands
  enabled is marked in yellow in the character list, forever. Worlds can be created that
  refuse those characters outright.

## Adding it to a pack you already play

Installing this into an existing modpack does not cost you anything.

Worlds that were played before the mod arrived are recognised by the player data already in
them. They are marked as belonging to **no gamemode in particular**, so survival *and*
creative characters can open them and nothing becomes unreachable. On first launch a
character is created for you under your Minecraft account name, wearing your own skin, and
the first character to walk into one of those worlds inherits everything that was in it.

That inheritance happens once per world and is recorded in the world's sidecar, so a second
character cannot walk in and receive a copy of the same items. Only worlds created *after*
the mod is installed are tied to a gamemode and subject to separation.

Set `general.adoptExistingWorlds = false` to turn all of this off; existing player data then
stays on disk but is no longer reachable through a character.

## Hardcore characters

Survival characters can be made **hardcore** at creation. Dying ends the run: the character
is marked dead, the session is closed, and the slot stays in the list as a record that can be
looked at but not played. The world itself is untouched — hardcore lives on the character,
not the world, so a hardcore character can be taken into any survival world.

## Cheat tracking

The first time a clean survival character enters a world with cheats enabled, it warns you
and keeps a copy of the character exactly as it was. Go in and the character is marked as
having used cheats; the **Restore** button in the character list takes it back to that
frozen copy at any point. Restoring is a rollback, not a merge — everything done since is
discarded, so it asks first.

When creating a world there is now a **Character** tab with *Require a clean character*.
Worlds made with it set will not let a marked character in. Turning it on also forces cheats
off for that world, because a world that hands out cheats and then bans cheaters would taint
the first player to enter and immediately lock them out.

Only survival characters are tracked. Creative characters are cheating by definition, so
marking them would mean nothing.

## How the separation works

Worlds stop holding player data. The character profile holds it, split into two buckets:

- **Shared** — follows the character into every world.
- **Per-world** — kept against one specific world inside the character.

The config chooses which bucket each kind of data lands in. Setting `inventory = false`
does not hand your inventory back to the world file; it narrows that data's scope to a
single world *within the character*. Same behaviour, and it means characters never need
their own account UUID — which is what keeps this from fighting Essential, world ownership
and `/op`.

Position is always per-world, so each character remembers where it left off in each world.
Turn off `rememberPositionPerWorld` for the Terraria behaviour of always arriving at spawn.

### Where things live

```
.minecraft/charselect/
  characters/<characterId>.dat        one file per slot
  skins/<sha1>.png                    deduplicated skin images
  data/<characterId>/
    advancements.json                 shared, when advancements transfer
    stats.json
    worlds/<worldFolder>/             per-world copies, when they do not
<world>/charselect_world.dat          which kind of character the world belongs to
```

A world is classified once, on first load, and pinned. Its default gamemode in level.dat is
only consulted for worlds that existed before the mod was installed — after that
`/defaultgamemode` cannot silently reclassify a world and lock its owner out.

## Multiplayer

Servers behave like vanilla. On any server you did not start yourself, the server owns your
player data and your account identity, exactly as it always did. Your character supplies
nickname and skin only, drawn locally and sent to other players who have the mod.

Essential fits this without special cases. An Essential-hosted world is still an integrated
server, so the **host** keeps the full character system while **guests** are on the ordinary
server path — the host's world stores their data, and their character is cosmetic. Skins and
nicknames are exchanged over an optional payload channel, so a server without the mod simply
never receives them and nothing breaks.

## Configuration

`config/charselect-common.toml`:

| Key | Default | Effect |
| --- | --- | --- |
| `transfer.inventory` | `true` | Inventory, hotbar, armour, offhand |
| `transfer.enderChest` | `true` | Ender chest contents |
| `transfer.vitals` | `true` | Health, hunger, air, fire |
| `transfer.experience` | `true` | XP level and progress |
| `transfer.effects` | `true` | Active potion effects |
| `transfer.advancements` | `true` | Advancement progress |
| `transfer.stats` | `true` | Statistics |
| `transfer.recipeBook` | `true` | Unlocked recipes |
| `transfer.attributes` | `true` | Persistent attribute modifiers |
| `transfer.moddedData` | `true` | Other mods' data, including unrecognised attachments |
| `transfer.worldLocalData` | `[]` | Ids kept world-local anyway, e.g. `["curios:inventory"]` |
| `general.worldSeparation` | `STRICT` | See below |
| `general.adoptExistingWorlds` | `true` | Let the first character inherit a pre-existing world's data |
| `general.trackCheatedWorlds` | `true` | Track and mark characters that entered cheated worlds |
| `general.rememberPositionPerWorld` | `true` | Return to where you left off in each world |
| `general.maxCharacterSlots` | `32` | Slot limit |
| `general.allowSkinFetchFromMojang` | `true` | Allow username lookups |
| `general.characterCosmeticsOnServers` | `true` | Show your character on remote servers |

`worldSeparation` takes one of:

| Value | Effect |
| --- | --- |
| `STRICT` | Survival characters see only survival worlds, creative only creative |
| `CREATIVE_SUPERSET` | Creative characters may also enter survival worlds |
| `WARN` | Everything is visible; crossing gamemode lines asks for confirmation |
| `OFF` | No gating at all |

Survival characters can never switch to creative regardless of this setting — separation
controls which worlds are reachable, not what a character is allowed to be.

Anything the mod does not recognise — including data added by other mods — is treated as
player progress and follows the character.

## For mod developers

Character Select already carries anything your mod writes into the player's NBT, including
NeoForge data attachments, so in most cases your mod works with no changes at all. **Curios
is supported out of the box** this way: its inventory is a serializable attachment, and it
is registered by default so accessories follow the character like the rest of their gear.

Register only if you want a stable id players can name in `worldLocalData`, or a different
default scope. Fire on the mod event bus:

```java
@SubscribeEvent
public static void onRegisterCharacterData(RegisterCharacterDataEvent event) {
    // A NeoForge attachment, by registry id.
    event.registerAttachment(
            ResourceLocation.fromNamespaceAndPath("mymod", "backpack"),
            CharacterDataScope.SHARED);

    // Plain keys in the player's saved NBT, kept with the world they came from.
    event.registerNbtKeys(
            ResourceLocation.fromNamespaceAndPath("mymod", "home"),
            CharacterDataScope.PER_WORLD,
            "mymod_home_pos", "mymod_home_dim");

    // Full control, for data that needs transforming on the way in or out.
    event.register(new MyCharacterDataHandler());
}
```

No hard dependency is needed — guard the subscriber with
`ModList.get().isLoaded("charselect")`, or keep it in a class that is only loaded when the
event fires. Each handler gets a private compound inside the character's storage, so two
mods can never tread on each other, and a handler that throws is logged and skipped rather
than taking the save down with it.

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/`. `./gradlew runClient` starts a dev client; `runClientAlt`
starts a second one under a different account name, for testing two characters against one
hosted world. You need JDK 21; the wrapper fetches everything else.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more, including the things most likely to catch
you out when changing the code.

## Licence

Released into the public domain under [CC0 1.0 Universal](LICENSE). Do whatever you like
with it — use it, fork it, put it in a modpack, sell it, relicense it. No credit required,
though it is always nice. No warranty of any kind.
