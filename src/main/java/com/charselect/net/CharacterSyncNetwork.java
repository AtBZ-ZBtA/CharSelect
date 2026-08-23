package com.charselect.net;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.client.ClientCharacterSync;
import com.charselect.compat.CosmeticArmorCompat;
import com.charselect.server.CharacterSession;
import com.charselect.server.PlayerSaveAccess;
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
        // Run vanilla's own save for this player first. That is what fires
        // PlayerEvent.SaveToFile, and mods that keep player data in their own files only write
        // it when it does - Cosmetic Armor Reworked being the one this mod integrates with.
        // Without this the loadout handed back would be whatever was on disk when the player
        // logged in: the version the mod writes on logout is produced long after this packet,
        // because the client has to still be connected to receive the answer at all.
        ((PlayerSaveAccess) server.getPlayerList()).charselect$savePlayer(player);
        CosmeticArmorCompat.captureInto(server, player, profile);

        CharacterSession.capture(server, player);
        CharacterSession.pushToClient(server, player, profile);
    }
}
