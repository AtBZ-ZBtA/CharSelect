package com.charselect.net;

import com.charselect.CharSelect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Play-phase packets that keep a remote connection's local, singleplayer copy of a character
 * in sync with what actually happened to it on the server - the other half of the join
 * upload in {@code CharacterJoinPayloads}, sent once the play phase is already underway
 * rather than during the configuration handshake.
 */
public final class CharacterSyncPayloads {

    private CharacterSyncPayloads() {
    }

    /**
     * Sent by a client that is about to quit of its own accord, asking the server to capture
     * its character and hand it straight back before the socket closes - see
     * {@code mixin.client.MinecraftDisconnectMixin}, which stalls the quit briefly to wait for
     * the {@link DownloadCharacter} that answers this.
     *
     * <p>Exists because the server has no way to do this on its own initiative for a client
     * that simply leaves: it first learns of the departure from the socket already being dead
     * (see {@code server.CharacterSession#pushToClient}), far too late to send anything. Only
     * the client knows a graceful quit is coming, so only the client can ask in time.
     */
    public record RequestFinalSync() implements CustomPacketPayload {
        public static final Type<RequestFinalSync> TYPE =
                new Type<>(CharSelect.id("request_final_sync"));

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestFinalSync> STREAM_CODEC =
                StreamCodec.unit(new RequestFinalSync());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Hands a player back whatever the server currently holds for their character, so their
     * local copy - the one they will re-upload on their next join, and the one their character
     * list and inventory preview are drawn from - is not left stale.
     */
    public record DownloadCharacter(CompoundTag profile) implements CustomPacketPayload {
        public static final Type<DownloadCharacter> TYPE =
                new Type<>(CharSelect.id("download_character"));

        public static final StreamCodec<RegistryFriendlyByteBuf, DownloadCharacter> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.COMPOUND_TAG, DownloadCharacter::profile,
                        DownloadCharacter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
