package com.charselect.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accounts whose next login owes a death, because the join upload found a corpse standing in
 * for the character being uploaded - see {@code net.CharacterJoinNetwork}. Deliberately not
 * stored on the {@code character.CharacterProfile} itself: the corpse entity is what actually
 * decides this now, not a separately-persisted flag that could fall out of step with it.
 *
 * <p>Marked at upload time (configuration phase, before the player exists), consumed once the
 * player has actually spawned and can be killed for real - see
 * {@code server.CharacterLifecycle#onLogin}.
 */
public final class PendingCharacterKill {
    private static final Set<UUID> ACCOUNTS = ConcurrentHashMap.newKeySet();

    private PendingCharacterKill() {
    }

    public static void mark(UUID accountId) {
        ACCOUNTS.add(accountId);
    }

    /** @return true if this account owed a death, clearing the mark either way. */
    public static boolean consume(UUID accountId) {
        return ACCOUNTS.remove(accountId);
    }
}
