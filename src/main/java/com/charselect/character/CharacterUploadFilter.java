package com.charselect.character;

import com.charselect.server.PlayerDataSplitter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Builds the version of a character that is safe to hand to a server whose
 * {@code itemsTransfer} gamerule is off - the same character, minus every item-bearing key,
 * so nothing about inventory or equipment travels at all rather than travelling and then
 * being discarded. Identity (nickname, skin, gamemode) is untouched either way; that already
 * travels separately, over the cosmetics channel.
 *
 * <p>Used on both ends of the join upload: the client builds the stripped copy before
 * sending, so a server with the gamerule off never even receives the data, and the server
 * strips again on receipt regardless, since honouring the gamerule is the server's job to
 * enforce, not something a modified client can be trusted to have done on its own.
 */
public final class CharacterUploadFilter {

    private CharacterUploadFilter() {
    }

    public static CharacterProfile forItemsTransfer(CharacterProfile profile, boolean allowed) {
        if (allowed) {
            return profile;
        }
        // save() hands back the profile's own live SharedData/WorldSlot compounds, not
        // copies - copy() before mutating anything, or this would corrupt the real profile.
        CompoundTag copy = profile.save().copy();
        strip(copy.getCompound("SharedData"));
        ListTag worlds = copy.getList("Worlds", Tag.TAG_COMPOUND);
        for (int i = 0; i < worlds.size(); i++) {
            strip(worlds.getCompound(i).getCompound("Data"));
        }
        return CharacterProfile.load(copy);
    }

    private static void strip(CompoundTag data) {
        PlayerDataSplitter.itemBearingKeys().forEach(data::remove);
    }
}
