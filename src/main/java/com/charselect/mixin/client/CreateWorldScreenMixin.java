package com.charselect.mixin.client;

import com.charselect.character.ActiveCharacter;
import com.charselect.client.gui.CharacterWorldTab;
import com.charselect.config.CharSelectConfig;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Arrays;

/** Adds the character tab to the world creation screen. */
@Mixin(CreateWorldScreen.class)
public class CreateWorldScreenMixin {

    @ModifyArg(
            method = "init",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"
                            + "addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)"
                            + "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"),
            index = 0)
    private Tab[] charselect$addCharacterTab(Tab[] tabs) {
        // Nothing on the tab is meaningful without a character, or with tracking switched off.
        if (ActiveCharacter.getOrNull() == null
                || !CharSelectConfig.INSTANCE.trackCheatedWorlds.get()) {
            return tabs;
        }

        CreateWorldScreen self = (CreateWorldScreen) (Object) this;
        Tab[] extended = Arrays.copyOf(tabs, tabs.length + 1);
        extended[tabs.length] = new CharacterWorldTab(self.getUiState());
        return extended;
    }
}
