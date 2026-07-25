package com.charselect.server;

import com.charselect.CharSelect;
import com.charselect.character.CharacterGameMode;
import com.charselect.character.CharacterProfile;
import com.charselect.world.PendingWorldFlags;
import com.charselect.world.WorldFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.file.Path;

/**
 * Holds each character to the gamemode it was created under.
 *
 * <p>A survival character cannot reach creative or spectator by any route: not the command,
 * not the F3+F4 menu, not another mod calling the setter, and not by the world having cheats
 * enabled. Creative characters are free to move between modes.
 */
@EventBusSubscriber(modid = CharSelect.MODID)
public final class GameModeGuard {

    private GameModeGuard() {
    }

    @SubscribeEvent
    public static void onChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        if (profile == null || profile.gameMode().permits(event.getNewGameMode())) {
            return;
        }

        event.setCanceled(true);
        player.sendSystemMessage(Component
                .translatable("charselect.gamemode.locked", profile.gameMode().displayName())
                .withStyle(ChatFormatting.RED));
        CharSelect.LOGGER.debug("Blocked '{}' from switching to {}",
                profile.nickname(), event.getNewGameMode());
    }

    /**
     * Login runs through {@code loadGameTypes} rather than the change event, so the clamp is
     * reapplied here in case a profile arrives holding a mode it should never have had.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(server, player);
        if (profile == null) {
            return;
        }

        GameType current = player.gameMode.getGameModeForPlayer();
        GameType clamped = profile.gameMode().clamp(current);
        if (clamped != current) {
            player.setGameMode(clamped);
            CharSelect.LOGGER.info("Clamped '{}' from {} to {} on login",
                    profile.nickname(), current, clamped);
        }
    }

    /**
     * Stamps a world's rules the first time it is loaded.
     *
     * <p>Only worlds that are genuinely new get tied to a gamemode. A world that already
     * holds player data existed before this mod did, so it is left open to every character
     * and nobody is locked out of a save they already had.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Path dir = worldDir(server);
        if (WorldFlags.read(dir) != null) {
            return;
        }

        boolean preExisting = LegacyWorlds.hasExistingPlayerData(server);
        WorldFlags.Data data = preExisting
                ? new WorldFlags.Data(null, false, false)
                : new WorldFlags.Data(
                        CharacterGameMode.forWorldType(server.getWorldData().getGameType()),
                        PendingWorldFlags.consume(), true);

        if (preExisting) {
            CharSelect.LOGGER.info("{} predates this mod; leaving it open to every character",
                    dir.getFileName());
        }
        WorldFlags.write(dir, data);
    }

    /** The kind of character a running world belongs to, or null if it accepts anyone. */
    public static CharacterGameMode worldGameMode(MinecraftServer server) {
        return WorldFlags.resolve(worldDir(server)).mode();
    }

    static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }
}
