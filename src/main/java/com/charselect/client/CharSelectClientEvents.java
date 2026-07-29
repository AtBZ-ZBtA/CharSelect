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
        ActiveServerPolicy.reset();
        // A null player means nobody actually logged out: opening a singleplayer world makes
        // vanilla call Minecraft#disconnect first, purely to tidy up, and that fires this event
        // with nothing attached to it. Treating that as a real logout cleared the character the
        // player had just picked, moments before the world loaded them - and with no active
        // character, CharacterSession#isEngaged says no, the load hook stands down, and vanilla
        // reads the player straight out of level.dat. That is the whole character system
        // silently switching itself off, and it looks exactly like items being tied to the
        // world rather than the character.
        //
        // The singleplayer server is no help in telling those apart here: the old session is
        // already gone and the new one has not started, so it reads null either way.
        if (event.getPlayer() != null && Minecraft.getInstance().getSingleplayerServer() == null) {
            // A remote connection's character selection is cosmetics-only scaffolding (see
            // ActiveCharacter's own doc comment) and must not silently follow the player into
            // a different server or back into singleplayer. The singleplayer/Essential-host
            // path clears this itself via ScreenRouter reaching TitleScreen instead - clearing
            // it here too would race with that screen's own read of the still-active selection.
            ActiveCharacter.clear();
        }
        // No-op unless server.CharacterSwitcher just told us a reconnect is coming.
        ClientCharacterSwitch.reconnectIfPending();
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
