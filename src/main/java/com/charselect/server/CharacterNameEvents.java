package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Makes chat and the player list show the character's nickname rather than the account name.
 *
 * <p>This only runs where the character system is actually in charge - your own singleplayer
 * or Essential-hosted world. On someone else's server the server owns your name, and the
 * nickname is drawn client-side instead.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterNameEvents {

    private CharacterNameEvents() {
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        nicknameOf(event.getEntity()).ifPresent(nickname ->
                event.setDisplayname(Component.literal(nickname)));
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat event) {
        nicknameOf(event.getEntity()).ifPresent(nickname ->
                event.setDisplayName(Component.literal(nickname)));
    }

    private static java.util.Optional<String> nicknameOf(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return java.util.Optional.empty();
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        return profile == null || profile.nickname().isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(profile.nickname());
    }
}
