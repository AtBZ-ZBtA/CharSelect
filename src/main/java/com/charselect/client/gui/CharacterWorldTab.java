package com.charselect.client.gui;

import com.charselect.character.ActiveCharacter;
import com.charselect.world.PendingWorldFlags;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A "Character" tab on the world creation screen, holding the rules this world places on the
 * characters allowed into it.
 *
 * <p>A tab of its own rather than extra rows squeezed into the vanilla Game tab: these are
 * this mod's rules, they are easier to find under their own heading, and there is somewhere
 * obvious to put the next one.
 */
public class CharacterWorldTab extends GridLayoutTab {
    private static final Component TITLE = Component.translatable("charselect.createworld.tab");
    private static final int WIDGET_WIDTH = 210;

    public CharacterWorldTab(WorldCreationUiState uiState) {
        super(TITLE);
        PendingWorldFlags.reset();

        GridLayout.RowHelper rows = this.layout.rowSpacing(8).createRowHelper(1);

        rows.addChild(new StringWidget(WIDGET_WIDTH, 20,
                ActiveCharacter.get()
                        .map(profile -> (Component) Component.translatable(
                                "charselect.createworld.creating_as", profile.nickname()))
                        .orElse(Component.translatable("charselect.createworld.no_character")),
                Minecraft.getInstance().font));

        CycleButton<Boolean> banCheated = CycleButton
                .onOffBuilder(PendingWorldFlags.banCheatedCharacters())
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("charselect.createworld.ban_cheated.tooltip")))
                .create(0, 0, WIDGET_WIDTH, 20,
                        Component.translatable("charselect.createworld.ban_cheated"),
                        (button, value) -> {
                            PendingWorldFlags.setBanCheatedCharacters(value);
                            // A world that demands clean characters cannot itself hand out
                            // cheats, or the first person in would be tainted and then
                            // refused by their own world.
                            if (value) {
                                uiState.setAllowCommands(false);
                            }
                        });
        rows.addChild(banCheated);

        rows.addChild(new StringWidget(WIDGET_WIDTH, 20,
                Component.translatable("charselect.createworld.ban_cheated.hint"),
                Minecraft.getInstance().font));
    }
}
