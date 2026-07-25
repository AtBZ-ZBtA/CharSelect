package com.charselect.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Claims a single NeoForge attachment out of the player's saved data.
 *
 * <p>NeoForge writes every serializable attachment into one {@code neoforge:attachments}
 * compound keyed by registry id. Left alone that whole compound moves as a unit, so one mod
 * could not keep its attachment world-local while another's followed the character. This
 * pulls a single id out of that compound and puts it back on the way in.
 */
public record AttachmentDataHandler(ResourceLocation attachmentId, CharacterDataScope scope)
        implements CharacterDataHandler {

    /** Matches {@code AttachmentHolder.ATTACHMENTS_NBT_KEY}. */
    public static final String ATTACHMENTS_KEY = "neoforge:attachments";

    @Override
    public ResourceLocation id() {
        return attachmentId;
    }

    @Override
    public CharacterDataScope defaultScope() {
        return scope;
    }

    @Override
    public void capture(CompoundTag playerNbt, CompoundTag into) {
        if (!playerNbt.contains(ATTACHMENTS_KEY)) {
            return;
        }
        CompoundTag attachments = playerNbt.getCompound(ATTACHMENTS_KEY);
        String key = attachmentId.toString();
        if (!attachments.contains(key)) {
            return;
        }

        into.put(key, attachments.get(key).copy());
        // Claimed, so the leftover compound is filed by the default rules without it.
        attachments.remove(key);
        if (attachments.isEmpty()) {
            playerNbt.remove(ATTACHMENTS_KEY);
        }
    }

    @Override
    public void restore(CompoundTag from, CompoundTag playerNbt) {
        String key = attachmentId.toString();
        if (!from.contains(key)) {
            return;
        }
        CompoundTag attachments = playerNbt.contains(ATTACHMENTS_KEY)
                ? playerNbt.getCompound(ATTACHMENTS_KEY)
                : new CompoundTag();
        attachments.put(key, from.get(key).copy());
        playerNbt.put(ATTACHMENTS_KEY, attachments);
    }
}
