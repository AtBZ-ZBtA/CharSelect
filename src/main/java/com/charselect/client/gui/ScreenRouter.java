package com.charselect.client.gui;

import com.charselect.character.ActiveCharacter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

import javax.annotation.Nullable;

/**
 * Puts character selection in front of world selection.
 *
 * <p>Rather than hunting for the Singleplayer button on the title screen - which any other
 * mod may have moved or replaced - this intercepts the world list itself. Every route into
 * singleplayer goes through {@link SelectWorldScreen}, so gating there catches them all.
 */
public final class ScreenRouter {

    private ScreenRouter() {
    }

    @Nullable
    public static Screen route(@Nullable Screen incoming) {
        if (incoming == null) {
            return null;
        }

        // Returning to the main menu ends the session with that character.
        if (incoming instanceof TitleScreen) {
            ActiveCharacter.clear();
            return incoming;
        }

        if (incoming instanceof SelectWorldScreen && ActiveCharacter.getOrNull() == null) {
            // Whatever screen asked for the world list is the one Back should return to.
            Screen previous = Minecraft.getInstance().screen;
            Screen back = previous == null || previous instanceof CharacterSelectScreen
                    ? new TitleScreen()
                    : previous;
            return new CharacterSelectScreen(back);
        }

        return incoming;
    }
}
