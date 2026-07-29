package com.charselect.net;

import com.charselect.CharSelect;
import com.charselect.character.CharacterGameMode;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterUploadFilter;
import com.charselect.client.ClientCharacterJoin;
import com.charselect.config.ModGameRules;
import com.charselect.entity.CharacterCorpseEntity;
import com.charselect.entity.CharacterStandInEntity;
import com.charselect.server.CharacterSession;
import com.charselect.server.GameModeGuard;
import com.charselect.server.PendingCharacterKill;
import com.charselect.server.RemoteCharacterStore;
import com.charselect.server.StandInRegistry;
import com.charselect.server.net.CharacterUploadTask;
import com.charselect.world.ServerCharacterFlags;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Registers the configuration-phase handshake a remote connection goes through before it
 * ever spawns, and the packets it uses. See {@code server.net.CharacterUploadTask} for why
 * this runs in the configuration phase rather than as an ordinary play-phase exchange.
 *
 * <p>Optional, like {@code CosmeticsNetwork}: a server without the mod never sends the
 * configuration task in the first place (it is only ever registered by this same mod), so a
 * vanilla client connecting to a modded server, or a modded client connecting to a vanilla
 * one, never sees any of this.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterJoinNetwork {

    private CharacterJoinNetwork() {
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();

        registrar.configurationToClient(CharacterJoinPayloads.RequestCharacterUpload.TYPE,
                CharacterJoinPayloads.RequestCharacterUpload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientCharacterJoin.onRequestUpload(payload, context)));

        registrar.configurationToServer(CharacterJoinPayloads.UploadCharacter.TYPE,
                CharacterJoinPayloads.UploadCharacter.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    // onCharacterUploaded returns false only when it has already disconnected
                    // the connection outright (a dead character trying to rejoin) - finishing
                    // the task on a connection that no longer exists has nothing to finish.
                    if (onCharacterUploaded(context, payload)) {
                        context.finishCurrentTask(CharacterUploadTask.TYPE);
                    }
                }));

        registrar.configurationToClient(CharacterJoinPayloads.UploadRefused.TYPE,
                CharacterJoinPayloads.UploadRefused.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() ->
                        ClientCharacterJoin.onUploadRefused(payload)));

        registrar.configurationToServer(CharacterJoinPayloads.ItemsTransferPolicyAnswer.TYPE,
                CharacterJoinPayloads.ItemsTransferPolicyAnswer.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> onPolicyAnswered(context, payload)));
    }

    /**
     * Only ever registers the task for a connection that is not the integrated server's own
     * host - that player already chose a character locally, on the way in, before the world
     * even started, and has nothing to upload.
     */
    @SubscribeEvent
    public static void registerTask(RegisterConfigurationTasksEvent event) {
        ServerCommonPacketListenerImpl listener =
                (ServerCommonPacketListenerImpl) event.getListener();
        MinecraftServer server = (MinecraftServer) listener.getMainThreadEventLoop();
        GameProfile owner = listener.getOwner();
        if (server.isSingleplayerOwner(owner)) {
            return;
        }

        boolean itemsTransferAllowed = server.getGameRules().getBoolean(ModGameRules.ITEMS_TRANSFER);
        boolean prompt = !ServerCharacterFlags.resolve(GameModeGuard.worldDir(server))
                .itemsTransferPolicyInitialized();
        event.register(new CharacterUploadTask(itemsTransferAllowed, prompt));
    }

    /** @return false if the connection was refused outright and there is nothing left to finish. */
    private static boolean onCharacterUploaded(IPayloadContext context,
                                                CharacterJoinPayloads.UploadCharacter payload) {
        ServerCommonPacketListenerImpl listener = (ServerCommonPacketListenerImpl) context.listener();
        MinecraftServer server = (MinecraftServer) listener.getMainThreadEventLoop();
        GameProfile owner = listener.getOwner();

        CharacterProfile uploaded = CharacterProfile.load(payload.profile());

        // A creative character on a server is effectively a permanent creative-mode grant, so
        // it is gated behind the same permission an operator would need to hand themselves
        // creative in the first place. Checked against ops.json by profile rather than against
        // a live ServerPlayer, since this runs in the configuration phase - there is no player
        // yet, which is the whole point: the connection is refused before one ever spawns.
        // The integrated server's own host never reaches this (see registerTask), so a
        // singleplayer creative character is unaffected.
        if (uploaded.gameMode() == CharacterGameMode.CREATIVE
                && !server.getPlayerList().isOp(owner)) {
            context.reply(new CharacterJoinPayloads.UploadRefused(
                    "charselect.join.creative_needs_op", uploaded.nickname()));
            context.disconnect(Component.translatable("charselect.join.creative_needs_op",
                    uploaded.nickname()));
            return false;
        }

        // Checked before anything else: a hardcore character that has actually died - whether
        // from ordinary damage or from a stand-in's death catching up with it on a previous
        // load, see server.CharacterLifecycle - is gone for good, the same rule hardcore
        // worlds already enforce; the client re-uploading a not-yet-dead copy of the same
        // character does not undo that. A killed stand-in whose character is not hardcore is
        // not refused here at all - it is simply going to die for real once it finishes
        // loading in, same as any other character would from any other death.
        // Matched by id, not just "whatever this account has cached" - RemoteCharacterStore
        // only ever remembers the one character an account last used, and a death there must
        // not lock the account out of ever reconnecting with a different, living character.
        CharacterProfile existing = RemoteCharacterStore.get(server, owner.getId());
        if (existing != null && existing.isDead() && existing.id().equals(uploaded.id())) {
            // Sent over our own channel first, not left to whatever the disconnect's own
            // screen happens to leave visible - a configuration-phase disconnect has no
            // player-facing world/HUD to fall back on if that screen transition does not
            // take, unlike a play-phase one, so the client must not have to depend on it.
            context.reply(new CharacterJoinPayloads.UploadRefused(
                    "charselect.hardcore.died", existing.nickname()));
            context.disconnect(Component.translatable("charselect.hardcore.died", existing.nickname()));
            return false;
        }

        // Enforced again here regardless of what the client actually sent: honouring the
        // gamerule is the server's job, not something a modified client can be trusted to
        // have already done just because it was asked nicely.
        boolean itemsTransferAllowed = server.getGameRules().getBoolean(ModGameRules.ITEMS_TRANSFER);
        CharacterProfile stored = CharacterUploadFilter.forItemsTransfer(uploaded, itemsTransferAllowed);

        // The client is always authoritative on join, but its idea of where this character
        // last stood can be stale - the hand-back-on-logout packet is best-effort and often
        // never arrives, since it is sent right as the connection is already closing. If a
        // living stand-in or a corpse exists, its actual position is ground truth for where
        // this character reappears; checkStandIn overwrites stored's remembered position with
        // it either way, and marks the account for a real death on login if what it found was
        // a corpse.
        checkStandIn(server, owner.getId(), stored, CharacterSession.worldKey(server));

        RemoteCharacterStore.put(server, owner.getId(), stored);
        CharSelect.LOGGER.info("{} uploaded character '{}' ({})",
                owner.getName(), stored.nickname(), stored.id());
        return true;
    }

    /**
     * Looked up by account <b>and</b> character id together, not account alone - the same
     * account can have left more than one of its characters standing, and matching on account
     * alone would find whichever one happened to be registered most recently regardless of
     * which character is actually loading now.
     *
     * <p>Two different outcomes depending on what is actually found:
     *
     * <ul>
     *   <li>A living {@code entity.CharacterStandInEntity}: reconnecting means the client's
     *       fresh upload is about to become this character's server-side state and the
     *       stand-in has nothing left to represent, so it is reclaimed - removed, after
     *       correcting {@code stored}'s remembered position for this world to match exactly
     *       where it was standing.
     *   <li>A permanent {@code entity.CharacterCorpseEntity}: this character owes a death.
     *       {@code stored}'s position is corrected to the corpse's spot the same way, but the
     *       corpse itself is left standing - it is a permanent marker, never reclaimed - and
     *       the account is flagged in {@link PendingCharacterKill} to actually collect that
     *       death once the player has finished loading in, by
     *       {@code server.CharacterLifecycle#onLogin}.
     * </ul>
     */
    private static void checkStandIn(MinecraftServer server, UUID accountId, CharacterProfile stored,
                                     String worldKey) {
        UUID entityId = StandInRegistry.entityFor(accountId, stored.id());
        if (entityId == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity found = level.getEntity(entityId);
            if (found instanceof CharacterCorpseEntity corpse) {
                stored.worldSlot(worldKey).setPosition(corpse.getX(), corpse.getY(), corpse.getZ(),
                        corpse.getYRot(), corpse.getXRot(), level.dimension().location().toString());
                PendingCharacterKill.mark(accountId);
                CharSelect.LOGGER.info("'{}' will die where it fell - a corpse is standing in for it",
                        stored.nickname());
                return;
            }
            if (found instanceof CharacterStandInEntity standIn) {
                stored.worldSlot(worldKey).setPosition(standIn.getX(), standIn.getY(), standIn.getZ(),
                        standIn.getYRot(), standIn.getXRot(), level.dimension().location().toString());
                StandInRegistry.unregister(accountId, stored.id());
                PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Forget(standIn.getUUID()));
                standIn.discard();
                CharSelect.LOGGER.info("Reclaimed the stand-in for '{}'", stored.nickname());
                return;
            }
        }
        // Not found in any currently-loaded chunk - most likely a marker from a previous
        // server run (StandInRegistry does not survive a restart, see its own doc comment).
        // It is still out there as a perfectly normal entity (and will discard itself the
        // moment its own chunk loads, see AbstractCharacterMarkerEntity); this just stops
        // treating it as this character's reclaimable one.
        StandInRegistry.unregister(accountId, stored.id());
    }

    private static void onPolicyAnswered(IPayloadContext context,
                                         CharacterJoinPayloads.ItemsTransferPolicyAnswer payload) {
        ServerCommonPacketListenerImpl listener = (ServerCommonPacketListenerImpl) context.listener();
        MinecraftServer server = (MinecraftServer) listener.getMainThreadEventLoop();

        Path dir = GameModeGuard.worldDir(server);
        ServerCharacterFlags.Data flags = ServerCharacterFlags.resolve(dir);
        if (flags.itemsTransferPolicyInitialized()) {
            // Someone else already answered first; the gamerule already reflects a real
            // decision, so a late second answer must not silently override it.
            return;
        }

        server.getGameRules().getRule(ModGameRules.ITEMS_TRANSFER).set(payload.allowItems(), server);
        ServerCharacterFlags.write(dir, flags.withPolicyInitialized());
        CharSelect.LOGGER.info("itemsTransfer set to {} by the first player to join", payload.allowItems());
    }
}
