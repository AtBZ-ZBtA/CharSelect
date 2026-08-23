package com.charselect.server;

import net.minecraft.server.level.ServerPlayer;

/**
 * Runs vanilla's own per-player save on demand, implemented by {@code mixin.PlayerListMixin}.
 *
 * <p>Exists for one reason: {@code PlayerList#save} is what fires
 * {@code PlayerEvent.SaveToFile}, and that event is the only moment mods keeping player data
 * in their own files - Cosmetic Armor Reworked, in particular - write it out. Anything that
 * needs a genuinely current picture of that data, rather than whatever was last flushed, has
 * to ask for a save first. The method is {@code protected} on {@code PlayerList} and the
 * public {@code saveAll} would save every connected player just to refresh one, which is a
 * poor trade on a busy server.
 */
public interface PlayerSaveAccess {

    /** Saves this one player through vanilla's usual path, firing the usual events. */
    void charselect$savePlayer(ServerPlayer player);
}
