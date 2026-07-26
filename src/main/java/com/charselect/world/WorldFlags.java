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
    private static final int VERSION = 4;

    /**
     * Everything the sidecar records about one world.
     *
     * <p>Several independent things get decided over a world's lifetime, at different
     * moments and by different code: gamemode classification at server start, player-data
     * adoption when the first character actually loads, and quest-data adoption from a
     * third-party compat layer that runs earlier than either. Each has to know whether
     * <em>it specifically</em> has already run, not just whether the sidecar file happens to
     * exist yet - a file written for one of these reasons must never look, to one of the
     * others, like its own question has already been answered.
     *
     * @param mode                 which kind of character the world belongs to, or null when
     *                             the world accepts any character; meaningless unless
     *                             {@code classified} is true, since null is also what an
     *                             untouched world defaults to
     * @param banCheatedCharacters refuses characters that have been in a cheated world
     * @param adopted              whether a character has already taken over this world's
     *                             pre-existing player data; only the first one may
     * @param questsManaged        whether a third-party quest mod's progress for this world
     *                             has come under a character's ownership yet; only the first
     *                             character to enter after the mod arrives may adopt it, and
     *                             every visit after that belongs to whichever character is
     *                             active, never to whoever played most recently
     * @param classified           whether this world's gamemode has been decided for good
     */
    public record Data(@Nullable CharacterGameMode mode, boolean banCheatedCharacters,
                       boolean adopted, boolean questsManaged, boolean classified) {

        /**
         * A world that predates the mod, or was made without one. It has no allegiance, so
         * every character can play it and nobody loses access to what they already had.
         */
        public boolean openToAnyone() {
            return mode == null;
        }

        public Data withAdopted() {
            return new Data(mode, banCheatedCharacters, true, questsManaged, classified);
        }

        public Data withQuestsManaged() {
            return new Data(mode, banCheatedCharacters, adopted, true, classified);
        }

        public Data withClassification(@Nullable CharacterGameMode newMode, boolean banCheated) {
            return new Data(newMode, banCheated, adopted, questsManaged, true);
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
        Data result = read != null ? read : new Data(null, false, false, false, false);
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

            // A file from before "Classified" existed has no such key, which would read
            // back false - but under every earlier version, a sidecar existing at all only
            // ever meant classification had already happened, so that is still true here.
            // Getting this wrong would let an upgrade quietly reopen an already-classified
            // world to every gamemode again.
            boolean classified = tag.getInt("Version") >= 4
                    ? tag.getBoolean("Classified")
                    : true;

            return new Data(
                    ANY_MODE.equals(mode) ? null
                            : CharacterGameMode.byKey(mode, CharacterGameMode.SURVIVAL),
                    tag.getBoolean("BanCheated"),
                    tag.getBoolean("Adopted"),
                    tag.getBoolean("QuestsManaged"),
                    classified);
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
            tag.putBoolean("QuestsManaged", data.questsManaged());
            tag.putBoolean("Classified", data.classified());
            NbtIo.writeCompressed(tag, file);
            CACHE.put(worldDir, data);
            CharSelect.LOGGER.debug(
                    "Wrote world sidecar for {}: mode={} banCheated={} adopted={} questsManaged={} "
                            + "classified={}",
                    worldDir.getFileName(),
                    data.openToAnyone() ? ANY_MODE : data.mode().key(),
                    data.banCheatedCharacters(), data.adopted(), data.questsManaged(),
                    data.classified());
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not write the world sidecar at {}", file, e);
        }
    }

    /** Dropped when the world list is rebuilt, so new and deleted worlds re-resolve. */
    public static void invalidateCache() {
        CACHE.clear();
    }
}
