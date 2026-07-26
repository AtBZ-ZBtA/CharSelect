package com.charselect.compat;

import com.charselect.api.AttachmentDataHandler;
import com.charselect.character.CharacterProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Marks a character permanently "cursed" the moment it is ever caught wearing Enigmatic
 * Legacy's Ring of Seven Curses - a curio that, by that mod's own design, cannot be taken
 * off once equipped. That permanence is exactly what makes it worth tracking the same way
 * this mod already tracks hardcore: a fact about the character that should follow it
 * everywhere and never quietly reset.
 *
 * <p>No dependency on Enigmatic Legacy or Curios, at compile time or runtime. This looks for
 * the ring's registry id inside whatever the Curios attachment's NBT happens to contain,
 * rather than parsing Curios' own slot layout - a structural detail this mod has no reason
 * to know precisely and no interest in staying in step with if it changes.
 *
 * <p>Enigmatic Legacy has no NeoForge build for 1.21.1 as of writing - its newest NeoForge
 * branch targets 1.20.1. This is written against the registry id from that branch, which
 * mods very rarely change on a version bump, so detection should start working the moment a
 * matching build exists with no update needed here. Until then this is simply inert: nobody
 * can have the item equipped if the item cannot be installed.
 */
public final class EnigmaticLegacyCompat {
    /** Confirmed against Aizistral-Studios/Enigmatic-Legacy, branch neoforge_1.20.1. */
    private static final String CURSED_RING_ID = "enigmaticlegacy:cursed_ring";

    private EnigmaticLegacyCompat() {
    }

    /**
     * Checks a captured player tag's Curios attachment for the ring and marks the profile if
     * found. Safe to call unconditionally - both Curios and Enigmatic Legacy being absent
     * just mean there is nothing to find.
     */
    public static void checkForCurse(CompoundTag playerNbt, CharacterProfile profile) {
        if (profile.isCursed()) {
            return;
        }
        if (!playerNbt.contains(AttachmentDataHandler.ATTACHMENTS_KEY)) {
            return;
        }
        CompoundTag attachments = playerNbt.getCompound(AttachmentDataHandler.ATTACHMENTS_KEY);
        if (!attachments.contains("curios:inventory")) {
            return;
        }
        if (containsItem(attachments.get("curios:inventory"), CURSED_RING_ID)) {
            profile.markCursed();
        }
    }

    /** Walks an arbitrarily nested NBT structure looking for an ItemStack-shaped id field. */
    private static boolean containsItem(Tag tag, String itemId) {
        if (tag instanceof CompoundTag compound) {
            if (itemId.equals(compound.getString("id"))) {
                return true;
            }
            for (String key : compound.getAllKeys()) {
                if (containsItem(compound.get(key), itemId)) {
                    return true;
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                if (containsItem(element, itemId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
