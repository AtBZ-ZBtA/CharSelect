package com.charselect.server;

import java.util.UUID;

/**
 * Implemented by {@code PlayerListMixin} (woven onto the real {@code PlayerList}), so a
 * mid-game character switch can evict a player's cached advancement/stats trackers.
 *
 * <p>Both are cached per account UUID, which does not change across a switch - only the
 * character behind it does - so without this, a switched-to character would silently keep
 * showing the previous one's advancement and stat progress until the next full reconnect,
 * since nothing would ever prompt {@code PlayerListMixin}'s own hooks to re-resolve them.
 */
public interface CharacterTrackerAccess {
    void charselect$forgetCharacterTrackers(UUID playerId);
}
