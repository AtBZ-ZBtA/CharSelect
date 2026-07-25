package com.charselect.character;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;

/**
 * The gamemode a character is created under. This is permanent: it decides which worlds the
 * character may enter, and for survival characters it is also a hard ceiling on the gamemode
 * they can ever be in.
 */
public enum CharacterGameMode {
    SURVIVAL("survival", GameType.SURVIVAL),
    CREATIVE("creative", GameType.CREATIVE);

    private final String key;
    private final GameType defaultType;

    CharacterGameMode(String key, GameType defaultType) {
        this.key = key;
        this.defaultType = defaultType;
    }

    public String key() {
        return key;
    }

    /** The gamemode a character of this kind starts in when it enters a world. */
    public GameType defaultType() {
        return defaultType;
    }

    /**
     * Whether a character of this kind is allowed to be in the given gamemode. Creative
     * characters roam freely; survival characters are pinned to survival and adventure,
     * cheats or no cheats.
     */
    public boolean permits(GameType type) {
        if (this == CREATIVE) {
            return true;
        }
        return type == GameType.SURVIVAL || type == GameType.ADVENTURE;
    }

    /** Clamps a requested gamemode down to something this character is allowed to be in. */
    public GameType clamp(GameType requested) {
        return permits(requested) ? requested : GameType.SURVIVAL;
    }

    public Component displayName() {
        return Component.translatable("charselect.gamemode." + key);
    }

    public static CharacterGameMode byKey(String key, CharacterGameMode fallback) {
        for (CharacterGameMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return fallback;
    }

    /** The character kind a world of the given gamemode belongs to. */
    public static CharacterGameMode forWorldType(GameType type) {
        return type == GameType.CREATIVE ? CREATIVE : SURVIVAL;
    }
}
