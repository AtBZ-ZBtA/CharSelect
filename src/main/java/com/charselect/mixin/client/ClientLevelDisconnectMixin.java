package com.charselect.mixin.client;

import com.charselect.client.ClientCharacterSync;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the server a last chance to hand this character back before the client closes the
 * connection.
 *
 * <p>{@code ClientLevel#disconnect} is the exact line that closes the socket on a graceful
 * quit - {@code PauseScreen} calls it first, and only then calls {@code Minecraft#disconnect}
 * to tear the client down. Hooking the latter looked like the obvious funnel and was verified
 * against a real server to be useless: by the time it runs the connection is already going, so
 * the request went nowhere and the server's only captures were the ones the logout path does
 * anyway, after the socket was gone. This is the last moment a round trip is still possible.
 *
 * <p>Harmless where there is nothing to do: a singleplayer quit has no network to wait on, and
 * a server without this mod is spotted by its missing channel.
 * {@link ClientCharacterSync#requestFinalSyncAndWait} checks both and returns immediately
 * rather than making anyone wait for a reply that is not coming.
 */
@Mixin(ClientLevel.class)
public class ClientLevelDisconnectMixin {

    @Inject(method = "disconnect", at = @At("HEAD"))
    private void charselect$syncBeforeDisconnect(CallbackInfo ci) {
        ClientCharacterSync.requestFinalSyncAndWait();
    }
}
