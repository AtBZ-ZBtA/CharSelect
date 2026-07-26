package com.charselect.client.gui;

import com.charselect.CharSelect;
import com.charselect.character.CharacterGameMode;
import com.charselect.character.CharacterProfile;
import com.charselect.character.CharacterStore;
import com.charselect.character.SkinRef;
import com.charselect.client.skin.MojangSkinLookup;
import com.charselect.client.skin.SkinFilePicker;
import com.charselect.client.skin.SkinTextureCache;
import com.charselect.config.CharSelectConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CompletionException;

/**
 * Creates a new character, or edits an existing one's nickname and skin.
 *
 * <p>Gamemode is fixed at creation and never editable afterwards: it is what decides which
 * worlds the character can ever enter, so letting it change later would strand the character
 * in worlds it no longer belongs to.
 */
public class CharacterCreationScreen extends Screen {
    private static final int DOLL_WIDTH = 84;
    private static final int DOLL_HEIGHT = 120;

    private static final int FORM_WIDTH = 180;
    /**
     * Text may run a little wider than the widgets, but must still stop short of the paper
     * doll at {@code formLeft + 210}.
     */
    private static final int TEXT_WIDTH = 200;
    private static final int LINE_HEIGHT = 10;

    // One source of truth for the vertical layout, so init() and render() cannot drift apart.
    private static final int NICKNAME_LABEL_Y = 34;
    private static final int NICKNAME_Y = 46;
    private static final int GAMEMODE_LABEL_Y = 76;
    private static final int GAMEMODE_Y = 88;
    private static final int HINT_Y = 112;
    private static final int SKIN_LABEL_Y = 146;
    private static final int SKIN_ROW_Y = 158;
    private static final int USERNAME_Y = 182;
    private static final int SOURCE_Y = 208;
    private static final int STATUS_Y = 220;

    private final Screen lastScreen;
    @Nullable
    private final CharacterProfile editing;

    private EditBox nicknameBox;
    private EditBox usernameBox;
    private Button fetchButton;
    private Button createButton;

    private CharacterGameMode gameMode = CharacterGameMode.SURVIVAL;
    private SkinRef skin = SkinRef.STEVE;
    private boolean hardcore;
    private CycleButton<Boolean> hardcoreButton;

    // Held outside the widgets so a rebuildWidgets() call does not throw away typed text.
    private String nickname = "";
    private String username = "";

    private Component status = CommonComponents.EMPTY;
    private boolean statusIsError;
    private boolean busy;

    public CharacterCreationScreen(Screen lastScreen) {
        this(lastScreen, null);
    }

    private CharacterCreationScreen(Screen lastScreen, @Nullable CharacterProfile editing) {
        super(Component.translatable(editing == null
                ? "charselect.create.title" : "charselect.edit.title"));
        this.lastScreen = lastScreen;
        this.editing = editing;
        if (editing != null) {
            this.gameMode = editing.gameMode();
            this.skin = editing.skin();
            this.nickname = editing.nickname();
            this.hardcore = editing.isHardcore();
        }
    }

    public static CharacterCreationScreen editing(Screen lastScreen, CharacterProfile profile) {
        return new CharacterCreationScreen(lastScreen, profile);
    }

    private boolean isEditing() {
        return editing != null;
    }

