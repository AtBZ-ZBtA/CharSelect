package com.charselect.server;

import com.charselect.net.CharacterCosmetics;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which stand-in entity, if any, is currently representing each character - a fast
 * lookup for reclaiming one on reconnect or a mid-game switch back, without scanning entities.
 *
 * <p>Keyed by account <b>and</b> character, not account alone: one account can leave more than
 * one of its own characters standing over the course of a session (switch to A leaves A behind,
 * later switch to B leaves B behind too), and an account-only key would let the second
 * registration silently overwrite the first - losing track of A's stand-in entirely, and worse,
 * having a later reclaim for a completely different character grab whichever stand-in happened
 * to be registered most recently instead of its own.
 *
 * <p>Also remembers the cosmetics each one was created with. Broadcasting {@code
 * CosmeticsPayloads.Apply} for a stand-in only reaches whoever is online at the exact moment
 * it is spawned - a player who was not there yet (including the departed owner themselves,
 * reconnecting later) never receives it any other way, since nothing about a stand-in's
 * appearance is carried by the entity's own vanilla spawn packet. {@code net.CosmeticsNetwork}
 * reads this to catch every newly-joining player up on every stand-in currently standing,
 * the same way it already catches them up on every other connected player.
 *
 * <p>In-memory only, and deliberately so: the entities themselves are what actually persist
 * across a server restart (they are normal registered entities, saved with their chunks like
 * any other), but a restart clears this index. Rebuilding it from persisted entities at
 * startup would need forcing every chunk in the world to load just to scan them, which is a
 * worse trade than the alternative: {@code entity.AbstractCharacterMarkerEntity} checks itself
 * against this registry the moment its own chunk actually loads, and discards itself if it
 * finds no entry - a restart-orphaned marker is simply removed rather than left standing
 * forever with no cosmetics and nothing able to find it again.
 */
public final class StandInRegistry {
    private record Key(UUID accountId, UUID characterId) {
    }

    private record Entry(UUID entityId, CharacterCosmetics cosmetics) {
    }

    private static final Map<Key, Entry> ENTRIES = new ConcurrentHashMap<>();

    private StandInRegistry() {
    }

    public static void register(UUID accountId, UUID characterId, UUID entityId, CharacterCosmetics cosmetics) {
        ENTRIES.put(new Key(accountId, characterId), new Entry(entityId, cosmetics));
    }

    @Nullable
    public static UUID entityFor(UUID accountId, UUID characterId) {
        Entry entry = ENTRIES.get(new Key(accountId, characterId));
        return entry == null ? null : entry.entityId();
    }

    public static void unregister(UUID accountId, UUID characterId) {
        ENTRIES.remove(new Key(accountId, characterId));
    }

    /** The cosmetics currently on file for this character's marker, if any is standing. */
    @Nullable
    public static CharacterCosmetics cosmeticsFor(UUID accountId, UUID characterId) {
        Entry entry = ENTRIES.get(new Key(accountId, characterId));
        return entry == null ? null : entry.cosmetics();
    }

    /** Every currently-standing stand-in's entity id and the cosmetics it was given. */
    public static Map<UUID, CharacterCosmetics> allCosmetics() {
        Map<UUID, CharacterCosmetics> result = new HashMap<>();
        ENTRIES.values().forEach(entry -> result.put(entry.entityId(), entry.cosmetics()));
        return result;
    }
}
