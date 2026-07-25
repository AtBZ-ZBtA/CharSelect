package com.charselect.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Claims a fixed set of top-level keys out of the player's saved data. */
public record NbtKeyDataHandler(ResourceLocation handlerId, CharacterDataScope scope,
                                List<String> keys) implements CharacterDataHandler {

    public NbtKeyDataHandler(ResourceLocation handlerId, CharacterDataScope scope, String... keys) {
        this(handlerId, scope, List.of(keys));
    }

    @Override
    public ResourceLocation id() {
        return handlerId;
    }

    @Override
    public CharacterDataScope defaultScope() {
        return scope;
    }

    @Override
    public void capture(CompoundTag playerNbt, CompoundTag into) {
        for (String key : keys) {
            Tag value = playerNbt.get(key);
            if (value != null) {
                into.put(key, value.copy());
                playerNbt.remove(key);
            }
        }
    }

    @Override
    public void restore(CompoundTag from, CompoundTag playerNbt) {
        for (String key : keys) {
            Tag value = from.get(key);
            if (value != null) {
                playerNbt.put(key, value.copy());
            }
        }
    }
}
