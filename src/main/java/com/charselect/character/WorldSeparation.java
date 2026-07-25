package com.charselect.character;

/** How strictly a character's gamemode decides which worlds it may enter. */
public enum WorldSeparation {
    /** Terraria-style: survival characters see only survival worlds, creative only creative. */
    STRICT,
    /** Creative characters may also enter survival worlds; survival characters stay locked. */
    CREATIVE_SUPERSET,
    /** Everything is visible, but crossing gamemode lines asks for confirmation first. */
    WARN,
    /** No gating at all. Characters keep their own gamemode rules, worlds accept anyone. */
    OFF;

    /** Whether a world of {@code world}'s kind should appear in the list for this character. */
    public boolean allowsListing(CharacterGameMode character, CharacterGameMode world) {
        return switch (this) {
            case STRICT -> character == world;
            case CREATIVE_SUPERSET -> character == world || character == CharacterGameMode.CREATIVE;
            case WARN, OFF -> true;
        };
    }

    /** Whether entering this world should prompt first. Only ever true under {@link #WARN}. */
    public boolean warnsAbout(CharacterGameMode character, CharacterGameMode world) {
        return this == WARN && character != world;
    }
}
