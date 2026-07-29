package com.charselect.config;

import net.minecraft.world.level.GameRules;

/**
 * Per-world multiplayer toggles, as real vanilla gamerules rather than mod config.
 *
 * <p>{@link CharSelectConfig} answers "how does this installation behave" - the same on
 * every world a server hosts, only changeable by editing a file. These two answer "how does
 * this particular world behave", the same question vanilla's own gamerules answer, and for
 * the same reason: an operator should be able to see and change them with {@code /gamerule}
 * without touching a config file or restarting the server.
 *
 * <p>NeoForge has no separate gamerule-registration API - a mod registers a custom gamerule
 * the same way vanilla registers its own forty-odd, by calling {@link GameRules#register}
 * directly. That only works if this class has been loaded (forcing these fields to
 * initialise) before the first {@code GameRules} instance is ever constructed, which is why
 * {@link #init()} is called from the mod constructor rather than left to whichever class
 * happens to touch these keys first.
 */
public final class ModGameRules {

    /**
     * Whether a player's items - inventory, ender chest, and other item-bearing data - may
     * come with them from their singleplayer character onto this server. Off means only
     * identity (nickname, skin, gamemode) follows; the character still starts server-side
     * play with an empty, server-local inventory.
     */
    public static final GameRules.Key<GameRules.BooleanValue> ITEMS_TRANSFER =
            GameRules.register("itemsTransfer", GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(false));

    /**
     * Whether leaving a character - by switching to another one or disconnecting - leaves it
     * behind in the world as a stand-in, rather than simply vanishing.
     */
    public static final GameRules.Key<GameRules.BooleanValue> CHARACTERS_STAY_BEHIND =
            GameRules.register("charactersStayBehind", GameRules.Category.PLAYER,
                    GameRules.BooleanValue.create(true));

    private ModGameRules() {
    }

    /** No-op body - calling this is only ever about forcing the class to load. */
    public static void init() {
    }
}
