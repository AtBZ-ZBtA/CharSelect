package com.charselect.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The part of a character other players are allowed to see: what it is called and what it
 * looks like. No inventory, no progress, nothing the server owns.
 *
 * @param nickname the character's display name
 * @param slim     whether the skin uses the 3-pixel arm model
 * @param skinPng  the raw skin PNG, or an empty array to mean "use the default skin"
 */
public record CharacterCosmetics(String nickname, boolean slim, byte[] skinPng) {

    /** Comfortably above a 64x64 skin while still refusing anything absurd. */
    private static final int MAX_SKIN_BYTES = 256 * 1024;
    private static final int MAX_NICKNAME = 64;

    public static final CharacterCosmetics NONE = new CharacterCosmetics("", false, new byte[0]);

    public static final StreamCodec<RegistryFriendlyByteBuf, CharacterCosmetics> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_NICKNAME), CharacterCosmetics::nickname,
                    ByteBufCodecs.BOOL, CharacterCosmetics::slim,
                    ByteBufCodecs.byteArray(MAX_SKIN_BYTES), CharacterCosmetics::skinPng,
                    CharacterCosmetics::new);

    public boolean hasSkin() {
        return skinPng.length > 0;
    }

    public boolean isEmpty() {
        return nickname.isEmpty() && !hasSkin();
    }
}
