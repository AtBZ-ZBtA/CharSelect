package com.charselect.compat;

import net.neoforged.fml.ModList;

/**
 * Detects FancyMenu. Nothing more - no dependency on its classes, no attempt to read or
 * influence its configuration, just a presence check so the character screens can mention
 * that FancyMenu identifies screens by Java class and has to be told about this mod's
 * screens explicitly; see the README for the two class names to point it at.
 */
public final class FancyMenuCompat {
    private static final String FANCYMENU_MOD_ID = "fancymenu";

    private static Boolean present;

    private FancyMenuCompat() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(FANCYMENU_MOD_ID);
        }
        return present;
    }
}
