package com.charselect.net;

import com.charselect.CharSelect;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The configuration-phase handshake a remote connection goes through before it ever spawns.
 *
 * <p>Configuration phase, not play phase, on purpose: {@code PlayerList.load} - the seam
 * {@code PlayerListMixin} hooks to hand a character's data to a not-yet-spawned player -
 * runs before any play-phase packet channel exists. Resolving "which character" has to
 * finish before that point, or the player would spawn on the vanilla path and need a
 * disruptive mid-game swap immediately after. See {@code server.net.CharacterUploadTask}.
 */
public final class CharacterJoinPayloads {

    private CharacterJoinPayloads() {
    }

    /**
     * Sent to the client as soon as the upload task starts. {@code promptItemsTransferPolicy}
     * is only ever true for the first player to ever join a fresh server world - see
     * {@code server.DedicatedServerFirstJoinEvents}.
     */
    public record RequestCharacterUpload(boolean itemsTransferAllowed,
                                         boolean promptItemsTransferPolicy)
            implements CustomPacketPayload {
        public static final Type<RequestCharacterUpload> TYPE =
                new Type<>(CharSelect.id("request_character_upload"));

        public static final StreamCodec<FriendlyByteBuf, RequestCharacterUpload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, RequestCharacterUpload::itemsTransferAllowed,
                        ByteBufCodecs.BOOL, RequestCharacterUpload::promptItemsTransferPolicy,
                        RequestCharacterUpload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * The client's answer: the chosen character, as exactly the NBT {@code CharacterProfile}
     * already saves itself as. If {@code itemsTransferAllowed} was false, the client has
     * already stripped the item-bearing keys out of the shared/world-slot data before
     * building this - the server never sees them at all, rather than receiving and
     * discarding them.
     */
    public record UploadCharacter(CompoundTag profile) implements CustomPacketPayload {
        public static final Type<UploadCharacter> TYPE =
                new Type<>(CharSelect.id("upload_character"));

        public static final StreamCodec<FriendlyByteBuf, UploadCharacter> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.COMPOUND_TAG, UploadCharacter::profile,
                        UploadCharacter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent right before the connection is refused outright over a dead character (see
     * {@code net.CharacterJoinNetwork}'s upload handler), so the client learns why on a
     * channel it controls directly rather than solely through whatever screen the ensuing
     * disconnect happens to leave behind. {@code messageKey} is a translation key, never
     * player-authored text; {@code argument} its one substitution.
     */
    public record UploadRefused(String messageKey, String argument) implements CustomPacketPayload {
        public static final Type<UploadRefused> TYPE =
                new Type<>(CharSelect.id("upload_refused"));

        public static final StreamCodec<FriendlyByteBuf, UploadRefused> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(64), UploadRefused::messageKey,
                        ByteBufCodecs.stringUtf8(64), UploadRefused::argument,
                        UploadRefused::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Sent only when {@link RequestCharacterUpload#promptItemsTransferPolicy()} was true -
     * this player's answer to the one-time "allow items from singleplayer?" question, which
     * becomes this world's {@code itemsTransfer} gamerule.
     */
    public record ItemsTransferPolicyAnswer(boolean allowItems) implements CustomPacketPayload {
        public static final Type<ItemsTransferPolicyAnswer> TYPE =
                new Type<>(CharSelect.id("items_transfer_policy_answer"));

        public static final StreamCodec<FriendlyByteBuf, ItemsTransferPolicyAnswer> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, ItemsTransferPolicyAnswer::allowItems,
                        ItemsTransferPolicyAnswer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
