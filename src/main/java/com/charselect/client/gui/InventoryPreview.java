package com.charselect.client.gui;

import com.charselect.character.CharacterProfile;
import com.charselect.character.WorldSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads what a character is carrying out of its stored data, for display only.
 *
 * <p>Deliberately lenient: only the item id and count are read, and everything else on the
 * stack is ignored. A full {@code ItemStack.parse} needs a registry lookup that simply does
 * not exist on the title screen - since 1.21 even enchantments live in a datapack registry -
 * so a strict parse would fail on exactly the interesting items. Losing the enchantment
 * glint on a preview icon is a fair trade for it working at all.
 */
public final class InventoryPreview {

    /** Vanilla slot numbering inside the saved inventory list. */
    private static final int OFFHAND_SLOT = 150;
    private static final int ARMOUR_SLOT_START = 100;
    private static final int ARMOUR_SLOT_END = 103;

    /** Boots, leggings, chestplate, helmet, then offhand. */
    private final List<ItemStack> equipment = new ArrayList<>();
    /** Hotbar first, then the rest of the inventory. */
    private final List<ItemStack> carried = new ArrayList<>();

    private InventoryPreview() {
    }

    public List<ItemStack> equipment() {
        return equipment;
    }

    public List<ItemStack> carried() {
        return carried;
    }

    public boolean isEmpty() {
        return equipment.isEmpty() && carried.isEmpty();
    }

    /**
     * Builds a preview for a character. Looks in the shared bucket first, then falls back to
     * the last world it played, which is where the inventory lives when the config keeps it
     * world-local.
     */
    public static InventoryPreview of(CharacterProfile profile) {
        InventoryPreview preview = new InventoryPreview();

        ListTag items = findInventory(profile);
        if (items == null) {
            return preview;
        }

        ItemStack[] armour = new ItemStack[4];
        ItemStack offhand = ItemStack.EMPTY;
        List<ItemStack> hotbar = new ArrayList<>();
        List<ItemStack> main = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            ItemStack stack = readStack(entry);
            if (stack.isEmpty()) {
                continue;
            }
            int slot = entry.getInt("Slot") & 0xFF;
            if (slot >= ARMOUR_SLOT_START && slot <= ARMOUR_SLOT_END) {
                armour[slot - ARMOUR_SLOT_START] = stack;
            } else if (slot == OFFHAND_SLOT) {
                offhand = stack;
            } else if (slot < 9) {
                hotbar.add(stack);
            } else {
                main.add(stack);
            }
        }

        // Helmet first reads more naturally than the stored boots-first order.
        for (int i = armour.length - 1; i >= 0; i--) {
            if (armour[i] != null) {
                preview.equipment.add(armour[i]);
            }
        }
        if (!offhand.isEmpty()) {
            preview.equipment.add(offhand);
        }
        preview.carried.addAll(hotbar);
        preview.carried.addAll(main);
        return preview;
    }

    private static ListTag findInventory(CharacterProfile profile) {
        CompoundTag shared = profile.sharedData();
        if (shared.contains("Inventory", Tag.TAG_LIST)) {
            return shared.getList("Inventory", Tag.TAG_COMPOUND);
        }
        WorldSlot slot = profile.lastWorld().isEmpty()
                ? null : profile.peekWorldSlot(profile.lastWorld());
        if (slot != null && slot.data().contains("Inventory", Tag.TAG_LIST)) {
            return slot.data().getList("Inventory", Tag.TAG_COMPOUND);
        }
        return null;
    }

    private static ItemStack readStack(CompoundTag entry) {
        String id = entry.getString("id");
        if (id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.getOptional(location)
                .map(item -> new ItemStack(item, Math.max(1, entry.getInt("count"))))
                .orElse(ItemStack.EMPTY);
    }
}
