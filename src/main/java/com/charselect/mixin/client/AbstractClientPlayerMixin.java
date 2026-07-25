package com.charselect.mixin.client;

import com.charselect.client.ClientCosmetics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws players wearing their chosen character's skin.
 *
 * <p>Applies to every player the client knows a character for, so on a modded server people
 * see each other's characters, while the account's real skin is untouched everywhere else.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void charselect$useCharacterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        PlayerSkin skin = ClientCosmetics.skinFor(self.getUUID());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}
