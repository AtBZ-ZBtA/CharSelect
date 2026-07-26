package com.charselect.compat;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.LocalAccount;
import com.charselect.server.CharacterSession;
import com.charselect.server.GameModeGuard;
import com.charselect.world.WorldFlags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Makes FTB Quests progress follow the character instead of the world.
 *
 * <p>FTB Quests does not store a player's quest progress in the player's own saved data at
 * all - it keeps one file per FTB Teams "team" directly under the world save folder, at
 * {@code <world>/ftbquests/<teamId>.snbt}, read wholesale when the server starts and written
 * back on autosave. A solo player's personal team id is not random: FTB Teams sets it equal
 * to the player's own account UUID. That is what makes this possible without touching FTB
 * Teams or FTB Quests at all - this class never references either mod's classes, it only
 * relocates a file whose name and location are documented by their own save format.
 *
 * <p>Because that file lives outside the player entirely, none of this mod's usual
 * mechanisms ever saw it, which is exactly the bug this class fixes: progress stayed with
 * the world, and a reward's "claimed" flag reset for the same character in every new world.
 *
 * <p>The fix is staging, not redirection: FTB Quests offers no hook to redirect its own file
 * path the way vanilla lets this mod redirect advancements and stats, so the character's
 * copy is copied into place before FTB Quests reads it, and copied back out afterwards.
 * Timing is handled by choosing NeoForge lifecycle events strictly before and after FTB
 * Quests' own load and save - see the per-method comments for exactly why each one is safe.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class FtbQuestsCompat {
    private static final String FTBQUESTS_MOD_ID = "ftbquests";

    /** Matches {@code ServerQuestFile.FTBQUESTS_DATA} - confirmed against FTB Quests' own source. */
    private static final LevelResource QUESTS_FOLDER = new LevelResource("ftbquests");

    private static Boolean present;

    private FtbQuestsCompat() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(FTBQUESTS_MOD_ID);
        }
        return present;
    }

    // ------------------------------------------------------------------ materialize in

    /**
     * Stages the active character's quest progress into the world, before FTB Quests reads
     * anything.
     *
     * <p>{@code ServerAboutToStartEvent} fires before {@code ServerStartingEvent}, which
     * fires before {@code ServerStartedEvent} - the event FTB Quests waits for before it
     * scans the quests folder and loads every file in it. Firing on an earlier lifecycle
     * phase entirely means this is never a same-event ordering race against FTB Quests' own
     * listener: by the time anything of theirs can run, this has already finished, no matter
     * which mod's listener NeoForge happens to call first within a shared event.
     *
     * <p>The account UUID has to come from {@link LocalAccount} rather than a
     * {@code ServerPlayer}, because no player has connected yet at this point - not even in
     * singleplayer, where the client only opens its loopback connection after the server has
     * finished starting.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!isPresent()) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.isDedicatedServer()) {
            return;
        }
        CharacterProfile profile = ActiveCharacter.getOrNull();
        UUID accountId = LocalAccount.id().orElse(null);
        if (profile == null || accountId == null) {
            return;
        }

        Path worldDir = GameModeGuard.worldDir(server);
        Path worldQuestFile = teamFile(server, accountId);
        Path characterQuestFile = CharacterSession.questsPath(profile, CharacterSession.worldKey(server));
        WorldFlags.Data flags = WorldFlags.resolve(worldDir);

        try {
            boolean characterHasData = Files.isRegularFile(characterQuestFile);

            if (!flags.questsManaged()) {
                // First time this mod has touched this world's FTB Quests data. If the
                // character has nothing of its own yet and something is already sitting
                // here, it predates the mod - adopt a copy into the character rather than
                // disturb the world's own file, so FTB Quests' own load a moment from now
                // behaves exactly as it always would this session.
                if (!characterHasData && Files.isRegularFile(worldQuestFile)) {
                    Files.createDirectories(characterQuestFile.getParent());
                    Files.copy(worldQuestFile, characterQuestFile, StandardCopyOption.REPLACE_EXISTING);
                    characterHasData = true;
                    CharSelect.LOGGER.info(
                            "Character '{}' adopted existing FTB Quests progress in {}",
                            profile.nickname(), CharacterSession.worldKey(server));
                }
                WorldFlags.write(worldDir, flags.withQuestsManaged());
                // No early return: a character that already has progress of its own - or
                // just adopted some - still needs it staged into the world below. Only the
                // adoption decision above is one-time; pushing the character's own state in
                // happens on every visit, including this first one.
            }

            // Whichever character is active owns this world's quest file from here on, not
            // whoever played most recently - the actual fix for rewards being claimable
            // again in a different world, and for progress not following at all.
            if (characterHasData) {
                Files.createDirectories(worldQuestFile.getParent());
                Files.copy(characterQuestFile, worldQuestFile, StandardCopyOption.REPLACE_EXISTING);
            } else if (Files.isRegularFile(worldQuestFile)) {
                // This character has never played this world's quests before. Leaving
                // another character's file in place would hand its progress over for free.
                Files.delete(worldQuestFile);
            }
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not stage FTB Quests progress for '{}' in {}",
                    profile.nickname(), CharacterSession.worldKey(server), e);
        }
    }

    // ------------------------------------------------------------------ capture out

    /**
     * Copies the world's quest file back into the character on every autosave.
     *
     * <p>This one is best-effort: FTB Quests listens for the same save event to flush its
     * own dirty team data to disk, and nothing guarantees this runs after that within the
     * same event. Missing the very latest change on one autosave is recovered by the next
     * one a few minutes later, or by {@link #onServerStopped}, which is not a race. This is
     * insurance against a crash between saves, not the mechanism the fix depends on.
     */
    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        captureNow(level.getServer());
    }

    /**
     * Copies the world's quest file back into the character once the server has fully
     * stopped.
     *
     * <p>This is the authoritative capture. FTB Quests flushes its data during
     * {@code ServerStoppingEvent}; {@code ServerStoppedEvent} is documented to fire strictly
     * after it, so unlike the autosave hook above, this one is never in a race - by the time
     * it runs, FTB Quests has certainly already written its final state for the session.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        captureNow(event.getServer());
    }

    private static void captureNow(MinecraftServer server) {
        if (!isPresent() || server.isDedicatedServer()) {
            return;
        }
        CharacterProfile profile = ActiveCharacter.getOrNull();
        UUID accountId = LocalAccount.id().orElse(null);
        if (profile == null || accountId == null) {
            return;
        }

        Path worldQuestFile = teamFile(server, accountId);
        if (!Files.isRegularFile(worldQuestFile)) {
            return;
        }

        Path characterQuestFile = CharacterSession.questsPath(profile, CharacterSession.worldKey(server));
        try {
            Files.createDirectories(characterQuestFile.getParent());
            Files.copy(worldQuestFile, characterQuestFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not capture FTB Quests progress for '{}' from {}",
                    profile.nickname(), CharacterSession.worldKey(server), e);
        }
    }

    private static Path teamFile(MinecraftServer server, UUID teamId) {
        // FTB Quests writes with plain UUID#toString(), confirmed against TeamData#saveIfChanged.
        return server.getWorldPath(QUESTS_FOLDER).resolve(teamId + ".snbt");
    }
}
