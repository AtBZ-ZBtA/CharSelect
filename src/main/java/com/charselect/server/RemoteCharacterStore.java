package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server's own last-known copy of whichever character a connected (or recently
 * connected) account is playing.
 *
 * <p>Unlike {@link com.charselect.character.CharacterStore}, this is not a roster a player
 * picks from - a dedicated server never sees more than "the one character this account is
 * currently using" at a time. The client remains the source of truth for the character
 * *list* and for what gets uploaded on join; this store exists so the server has somewhere
 * authoritative to read from between the moment a character is uploaded and the moment it
 * is safely handed back to the client, including across a crash or kick that skips the
 * normal handback entirely. Saved inside the world folder specifically so it survives a
 * server restart and travels with a world backup, the same way a stand-in character entity
 * (see the {@code entity} package) would if one is standing in for this account instead.
 */
public final class RemoteCharacterStore {
    private static final String FILE_SUFFIX = ".dat";
    private static final LevelResource FOLDER = new LevelResource("charselect_remote");

    private static final Map<UUID, CharacterProfile> CACHE = new ConcurrentHashMap<>();

    private RemoteCharacterStore() {
    }

    /** The character currently held for this account, loading it from disk on first use. */
    @Nullable
    public static CharacterProfile get(MinecraftServer server, UUID accountId) {
        CharacterProfile cached = CACHE.get(accountId);
        if (cached != null) {
            return cached;
        }
        CharacterProfile loaded = read(server, accountId);
        if (loaded != null) {
            CACHE.put(accountId, loaded);
        }
        return loaded;
    }

    /** Records the given profile as this account's current server-side state. */
    public static void put(MinecraftServer server, UUID accountId, CharacterProfile profile) {
        CACHE.put(accountId, profile);
        write(server, accountId, profile);
    }

    /** Clears this account's held state, once it has been safely handed back to the client. */
    public static void remove(MinecraftServer server, UUID accountId) {
        CACHE.remove(accountId);
        try {
            Files.deleteIfExists(file(server, accountId));
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not delete remote character file for {}", accountId, e);
        }
    }

    private static Path file(MinecraftServer server, UUID accountId) {
        return server.getWorldPath(FOLDER).resolve(accountId + FILE_SUFFIX);
    }

    @Nullable
    private static CharacterProfile read(MinecraftServer server, UUID accountId) {
        Path file = file(server, accountId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            return CharacterProfile.load(tag);
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not read remote character file for {}", accountId, e);
            return null;
        }
    }

    private static void write(MinecraftServer server, UUID accountId, CharacterProfile profile) {
        Path dir = server.getWorldPath(FOLDER);
        Path target = dir.resolve(accountId + FILE_SUFFIX);
        Path temp = dir.resolve(accountId + FILE_SUFFIX + ".tmp");
        try {
            Files.createDirectories(dir);
            NbtIo.writeCompressed(profile.save(), temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not save remote character file for {}", accountId, e);
        }
    }
}
