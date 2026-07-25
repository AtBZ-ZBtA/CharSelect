package com.charselect.character;

import com.charselect.CharSelect;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The character the player picked before choosing a world.
 *
 * <p>Singleplayer and Essential-hosted worlds run the integrated server inside this same
 * JVM, so a static holder is all the coordination the client GUI and the server-side data
 * handling need. On a remote dedicated server there is no owning integrated server, and the
 * selection is used for cosmetics only.
 */
public final class ActiveCharacter {
    @Nullable
    private static CharacterProfile active;

    private ActiveCharacter() {
    }

    public static Optional<CharacterProfile> get() {
        return Optional.ofNullable(active);
    }

    @Nullable
    public static CharacterProfile getOrNull() {
        return active;
    }

    public static boolean isActive(UUID id) {
        return active != null && active.id().equals(id);
    }

    public static void select(@Nullable CharacterProfile profile) {
        active = profile;
        if (profile != null) {
            CharSelect.LOGGER.info("Active character is now '{}' ({}, {})",
                    profile.nickname(), profile.gameMode().key(), profile.id());
        } else {
            CharSelect.LOGGER.info("Active character cleared");
        }
    }

    public static void clear() {
        select(null);
    }

    /** The gamemode of worlds the current selection may enter, if anything is selected. */
    public static Optional<CharacterGameMode> gameMode() {
        return get().map(CharacterProfile::gameMode);
    }
}
