package com.deathswap.items;

import com.deathswap.effects.EffectManager;
import com.deathswap.game.GameManager;
import net.minecraft.server.MinecraftServer;

/**
 * Services handed to an {@link ItemEffect} when it fires, so individual effects
 * stay small and declarative.
 */
public record ItemContext(MinecraftServer server, GameManager game, EffectManager effects) {
}
