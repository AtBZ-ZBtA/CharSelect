package com.charselect.character;

import com.charselect.CharSelect;
import com.charselect.config.CharSelectConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Owns the on-disk character slots under {@code .minecraft/charselect/}.
 *
 * <p>Characters live outside any world folder on purpose: that separation is the whole
 * point of the mod. Deleting a world never costs you a character, and deleting a character
 * never costs you a world.
 */
public final class CharacterStore {
    private static final String CHARACTER_SUFFIX = ".dat";

    private static CharacterStore instance;

    private final Path root;
    private final Path charactersDir;
    private final Path skinsDir;
    private final Map<UUID, CharacterProfile> profiles = new LinkedHashMap<>();
    private boolean loaded;

    private CharacterStore(Path root) {
        this.root = root;
        this.charactersDir = root.resolve("characters");
        this.skinsDir = root.resolve("skins");
    }

    public static CharacterStore get() {
        if (instance == null) {
            instance = new CharacterStore(FMLPaths.GAMEDIR.get().resolve(CharSelect.MODID));
            instance.load();
        }
        return instance;
    }

    public Path skinsDir() {
        return skinsDir;
    }

    public Path root() {
        return root;
    }

    // ------------------------------------------------------------------ loading

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Files.createDirectories(charactersDir);
            Files.createDirectories(skinsDir);
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not create the charselect directory at {}", root, e);
            return;
        }

        List<CharacterProfile> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(charactersDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(CHARACTER_SUFFIX))
                 .forEach(p -> readProfile(p).ifPresent(found::add));
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not list character files in {}", charactersDir, e);
        }

        found.sort(Comparator.comparingLong(CharacterProfile::lastPlayed).reversed()
                             .thenComparingLong(CharacterProfile::created));
        found.forEach(p -> profiles.put(p.id(), p));
        CharSelect.LOGGER.info("Loaded {} character(s) from {}", profiles.size(), charactersDir);
    }

    private Optional<CharacterProfile> readProfile(Path path) {
        try {
            CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            CharacterProfile profile = CharacterProfile.load(tag);
            if (profile.nickname().isBlank()) {
                CharSelect.LOGGER.warn("Skipping character file {} - it has no nickname", path);
                return Optional.empty();
            }
            return Optional.of(profile);
        } catch (Exception e) {
            // A corrupt slot must never stop the other slots from loading.
            CharSelect.LOGGER.error("Could not read character file {}", path, e);
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ queries

    /** Every character, most recently played first. */
    public List<CharacterProfile> all() {
        return new ArrayList<>(profiles.values());
    }

    /** Characters that may enter worlds of the given kind. Gating is strictly two-way. */
    public List<CharacterProfile> ofGameMode(CharacterGameMode mode) {
        return profiles.values().stream().filter(p -> p.gameMode() == mode).toList();
    }

    public Optional<CharacterProfile> byId(UUID id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public int count() {
        return profiles.size();
    }

    public boolean atSlotLimit() {
        return count() >= CharSelectConfig.INSTANCE.maxCharacterSlots.get();
    }

    /** Nicknames are not unique keys, but colliding ones are confusing in the slot list. */
    public boolean nicknameTaken(String nickname, UUID ignoring) {
        return profiles.values().stream()
                .anyMatch(p -> !p.id().equals(ignoring)
                        && p.nickname().equalsIgnoreCase(nickname));
    }

    // ------------------------------------------------------------------ mutation

    public CharacterProfile create(String nickname, CharacterGameMode mode, SkinRef skin) {
        return create(nickname, mode, skin, false);
    }

    public CharacterProfile create(String nickname, CharacterGameMode mode, SkinRef skin,
                                   boolean hardcore) {
        CharacterProfile profile = CharacterProfile.create(nickname, mode, skin, hardcore);
        profiles.put(profile.id(), profile);
        save(profile);
        CharSelect.LOGGER.info("Created {}{} character '{}' ({})",
                profile.isHardcore() ? "hardcore " : "", mode.key(), nickname, profile.id());
        return profile;
    }

    /**
     * Copies a survival character into a brand new creative one, leaving the original
     * exactly as it was. Everything travels with the copy - inventory, quest progress, cheat
     * and curse history, per-world memory, all of it - because the point is a fork, not a
     * blank slate wearing the same skin.
     *
     * <p>Built by round-tripping through the same save/load NBT this store already uses for
     * every character, rather than a field-by-field copy constructor, so a future field
     * added to {@link CharacterProfile} is carried over automatically instead of silently
     * being left behind the next time someone forgets to update a manual copy.
     */
    public CharacterProfile convertToCreative(CharacterProfile source) {
        CompoundTag data = source.save();
        data.putUUID("Id", UUID.randomUUID());
        data.putString("Nickname", source.nickname() + " (creative)");
        data.putString("GameMode", CharacterGameMode.CREATIVE.key());
        // Neither carries meaning for a creative character: hardcore requires being able to
        // die in survival, and dead requires having been hardcore. Cheated and cursed are
        // left alone deliberately - they are history the copy actually shares, and dropping
        // them would let a cheated character's data walk into a clean-only world wearing a
        // new gamemode as cover.
        data.putBoolean("Hardcore", false);
        data.putBoolean("Dead", false);
        data.putLong("Created", System.currentTimeMillis());

        CharacterProfile copy = CharacterProfile.load(data);
        profiles.put(copy.id(), copy);
        save(copy);
        CharSelect.LOGGER.info("Converted '{}' ({}) into creative character '{}' ({})",
                source.nickname(), source.id(), copy.nickname(), copy.id());
        return copy;
    }

    public void save(CharacterProfile profile) {
        Path target = charactersDir.resolve(profile.id() + CHARACTER_SUFFIX);
        Path temp = charactersDir.resolve(profile.id() + CHARACTER_SUFFIX + ".tmp");
        try {
            Files.createDirectories(charactersDir);
            NbtIo.writeCompressed(profile.save(), temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not save character '{}' ({})",
                    profile.nickname(), profile.id(), e);
        }
    }

    public void saveAll() {
        profiles.values().forEach(this::save);
    }

    public void delete(CharacterProfile profile) {
        profiles.remove(profile.id());
        try {
            Files.deleteIfExists(charactersDir.resolve(profile.id() + CHARACTER_SUFFIX));
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not delete character file for {}", profile.id(), e);
        }
        CharSelect.LOGGER.info("Deleted character '{}' ({})", profile.nickname(), profile.id());
    }

    /** Called when a world is deleted so no character keeps a stale memory of it. */
    public void forgetWorld(String worldKey) {
        for (CharacterProfile profile : profiles.values()) {
            if (profile.peekWorldSlot(worldKey) != null) {
                profile.forgetWorld(worldKey);
                if (profile.lastWorld().equals(worldKey)) {
                    profile.setLastWorld("");
                }
                save(profile);
            }
        }
    }
}
