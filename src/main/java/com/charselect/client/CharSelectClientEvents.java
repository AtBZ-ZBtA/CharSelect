package com.charselect.client;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import com.charselect.client.world.WorldVisibility;
import com.charselect.net.CosmeticsPayloads;
import com.charselect.world.WorldFlags;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Client-side wiring for announcing the chosen character and drawing everyone's nickname. */
@EventBusSubscriber(modid = CharSelect.MODID, value = Dist.CLIENT)
public final class CharSelectClientEvents {

    private CharSelectClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ActiveCharacter.get().ifPresent(profile ->
                CharSelect.LOGGER.info("Entering the world as '{}'", profile.nickname()));

        // Only worth sending if the connection actually accepts our payloads.
        if (Minecraft.getInstance().getConnection() != null
                && Minecraft.getInstance().getConnection().isAcceptingMessages()) {
            trySendCosmetics();
        }
    }

    private static void trySendCosmetics() {
        try {
            PacketDistributor.sendToServer(
                    new CosmeticsPayloads.Announce(ClientCosmetics.announcement()));
        } catch (Exception e) {
            // A server without the mod refuses the channel; that is a normal outcome.
            CharSelect.LOGGER.debug("This server does not accept character cosmetics", e);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCosmetics.clear();
        WorldFlags.invalidateCache();
        WorldVisibility.clearAccepted();
    }

    /** Draws the character's nickname above the player instead of the account name. */
    @SubscribeEvent
    public static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Component nickname = ClientCosmetics.nameFor(player.getUUID());
        if (nickname != null) {
            event.setContent(nickname);
        }
    }
}
