package com.charselect.compat;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.server.CharacterSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.UUID;

/**
 * Makes Cosmetic Armor Reworked
 * (<a href="https://github.com/zlainsama/CosmeticArmorReworked">zlainsama/CosmeticArmorReworked</a>,
 * mod id {@code cosmeticarmorreworked}) follow the character rather than the account.
 *
 * <p>Cosmetic Armor keeps a player's cosmetic loadout entirely outside the player's own saved
 * data, in its own file at {@code <world>/playerdata/<accountUuid>.cosarmor}. Nothing this mod
 * normally does ever saw it, which is the bug: the file is keyed by <em>account</em>, so every
 * character on one account shared a single cosmetic loadout, and switching characters left the
 * previous one's cosmetic armour on.
 *
 * <p>Like {@code FtbQuestsCompat}, the fix is staging rather than redirection - Cosmetic Armor
 * offers no hook to point its own path elsewhere, so the character's loadout is written to that
 * path before the mod reads it and read back after the mod writes it. This class never
 * references any Cosmetic Armor class; it only works with a file whose name and location come
 * from that mod's own save format.
 *
 * <p>Unlike FTB Quests, the loadout is kept on the {@code CharacterProfile} itself rather than
 * in a side file next to the character. That is what lets it travel between singleplayer and a
 * server: the profile is uploaded on join and handed back on quit, so the loadout rides along
 * with it exactly the way inventory does. A side file would have been simpler, but it would
 * have stranded the loadout on whichever side wrote it - stored per character <em>on that
 * server</em>, the way advancements and stats still are.
 *
 * <h2>Why the ordering here is safe</h2>
 *
 * <p>Both mods listen to the same two player events, so unlike the FTB Quests case there is no
 * earlier lifecycle phase to hide behind - this has to win an ordering race inside a shared
 * event. It does so deterministically because Cosmetic Armor registers all four of its handlers
 * with {@code NeoForge.EVENT_BUS.addListener(this::handleX)}, taking the default {@code NORMAL}
 * priority. {@code HIGHEST} therefore runs strictly before its load, and {@code LOWEST} strictly
 * after its save. Confirmed against that mod's own {@code InventoryManager} source; if a future
 * version ever gives those handlers an explicit priority, this is the assumption that breaks,
 * and cosmetic armour would start persisting per account again rather than failing loudly.
 *
 * <p>Server shutdown needs no separate hook: {@code MinecraftServer.stopServer} calls
 * {@code playerList.removeAll()}, which fires a normal logout for everyone still connected.
 *
 * <h2>Known limits</h2>
 *
 * <ul>
 *   <li>Because the loadout travels with the character, it counts as items crossing onto a
 *       server - those four slots hold real stacks a player can take back out. It is therefore
 *       stripped along with the rest of a character's items when {@code itemsTransfer} is off,
 *       see {@code CharacterUploadFilter}; otherwise cosmetic slots would be the one way to
 *       walk items past that gamerule.
 *   <li>A cosmetic loadout that predates this mod is not adopted by the first character to
 *       play - it is cleared instead. Adopting it would need a way to tell "left over from
 *       before" apart from "belongs to a different character", and getting that wrong hands
 *       one character's armour to another. Re-equipping cosmetic armour is cheap; silently
 *       inheriting someone else's is not.
 * </ul>
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CosmeticArmorCompat {
    private static final String COSARMOR_MOD_ID = "cosmeticarmorreworked";

    /** Matches {@code InventoryManager#getDataFile} - confirmed against Cosmetic Armor's source. */
    private static final String FILE_SUFFIX = ".cosarmor";

    private static Boolean present;

    private CosmeticArmorCompat() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(COSARMOR_MOD_ID);
        }
        return present;
    }

    /**
     * Puts this character's cosmetic loadout where Cosmetic Armor is about to look for it.
     *
     * <p>Runs at {@link EventPriority#HIGHEST}, so it lands before that mod's own
     * normal-priority login handler, which invalidates its cache and reads the file fresh.
     *
     * <p>A character with nothing stored gets the file removed rather than left alone: the
     * path is keyed by account, so whatever is sitting there belongs to whichever character
     * played last, and leaving it would hand that loadout over.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!isPresent() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        if (profile == null) {
            return;
        }

        Path worldFile = worldFile(server, player.getGameProfile().getId());
        CompoundTag stored = profile.cosmeticArmor();
        try {
            if (!stored.isEmpty()) {
                Files.createDirectories(worldFile.getParent());
                NbtIo.write(stored, worldFile);
            } else {
                Files.deleteIfExists(worldFile);
            }
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not stage cosmetic armour for '{}'", profile.nickname(), e);
        }
    }

    /**
     * Takes the cosmetic loadout back out of the world and into the character.
     *
     * <p>Runs at {@link EventPriority#LOWEST}, so it lands after that mod's own
     * normal-priority logout handler has written the file and dropped its cache.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!isPresent() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        if (profile == null) {
            return;
        }

        captureInto(server, player, profile);
    }

    /**
     * Reads whatever Cosmetic Armor has currently written for this player into the character,
     * and saves the character.
     *
     * <p>Reads the file as-is rather than asking for a flush first, so the caller is
     * responsible for making sure it is current. On logout that is free - Cosmetic Armor has
     * just written it. Anywhere the player is still connected it is not, and the caller has to
     * run a player save first to make that mod write; see {@code net.CharacterSyncNetwork},
     * which does exactly that before handing the character back to a quitting client.
     */
    public static void captureInto(MinecraftServer server, ServerPlayer player,
                                   CharacterProfile profile) {
        if (!isPresent()) {
            return;
        }
        Path worldFile = worldFile(server, player.getGameProfile().getId());
        if (!Files.isRegularFile(worldFile)) {
            return;
        }
        try {
            CompoundTag read = NbtIo.read(worldFile);
            profile.setCosmeticArmor(read == null ? new CompoundTag() : read);
            // Persisted here rather than left to the ordinary capture: that rebuilds only the
            // buckets it reads out of player NBT, and this field comes from neither.
            CharacterSession.persist(server, player, profile);
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not capture cosmetic armour for '{}'", profile.nickname(), e);
        }
    }

    private static Path worldFile(MinecraftServer server, UUID accountId) {
        // Cosmetic Armor writes with plain UUID#toString(), confirmed against getDataFile.
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(accountId + FILE_SUFFIX);
    }
}
