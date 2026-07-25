package com.charselect.mixin.client;

import com.charselect.client.gui.WorldEntryGate;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the mod the last word before a world actually loads, which is where the cheat
 * warning and the cheated-character ban are enforced.
 */
@Mixin(WorldSelectionList.WorldListEntry.class)
public abstract class WorldListEntryMixin {

    @Shadow
    @Final
    private LevelSummary summary;

    @Inject(method = "joinWorld", at = @At("HEAD"), cancellable = true)
    private void charselect$gateEntry(CallbackInfo ci) {
        WorldSelectionList.WorldListEntry self = (WorldSelectionList.WorldListEntry) (Object) this;
        if (WorldEntryGate.intercept(this.summary, self::joinWorld)) {
            ci.cancel();
        }
    }

    @Shadow
    public abstract void joinWorld();
}
