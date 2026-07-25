package com.charselect.client.gui;

import com.charselect.CharSelect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Draws an item icon on a menu screen without trusting it to survive being drawn there.
 *
 * <p>The character list lives outside any world, and some mods resolve their item model
 * through data that only exists once a world is loaded - Sophisticated Backpacks reads its
 * server config to decide how many slots a backpack has, and server configs are not loaded
 * at the title screen. Rendering such an item throws, and in a render loop that takes the
 * game down.
 *
 * <p>There is no way to ask an item whether it is safe to draw here, and no chance of
 * auditing every mod in a large pack, so each item gets exactly one attempt. Anything that
 * throws is remembered and drawn as an empty slot from then on, which costs one caught
 * exception per item type per session rather than one per frame.
 */
public final class SafeItemRenderer {

    /** Item types that threw while rendering. Identity comparison: items are singletons. */
    private static final Set<Item> UNRENDERABLE =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private SafeItemRenderer() {
    }

    public static void render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || UNRENDERABLE.contains(stack.getItem())) {
            return;
        }
        try {
            graphics.renderItem(stack, x, y);
            graphics.renderItemDecorations(font, stack, x, y);
        } catch (Throwable t) {
            UNRENDERABLE.add(stack.getItem());
            // The failure happened partway through a batched draw, so flush before carrying
            // on rather than leaving half an item in the buffer for the next widget.
            try {
                graphics.flush();
            } catch (Throwable ignored) {
                // Nothing useful to do; the next frame starts a fresh buffer regardless.
            }
            CharSelect.LOGGER.warn("{} cannot be drawn outside a world, so it will be left blank "
                            + "in the character preview. This is usually a mod resolving its item "
                            + "model from world or server-config data.",
                    stack.getItem(), t);
        }
    }
}
