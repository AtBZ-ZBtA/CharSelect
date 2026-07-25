package com.charselect.world;

/**
 * Carries the choices made on the character tab of the world creation screen through to the
 * moment the new world first starts, which is the earliest point its folder exists.
 *
 * <p>Set on the client, read by the integrated server in the same JVM. The value is consumed
 * when a world is stamped for the first time, so abandoning the creation screen cannot leak
 * the setting onto the next world made.
 */
public final class PendingWorldFlags {

    private static volatile boolean banCheatedCharacters;

    private PendingWorldFlags() {
    }

    public static boolean banCheatedCharacters() {
        return banCheatedCharacters;
    }

    public static void setBanCheatedCharacters(boolean value) {
        banCheatedCharacters = value;
    }

    /** Called when the creation screen opens, so a previous visit does not bleed through. */
    public static void reset() {
        banCheatedCharacters = false;
    }

    /** Reads the pending value and clears it. */
    public static boolean consume() {
        boolean value = banCheatedCharacters;
        banCheatedCharacters = false;
        return value;
    }
}