    @Override
    protected void init() {
        int formLeft = this.width / 2 - 160;
        int formWidth = FORM_WIDTH;
        int dollX = this.width / 2 + 50;

        this.nicknameBox = new EditBox(this.font, formLeft, NICKNAME_Y, formWidth, 20,
                Component.translatable("charselect.create.nickname"));
        this.nicknameBox.setMaxLength(CharacterProfile.MAX_NICKNAME_LENGTH);
        this.nicknameBox.setHint(Component.translatable("charselect.create.nickname.hint"));
        this.nicknameBox.setValue(this.nickname);
        this.nicknameBox.setResponder(v -> {
            this.nickname = v;
            updateCreateButton();
        });
        addRenderableWidget(this.nicknameBox);
        setInitialFocus(this.nicknameBox);

        int halfWidth = (formWidth - 4) / 2;
        CycleButton<CharacterGameMode> gameModeButton = CycleButton
                .<CharacterGameMode>builder(CharacterGameMode::displayName)
                .withValues(CharacterGameMode.SURVIVAL, CharacterGameMode.CREATIVE)
                .withInitialValue(this.gameMode)
                .create(formLeft, GAMEMODE_Y, halfWidth, 20,
                        Component.translatable("charselect.create.gamemode"),
                        (button, value) -> {
                            this.gameMode = value;
                            // Hardcore means nothing for a character that cannot die.
                            if (value != CharacterGameMode.SURVIVAL) {
                                this.hardcore = false;
                            }
                            rebuildWidgets();
                        });
        // Permanent once the character exists.
        gameModeButton.active = !isEditing();
        addRenderableWidget(gameModeButton);

        this.hardcoreButton = CycleButton.onOffBuilder(this.hardcore)
                .withTooltip(value -> Tooltip.create(
                        Component.translatable("charselect.create.hardcore.tooltip")))
                .create(formLeft + halfWidth + 4, GAMEMODE_Y, halfWidth, 20,
                        Component.translatable("charselect.create.hardcore"),
                        (button, value) -> this.hardcore = value);
        this.hardcoreButton.active = !isEditing() && this.gameMode == CharacterGameMode.SURVIVAL;
        addRenderableWidget(this.hardcoreButton);

        int thirdWidth = (formWidth - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("charselect.create.skin.default"),
                        b -> useDefaultSkin())
                .bounds(formLeft, SKIN_ROW_Y, thirdWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("charselect.create.skin.upload"),
                        b -> uploadSkin())
                .bounds(formLeft + thirdWidth + 4, SKIN_ROW_Y, thirdWidth, 20).build());
        CycleButton<SkinRef.SkinModel> armButton = CycleButton
                .<SkinRef.SkinModel>builder(CharacterCreationScreen::armLabel)
                .withValues(SkinRef.SkinModel.WIDE, SkinRef.SkinModel.SLIM)
                .withInitialValue(this.skin.model())
                .displayOnlyValue()
                .create(formLeft + (thirdWidth + 4) * 2, SKIN_ROW_Y, thirdWidth, 20,
                        Component.translatable("charselect.create.skin.arms"),
                        (button, value) -> this.skin = this.skin.withModel(value));
        addRenderableWidget(armButton);

        int fetchWidth = 52;
        this.usernameBox = new EditBox(this.font, formLeft, USERNAME_Y, formWidth - fetchWidth - 4, 20,
                Component.translatable("charselect.create.skin.username"));
        this.usernameBox.setMaxLength(16);
        this.usernameBox.setHint(Component.translatable("charselect.create.skin.username.hint"));
        this.usernameBox.setValue(this.username);
        this.usernameBox.setResponder(v -> {
            this.username = v;
            updateFetchButton();
        });
        addRenderableWidget(this.usernameBox);

        this.fetchButton = addRenderableWidget(
                Button.builder(Component.translatable("charselect.create.skin.fetch"), b -> fetchSkin())
                        .bounds(formLeft + formWidth - fetchWidth, USERNAME_Y, fetchWidth, 20)
                        .build());

        PlayerSkinWidget doll = new PlayerSkinWidget(DOLL_WIDTH, DOLL_HEIGHT,
                this.minecraft.getEntityModels(), this::previewSkin);
        doll.setPosition(dollX, NICKNAME_Y);
        addRenderableWidget(doll);

