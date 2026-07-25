package com.charselect.server;

import com.charselect.CharSelect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Recognises worlds that were played before this mod was installed.
 *
 * <p>Adding a mod to a modpack someone already has a save in must not make that save look
 * wiped. A world holding player data is one that was played without the character system, so
 * it stays open to everyone, and the first character to walk in inherits what is there.
 */
public final class LegacyWorlds {

    private LegacyWorlds() {
    }

    /**
     * Whether this world already holds player data.
     *
     * <p>Two places to look: the {@code Player} tag inside level.dat, which is where a
     * singleplayer host is kept, and the playerdata folder, which is where everyone else is.
     * A world created moments ago has neither.
     */
    public static boolean hasExistingPlayerData(MinecraftServer server) {
        if (server.getWorldData().getLoadedPlayerTag() != null) {
            return true;
        }
        return hasPlayerDataFiles(server.getWorldPath(LevelResource.PLAYER_DATA_DIR));
    }

    private static boolean hasPlayerDataFiles(Path playerDataDir) {
        if (!Files.isDirectory(playerDataDir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(playerDataDir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".dat"));
        } catch (IOException e) {
            CharSelect.LOGGER.warn("Could not inspect {} for existing player data",
                    playerDataDir, e);
            return false;
        }
    }
}
