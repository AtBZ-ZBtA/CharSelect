package com.charselect.api;

/** Whether a piece of data follows the character between worlds or stays with one world. */
public enum CharacterDataScope {
    /** Follows the character everywhere. Right for inventories, progress and unlocks. */
    SHARED,
    /** Kept against the world it came from. Right for anything tied to a place. */
    PER_WORLD
}
