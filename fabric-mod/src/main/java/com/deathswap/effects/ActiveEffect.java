package com.deathswap.effects;

import net.minecraft.server.level.ServerPlayer;

/**
 * A timed status effect applied by an item (e.g. shield, low gravity, bedrock
 * trail). Mirrors the datapack pattern of an effect scoreboard that counts down
 * and a {@code *_done} cleanup function.
 */
public final class ActiveEffect {

    @FunctionalInterface
    public interface PlayerAction {
        void run(ServerPlayer player);
    }

    /** Stable key, e.g. {@code "shield"}; only one effect per key per player. */
    public final String key;

    private int remainingTicks;
    private final PlayerAction onTick;   // nullable, runs every tick
    private final PlayerAction onEnd;    // nullable, runs once when expired/cleared

    public ActiveEffect(String key, int durationTicks, PlayerAction onTick, PlayerAction onEnd) {
        this.key = key;
        this.remainingTicks = durationTicks;
        this.onTick = onTick;
        this.onEnd = onEnd;
    }

    /** @return true when the effect has finished and should be removed. */
    public boolean tick(ServerPlayer player) {
        if (onTick != null) {
            onTick.run(player);
        }
        remainingTicks--;
        return remainingTicks <= 0;
    }

    public void end(ServerPlayer player) {
        if (onEnd != null) {
            onEnd.run(player);
        }
    }

    public int remainingTicks() {
        return remainingTicks;
    }
}
