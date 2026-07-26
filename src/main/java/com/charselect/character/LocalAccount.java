package com.charselect.character;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The real Minecraft account driving this client, stashed where server-side code can read it.
 *
 * <p>Some third-party mods key their save data by the account's real UUID rather than
 * anything this mod controls, and need that UUID before any {@code ServerPlayer} exists to
 * read it from - before the integrated server has even started. This class exists so that
 * information can cross from client to server the same way {@link ActiveCharacter} does:
 * a plain static holder with no client-only imports, safe to read from a mixin that also
 * loads on a dedicated server, populated by client code the moment a character is chosen.
 *
 * <p>Deliberately holds nothing else. Widening this into a general "who is playing" class
 * would invite people to reach for it instead of {@link ActiveCharacter}.
 */
public final class LocalAccount {
    @Nullable
    private static UUID id;

    private LocalAccount() {
    }

    public static void set(UUID accountId) {
        id = accountId;
    }

    public static Optional<UUID> id() {
        return Optional.ofNullable(id);
    }
}
