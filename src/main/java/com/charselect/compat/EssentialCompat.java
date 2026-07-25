package com.charselect.compat;

import com.charselect.CharSelect;
import net.neoforged.fml.ModList;

/**
 * Notes on living alongside Essential.
 *
 * <p>Essential's multiplayer works by opening your singleplayer world to friends, which is
 * still an integrated server: the host owns it, guests arrive over the network. That lines
 * up exactly with how this mod already decides who the character system applies to, so no
 * Essential-specific rewiring is needed.
 *
 * <ul>
 *   <li><b>Host</b> - keeps the full character system. Their character's inventory and
 *       progress live in the profile, not in the world they are hosting.</li>
 *   <li><b>Guests</b> - are on a server, so the server owns their player data exactly as
 *       vanilla would. Their character supplies the nickname and skin only, which is the
 *       "servers act like vanilla" behaviour.</li>
 *   <li><b>Skins</b> - both mods resolve player skins. This mod only claims players who have
 *       announced a character, so Essential keeps control of everyone else.</li>
 * </ul>
 *
 * <p>This class exists to detect Essential and say so in the log, which makes a bug report
 * from a player running both mods far easier to read.
 */
public final class EssentialCompat {
    private static final String ESSENTIAL_MOD_ID = "essential";

    private static Boolean present;

    private EssentialCompat() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = ModList.get() != null && ModList.get().isLoaded(ESSENTIAL_MOD_ID);
        }
        return present;
    }

    public static void logStatus() {
        if (isPresent()) {
            CharSelect.LOGGER.info(
                    "Essential detected. Hosted worlds keep the full character system for the host; "
                    + "guests use the host's world data and their character's cosmetics only.");
        }
    }
}
