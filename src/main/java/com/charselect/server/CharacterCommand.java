package com.charselect.server;

import com.charselect.CharSelect;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /character reconnect} - leaves the world to choose a different character, on a real
 * remote connection. Deliberately just a disconnect, not a live in-place swap: it runs through
 * the exact same join handshake any fresh connection does (see {@code net.CharacterJoinNetwork}
 * and {@code client.ClientCharacterJoin}), the one part of this whole feature that has actually
 * proven itself reliable, rather than a second, parallel "pick a character mid-game and carry
 * that choice through a reconnect" path with its own edge cases to keep working.
 *
 * <p>Refused for the integrated server's own host: disconnecting from your own integrated
 * server means the world closes, with no address to reconnect to - it would just boot the host
 * out to the title screen. Singleplayer already has a safe way to change characters (return to
 * the character select screen), so this is scoped to servers, exactly as originally asked for.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterCommand {

    private CharacterCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("character")
                .then(Commands.literal("reconnect")
                        .executes(context -> {
                            requireRemotePlayer(context.getSource(), CharacterSwitcher::reconnect);
                            return 1;
                        })));
    }

    private interface PlayerAction {
        void run(ServerPlayer player);
    }

    private static void requireRemotePlayer(CommandSourceStack source, PlayerAction action) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("charselect.command.players_only"));
            return;
        }
        if (source.getServer().isSingleplayerOwner(player.getGameProfile())) {
            source.sendFailure(Component.translatable("charselect.command.singleplayer_only_host"));
            return;
        }
        action.run(player);
    }
}
