package com.charselect.world;

import com.charselect.CharSelect;
import com.charselect.character.CharacterGameMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mod's own record of a world, kept in a small sidecar file beside level.dat.
 *
 * <p>Holds two things: which kind of character the world belongs to, and whether characters
 * that have been in a cheated world are refused entry. Both are decided when the world is
 * made and are not meant to drift afterwards - {@code /defaultgamemode} must not be able to
 * silently reclassify a world and lock its owner out.
 *
 * <p>Reads go through a cache because the world list consults this on every keystroke.
 */
public final class WorldFlags {
    private static final String FILE_NAME = "charselect_world.dat";
    private static final int VERSION = 2;

    /**
     * Everything the sidecar records about one world.
     *
     * @param mode                 which kind of character the world belongs to, or null when
     *                             the world accepts any character
     * @param banCheatedCharacters refuses characters that have been in a cheated world
     * @param adopted              whether a character has already taken over this world's
     *                             pre-existing player data; only the first one may
     */
    public record Data(@Nullable CharacterGameMode mode, boolean banCheatedCharacters,
                       boolean adopted) {

        /**
         * A world that predates the mod, or was made without one. It has no allegiance, so
         * every character can play it and nobody loses access to what they already had.
         */
        public boolean openToAnyone() {
            return mode == null;
        }

        public Data withAdopted() {
            return new Data(mode, banCheatedCharacters, true);
        }
    }

    /** The classification given to worlds that already existed when the mod arrived. */
    public static final String ANY_MODE = "any";

    private static final Map<Path, Data> CACHE = new ConcurrentHashMap<>();

    private WorldFlags() {
    }

    /**
     * What this world requires of a character.
     *
     * <p>A world with no sidecar is one the mod has never seen - it either predates the
     * install or was made without the mod - so it is treated as open to any character. That
     * is what stops an existing save becoming unreachable the moment the mod is added.
     */
    public static Data resolve(Path worldDir) {
        Data cached = CACHE.get(worldDir);
        if (cached != null) {
            return cached;
        }
        Data read = read(worldDir);
        Data result = read != null ? read : new Data(null, false, false);
        CACHE.put(worldDir, result);
        return result;
    }

    @Nullable
    public static Data read(Path worldDir) {
        Path file = worldDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            String mode = tag.getString("Mode");
            if (mode.isEmpty()) {
                return null;
            }
            return new Data(
                    ANY_MODE.equals(mode) ? null
                            : CharacterGameMode.byKey(mode, CharacterGameMode.SURVIVAL),
                    tag.getBoolean("BanCheated"),
                    tag.getBoolean("Adopted"));
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not read the world sidecar at {}", file, e);
            return null;
        }
    }

    public static void write(Path worldDir, Data data) {
        Path file = worldDir.resolve(FILE_NAME);
        try {
            Files.createDirectories(worldDir);
            CompoundTag tag = new CompoundTag();
            tag.putInt("Version", VERSION);
            tag.putString("Mode", data.openToAnyone() ? ANY_MODE : data.mode().key());
            tag.putBoolean("BanCheated", data.banCheatedCharacters());
            tag.putBoolean("Adopted", data.adopted());
            NbtIo.writeCompressed(tag, file);
            CACHE.put(worldDir, data);
            CharSelect.LOGGER.debug("Wrote world sidecar for {}: mode={} banCheated={} adopted={}",
                    worldDir.getFileName(),
                    data.openToAnyone() ? ANY_MODE : data.mode().key(),
                    data.banCheatedCharacters(), data.adopted());
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not write the world sidecar at {}", file, e);
        }
    }

    /** Dropped when the world list is rebuilt, so new and deleted worlds re-resolve. */
    public static void invalidateCache() {
        CACHE.clear();
    }
}
