package com.charselect.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One character slot.
 *
 * <p>The profile is the only place player data lives. Worlds keep terrain and entities and
 * nothing else. Data is held in two buckets: {@link #sharedData()} follows the character
 * everywhere, while {@link #worldSlot(String)} holds anything the config chose to keep
 * world-local, plus the position the character left that world at.
 */
public final class CharacterProfile {
    /** Bumped when the on-disk layout changes, so old files can be migrated rather than dropped. */
    public static final int FORMAT_VERSION = 1;

    public static final int MAX_NICKNAME_LENGTH = 32;

    private final UUID id;
    private String nickname;
    private final CharacterGameMode gameMode;
    private SkinRef skin;
    private CompoundTag sharedData;
    private final Map<String, WorldSlot> worldSlots;
    private final long created;
    private long lastPlayed;
    private String lastWorld = "";
    private boolean fresh;

    /** True once this character has been inside a world with commands enabled. */
    private boolean cheated;

    /** Chosen at creation and never changed: this character does not survive dying. */
    private boolean hardcore;

    /** Set when a hardcore character dies. A dead character can be looked at, not played. */
    private boolean dead;
    private long diedAt;

    /** Total milliseconds this character has been in a world. */
    private long playtimeMillis;

    /**
     * True once this character has ever been caught wearing an item that curses its owner
     * for good, such as Enigmatic Legacy's Ring of Seven Curses. One-way, the same as
     * {@link #hardcore}: the point of a permanent curse is that it does not come off clean.
     */
    private boolean cursed;

    /**
     * The character's game data frozen at the moment before it first entered a cheated
     * world, so the run can be taken back. Empty when there is nothing to restore.
     */
    private CompoundTag pristine = new CompoundTag();

    private CharacterProfile(UUID id, String nickname, CharacterGameMode gameMode, SkinRef skin,
                             CompoundTag sharedData, Map<String, WorldSlot> worldSlots,
                             long created, long lastPlayed, String lastWorld, boolean fresh) {
        this.id = id;
        this.nickname = nickname;
        this.gameMode = gameMode;
        this.skin = skin;
        this.sharedData = sharedData;
        this.worldSlots = worldSlots;
        this.created = created;
        this.lastPlayed = lastPlayed;
        this.lastWorld = lastWorld;
        this.fresh = fresh;
    }

    public static CharacterProfile create(String nickname, CharacterGameMode gameMode, SkinRef skin) {
        return create(nickname, gameMode, skin, false);
    }

    public static CharacterProfile create(String nickname, CharacterGameMode gameMode, SkinRef skin,
                                          boolean hardcore) {
        long now = System.currentTimeMillis();
        CharacterProfile profile = new CharacterProfile(UUID.randomUUID(), nickname, gameMode, skin,
                new CompoundTag(), new HashMap<>(), now, 0L, "", true);
        // Only ever meaningful for survival: a creative character cannot really die.
        profile.hardcore = hardcore && gameMode == CharacterGameMode.SURVIVAL;
        return profile;
    }

    public UUID id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public CharacterGameMode gameMode() {
        return gameMode;
    }

    public SkinRef skin() {
        return skin;
    }

    public void setSkin(SkinRef skin) {
        this.skin = skin;
    }

    /** Player data that follows this character into every world. */
    public CompoundTag sharedData() {
        return sharedData;
    }

    public void setSharedData(CompoundTag sharedData) {
        this.sharedData = sharedData;
    }

    /** True until the character has actually spawned somewhere for the first time. */
    public boolean isFresh() {
        return fresh;
    }

    public void markPlayed() {
        this.fresh = false;
        this.lastPlayed = System.currentTimeMillis();
    }

    public long created() {
        return created;
    }

    public long lastPlayed() {
        return lastPlayed;
    }

    public String lastWorld() {
        return lastWorld;
    }

    public void setLastWorld(String lastWorld) {
        this.lastWorld = lastWorld;
    }

    // ------------------------------------------------------------------ hardcore and playtime

    public boolean isHardcore() {
        return hardcore;
    }

    public boolean isDead() {
        return dead;
    }

    public long diedAt() {
        return diedAt;
    }

    /** A dead hardcore character stays in the list as a record, but cannot be played. */
    public boolean isPlayable() {
        return !dead;
    }

    public void markDead() {
        if (dead) {
            return;
        }
        this.dead = true;
        this.diedAt = System.currentTimeMillis();
    }

    public long playtimeMillis() {
        return playtimeMillis;
    }

    public void addPlaytime(long millis) {
        if (millis > 0) {
            this.playtimeMillis += millis;
        }
    }

    // ------------------------------------------------------------------ curses

    public boolean isCursed() {
        return cursed;
    }

    /** Marks the character as permanently cursed. Does nothing if already marked. */
    public void markCursed() {
        this.cursed = true;
    }

    // ------------------------------------------------------------------ cheat history

    public boolean isCheated() {
        return cheated;
    }

    /** Whether a pre-cheat state is stored and the character can still be taken back. */
    public boolean canRestore() {
        return cheated && !pristine.isEmpty();
    }

    /**
     * Freezes the character's current game data as the pre-cheat version and marks it as
     * having been in a cheated world. Does nothing if it is already marked, so the original
     * clean state is never overwritten by a later cheated one.
     */
    public void markCheated() {
        if (cheated) {
            return;
        }
        CompoundTag snapshot = new CompoundTag();
        snapshot.put("SharedData", sharedData.copy());
        snapshot.put("Worlds", saveWorldSlots());
        snapshot.putString("LastWorld", lastWorld);
        snapshot.putBoolean("Fresh", fresh);
        this.pristine = snapshot;
        this.cheated = true;
    }

    /**
     * Rolls the character back to its pre-cheat state, discarding everything done since.
     * Nickname and skin are left alone - they are cosmetic and were probably chosen since.
     *
     * @return false if there was nothing stored to roll back to
     */
    public boolean restorePristine() {
        if (!canRestore()) {
            return false;
        }
        this.sharedData = pristine.getCompound("SharedData").copy();
        this.worldSlots.clear();
        loadWorldSlots(pristine.getList("Worlds", Tag.TAG_COMPOUND), this.worldSlots);
        this.lastWorld = pristine.getString("LastWorld");
        this.fresh = pristine.getBoolean("Fresh");
        this.pristine = new CompoundTag();
        this.cheated = false;
        return true;
    }

    /** The character's memory of one world, created on first visit. */
    public WorldSlot worldSlot(String worldKey) {
        return worldSlots.computeIfAbsent(worldKey, k -> new WorldSlot());
    }

    public WorldSlot peekWorldSlot(String worldKey) {
        return worldSlots.get(worldKey);
    }

    public Map<String, WorldSlot> worldSlots() {
        return worldSlots;
    }

    /** Drops a world's memory, for when the world itself is deleted. */
    public void forgetWorld(String worldKey) {
        worldSlots.remove(worldKey);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", FORMAT_VERSION);
        tag.putUUID("Id", id);
        tag.putString("Nickname", nickname);
        tag.putString("GameMode", gameMode.key());
        tag.put("Skin", skin.save());
        tag.put("SharedData", sharedData);
        tag.putLong("Created", created);
        tag.putLong("LastPlayed", lastPlayed);
        tag.putString("LastWorld", lastWorld);
        tag.putBoolean("Fresh", fresh);
        tag.putBoolean("Cheated", cheated);
        tag.putBoolean("Hardcore", hardcore);
        tag.putBoolean("Dead", dead);
        tag.putLong("DiedAt", diedAt);
        tag.putLong("Playtime", playtimeMillis);
        tag.putBoolean("Cursed", cursed);
        if (!pristine.isEmpty()) {
            tag.put("Pristine", pristine);
        }

        tag.put("Worlds", saveWorldSlots());
        return tag;
    }

    private ListTag saveWorldSlots() {
        ListTag worlds = new ListTag();
        worldSlots.forEach((key, slot) -> {
            CompoundTag entry = slot.save();
            entry.putString("Key", key);
            worlds.add(entry);
        });
        return worlds;
    }

    private static void loadWorldSlots(ListTag worlds, Map<String, WorldSlot> into) {
        for (int i = 0; i < worlds.size(); i++) {
            CompoundTag entry = worlds.getCompound(i);
            into.put(entry.getString("Key"), WorldSlot.load(entry));
        }
    }

    public static CharacterProfile load(CompoundTag tag) {
        Map<String, WorldSlot> slots = new HashMap<>();
        loadWorldSlots(tag.getList("Worlds", Tag.TAG_COMPOUND), slots);

        UUID id = tag.hasUUID("Id") ? tag.getUUID("Id") : UUID.randomUUID();
        CharacterProfile profile = new CharacterProfile(
                id,
                tag.getString("Nickname"),
                CharacterGameMode.byKey(tag.getString("GameMode"), CharacterGameMode.SURVIVAL),
                tag.contains("Skin") ? SkinRef.load(tag.getCompound("Skin")) : SkinRef.defaultFor(id),
                tag.getCompound("SharedData"),
                slots,
                tag.getLong("Created"),
                tag.getLong("LastPlayed"),
                tag.getString("LastWorld"),
                tag.getBoolean("Fresh"));
        profile.cheated = tag.getBoolean("Cheated");
        profile.hardcore = tag.getBoolean("Hardcore");
        profile.dead = tag.getBoolean("Dead");
        profile.diedAt = tag.getLong("DiedAt");
        profile.playtimeMillis = tag.getLong("Playtime");
        profile.cursed = tag.getBoolean("Cursed");
        profile.pristine = tag.getCompound("Pristine").copy();
        return profile;
    }
}
