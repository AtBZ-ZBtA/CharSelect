package com.charselect.net;

import com.charselect.CharSelect;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** The two packets the cosmetics system needs: one up, one down. */
public final class CosmeticsPayloads {

    private CosmeticsPayloads() {
    }

    /** Sent by a client once on join, telling the server which character it is playing. */
    public record Announce(CharacterCosmetics cosmetics) implements CustomPacketPayload {
        public static final Type<Announce> TYPE = new Type<>(CharSelect.id("announce_cosmetics"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Announce> STREAM_CODEC =
                StreamCodec.composite(
                        CharacterCosmetics.STREAM_CODEC, Announce::cosmetics,
                        Announce::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sent by the server to tell clients how to draw a given player. */
    public record Apply(UUID player, CharacterCosmetics cosmetics) implements CustomPacketPayload {
        public static final Type<Apply> TYPE = new Type<>(CharSelect.id("apply_cosmetics"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Apply> STREAM_CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, Apply::player,
                        CharacterCosmetics.STREAM_CODEC, Apply::cosmetics,
                        Apply::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Sent when a player leaves, so clients stop drawing their character. */
    public record Forget(UUID player) implements CustomPacketPayload {
        public static final Type<Forget> TYPE = new Type<>(CharSelect.id("forget_cosmetics"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Forget> STREAM_CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, Forget::player,
                        Forget::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
