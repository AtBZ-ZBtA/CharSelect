package com.charselect.client.gui;

import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.character.LocalAccount;
import com.charselect.client.LegacyCharacterImport;
import com.charselect.client.skin.SkinTextureCache;
import com.charselect.compat.FancyMenuCompat;
import com.charselect.config.CharSelectConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The screen that now stands between the main menu and the world list.
 *
 * <p>Picking a character here decides which worlds the next screen will even show, so this
 * is the first choice the player makes rather than an afterthought inside a world.
 */
public class CharacterSelectScreen extends Screen {
    private static final int LIST_TOP = 40;
    private static final int FOOTER_HEIGHT = 64;
    private static final int DOLL_WIDTH = 76;
    private static final int DOLL_HEIGHT = 100;

    private static final int SLOT = 18;
    private static final int PREVIEW_COLUMNS = 5;
    private static final int EQUIPMENT_COLUMNS = 5;
    /** Wide enough for the paper doll and a five-slot preview grid beneath it. */
    private static final int PANEL_WIDTH = SLOT * PREVIEW_COLUMNS;

    private final Screen lastScreen;

    private CharacterListWidget list;
    private Button playButton;
    private Button editButton;
    private Button deleteButton;
    private Button restoreButton;

    private int panelLeft;
    private InventoryPreview preview = null;
    private UUID previewFor = null;

    public CharacterSelectScreen(Screen lastScreen) {
        super(Component.translatable("charselect.select.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        // On an installation that already has worlds, make the player a character rather
        // than showing them an empty list next to saves that look emptied.
        LegacyCharacterImport.ensureStarterCharacter();

        int listWidth = Math.min(this.width - PANEL_WIDTH - 30, 280);
        int listHeight = this.height - LIST_TOP - FOOTER_HEIGHT;
        int contentWidth = listWidth + PANEL_WIDTH + 20;
        int contentLeft = (this.width - contentWidth) / 2;
        this.panelLeft = contentLeft + listWidth + 20;

        this.list = new CharacterListWidget(this.minecraft, listWidth, listHeight, LIST_TOP, 36,
                CharacterStore.get().all(), this::play);
        this.list.setX(contentLeft);
        addRenderableWidget(this.list);

        PlayerSkinWidget doll = new PlayerSkinWidget(DOLL_WIDTH, DOLL_HEIGHT,
                this.minecraft.getEntityModels(), this::previewSkin);
        // Centred over the preview grid below it.
        doll.setPosition(this.panelLeft + (PANEL_WIDTH - DOLL_WIDTH) / 2, LIST_TOP);
        addRenderableWidget(doll);

        int buttonRowY = this.height - 52;
        this.playButton = addRenderableWidget(
                Button.builder(Component.translatable("charselect.select.play"), b -> playSelected())
                        .bounds(this.width / 2 - 154, buttonRowY, 150, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.translatable("charselect.select.create"),
                                b -> this.minecraft.setScreen(new CharacterCreationScreen(this)))
                        .bounds(this.width / 2 + 4, buttonRowY, 150, 20)
                        .build());

        int lowerRowY = this.height - 28;
        int quarter = 74;
        int step = quarter + 4;
        int rowLeft = this.width / 2 - 154;
        this.editButton = addRenderableWidget(
                Button.builder(Component.translatable("charselect.select.edit"), b -> editSelected())
                        .bounds(rowLeft, lowerRowY, quarter, 20)
                        .build());
        this.restoreButton = addRenderableWidget(
                Button.builder(Component.translatable("charselect.select.restore"), b -> restoreSelected())
                        .bounds(rowLeft + step, lowerRowY, quarter, 20)
                        .build());
        this.deleteButton = addRenderableWidget(
                Button.builder(Component.translatable("charselect.select.delete"), b -> deleteSelected())
                        .bounds(rowLeft + step * 2, lowerRowY, quarter, 20)
                        .build());
        addRenderableWidget(
                Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                        .bounds(rowLeft + step * 3, lowerRowY, quarter, 20)
                        .build());

        updateButtons();
    }

    /** The list drives the paper doll, so the preview always matches the highlighted row. */
    private PlayerSkin previewSkin() {
        CharacterProfile selected = selectedProfile();
        return selected == null
                ? new PlayerSkin(SkinTextureCache.STEVE, null, null, null, PlayerSkin.Model.WIDE, true)
                : SkinTextureCache.playerSkin(selected.skin());
    }

    @Nullable
    private CharacterProfile selectedProfile() {
        CharacterListWidget.CharacterEntry entry = this.list == null ? null : this.list.getSelected();
        return entry == null ? null : entry.profile();
    }

    private void updateButtons() {
        CharacterProfile selected = selectedProfile();
        boolean hasSelection = selected != null;
        // A hardcore character that died stays in the list as a record, but is not playable.
        this.playButton.active = hasSelection && selected.isPlayable();
        this.editButton.active = hasSelection;
        this.deleteButton.active = hasSelection;
        // Only offered while a pre-cheat copy is actually being held.
        this.restoreButton.active = hasSelection && selected.canRestore();
    }

    private void playSelected() {
        CharacterProfile selected = selectedProfile();
        if (selected != null) {
            play(selected);
        }
    }

    private void play(CharacterProfile profile) {
        if (!profile.isPlayable()) {
            return;
        }
        ActiveCharacter.select(profile);
        // Stashed now, before any integrated server exists, for compat code that needs the
        // real account UUID earlier than a ServerPlayer is available to read it from.
        LocalAccount.set(this.minecraft.getGameProfile().getId());
        // The world list filters itself to this character's gamemode from here on.
        this.minecraft.setScreen(new SelectWorldScreen(this));
    }

    private void editSelected() {
        CharacterProfile selected = selectedProfile();
        if (selected != null) {
            this.minecraft.setScreen(CharacterCreationScreen.editing(this, selected));
        }
    }

    /**
     * Rolls a character back to how it was before it first entered a world with cheats.
     * Destructive, so it asks first and spells out what is lost.
     */
    private void restoreSelected() {
        CharacterProfile selected = selectedProfile();
        if (selected == null || !selected.canRestore()) {
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed && selected.restorePristine()) {
                        CharacterStore.get().save(selected);
                    }
                    this.minecraft.setScreen(new CharacterSelectScreen(this.lastScreen));
                },
                Component.translatable("charselect.select.restore.title", selected.nickname()),
                Component.translatable("charselect.select.restore.message"),
                Component.translatable("charselect.select.restore.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private void deleteSelected() {
        CharacterProfile selected = selectedProfile();
        if (selected == null) {
            return;
        }
        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        CharacterStore.get().delete(selected);
                        if (ActiveCharacter.isActive(selected.id())) {
                            ActiveCharacter.clear();
                        }
                    }
                    this.minecraft.setScreen(new CharacterSelectScreen(this.lastScreen));
                },
                Component.translatable("charselect.select.delete.title", selected.nickname()),
                Component.translatable("charselect.select.delete.message"),
                Component.translatable("charselect.select.delete.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        updateButtons();

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        renderInventoryPreview(graphics);

        if (CharacterStore.get().count() == 0) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("charselect.select.empty"),
                    this.width / 2, this.height / 2 - 20, 0xA0A0A0);
        }

