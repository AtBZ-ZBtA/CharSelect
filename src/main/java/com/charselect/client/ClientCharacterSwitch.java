package com.charselect.client;

import com.charselect.CharSelect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import javax.annotation.Nullable;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Client side of {@code /character reconnect}: reconnecting to the same address without the
 * player needing to do anything themselves. Everything about actually choosing a character
 * happens through the ordinary join handshake once that reconnect lands - see
 * {@code client.ClientCharacterJoin} - not here.
 */
public final class ClientCharacterSwitch {

    private ClientCharacterSwitch() {
    }

    /**
     * Set when the server has told us a disconnect is coming as part of {@code /character
     * reconnect} - see {@code server.CharacterSwitcher}. Captured now, while the connection
     * this player used is still open, rather than trying to recover it after the fact: the
     * server itself has no reliable way to know what address got a given connection here (a
     * direct-connect address is never sent to it), but the client already knows exactly what
     * worked.
     */
    @Nullable
    private static InetSocketAddress pendingReconnectAddress;

    public static void onPrepareReconnect() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return;
        }
        SocketAddress remote = minecraft.getConnection().getConnection().getRemoteAddress();
        if (remote instanceof InetSocketAddress inet) {
            pendingReconnectAddress = inet;
        } else {
            CharSelect.LOGGER.debug("Could not determine this connection's address to auto-reconnect");
        }
    }

    /**
     * Called from the client logout hook. Reconnecting has to wait until the old connection
     * has actually finished tearing down - starting a new one from inside the same event that
     * is still unwinding the old one risks {@link ConnectScreen} seeing itself as already
     * connecting - so this only consumes the pending address and hands the actual reconnect
     * off to the next client tick.
     */
    public static void reconnectIfPending() {
        InetSocketAddress address = pendingReconnectAddress;
        if (address == null) {
            return;
        }
        pendingReconnectAddress = null;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.tell(() -> {
            String host = address.getHostString();
            ServerData serverData = new ServerData(host, host + ":" + address.getPort(), ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), minecraft,
                    new ServerAddress(host, address.getPort()), serverData, false, null);
        });
    }
}
