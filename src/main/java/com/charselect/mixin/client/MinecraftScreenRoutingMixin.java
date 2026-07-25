package com.charselect.mixin.client;

import com.charselect.client.gui.ScreenRouter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Swaps the world list for the character list until a character has been chosen. */
@Mixin(Minecraft.class)
public class MinecraftScreenRoutingMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen charselect$routeScreen(Screen screen) {
        return ScreenRouter.route(screen);
    }
}
