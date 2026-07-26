package com.charselect.config;

import com.charselect.character.WorldSeparation;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Which parts of a player follow the character between worlds, plus a few global limits.
 *
 * <p>Defaults are "everything player-owned": the world keeps terrain and entities, the
 * character keeps every scrap of progress. Turning an entry off hands that piece back to
 * vanilla per-world storage.
 */
public final class CharSelectConfig {
    public static final ModConfigSpec SPEC;
    public static final CharSelectConfig INSTANCE;

    // --- what travels with the character ---
    public final ModConfigSpec.BooleanValue transferInventory;
    public final ModConfigSpec.BooleanValue transferEnderChest;
    public final ModConfigSpec.BooleanValue transferVitals;
    public final ModConfigSpec.BooleanValue transferExperience;
    public final ModConfigSpec.BooleanValue transferEffects;
    public final ModConfigSpec.BooleanValue transferAdvancements;
    public final ModConfigSpec.BooleanValue transferStats;
    public final ModConfigSpec.BooleanValue transferRecipeBook;
    public final ModConfigSpec.BooleanValue transferAttributes;
    public final ModConfigSpec.BooleanValue transferQuestProgress;

    /** Data registered by other mods, plus any NeoForge attachment we do not recognise. */
    public final ModConfigSpec.BooleanValue transferModdedData;
    public final ModConfigSpec.ConfigValue<List<? extends String>> worldLocalData;

    // --- behaviour ---
    public final ModConfigSpec.EnumValue<WorldSeparation> worldSeparation;
    public final ModConfigSpec.BooleanValue adoptExistingWorlds;
    public final ModConfigSpec.BooleanValue trackCheatedWorlds;
    public final ModConfigSpec.BooleanValue rememberPositionPerWorld;
    public final ModConfigSpec.IntValue maxCharacterSlots;
    public final ModConfigSpec.BooleanValue allowSkinFetchFromMojang;
    public final ModConfigSpec.BooleanValue characterCosmeticsOnServers;
    public final ModConfigSpec.BooleanValue showFancyMenuHint;

    private CharSelectConfig(ModConfigSpec.Builder builder) {
        builder.comment("Which parts of a player follow the character from world to world.",
                        "Anything set to false falls back to vanilla per-world player data.")
               .push("transfer");

        transferInventory = builder
                .comment("Main inventory, hotbar, armour and offhand.")
                .define("inventory", true);
        transferEnderChest = builder
                .comment("Ender chest contents.")
                .define("enderChest", true);
        transferVitals = builder
                .comment("Health, hunger, saturation, exhaustion, air and fire ticks.")
                .define("vitals", true);
        transferExperience = builder
                .comment("XP level and progress.")
                .define("experience", true);
        transferEffects = builder
                .comment("Active potion effects.")
                .define("effects", true);
        transferAdvancements = builder
                .comment("Advancement progress. Off means each world tracks advancements separately.")
                .define("advancements", true);
        transferStats = builder
                .comment("Statistics (blocks mined, distance walked, ...).")
                .define("stats", true);
        transferRecipeBook = builder
                .comment("Unlocked recipes and recipe book settings.")
                .define("recipeBook", true);
        transferAttributes = builder
                .comment("Persistent attribute modifiers stored on the player.")
                .define("attributes", true);
        transferQuestProgress = builder
                .comment("FTB Quests progress, if that mod is installed. Off keeps each world's",
                         "quest progress separate, the way it behaves without this mod.")
                .define("questProgress", true);
        transferModdedData = builder
                .comment("Data belonging to other mods - Curios slots, backpacks, skill trees -",
                         "including any NeoForge attachment this mod does not recognise.",
                         "Off keeps all of it world-local instead.")
                .define("moddedData", true);
        worldLocalData = builder
                .comment("Ids kept world-local even when the setting above is on, for data that",
                         "only makes sense in the world it came from.",
                         "Use the id a mod registers, or a NeoForge attachment id, for example:",
                         "[\"curios:inventory\", \"somemod:home_waypoints\"]")
                .defineListAllowEmpty("worldLocalData", List.of(),
                        () -> "modid:data_id",
                        o -> o instanceof String s && !s.isBlank());

        builder.pop();

        builder.comment("General behaviour.").push("general");

        worldSeparation = builder
                .comment("How strictly a character's gamemode decides which worlds it may enter.",
                         "STRICT            - survival characters see only survival worlds, and",
                         "                    creative characters only creative worlds.",
                         "CREATIVE_SUPERSET - creative characters may also enter survival worlds.",
                         "WARN              - anything is allowed, but crossing asks first.",
                         "OFF               - no gating at all.",
                         "Survival characters can never switch to creative regardless of this.")
                .defineEnum("worldSeparation", WorldSeparation.STRICT);
        adoptExistingWorlds = builder
                .comment("When a character enters a world that was played before this mod was",
                         "installed, let it inherit that world's existing player data.",
                         "Only the first character to enter may, so items cannot be duplicated.",
                         "Off means every character starts empty in those worlds - their old",
                         "data stays on disk but is no longer reachable.")
                .define("adoptExistingWorlds", true);
        trackCheatedWorlds = builder
                .comment("Remember whether a survival character has ever entered a world with",
                         "commands enabled, and mark it in the character list. Turning this off",
                         "stops the warning, the marking and the pre-cheat backup.")
                .define("trackCheatedWorlds", true);
        rememberPositionPerWorld = builder
                .comment("Return the character to where it last stood in each world.",
                         "Off means always spawning at the world spawn, like Terraria.")
                .define("rememberPositionPerWorld", true);
        maxCharacterSlots = builder
                .comment("Maximum number of character slots.")
                .defineInRange("maxCharacterSlots", 32, 1, 256);
        allowSkinFetchFromMojang = builder
                .comment("Allow looking up skins by Minecraft username via Mojang's public API.",
                         "Off leaves file upload and the default Steve/Alex skins available.")
                .define("allowSkinFetchFromMojang", true);
        characterCosmeticsOnServers = builder
                .comment("On dedicated servers the server owns your player data and account identity,",
                         "but your character's nickname and skin are still shown locally. Off makes",
                         "remote servers look completely untouched.")
                .define("characterCosmeticsOnServers", true);
        showFancyMenuHint = builder
                .comment("When FancyMenu is installed, show a small note in the corner of the",
                         "character select screen saying it has not been set up to reskin this",
                         "screen. FancyMenu identifies screens by Java class and only reskins the",
                         "ones a pack author has explicitly configured, so a pack that only",
                         "customises the vanilla world list will not touch this screen on its own.")
                .define("showFancyMenuHint", true);

        builder.pop();
    }

    static {
        Pair<CharSelectConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(CharSelectConfig::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
