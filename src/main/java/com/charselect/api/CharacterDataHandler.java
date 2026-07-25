package com.charselect.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Full control over how one mod's data is split between the character and the world.
 *
 * <p>Most mods will not need this - see {@code RegisterCharacterDataEvent} for the one-line
 * ways to register NBT keys or a NeoForge attachment. Implement this only when your data
 * needs transforming on the way in or out, for example dropping a world-specific handle out
 * of an otherwise shareable blob.
 *
 * <p>Both methods are handed the player's full saved NBT and a bucket to work with. They run
 * on the server thread during player save and load.
 */
public interface CharacterDataHandler {

    /** Identifies this handler in logs and in the {@code worldLocalData} config list. */
    ResourceLocation id();

    /** Where this data belongs unless the player's config overrides it. */
    default CharacterDataScope defaultScope() {
        return CharacterDataScope.SHARED;
    }

    /**
     * Moves this handler's data out of the player's saved NBT and into storage.
     *
     * @param playerNbt the player's full saved data; remove what you claim so it is not also
     *                  filed away by the default rules
     * @param into      the bucket this handler's data is being stored in
     */
    void capture(CompoundTag playerNbt, CompoundTag into);

    /**
     * Puts this handler's data back into the NBT the player is about to be loaded from.
     *
     * @param from      the bucket previously filled by {@link #capture}, possibly empty if
     *                  this character has never stored anything
     * @param playerNbt the tag the player will be loaded from
     */
    void restore(CompoundTag from, CompoundTag playerNbt);
}
