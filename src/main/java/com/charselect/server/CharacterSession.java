package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.config.CharSelectConfig;
import com.charselect.character.WorldSlot;
import com.charselect.compat.EnigmaticLegacyCompat;
import com.charselect.world.WorldFlags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Server-side glue between a live player and the character profile behind it.
 *
 * <p>The character system only takes over for the player who owns the integrated server -
 * the one who picked a character on the way in. Anyone joining over the network, including
 * guests in an Essential-hosted world, is on the vanilla path: the host's world keeps their
 * player data exactly as it always would, and their character only supplies cosmetics.
 */
public final class CharacterSession {

    private CharacterSession() {
    }

    /** True when this player's data should come from a character profile instead of the world. */
    public static boolean isEngaged(MinecraftServer server, Player player) {
        return profileFor(server, player) != null;
    }

    /** The profile driving this player, or null if they are on the vanilla path. */
    @Nullable
    public static CharacterProfile profileFor(MinecraftServer server, Player player) {
        CharacterProfile active = ActiveCharacter.getOrNull();
        if (active == null) {
            return null;
        }
        // isSingleplayerOwner is only ever true on an integrated server, for its host.
        return server.isSingleplayerOwner(player.getGameProfile()) ? active : null;
    }

    /** The save folder name, which is unique per world and stable across renames. */
    public static String worldKey(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path name = root.getFileName();
        return name == null ? "world" : name.toString();
    }

    // ------------------------------------------------------------------ load / save

    /**
     * The player data this character should enter the world with, or null to let vanilla
     * spawn them fresh.
     */
    @Nullable
    public static CompoundTag loadTag(MinecraftServer server, ServerPlayer player) {
        CharacterProfile profile = profileFor(server, player);
        if (profile == null) {
            return null;
        }
        String worldKey = worldKey(server);
        CompoundTag merged = PlayerDataSplitter.merge(profile, worldKey);
        if (merged == null) {
            CharSelect.LOGGER.info("Character '{}' is entering {} for the first time",
                    profile.nickname(), worldKey);
            return null;
        }
        applyPosition(merged, profile, worldKey, player);
        return merged;
    }

    /**
     * Decides where in this world the character actually appears.
     *
     * <p>This has to be explicit rather than left to whatever {@code Pos} survived the merge.
     * {@code Entity#load} reads the position unconditionally, and a missing {@code Pos} reads
     * back as an empty list - which silently means the origin, not "leave them where they
     * are". So every load either restores this world's remembered spot or pins the spawn the
     * player was already placed at.
     */
    private static void applyPosition(CompoundTag tag, CharacterProfile profile,
                                      String worldKey, ServerPlayer player) {
        WorldSlot slot = profile.peekWorldSlot(worldKey);
        boolean remember = CharSelectConfig.INSTANCE.rememberPositionPerWorld.get();

        // Whatever the character was doing when it left, it arrives standing still.
        tag.remove("Motion");

        if (remember && slot != null && slot.hasPosition()) {
            putPosition(tag, slot.x(), slot.y(), slot.z(), slot.yaw(), slot.pitch());
            tag.putString("Dimension", slot.dimension());
            CharSelect.LOGGER.debug("Character '{}' returns to {} at {} {} {} in {}",
                    profile.nickname(), worldKey, slot.x(), slot.y(), slot.z(), slot.dimension());
            return;
        }

        // No memory of this world: keep the spawn the player was already fudged to, and let
        // the dimension fall back to the overworld.
        putPosition(tag, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        tag.remove("Dimension");
        CharSelect.LOGGER.debug("Character '{}' starts at the spawn of {}",
                profile.nickname(), worldKey);
    }

    private static void putPosition(CompoundTag tag, double x, double y, double z,
                                    float yaw, float pitch) {
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        tag.put("Pos", pos);

        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(yaw));
        rotation.add(FloatTag.valueOf(pitch));
        tag.put("Rotation", rotation);
    }

    /**
     * Decides whether this character should inherit the player data a pre-existing world is
     * already holding, and records the claim so only one character ever can.
     *
     * @return true if the caller should let vanilla load run, handing the world's existing
     *         data to this character
     */
    public static boolean claimExistingWorldData(MinecraftServer server, ServerPlayer player) {
        if (!CharSelectConfig.INSTANCE.adoptExistingWorlds.get()) {
            return false;
        }
        CharacterProfile profile = profileFor(server, player);
        if (profile == null) {
            return false;
        }

        Path dir = GameModeGuard.worldDir(server);
        WorldFlags.Data flags = WorldFlags.resolve(dir);
        if (flags.adopted() || !LegacyWorlds.hasExistingPlayerData(server)) {
            return false;
        }

        WorldFlags.write(dir, flags.withAdopted());
        CharSelect.LOGGER.info("Character '{}' has taken over the existing player data in {}",
                profile.nickname(), worldKey(server));
        return true;
    }

    /** Files the player's current state back into the character profile. */
    public static void capture(MinecraftServer server, ServerPlayer player) {
        CharacterProfile profile = profileFor(server, player);
        if (profile == null) {
            return;
        }
        String worldKey = worldKey(server);
        CompoundTag full = player.saveWithoutId(new CompoundTag());

        // Checked against the untouched tag, before the splitter sorts anything into buckets.
        EnigmaticLegacyCompat.checkForCurse(full, profile);

        PlayerDataSplitter.capture(full, profile, worldKey);

        if (CharSelectConfig.INSTANCE.rememberPositionPerWorld.get()) {
            WorldSlot slot = profile.worldSlot(worldKey);
            slot.setPosition(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    player.level().dimension().location().toString());
        }

        profile.markPlayed();
        CharacterLifecycle.bankPlaytime(profile);
        CharacterStore.get().save(profile);
    }

    // ------------------------------------------------------------------ side files

    /**
     * Where this character keeps files that vanilla would put in the world folder under the
     * account's UUID. Two characters on one account would otherwise share them.
     */
    public static Path dataDir(CharacterProfile profile) {
        return CharacterStore.get().root().resolve("data").resolve(profile.id().toString());
    }

    public static Path sharedFile(CharacterProfile profile, String fileName) {
        return dataDir(profile).resolve(fileName);
    }

    public static Path worldFile(CharacterProfile profile, String worldKey, String fileName) {
        return dataDir(profile).resolve("worlds").resolve(worldKey).resolve(fileName);
    }

    /** Advancements follow the character or stay world-local, depending on the config. */
    public static Path advancementsPath(CharacterProfile profile, String worldKey) {
        return CharSelectConfig.INSTANCE.transferAdvancements.get()
                ? sharedFile(profile, "advancements.json")
                : worldFile(profile, worldKey, "advancements.json");
    }

    public static Path statsPath(CharacterProfile profile, String worldKey) {
        return CharSelectConfig.INSTANCE.transferStats.get()
                ? sharedFile(profile, "stats.json")
                : worldFile(profile, worldKey, "stats.json");
    }

    /**
     * Where this character's copy of its FTB Quests progress is kept. Unlike advancements
     * and stats, this is not redirected by overriding a factory method - FTB Quests offers
     * no such hook - so it is a plain file copy, staged into the world before FTB Quests
     * reads it and captured back out afterwards. See {@code FtbQuestsCompat}.
     */
    public static Path questsPath(CharacterProfile profile, String worldKey) {
        return CharSelectConfig.INSTANCE.transferQuestProgress.get()
                ? sharedFile(profile, "ftbquests.snbt")
                : worldFile(profile, worldKey, "ftbquests.snbt");
    }
}
