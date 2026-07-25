package com.charselect.client;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.SkinRef;
import com.charselect.client.skin.SkinStorage;
import com.charselect.client.skin.SkinTextureCache;
import com.charselect.config.CharSelectConfig;
import com.charselect.net.CharacterCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the client knows about which character each player is wearing.
 *
 * <p>The local player's own character is applied straight from the active selection rather
 * than waiting for the server to echo it back, so your skin is right from the first frame
 * even on a server that has never heard of this mod.
 */
public final class ClientCosmetics {

    /** Nickname and resolved skin for one player. */
    public record Look(String nickname, SkinRef skin) {
    }

    private static final Map<UUID, Look> REMOTE = new ConcurrentHashMap<>();

    private ClientCosmetics() {
    }

    // ------------------------------------------------------------------ lookup

    /** The character look for this player, or null if they are just themselves. */
    @Nullable
    public static Look lookFor(UUID playerId) {
        Look local = localLook(playerId);
        if (local != null) {
            return local;
        }
        return REMOTE.get(playerId);
    }

    @Nullable
    public static PlayerSkin skinFor(UUID playerId) {
        Look look = lookFor(playerId);
        if (look == null || look.skin().usesAccountSkin()) {
            // Null means "no override", so the account's real skin resolves as usual.
            return null;
        }
        return SkinTextureCache.playerSkin(look.skin());
    }

    @Nullable
    public static Component nameFor(UUID playerId) {
        Look look = lookFor(playerId);
        return look == null || look.nickname().isEmpty() ? null : Component.literal(look.nickname());
    }

    /**
     * The local player's own character, if one is selected and cosmetics are wanted here.
     * Returns null when the player asked for remote servers to look completely untouched.
     */
    @Nullable
    private static Look localLook(UUID playerId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.getUUID().equals(playerId)) {
            return null;
        }
        CharacterProfile active = ActiveCharacter.getOrNull();
        if (active == null) {
            return null;
        }
        boolean remoteServer = minecraft.getSingleplayerServer() == null;
        if (remoteServer && !CharSelectConfig.INSTANCE.characterCosmeticsOnServers.get()) {
            return null;
        }
        return new Look(active.nickname(), active.skin());
    }

    // ------------------------------------------------------------------ incoming

    /** Stores a look announced by the server, caching the skin PNG locally as we go. */
    public static void accept(UUID playerId, CharacterCosmetics cosmetics) {
        SkinRef skin = cosmetics.slim() ? SkinRef.ALEX : SkinRef.STEVE;

        if (cosmetics.hasSkin()) {
            try {
                String hash = SkinStorage.store(cosmetics.skinPng());
                skin = SkinRef.file(cosmetics.nickname(), hash, modelOf(cosmetics));
                SkinTextureCache.preload(skin);
            } catch (Exception e) {
                CharSelect.LOGGER.warn("Ignoring an unusable skin sent for {}", playerId, e);
            }
        }

        REMOTE.put(playerId, new Look(cosmetics.nickname(), skin));
    }

    public static void forget(UUID playerId) {
        REMOTE.remove(playerId);
    }

    public static void clear() {
        REMOTE.clear();
    }

    private static SkinRef.SkinModel modelOf(CharacterCosmetics cosmetics) {
        return cosmetics.slim() ? SkinRef.SkinModel.SLIM : SkinRef.SkinModel.WIDE;
    }

    // ------------------------------------------------------------------ outgoing

    /** Packages the active character for the wire, or empty cosmetics if there is nothing to send. */
    public static CharacterCosmetics announcement() {
        CharacterProfile active = ActiveCharacter.getOrNull();
        if (active == null) {
            return CharacterCosmetics.NONE;
        }

        SkinRef skin = active.skin();
        byte[] png = new byte[0];
        if (skin.hasCustomTexture()) {
            try {
                png = SkinStorage.read(skin.hash());
            } catch (Exception e) {
                CharSelect.LOGGER.warn("Could not read the skin for '{}' to send it",
                        active.nickname(), e);
            }
        }
        return new CharacterCosmetics(active.nickname(), skin.model().slim(), png);
    }
}
