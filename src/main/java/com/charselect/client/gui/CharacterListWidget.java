package com.charselect.client.gui;

import com.charselect.character.CharacterProfile;
import com.charselect.client.skin.SkinTextureCache;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/** The slot list on the character select screen. */
public class CharacterListWidget extends ObjectSelectionList<CharacterListWidget.CharacterEntry> {
    private static final int FACE_SIZE = 24;
    private static final DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private final Consumer<CharacterProfile> onConfirm;

    public CharacterListWidget(Minecraft minecraft, int width, int height, int y, int itemHeight,
                               List<CharacterProfile> profiles, Consumer<CharacterProfile> onConfirm) {
        super(minecraft, width, height, y, itemHeight);
        this.onConfirm = onConfirm;
        profiles.forEach(profile -> addEntry(new CharacterEntry(profile)));
    }

    @Override
    public int getRowWidth() {
        return 240;
    }

    @Override
    protected int getScrollbarPosition() {
        return getX() + getWidth() - 6;
    }

    public class CharacterEntry extends ObjectSelectionList.Entry<CharacterEntry> {
        private final CharacterProfile profile;
        private long lastClickTime;

        CharacterEntry(CharacterProfile profile) {
            this.profile = profile;
            // Warm the texture now so the row is not showing Steve when the screen opens.
            SkinTextureCache.preload(profile.skin());
        }

        public CharacterProfile profile() {
            return profile;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            int textLeft = left + FACE_SIZE + 8;

            renderFace(graphics, left + 2, top + 2);

            // A character that has been in a world with cheats is flagged in yellow, so a
            // legitimate survival run is distinguishable at a glance. A dead hardcore
            // character is greyed out, since it can only be looked at now.
            int nameColour = profile.isDead() ? 0x808080
                    : profile.isCheated() ? 0xFFD24A
                    : 0xFFFFFF;
            graphics.drawString(minecraft.font, profile.nickname(), textLeft, top + 2, nameColour, false);
            graphics.drawString(minecraft.font, subtitle(), textLeft, top + 14, 0xA0A0A0, false);
            graphics.drawString(minecraft.font, detail(), textLeft, top + 24, 0x808080, false);
        }

        /** Draws the skin's head, hat layer included, the way the vanilla social screen does. */
        private void renderFace(GuiGraphics graphics, int x, int y) {
            ResourceLocation texture = SkinTextureCache.texture(profile.skin());
            graphics.blit(texture, x, y, FACE_SIZE, FACE_SIZE, 8.0F, 8.0F, 8, 8, 64, 64);
            graphics.blit(texture, x, y, FACE_SIZE, FACE_SIZE, 40.0F, 8.0F, 8, 8, 64, 64);
        }

        private Component subtitle() {
            ChatFormatting colour = switch (profile.gameMode()) {
                case SURVIVAL -> ChatFormatting.GREEN;
                case CREATIVE -> ChatFormatting.AQUA;
            };
            MutableComponent line = profile.gameMode().displayName().copy().withStyle(colour);
            if (profile.isHardcore()) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("charselect.select.hardcore")
                            .withStyle(ChatFormatting.DARK_RED));
            }
            if (profile.isDead()) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("charselect.select.dead")
                            .withStyle(ChatFormatting.RED));
            }
            if (profile.isCheated()) {
                line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("charselect.select.cheated")
                            .withStyle(ChatFormatting.YELLOW));
            }
            return line;
        }

        private Component detail() {
            if (profile.isFresh()) {
                return Component.translatable("charselect.select.never_played");
            }
            int worlds = profile.worldSlots().size();
            return Component.translatable("charselect.select.detail",
                    DATE_FORMAT.format(new Date(profile.lastPlayed())), worlds,
                    formatPlaytime(profile.playtimeMillis()));
        }

        /** Short and readable: "3h 12m" once past an hour, minutes before that. */
        private static String formatPlaytime(long millis) {
            long minutes = millis / 60_000L;
            if (minutes < 1) {
                return "<1m";
            }
            long hours = minutes / 60;
            return hours < 1 ? minutes + "m" : hours + "h " + (minutes % 60) + "m";
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            setSelected(this);
            if (Util.getMillis() - lastClickTime < 250L) {
                onConfirm.accept(profile);
                return true;
            }
            lastClickTime = Util.getMillis();
            return true;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", profile.nickname());
        }
    }
}
