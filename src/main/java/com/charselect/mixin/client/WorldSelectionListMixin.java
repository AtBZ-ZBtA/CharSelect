package com.charselect.mixin.client;

import com.charselect.client.world.WorldVisibility;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides worlds the chosen character may not enter.
 *
 * <p>Hooking the existing search filter means the hidden worlds never become list entries at
 * all, so every button that acts on a selection is gated for free.
 */
@Mixin(WorldSelectionList.class)
public class WorldSelectionListMixin {

    @Inject(method = "filterAccepts", at = @At("RETURN"), cancellable = true)
    private void charselect$gateByCharacter(String search, LevelSummary summary,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !WorldVisibility.isVisibleToActiveCharacter(summary)) {
            cir.setReturnValue(false);
        }
    }
}
