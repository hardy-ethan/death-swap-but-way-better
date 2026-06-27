package com.deathswap.items;

/**
 * Who an item's effect is applied to. Determines whether the player is asked to
 * pick a target after dropping the item (the datapack's {@code select} trigger).
 */
public enum ItemTarget {
    /** Affects the user only. Applied immediately on drop. */
    SELF,
    /** Affects a single chosen opponent (prompts for a target). */
    OPPONENT,
    /** Affects every playing player except the user. */
    ALL_OTHERS,
    /** Affects a single random opponent. */
    RANDOM_OPPONENT,
    /** Affects everyone, user included (e.g. low gravity, difficulty change). */
    EVERYONE
}