        renderFancyMenuHint(graphics);
    }

    /**
     * FancyMenu identifies screens by Java class and only reskins the ones a pack author has
     * pointed it at explicitly, so a pack that customises the vanilla world list does not
     * automatically touch this screen too. This just says so, rather than leaving a modpack
     * author to wonder why their reskin stopped partway through character select.
     *
     * <p>The bottom-right corner is not actually empty - the button row's right edge (the
     * Back button) reaches to {@code width/2 + 154}, and on a narrow enough window that
     * leaves no real gap to its right. Rather than gamble on the text fitting and risk
     * overlapping Back, this only draws when it demonstrably does not - dropping the hint on
     * a cramped window is a fair trade for never drawing on top of a button.
     */
    private void renderFancyMenuHint(GuiGraphics graphics) {
        if (!FancyMenuCompat.isPresent() || !CharSelectConfig.INSTANCE.showFancyMenuHint.get()) {
            return;
        }
        Component hint = Component.translatable("charselect.select.fancymenu_hint");
        int textX = this.width - this.font.width(hint) - 6;
        int backButtonRight = this.width / 2 + 154;
        if (textX <= backButtonRight + 6) {
            return;
        }
        graphics.drawString(this.font, hint, textX, this.height - 12, 0x808080, false);
    }

    /** Shows what the highlighted character is carrying, under the paper doll. */
    private void renderInventoryPreview(GuiGraphics graphics) {
        CharacterProfile selected = selectedProfile();
        if (selected == null) {
            return;
        }
        if (this.previewFor == null || !this.previewFor.equals(selected.id())) {
            // Reparsing every frame would be wasteful; the data only changes on selection.
            this.previewFor = selected.id();
            this.preview = InventoryPreview.of(selected);
        }
        if (this.preview.isEmpty()) {
            return;
        }

        int x = this.panelLeft;
        int y = LIST_TOP + DOLL_HEIGHT + 12;

        if (!this.preview.equipment().isEmpty()) {
            renderRow(graphics, this.preview.equipment(), x, y, EQUIPMENT_COLUMNS);
            y += SLOT + 4;
        }

        int rowsLeft = (this.height - FOOTER_HEIGHT - y) / SLOT;
        if (rowsLeft > 0) {
            List<ItemStack> carried = this.preview.carried();
            int shown = Math.min(carried.size(), rowsLeft * PREVIEW_COLUMNS);
            renderRow(graphics, carried.subList(0, shown), x, y, PREVIEW_COLUMNS);

            int hidden = carried.size() - shown;
            if (hidden > 0) {
                int lastRow = y + ((shown - 1) / PREVIEW_COLUMNS + 1) * SLOT;
                graphics.drawString(this.font,
                        Component.translatable("charselect.select.more_items", hidden),
                        x, lastRow, 0x808080, false);
            }
        }
    }

    private void renderRow(GuiGraphics graphics, List<ItemStack> stacks, int x, int y, int columns) {
        for (int i = 0; i < stacks.size(); i++) {
            int slotX = x + (i % columns) * SLOT;
            int slotY = y + (i / columns) * SLOT;
            graphics.fill(slotX, slotY, slotX + SLOT - 2, slotY + SLOT - 2, 0x40000000);
            // Modded items can throw while resolving their model outside a world.
            SafeItemRenderer.render(graphics, this.font, stacks.get(i), slotX + 1, slotY + 1);
        }
    }

    @Override
    public void onClose() {
        // Backing out drops the selection so the next visit asks again.
        ActiveCharacter.clear();
        this.minecraft.setScreen(this.lastScreen);
    }
}