        // Only a saved survival character has anything to convert - a new, unsaved one has
        // no slot to copy from yet, and a creative character is already what this offers.
        boolean offerConvert = isEditing() && editing.gameMode() == CharacterGameMode.SURVIVAL;
        if (offerConvert) {
            this.createButton = addRenderableWidget(
                    Button.builder(Component.translatable("charselect.edit.save"), b -> confirm())
                            .bounds(this.width / 2 - 154, this.height - 28, 100, 20)
                            .build());
            addRenderableWidget(
                    Button.builder(Component.translatable("charselect.edit.convert"),
                                    b -> convertToCreative())
                            .bounds(this.width / 2 - 50, this.height - 28, 100, 20)
                            .build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(this.width / 2 + 54, this.height - 28, 100, 20)
                    .build());
        } else {
            this.createButton = addRenderableWidget(
                    Button.builder(Component.translatable(isEditing()
                                    ? "charselect.edit.save" : "charselect.create.confirm"), b -> confirm())
                            .bounds(this.width / 2 - 154, this.height - 28, 150, 20)
                            .build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(this.width / 2 + 4, this.height - 28, 150, 20)
                    .build());
        }

        updateCreateButton();
        updateFetchButton();
    }

    private static Component armLabel(SkinRef.SkinModel model) {
        return Component.translatable("charselect.create.skin.arms." + model.key());
    }

    private PlayerSkin previewSkin() {
        return SkinTextureCache.playerSkin(this.skin);
    }

    // ------------------------------------------------------------------ skin actions

    private void useDefaultSkin() {
        // Keep whichever arm width is currently selected; Steve and Alex differ only by that.
        this.skin = this.skin.model().slim() ? SkinRef.ALEX : SkinRef.STEVE;
        setStatus(Component.translatable("charselect.create.skin.default.applied"), false);
    }

    private void uploadSkin() {
        if (busy) {
            return;
        }
        setBusy(true);
        setStatus(Component.translatable("charselect.create.skin.choosing"), false);
        SkinFilePicker.pick().whenComplete((result, error) -> this.minecraft.execute(() -> {
            setBusy(false);
            if (error != null) {
                reportFailure(error);
            } else if (result.isEmpty()) {
                setStatus(CommonComponents.EMPTY, false);
            } else {
                applySkin(result.get(), Component.translatable(
                        "charselect.create.skin.uploaded", result.get().origin()));
            }
        }));
    }

    private void fetchSkin() {
        if (busy || !canFetch()) {
            return;
        }
        String username = this.username.trim();
        setBusy(true);
        setStatus(Component.translatable("charselect.create.skin.fetching", username), false);
        MojangSkinLookup.byUsername(username).whenComplete((ref, error) -> this.minecraft.execute(() -> {
            setBusy(false);
            if (error != null) {
                reportFailure(error);
            } else {
                applySkin(ref, Component.translatable("charselect.create.skin.fetched", username));
            }
        }));
    }

    private void applySkin(SkinRef ref, Component message) {
        this.skin = ref;
        SkinTextureCache.preload(ref);
        setStatus(message, false);
        // The arm-width toggle is a widget with its own state, so rebuild to resync it.
        rebuildWidgets();
    }

    private void reportFailure(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null
                ? error.getCause() : error;
        String key = switch (cause) {
            case MojangSkinLookup.SkinLookupException e -> e.translationKey();
            case SkinFilePicker.SkinImportException e -> e.translationKey();
            default -> "charselect.skin.error.unknown";
        };
        CharSelect.LOGGER.warn("Skin selection failed", cause);
        setStatus(Component.translatable(key), true);
    }

    // ------------------------------------------------------------------ confirm

    private void confirm() {
        String nickname = this.nickname.trim();
        if (!isNicknameValid(nickname)) {
            return;
        }
        CharacterStore store = CharacterStore.get();
        if (isEditing()) {
            editing.setNickname(nickname);
            editing.setSkin(this.skin);
            store.save(editing);
        } else {
            store.create(nickname, this.gameMode, this.skin, this.hardcore);
        }
        this.minecraft.setScreen(this.lastScreen);
    }

    private boolean isNicknameValid(String nickname) {
        return !nickname.isEmpty() && nickname.length() <= CharacterProfile.MAX_NICKNAME_LENGTH;
    }

    /**
     * Copies this survival character into a brand new creative one, leaving the original
     * completely untouched. A copy rather than an in-place switch because gamemode decides
     * which worlds a character can enter, so flipping it in place would either strand the
     * character out of worlds it already has progress in, or - if separation is loose enough
     * to still allow them - hand a creative character access to worlds a survival player
     * earned normally. Two characters means both keep working exactly as they did.
     */
    private void convertToCreative() {
        if (!isEditing() || editing.gameMode() != CharacterGameMode.SURVIVAL) {
            return;
        }
        if (CharacterStore.get().atSlotLimit()) {
            setStatus(Component.translatable("charselect.create.slots_full",
                    CharSelectConfig.INSTANCE.maxCharacterSlots.get()), true);
            return;
        }

        this.minecraft.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        CharacterProfile copy = CharacterStore.get().convertToCreative(editing);
                        this.minecraft.setScreen(CharacterCreationScreen.editing(this.lastScreen, copy));
                    } else {
                        this.minecraft.setScreen(this);
                    }
                },
                Component.translatable("charselect.edit.convert.title", editing.nickname()),
                Component.translatable("charselect.edit.convert.message", editing.nickname()),
                Component.translatable("charselect.edit.convert.confirm"),
                CommonComponents.GUI_CANCEL));
    }

    private void updateCreateButton() {
        if (this.createButton == null) {
            return;
        }
        boolean slotsAvailable = isEditing() || !CharacterStore.get().atSlotLimit();
        this.createButton.active = !busy && isNicknameValid(this.nickname.trim()) && slotsAvailable;

        if (!slotsAvailable) {
            setStatus(Component.translatable("charselect.create.slots_full",
                    CharSelectConfig.INSTANCE.maxCharacterSlots.get()), true);
        }
    }

    private boolean canFetch() {
        return CharSelectConfig.INSTANCE.allowSkinFetchFromMojang.get()
                && MojangSkinLookup.isPlausibleUsername(this.username.trim());
    }

    private void updateFetchButton() {
        if (this.fetchButton != null) {
            this.fetchButton.active = !busy && canFetch();
        }
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateCreateButton();
        updateFetchButton();
    }

    private void setStatus(Component status, boolean isError) {
        this.status = status;
        this.statusIsError = isError;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int formLeft = this.width / 2 - 160;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);

        graphics.drawString(this.font, Component.translatable("charselect.create.nickname"),
                formLeft, NICKNAME_LABEL_Y, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("charselect.create.gamemode"),
                formLeft, GAMEMODE_LABEL_Y, 0xA0A0A0, false);

        // Wrapped, so the sentence stays in its column instead of running under the doll.
        drawWrapped(graphics, gameModeHint(), formLeft, HINT_Y, 0x808080);

        graphics.drawString(this.font, Component.translatable("charselect.create.skin"),
                formLeft, SKIN_LABEL_Y, 0xA0A0A0, false);
        drawWrapped(graphics, skinSourceLabel(), formLeft, SOURCE_Y, 0x808080);

        if (!this.status.getString().isEmpty()) {
            drawWrapped(graphics, this.status, formLeft, STATUS_Y,
                    statusIsError ? 0xFF5555 : 0x55FF55);
        }
    }

    private void drawWrapped(GuiGraphics graphics, Component text, int x, int y, int colour) {
        List<FormattedCharSequence> lines = this.font.split(text, TEXT_WIDTH);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(this.font, lines.get(i), x, y + i * LINE_HEIGHT, colour, false);
        }
    }

    private Component gameModeHint() {
        if (isEditing()) {
            return Component.translatable("charselect.create.gamemode.locked")
                    .withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.translatable("charselect.create.gamemode.hint." + this.gameMode.key());
    }

    private Component skinSourceLabel() {
        return switch (this.skin.source()) {
            case DEFAULT -> Component.translatable("charselect.create.skin.source.default",
                    armLabel(this.skin.model()));
            case MOJANG -> Component.translatable("charselect.create.skin.source.mojang",
                    this.skin.origin());
            case FILE -> Component.translatable("charselect.create.skin.source.file",
                    this.skin.origin());
            case ACCOUNT -> Component.translatable("charselect.create.skin.source.account");
        };
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.lastScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
