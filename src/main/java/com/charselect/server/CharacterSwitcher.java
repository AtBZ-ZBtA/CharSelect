package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.net.CharacterSwitchPayloads;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Leaves the world to choose a different character - the mechanism behind
 * {@code /character reconnect}.
 *
 * <p><b>This used to try to reload the already-spawned {@link ServerPlayer} in place, via
 * vanilla's {@code PlayerList.respawn}.</b> In practice that left a duplicate UUID registered
 * in the level's entity tracking - confirmed from a real server's own log ({@code "Force-added
 * player with duplicate UUID"}), immediately followed by every subsequent reconnect to that
 * server hanging at login until it timed out. That is not a per-player inconvenience, it is
 * server corruption: it would have broken reconnecting for anyone, not just the player who
 * switched. Reusing {@code respawn} outside the death/dimension-change situations vanilla
 * actually exercises it for was the riskiest, least-precedented piece of this whole feature
 * from the start, and this is exactly the failure that risk was flagged for.
 *
 * <p>A later version tried to have the player pick the new character before disconnecting, then
 * carry that choice through the reconnect so the ordinary join handshake could skip asking
 * again. That meant keeping two related but separately-evolving pieces of state in sync across
 * a disconnect boundary - which character was picked, and whether a reconnect was in flight -
 * and each attempt to patch one side surfaced a new way for the other to fall out of step.
 * This drops all of that: it captures the outgoing character's state, then disconnects the
 * player outright with nothing decided about what comes next. The ordinary logout that follows
 * (see {@code server.net.CharacterDownloadOnLeave}) hands the data back and leaves a stand-in
 * behind if the gamerule wants one, same as any other disconnect. Reconnecting runs through the
 * ordinary join handshake - the one mechanism in this whole feature set that has actually been
 * proven to work end-to-end - and asks which character to play, exactly the way it does for
 * anyone else connecting. One extra click, in exchange for not having a second path to keep
 * correct.
 */
public final class CharacterSwitcher {

    private CharacterSwitcher() {
    }

    public static void reconnect(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        CharacterSession.capture(server, player);

        CharSelect.LOGGER.info("{} is reconnecting to choose a different character",
                player.getGameProfile().getName());

        // Queued while the connection is definitely still up, unlike the logout hook - see
        // CharacterSession#pushToClient. Without this the character being left behind would
        // reconnect holding whatever it had at the last autosave, not what it actually has now.
        CharacterProfile leaving = CharacterSession.profileFor(server, player);
        if (leaving != null) {
            CharacterSession.pushToClient(server, player, leaving);
        }

        PacketDistributor.sendToPlayer(player, new CharacterSwitchPayloads.PrepareReconnect());
        player.connection.disconnect(Component.translatable("charselect.command.reconnecting"));
    }
}
