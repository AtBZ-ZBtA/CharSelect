package com.charselect.mixin;

import com.charselect.CharSelect;
import com.charselect.character.CharacterProfile;
import com.charselect.server.CharacterSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Redirects everything the server would store about a player from the world folder into the
 * chosen character's profile.
 *
 * <p>{@code PlayerList} rather than {@code PlayerDataStorage} is the seam that matters: the
 * host of a singleplayer world is loaded from the {@code Player} tag inside level.dat and
 * never touches the playerdata folder at all, so hooking storage alone would miss the one
 * player this mod exists for.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin implements com.charselect.server.CharacterTrackerAccess {

    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    @Final
    private PlayerDataStorage playerIo;

    @Shadow
    @Final
    private Map<UUID, ServerStatsCounter> stats;

    @Shadow
    @Final
    private Map<UUID, PlayerAdvancements> advancements;

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void charselect$loadFromCharacter(ServerPlayer player,
                                              CallbackInfoReturnable<Optional<CompoundTag>> cir) {
        if (!CharacterSession.isEngaged(this.server, player)) {
            return;
        }

        CompoundTag tag = CharacterSession.loadTag(this.server, player);
        if (tag == null) {
            // Nothing stored for this character here. If the world predates the mod and no
            // character has claimed its data yet, fall through to vanilla so this character
            // inherits it - that is what keeps an existing save from looking wiped. The
            // claim is recorded so a second character cannot inherit the same items again.
            if (CharacterSession.claimExistingWorldData(this.server, player)) {
                return;
            }
            // Otherwise a fresh character starts fresh, rather than picking up whatever the
            // last person to play this world left behind.
            cir.setReturnValue(Optional.empty());
            return;
        }

        player.load(tag);
        EventHooks.firePlayerLoadingEvent(player, this.playerIo, player.getUUID().toString());
        cir.setReturnValue(Optional.of(tag));
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void charselect$captureToCharacter(ServerPlayer player, CallbackInfo ci) {
        if (player.connection == null) {
            return;
        }
        // Vanilla still writes its own copy into the world afterwards, so the world stays
        // playable if the mod is ever removed.
        CharacterSession.capture(this.server, player);
    }

    @Inject(method = "getPlayerAdvancements", at = @At("HEAD"), cancellable = true)
    private void charselect$characterAdvancements(ServerPlayer player,
                                                  CallbackInfoReturnable<PlayerAdvancements> cir) {
        if (player.isFakePlayer()) {
            return;
        }
        CharacterProfile profile = CharacterSession.profileFor(this.server, player);
        if (profile == null) {
            return;
        }

        PlayerAdvancements existing = this.advancements.get(player.getUUID());
        if (existing != null) {
            existing.setPlayer(player);
            cir.setReturnValue(existing);
            return;
        }

        Path path = CharacterSession.advancementsPath(this.server, player, profile,
                CharacterSession.worldKey(this.server));
        ensureParent(path);
        PlayerAdvancements created = new PlayerAdvancements(
                this.server.getFixerUpper(), (PlayerList) (Object) this,
                this.server.getAdvancements(), path, player);
        this.advancements.put(player.getUUID(), created);
        created.setPlayer(player);
        cir.setReturnValue(created);
    }

    @Inject(method = "getPlayerStats", at = @At("HEAD"), cancellable = true)
    private void charselect$characterStats(net.minecraft.world.entity.player.Player player,
                                           CallbackInfoReturnable<ServerStatsCounter> cir) {
        CharacterProfile profile = CharacterSession.profileFor(this.server, player);
        if (profile == null) {
            return;
        }

        ServerStatsCounter existing = this.stats.get(player.getUUID());
        if (existing != null) {
            cir.setReturnValue(existing);
            return;
        }

        Path path = CharacterSession.statsPath(this.server, player, profile,
                CharacterSession.worldKey(this.server));
        ensureParent(path);
        ServerStatsCounter created = new ServerStatsCounter(this.server, path.toFile());
        this.stats.put(player.getUUID(), created);
        cir.setReturnValue(created);
    }

    private static void ensureParent(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            CharSelect.LOGGER.error("Could not create the character data folder at {}", path, e);
        }
    }

    @Override
    public void charselect$forgetCharacterTrackers(UUID playerId) {
        this.advancements.remove(playerId);
        this.stats.remove(playerId);
    }
}
