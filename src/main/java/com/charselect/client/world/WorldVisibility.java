package com.charselect.client.world;

import com.charselect.character.ActiveCharacter;
import com.charselect.character.CharacterGameMode;
import com.charselect.character.CharacterProfile;
import com.charselect.character.WorldSeparation;
import com.charselect.config.CharSelectConfig;
import com.charselect.world.WorldFlags;
import net.minecraft.world.level.storage.LevelSummary;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Decides which worlds a character may see, and what to ask before it enters one. */
public final class WorldVisibility {

    /** What has to happen before this character can enter a world. */
    public enum Verdict {
        /** Go straight in. */
        ALLOW,
        /** The world refuses characters that have been in a cheated world. */
        BLOCK_CHEATED,
        /** Separation is set to warn and the gamemodes do not match. */
        WARN_GAMEMODE,
        /** A clean survival character is about to enter a world with commands enabled. */
        WARN_CHEAT_FORK
    }

    /**
     * Worlds the player has already agreed to enter this session, so a confirmed warning is
     * not asked again on the retry that follows it. Keyed by level folder.
     */
    private static final Set<String> ACCEPTED = ConcurrentHashMap.newKeySet();

    private WorldVisibility() {
    }

    // ------------------------------------------------------------------ listing

    public static boolean isVisibleToActiveCharacter(LevelSummary summary) {
        CharacterProfile profile = ActiveCharacter.getOrNull();
        if (profile == null) {
            // Something reached the world list another way; show everything rather than
            // presenting an empty screen.
            return true;
        }
        WorldFlags.Data flags = flagsOf(summary);
        // Worlds that predate the mod belong to nobody in particular, so every character
        // keeps access to them.
        if (flags.openToAnyone()) {
            return true;
        }
        return separation().allowsListing(profile.gameMode(), flags.mode());
    }

    private static WorldSeparation separation() {
        return CharSelectConfig.INSTANCE.worldSeparation.get();
    }

    /** The kind of character this world belongs to, or null if it accepts anyone. */
    @Nullable
    public static CharacterGameMode gameModeOf(LevelSummary summary) {
        return flagsOf(summary).mode();
    }

    public static WorldFlags.Data flagsOf(LevelSummary summary) {
        return WorldFlags.resolve(worldDirOf(summary));
    }

    /** The icon always sits at the root of the world folder, so its parent is that folder. */
    public static Path worldDirOf(LevelSummary summary) {
        return summary.getIcon().getParent();
    }

    /** Whether this world was created with commands enabled. */
    public static boolean hasCheats(LevelSummary summary) {
        return summary.getSettings().allowCommands();
    }

    // ------------------------------------------------------------------ entry

    /**
     * What should happen when this character tries to enter this world. Warnings are
     * returned one at a time; confirming one and retrying surfaces the next, if any.
     */
    public static Verdict check(LevelSummary summary, CharacterProfile profile) {
        WorldFlags.Data flags = flagsOf(summary);

        // A hard refusal, never bypassable by confirming.
        if (flags.banCheatedCharacters() && profile.isCheated()) {
            return Verdict.BLOCK_CHEATED;
        }

        boolean alreadyAccepted = ACCEPTED.contains(summary.getLevelId());

        if (!alreadyAccepted && !flags.openToAnyone()
                && separation().warnsAbout(profile.gameMode(), flags.mode())) {
            return Verdict.WARN_GAMEMODE;
        }

        if (wouldTaint(summary, profile)) {
            return Verdict.WARN_CHEAT_FORK;
        }

        return Verdict.ALLOW;
    }

    /**
     * Whether entering would mark this character as having used a cheated world. Only
     * survival characters are tracked: creative ones are cheating by definition.
     */
    public static boolean wouldTaint(LevelSummary summary, CharacterProfile profile) {
        return CharSelectConfig.INSTANCE.trackCheatedWorlds.get()
                && profile.gameMode() == CharacterGameMode.SURVIVAL
                && !profile.isCheated()
                && hasCheats(summary);
    }

    /** Records that the player confirmed a warning for this world. */
    public static void accept(LevelSummary summary) {
        ACCEPTED.add(summary.getLevelId());
    }

    public static void clearAccepted() {
        ACCEPTED.clear();
    }
}
