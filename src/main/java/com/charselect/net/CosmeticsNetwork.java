package com.charselect.net;

import com.charselect.CharSelect;
import com.charselect.client.ClientCosmetics;
import com.charselect.server.StandInRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carries character nicknames and skins between clients.
 *
 * <p>Every packet here is optional: a vanilla server, or one without the mod, simply never
 * receives them and nothing breaks. That is what keeps servers feeling untouched while
 * players who do have the mod still see each other's characters.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CosmeticsNetwork {

    /** What each connected player currently looks like, server-side. */
    private static final Map<UUID, CharacterCosmetics> ANNOUNCED = new HashMap<>();

    private CosmeticsNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Optional, so a client with the mod can still join a server without it.
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.playToServer(CosmeticsPayloads.Announce.TYPE,
                CosmeticsPayloads.Announce.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sender) {
                        onAnnounced(sender, payload.cosmetics());
                    }
                }));

        registrar.playToClient(CosmeticsPayloads.Apply.TYPE,
                CosmeticsPayloads.Apply.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientCosmetics.accept(payload.player(), payload.cosmetics())));

        registrar.playToClient(CosmeticsPayloads.Forget.TYPE,
                CosmeticsPayloads.Forget.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientCosmetics.forget(payload.player())));
    }

    // ------------------------------------------------------------------ server side

    /**
     * What the server last heard this player is playing as, for anything that needs to hand
     * the same look to something else - a stand-in entity taking their place, most notably.
     * Empty rather than null if nothing has been announced.
     */
    public static CharacterCosmetics announced(UUID playerId) {
        return ANNOUNCED.getOrDefault(playerId, CharacterCosmetics.NONE);
    }

    private static void onAnnounced(ServerPlayer sender, CharacterCosmetics cosmetics) {
        if (cosmetics.isEmpty()) {
            ANNOUNCED.remove(sender.getUUID());
            PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Forget(sender.getUUID()));
            return;
        }

        ANNOUNCED.put(sender.getUUID(), cosmetics);
        CharSelect.LOGGER.debug("{} is playing as '{}'",
                sender.getGameProfile().getName(), cosmetics.nickname());

        // Tell everyone about the newcomer, and the newcomer about everyone - both every
        // other connected player, and every stand-in currently left standing. A stand-in's
        // own Apply only ever went out once, to whoever was online the moment it was spawned
        // (see server.StandInRegistry's own doc comment on exactly why) - without this, a
        // player who was not there for that broadcast, very often the departed owner
        // reconnecting later to reclaim it, would never learn its appearance at all.
        PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Apply(sender.getUUID(), cosmetics));
        ANNOUNCED.forEach((id, existing) -> {
            if (!id.equals(sender.getUUID())) {
                PacketDistributor.sendToPlayer(sender, new CosmeticsPayloads.Apply(id, existing));
            }
        });
        StandInRegistry.allCosmetics().forEach((entityId, standInCosmetics) ->
                PacketDistributor.sendToPlayer(sender, new CosmeticsPayloads.Apply(entityId, standInCosmetics)));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer leaving)) {
            return;
        }
        UUID id = leaving.getUUID();
        if (ANNOUNCED.remove(id) != null) {
            PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Forget(id));
        }
    }
}
