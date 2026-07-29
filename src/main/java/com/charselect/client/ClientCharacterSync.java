package com.charselect.client;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.net.CharacterSyncPayloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of handing a character's server-side changes back into its local copy. */
public final class ClientCharacterSync {

    /**
     * How long a quit will stall waiting for the server to hand this character back. Long
     * enough to cover an ordinary round trip on a real connection, short enough that a server
     * which is hung, lagging, or simply never going to answer cannot hold the game hostage -
     * the periodic push in {@code server.CharacterLifecycle} is what covers that case anyway.
     */
    private static final long FINAL_SYNC_TIMEOUT_MS = 2000L;

    private static volatile boolean awaitingFinalSync;

    private ClientCharacterSync() {
    }

    public static void onDownload(CharacterSyncPayloads.DownloadCharacter payload) {
        CharacterProfile updated = CharacterProfile.load(payload.profile());
        // update() itself no-ops if this id no longer names a local slot - most likely
        // because the player deleted it while it was away, which this must not undo.
        CharacterStore.get().update(updated);
        awaitingFinalSync = false;
    }

    /**
     * Asks the server for this character's current state and waits for it, before the caller
     * closes the connection - see {@code mixin.client.MinecraftDisconnectMixin}.
     *
     * <p>Stalling the quit is the entire point: once the socket is closed the server cannot
     * send anything back, and the client would go on holding a stale copy that it re-uploads
     * on its next join, silently undoing the session that just happened. Vanilla already
     * stalls a singleplayer quit the same way while the world finishes saving, so a brief
     * pause here is not out of character for the moment it happens in.
     *
     * <p>Only the graceful quit path reaches this. A crash, an alt-F4, a kick, or a dropped
     * connection all take the socket away without warning, and nothing client-side can ask in
     * time - that is what the periodic push exists to bound.
     */
    public static void requestFinalSyncAndWait() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener listener = minecraft.getConnection();
        if (listener == null || minecraft.getSingleplayerServer() != null) {
            // The integrated server's host shares this JVM and already writes straight to the
            // same local store; there is nothing to fetch over a network that is not a network.
            return;
        }
        Connection connection = listener.getConnection();
        if (!connection.isConnected()
                || !listener.hasChannel(CharacterSyncPayloads.RequestFinalSync.TYPE)) {
            // Already gone, or a server that never heard of this mod - either way, nothing
            // will ever answer and there is no reason to make the player wait to find out.
            return;
        }

        awaitingFinalSync = true;
        try {
            PacketDistributor.sendToServer(new CharacterSyncPayloads.RequestFinalSync());
        } catch (Exception e) {
            awaitingFinalSync = false;
            CharSelect.LOGGER.debug("Could not ask the server for a final character sync", e);
            return;
        }

        long deadline = Util.getMillis() + FINAL_SYNC_TIMEOUT_MS;
        while (awaitingFinalSync && Util.getMillis() < deadline && connection.isConnected()) {
            // Flush what is queued outbound, then run whatever the netty threads have handed
            // back to the main thread - an incoming payload is dispatched through exactly that
            // queue, so draining it here is what actually lets the answer land.
            connection.tick();
            while (minecraft.pollTask()) {
                // Drained for the side effects; the reply arrives as one of these tasks.
            }
        }

        if (awaitingFinalSync) {
            awaitingFinalSync = false;
            CharSelect.LOGGER.warn("Quit without the server handing this character back in time;"
                    + " anything since the last autosave stays only on the server");
        } else {
            CharSelect.LOGGER.info("Server handed this character back before quitting");
        }
    }
}
