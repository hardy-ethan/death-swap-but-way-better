package com.deathswap.items;

import net.minecraft.server.level.ServerPlayer;

/**
 * The behaviour of a single death-swap item. Implementations correspond to the
 * datapack's {@code items/use/<n>} functions.
 */
@FunctionalInterface
public interface ItemEffect {

    /**
     * @param ctx    shared services (server, game state, effect manager)
     * @param self   the player who used the item
     * @param target the resolved target. For {@link ItemTarget#SELF} and
     *               {@link ItemTarget#CUSTOM} this equals {@code self} (CUSTOM
     *               effects resolve their own scope and ignore it); for
     *               {@link ItemTarget#CHOSEN_OPPONENT} it is the selected opponent.
     */
    void apply(ItemContext ctx, ServerPlayer self, ServerPlayer target);
}
