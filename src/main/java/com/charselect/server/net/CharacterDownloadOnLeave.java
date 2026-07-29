package com.charselect.server.net;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.config.ModGameRules;
import com.charselect.entity.CharacterStandInEntity;
import com.charselect.net.CosmeticsNetwork;
import com.charselect.net.CosmeticsPayloads;
import com.charselect.server.CharacterSession;
import com.charselect.server.RemoteCharacterStore;
import com.charselect.server.StandInRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * Everything that happens the moment a remote connection's character stops being controlled
 * by anyone - handing its data back to the client's local store, and, if
 * {@code charactersStayBehind} is on, leaving it standing right where the player was.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} specifically so it captures this account's
 * cosmetics (nickname, skin) via {@link CosmeticsNetwork#announced} before {@code
 * CosmeticsNetwork}'s own, normal-priority logout handler forgets them - the stand-in needs
 * that same look relayed onto its own entity id before the source of it disappears.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterDownloadOnLeave {

    private CharacterDownloadOnLeave() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null || server.isSingleplayerOwner(player.getGameProfile())) {
            // The host's character already lives locally; there is nothing to hand back or
            // leave behind - ActiveCharacter already covers the singleplayer case entirely.
            return;
        }

        // PlayerList.remove() fires this event before its own save() call, so without this,
        // RemoteCharacterStore would still hold this session's second-to-last state rather
        // than what the player is actually leaving with.
        CharacterSession.capture(server, player);

        UUID accountId = player.getGameProfile().getId();
        CharacterProfile profile = RemoteCharacterStore.get(server, accountId);
        if (profile == null) {
            return;
        }

        if (server.getGameRules().getBoolean(ModGameRules.CHARACTERS_STAY_BEHIND)
                && player.level() instanceof ServerLevel level) {
            var cosmetics = CosmeticsNetwork.announced(accountId);
            CharacterStandInEntity standIn = CharacterStandInEntity.spawn(
                    level, player.position(), accountId, profile);
            StandInRegistry.register(accountId, profile.id(), standIn.getUUID(), cosmetics);
            // Relayed under the stand-in's own entity id, not the account's - the account
            // has no connection left to be "the sender" of anything, and every existing
            // cosmetics lookup (ClientCosmetics.skinFor, the nameplate renderer) is already
            // written to key off whatever UUID the entity being drawn actually has.
            PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Apply(standIn.getUUID(), cosmetics));
            CharSelect.LOGGER.info("'{}' stays behind at {}", profile.nickname(), player.position());
        }

        // NOTE: nothing is sent to the client here, and nothing can be. This event is fired by
        // PlayerList#remove, which ServerGamePacketListenerImpl#onDisconnect calls after the
        // connection is already torn down - a packet queued now is dropped, every time. The
        // client gets its copy while it is still connected instead, from the autosave tick and
        // from server-initiated disconnects; see CharacterSession#pushToClient.
    }
}
