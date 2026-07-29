package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.entity.CharacterCorpseEntity;
import com.charselect.net.CosmeticsPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts how long each character has been played, ends hardcore characters when they die, and
 * periodically saves every connected character - the same 6000-tick cadence vanilla uses for
 * its own autosave, run independently rather than piggybacking on {@code PlayerList#save}
 * (which this mod's {@code mixin.PlayerListMixin} already redirects into character data too,
 * but a second, explicit save here is a deliberately cheap safety net against losing anything
 * to a crash or an ungraceful disconnect between autosaves).
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterLifecycle {

    /**
     * Matches the world's own autosave cadence. This is the backstop, not the main path: an
     * ordinary quit asks for a fresh sync on its way out (see
     * {@code client.ClientCharacterSync}), so what this actually bounds is how much a crash,
     * an alt-F4, a kick, or a dropped connection can cost - none of which give the client a
     * chance to ask for anything.
     */
    private static final int AUTOSAVE_INTERVAL_TICKS = 6000;

    /** When the current session started, per character. */
    private static final Map<UUID, Long> SESSION_START = new ConcurrentHashMap<>();

    private CharacterLifecycle() {
    }

    // ------------------------------------------------------------------ autosave

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() <= 0 || server.getTickCount() % AUTOSAVE_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CharacterProfile profile = CharacterSession.profileFor(server, player);
            if (profile == null) {
                continue;
            }
            CharacterSession.capture(server, player);
            // Handed straight back to the owning client too, while it is definitely still
            // connected - see CharacterSession#pushToClient for why waiting until logout to do
            // this never worked, and quietly cost every remote session its progress.
            CharacterSession.pushToClient(server, player, profile);
            CharSelect.LOGGER.info("Autosaved character '{}'", profile.nickname());
        }
    }

    // ------------------------------------------------------------------ playtime

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        withProfile(event.getEntity(), (server, player, profile) -> {
            SESSION_START.put(profile.id(), System.currentTimeMillis());
            applyPendingCorpseKill(server, player, profile);
        });
    }

    /**
     * Collects a death owed from a killed stand-in (see {@code entity.CharacterCorpseEntity}
     * and {@code net.CharacterJoinNetwork}, which marks this at upload time the moment it
     * finds a corpse standing in for the character being uploaded). The character has already
     * loaded in standing exactly where the corpse is - the same upload handler wrote that
     * position in - so this only has to trigger an ordinary death right here.
     * {@link Entity#kill()} runs the real thing: drops, hunger/XP loss, a normal respawn, and -
     * via the very same {@link #onDeath} below - permanent hardcore consequences if and only
     * if this character is actually hardcore. Nothing here decides that separately; it is the
     * same death any other one would be.
     *
     * <p>The corpse itself is removed once this runs - its one job, marking that this death is
     * owed, is done the moment it is actually collected, so it does not need to go on standing
     * there forever after.
     */
    private static void applyPendingCorpseKill(MinecraftServer server, ServerPlayer player,
                                                CharacterProfile profile) {
        UUID accountId = player.getGameProfile().getId();
        if (!PendingCharacterKill.consume(accountId)) {
            return;
        }
        player.displayClientMessage(Component.translatable("charselect.standin.died", profile.nickname())
                .withStyle(ChatFormatting.RED), false);
        player.kill();
        removeCorpse(server, accountId, profile.id());
    }

    private static void removeCorpse(MinecraftServer server, UUID accountId, UUID characterId) {
        UUID entityId = StandInRegistry.entityFor(accountId, characterId);
        if (entityId == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(entityId) instanceof CharacterCorpseEntity corpse) {
                StandInRegistry.unregister(accountId, characterId);
                PacketDistributor.sendToAllPlayers(new CosmeticsPayloads.Forget(corpse.getUUID()));
                corpse.discard();
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        withProfile(event.getEntity(), (server, player, profile) -> {
            bankPlaytime(profile);
            CharacterSession.persist(server, player, profile);
        });
    }

    /**
     * Folds the current session into the character's total and restarts the clock, so an
     * autosave mid-session is not lost if the game later crashes.
     */
    public static void bankPlaytime(CharacterProfile profile) {
        Long started = SESSION_START.get(profile.id());
        if (started == null) {
            return;
        }
        long now = System.currentTimeMillis();
        profile.addPlaytime(now - started);
        SESSION_START.put(profile.id(), now);
    }

    // ------------------------------------------------------------------ hardcore

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        if (profile == null || !profile.isHardcore() || profile.isDead()) {
            return;
        }

        profile.markDead();
        bankPlaytime(profile);
        // Save the character exactly as it fell, so the slot is a record of the run.
        CharacterSession.capture(server, player);
        CharacterSession.persist(server, player, profile);

        CharSelect.LOGGER.info("Hardcore character '{}' died in {}",
                profile.nickname(), CharacterSession.worldKey(server));

        // The world is not hardcore, so vanilla would happily offer a respawn. Ending the
        // session is what actually makes the death final.
        server.execute(() -> player.connection.disconnect(
                Component.translatable("charselect.hardcore.died", profile.nickname())
                        .withStyle(ChatFormatting.RED)));
    }

    private interface ProfileAction {
        void accept(MinecraftServer server, ServerPlayer player, CharacterProfile profile);
    }

    private static void withProfile(net.minecraft.world.entity.player.Player player,
                                    ProfileAction action) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        MinecraftServer server = serverPlayer.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, serverPlayer);
        if (profile != null) {
            action.accept(server, serverPlayer, profile);
        }
    }
}
