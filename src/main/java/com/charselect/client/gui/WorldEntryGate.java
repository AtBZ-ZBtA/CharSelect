package com.charselect.client.gui;

import com.charselect.CharSelect;
import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.client.world.WorldVisibility;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Stands between clicking a world and actually loading it.
 *
 * <p>Checks are asked one at a time: each confirmation retries the join, which surfaces the
 * next question if there is one, so the player is never shown two dialogs at once.
 */
public final class WorldEntryGate {

    private WorldEntryGate() {
    }

    /**
     * @return true if the join should be stopped, because a dialog is now on screen or the
     *         world is off limits
     */
    public static boolean intercept(LevelSummary summary, Runnable retry) {
        CharacterProfile profile = ActiveCharacter.getOrNull();
        if (profile == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;

        return switch (WorldVisibility.check(summary, profile)) {
            case ALLOW -> false;

            case BLOCK_CHEATED -> {
                minecraft.setScreen(new ConfirmScreen(
                        accepted -> minecraft.setScreen(current),
                        Component.translatable("charselect.entry.blocked.title")
                                .withStyle(ChatFormatting.RED),
                        Component.translatable("charselect.entry.blocked.message",
                                profile.nickname()),
                        CommonComponents.GUI_BACK,
                        CommonComponents.GUI_BACK));
                yield true;
            }

            case WARN_GAMEMODE -> {
                minecraft.setScreen(new ConfirmScreen(
                        accepted -> {
                            if (accepted) {
                                WorldVisibility.accept(summary);
                                minecraft.setScreen(current);
                                retry.run();
                            } else {
                                minecraft.setScreen(current);
                            }
                        },
                        Component.translatable("charselect.entry.gamemode.title"),
                        Component.translatable("charselect.entry.gamemode.message",
                                profile.nickname(),
                                profile.gameMode().displayName(),
                                WorldVisibility.gameModeOf(summary).displayName()),
                        Component.translatable("charselect.entry.gamemode.confirm"),
                        CommonComponents.GUI_CANCEL));
                yield true;
            }

            case WARN_CHEAT_FORK -> {
                minecraft.setScreen(new ConfirmScreen(
                        accepted -> {
                            if (accepted) {
                                fork(profile);
                                minecraft.setScreen(current);
                                retry.run();
                            } else {
                                minecraft.setScreen(current);
                            }
                        },
                        Component.translatable("charselect.entry.cheats.title")
                                .withStyle(ChatFormatting.YELLOW),
                        Component.translatable("charselect.entry.cheats.message",
                                profile.nickname()),
                        Component.translatable("charselect.entry.cheats.confirm"),
                        CommonComponents.GUI_CANCEL));
                yield true;
            }
        };
    }

    /** Freezes the pre-cheat character so the run can be taken back later. */
    private static void fork(CharacterProfile profile) {
        profile.markCheated();
        CharacterStore.get().save(profile);
        CharSelect.LOGGER.info("Character '{}' entered a world with cheats; "
                + "a pre-cheat copy was kept", profile.nickname());
    }
}
