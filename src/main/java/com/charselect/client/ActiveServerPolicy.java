package com.charselect.client;

/**
 * What the current remote connection last told the client about its {@code itemsTransfer}
 * gamerule - set once during the join handshake (see {@code ClientCharacterJoin}) and reused
 * by a later mid-game {@code /character select}/{@code gui} switch, so switching does not
 * need its own round trip just to ask the same question again.
 *
 * <p>Meaningless in singleplayer (that path never goes through the upload handshake at all)
 * and reset on disconnect so a stale value from one server can never leak into another.
 */
public final class ActiveServerPolicy {
    private static boolean itemsTransferAllowed;

    private ActiveServerPolicy() {
    }

    public static void set(boolean allowed) {
        itemsTransferAllowed = allowed;
    }

    public static boolean itemsTransferAllowed() {
        return itemsTransferAllowed;
    }

    public static void reset() {
        itemsTransferAllowed = false;
    }
}
