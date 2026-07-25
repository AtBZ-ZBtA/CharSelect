package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts how long each character has been played, and ends hardcore characters when they die.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class CharacterLifecycle {

    /** When the current session started, per character. */
    private static final Map<UUID, Long> SESSION_START = new ConcurrentHashMap<>();

    private CharacterLifecycle() {
    }

    // ------------------------------------------------------------------ playtime

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        withProfile(event.getEntity(), (server, profile) ->
                SESSION_START.put(profile.id(), System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        withProfile(event.getEntity(), (server, profile) -> {
            bankPlaytime(profile);
            CharacterStore.get().save(profile);
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
        CharacterStore.get().save(profile);

        CharSelect.LOGGER.info("Hardcore character '{}' died in {}",
                profile.nickname(), CharacterSession.worldKey(server));

        // The world is not hardcore, so vanilla would happily offer a respawn. Ending the
        // session is what actually makes the death final.
        server.execute(() -> player.connection.disconnect(
                Component.translatable("charselect.hardcore.died", profile.nickname())
                        .withStyle(ChatFormatting.RED)));
    }

    private interface ProfileAction {
        void accept(MinecraftServer server, CharacterProfile profile);
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
            action.accept(server, profile);
        }
    }
}
