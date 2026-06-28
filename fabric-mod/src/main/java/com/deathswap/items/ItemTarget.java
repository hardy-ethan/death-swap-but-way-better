package com.deathswap.items;

/**
 * Who an item's effect is applied to. Determines whether the player is asked to
 * pick a target after dropping the item (the datapack's {@code select} trigger).
 */
public enum ItemTarget {
    /** Affects the user only. Applied immediately on drop. */
    SELF,
    /** Affects a single chosen opponent (prompts for a target). */
    CHOSEN_OPPONENT,
    /**
     * The effect resolves its own targets — everyone, a random player, or global
     * game state. Fired immediately with the user as a nominal {@code target},
     * which such effects ignore. This is the default so an item that never
     * declares a target can't silently inherit {@link #SELF} behaviour.
     */
    CUSTOM
}
