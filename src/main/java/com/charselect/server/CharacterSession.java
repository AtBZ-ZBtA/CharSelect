package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.config.CharSelectConfig;
import com.charselect.character.WorldSlot;
import com.charselect.compat.EnigmaticLegacyCompat;
import com.charselect.net.CharacterSyncPayloads;
import com.charselect.world.WorldFlags;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Server-side glue between a live player and the character profile behind it.
 *
 * <p>Two players can be "engaged" for very different reasons. The integrated server's own
 * host reads and writes {@link ActiveCharacter}, the client's local static holder - that
 * only works because host and server share a JVM. Anyone else - a real connection to a
 * dedicated server, or a guest in an Essential-hosted world who has uploaded a character -
 * is driven by {@link RemoteCharacterStore} instead, the server's own last-known copy of
 * whichever character that account is using. Everything downstream of {@link #profileFor}
 * (loading, saving, advancements, stats) is written against that one method and does not
 * need to know which of the two paths produced its answer.
 */
public final class CharacterSession {

    /** Where a remote character's advancements/stats live, mirroring CharacterStore's own
     * "data/&lt;characterId&gt;/..." layout but rooted in the world save instead of the
     * gamedir, since a dedicated server has no local character store of its own. */
    private static final LevelResource REMOTE_DATA_FOLDER = new LevelResource("charselect_remote_data");

    private CharacterSession() {
    }

    /** True when this player's data should come from a character profile instead of the world. */
    public static boolean isEngaged(MinecraftServer server, Player player) {
        return profileFor(server, player) != null;
    }

    /** The profile driving this player, or null if they are on the vanilla path. */
    @Nullable
    public static CharacterProfile profileFor(MinecraftServer server, Player player) {
        // isSingleplayerOwner is only ever true on an integrated server, for its host.
        if (server.isSingleplayerOwner(player.getGameProfile())) {
            return ActiveCharacter.getOrNull();
        }
        return RemoteCharacterStore.get(server, player.getGameProfile().getId());
    }

    /** Saves the profile back to wherever it actually lives for this player. */
    public static void persist(MinecraftServer server, Player player, CharacterProfile profile) {
        if (server.isSingleplayerOwner(player.getGameProfile())) {
            CharacterStore.get().save(profile);
        } else {
            RemoteCharacterStore.put(server, player.getGameProfile().getId(), profile);
        }
    }

    /**
     * Hands a remote player's current server-side character state back to the client that owns
     * it, so the client's own copy - the one it will re-upload on its next join, and the one
     * its character list and inventory preview are drawn from - stays current.
     *
     * <p><b>This has to happen while the player is still actually connected.</b> The obvious
     * place to do it, {@code PlayerLoggedOutEvent}, cannot work at all: {@code PlayerList
     * #remove} (which fires that event) is called from {@code ServerGamePacketListenerImpl
     * #onDisconnect}, by which point the connection is already torn down, so the packet is
     * silently dropped every single time. That is not a rare race - it is guaranteed, and it
     * silently cost every remote session its progress: the client kept its pre-session copy,
     * then re-uploaded that stale copy on the next join, overwriting the good server-side data.
     *
     * <p>So this is driven from the autosave tick instead (see
     * {@code server.CharacterLifecycle}), and once more immediately before any disconnect the
     * server itself initiates, where the packet is queued ahead of the disconnect and does
     * arrive. A client-initiated quit still loses whatever happened since the last autosave -
     * there is no packet that can outrun a connection the client has already closed.
     */
    public static void pushToClient(MinecraftServer server, ServerPlayer player,
                                    CharacterProfile profile) {
        if (server.isSingleplayerOwner(player.getGameProfile())) {
            // The host's characters already live in the same local store this would write to.
            return;
        }
        try {
            PacketDistributor.sendToPlayer(player,
                    new CharacterSyncPayloads.DownloadCharacter(profile.save()));
        } catch (Exception e) {
            CharSelect.LOGGER.debug("Could not hand character '{}' back to its client",
                    profile.nickname(), e);
        }
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
        CharSelect.LOGGER.debug("Capturing '{}': live inventory has {} slot(s), player.getInventory().isEmpty()={}",
                profile.nickname(), full.getList("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                player.getInventory().isEmpty());

        // Checked against the untouched tag, before the splitter sorts anything into buckets.
        EnigmaticLegacyCompat.checkForCurse(full, profile);

        PlayerDataSplitter.capture(full, profile, worldKey);
        CharSelect.LOGGER.debug("Captured '{}': sharedData has {} key(s) including Inventory={}",
                profile.nickname(), profile.sharedData().getAllKeys().size(),
                profile.sharedData().contains("Inventory"));

        if (CharSelectConfig.INSTANCE.rememberPositionPerWorld.get()) {
            WorldSlot slot = profile.worldSlot(worldKey);
            slot.setPosition(player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    player.level().dimension().location().toString());
        }

        profile.markPlayed();
        CharacterLifecycle.bankPlaytime(profile);
        persist(server, player, profile);
    }

    // ------------------------------------------------------------------ side files

    /**
     * Where this character keeps files that vanilla would put in the world folder under the
     * account's UUID. Two characters on one account would otherwise share them.
     *
     * <p>Needs the player, not just the profile, because the root differs by path: the
     * singleplayer host's characters live in the local {@code CharacterStore}, but a remote
     * connection has no local store at all - its data has to live inside the world save,
     * same as {@link RemoteCharacterStore} itself.
     */
    private static Path dataDir(MinecraftServer server, Player player, CharacterProfile profile) {
        Path root = server.isSingleplayerOwner(player.getGameProfile())
                ? CharacterStore.get().root()
                : server.getWorldPath(REMOTE_DATA_FOLDER);
        return root.resolve("data").resolve(profile.id().toString());
    }

    private static Path sharedFile(MinecraftServer server, Player player, CharacterProfile profile,
                                   String fileName) {
        return dataDir(server, player, profile).resolve(fileName);
    }

    private static Path worldFile(MinecraftServer server, Player player, CharacterProfile profile,
                                  String worldKey, String fileName) {
        return dataDir(server, player, profile).resolve("worlds").resolve(worldKey).resolve(fileName);
    }

    /** Advancements follow the character or stay world-local, depending on the config. */
    public static Path advancementsPath(MinecraftServer server, Player player, CharacterProfile profile,
                                        String worldKey) {
        return CharSelectConfig.INSTANCE.transferAdvancements.get()
                ? sharedFile(server, player, profile, "advancements.json")
                : worldFile(server, player, profile, worldKey, "advancements.json");
    }

    public static Path statsPath(MinecraftServer server, Player player, CharacterProfile profile,
                                 String worldKey) {
        return CharSelectConfig.INSTANCE.transferStats.get()
                ? sharedFile(server, player, profile, "stats.json")
                : worldFile(server, player, profile, worldKey, "stats.json");
    }

    /**
     * Where this character's copy of its FTB Quests progress is kept. Unlike advancements
     * and stats, this is not redirected by overriding a factory method - FTB Quests offers
     * no such hook - so it is a plain file copy, staged into the world before FTB Quests
     * reads it and captured back out afterwards. See {@code FtbQuestsCompat}.
     *
     * <p>Always resolves against the local {@code CharacterStore}: {@code FtbQuestsCompat}
     * only ever runs for the singleplayer/integrated-server host (it explicitly bails out on
     * a dedicated server, since FTB Teams' solo-team file layout depends on an account UUID
     * that is only known before any player has connected in that case), so there is no
     * remote variant of this path to resolve.
     */
    public static Path questsPath(CharacterProfile profile, String worldKey) {
        Path root = CharacterStore.get().root().resolve("data").resolve(profile.id().toString());
        return CharSelectConfig.INSTANCE.transferQuestProgress.get()
                ? root.resolve("ftbquests.snbt")
                : root.resolve("worlds").resolve(worldKey).resolve("ftbquests.snbt");
    }
}
