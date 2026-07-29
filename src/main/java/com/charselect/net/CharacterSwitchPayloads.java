package com.charselect.net;

import com.charselect.CharSelect;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Play-phase packet behind {@code /character reconnect}. */
public final class CharacterSwitchPayloads {

    private CharacterSwitchPayloads() {
    }

    /**
     * Sent just before the disconnect {@code /character reconnect} triggers (see
     * {@code server.CharacterSwitcher}), so the client knows to reconnect on its own rather
     * than sitting on the disconnect screen waiting to be told to. An empty marker: the client
     * already knows what address got it here, which is all a reconnect needs - see
     * {@code client.ClientCharacterSwitch}.
     */
    public record PrepareReconnect() implements CustomPacketPayload {
        public static final Type<PrepareReconnect> TYPE =
                new Type<>(CharSelect.id("prepare_reconnect"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PrepareReconnect> STREAM_CODEC =
                StreamCodec.unit(new PrepareReconnect());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
