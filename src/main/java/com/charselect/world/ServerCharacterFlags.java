package com.charselect.world;

import com.charselect.CharSelect;
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
 * The mod's own record of a server world's multiplayer setup, kept in a small sidecar file
 * beside level.dat - a separate file from {@link WorldFlags}, which answers a completely
 * different question (which kind of character this world belongs to, decided client-side at
 * world creation) that does not apply to a dedicated server at all.
 *
 * <p>Right now this tracks exactly one thing: whether the one-time "can players bring items
 * from their singleplayer characters?" prompt has already been resolved for this world. It
 * has to be its own flag rather than inferred from the {@code itemsTransfer} gamerule's
 * current value, since an operator changing that gamerule later with {@code /gamerule} must
 * never re-trigger the prompt for the next player who joins.
 */
public final class ServerCharacterFlags {
    private static final String FILE_NAME = "charselect_server.dat";

    public record Data(boolean itemsTransferPolicyInitialized) {
        public static final Data DEFAULT = new Data(false);

        public Data withPolicyInitialized() {
            return new Data(true);
        }
    }

    private static final Map<Path, Data> CACHE = new ConcurrentHashMap<>();

    private ServerCharacterFlags() {
    }

    public static Data resolve(Path worldDir) {
        return CACHE.computeIfAbsent(worldDir, dir -> {
            Data read = read(dir);
            return read != null ? read : Data.DEFAULT;
        });
    }

    @Nullable
    private static Data read(Path worldDir) {
        Path file = worldDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            return new Data(tag.getBoolean("ItemsTransferPolicyInitialized"));
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not read the server sidecar at {}", file, e);
            return null;
        }
    }

    public static void write(Path worldDir, Data data) {
        Path file = worldDir.resolve(FILE_NAME);
        try {
            Files.createDirectories(worldDir);
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("ItemsTransferPolicyInitialized", data.itemsTransferPolicyInitialized());
            NbtIo.writeCompressed(tag, file);
            CACHE.put(worldDir, data);
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not write the server sidecar at {}", file, e);
        }
    }
}
