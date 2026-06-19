package com.deathswap.game;

import com.deathswap.items.DeathSwapItem;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Per-player game state. Replaces the datapack's per-player scoreboard scores
 * ({@code Lives}, {@code permPNo}, {@code PNo}, {@code Wins}, {@code no_death}, ...)
 * and the various per-player tags.
 *
 * <p>One instance lives for as long as the player is connected; the engine looks
 * it up by {@link UUID} every tick.
 */
public final class PlayerData {

    public final UUID uuid;

    /** Lives remaining this game. 0 == eliminated. */
    public int lives;

    /** Total wins across games this session (datapack {@code Wins}). */
    public int wins;

    /** Permanent slot (1..12) assigned at game prep; used for item targeting. */
    public int permPNo;

    /** Rotating swap-cycle slot (1..n), reshuffled before every swap. */
    public int swapPNo;

    public boolean playing;
    public boolean eliminated;
    public boolean winner;

    /**
     * Post-death immunity window. While > 0 the player cannot lose another life
     * (datapack {@code no_death} score / tag, counts down each tick).
     */
    public int deathImmunityTicks;

    /** Emergency "/deathswap tpaway" availability (datapack {@code tp_away} trigger). */
    public boolean canTpAway;

    /**
     * The initial spread location set as this player's spawn point at game start
     * (datapack {@code spawnpoint @s ~ ~ ~} in game_start.mcfunction). On death the
     * player is returned here, just as a vanilla death respawns them at their
     * spawn point. Null until the player has been spread.
     */
    public BlockPos spawnPos;
    public float spawnYaw;

    // ---- item offering state ----

    /** The three items currently offered to this player, or null when none. */
    public DeathSwapItem[] offeredItems;

    /** True once a new set of items has been handed out and not yet used. */
    public boolean choosingItem;

    /** Item awaiting a target (opponent-targeted items), or null. */
    public DeathSwapItem pendingTargetItem;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public boolean hasShield() {
        return false; // shields are tracked as ActiveEffects; see EffectManager#hasEffect
    }

    public void clearOffer() {
        offeredItems = null;
        choosingItem = false;
        pendingTargetItem = null;
    }

    /** Reset everything that should not survive into a fresh game. */
    public void resetForNewGame(int maxLives) {
        lives = maxLives;
        permPNo = 0;
        swapPNo = 0;
        playing = true;
        eliminated = false;
        winner = false;
        deathImmunityTicks = 0;
        canTpAway = false;
        spawnPos = null;
        spawnYaw = 0.0f;
        clearOffer();
    }
}
