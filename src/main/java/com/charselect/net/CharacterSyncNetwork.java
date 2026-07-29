package com.charselect.net;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.client.ClientCharacterSync;
import com.charselect.server.CharacterSession;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers {@link CharacterSyncPayloads}. Optional, like every other channel this mod uses. */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterSyncNetwork {

    private CharacterSyncNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToClient(CharacterSyncPayloads.DownloadCharacter.TYPE,
                CharacterSyncPayloads.DownloadCharacter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientCharacterSync.onDownload(payload)));

        registrar.playToServer(CharacterSyncPayloads.RequestFinalSync.TYPE,
                CharacterSyncPayloads.RequestFinalSync.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        onFinalSyncRequested(player);
                    }
                }));
    }

    /**
     * A client is quitting and has asked for its character before it goes. Captured fresh
     * rather than sent from whatever the last autosave happened to hold, since the whole point
     * of the request is to not lose what has happened since then.
     */
    private static void onFinalSyncRequested(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        if (profile == null) {
            return;
        }
        CharacterSession.capture(server, player);
        CharacterSession.pushToClient(server, player, profile);
    }
}
