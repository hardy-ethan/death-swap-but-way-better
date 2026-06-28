package com.deathswap.game;

/**
 * The high level state machine for a game, mirroring the datapack's
 * {@code gameOn} / {@code settingsOn} / {@code clockRunning} flags.
 *
 * <pre>
 *   HUB ──(start button / /deathswap settings)──▶ SETTINGS
 *   SETTINGS ──(/deathswap start)──▶ RUNNING
 *   RUNNING ──(one player left)──▶ ENDING
 *   ENDING ──(~10s)──▶ HUB
 * </pre>
 */
public enum GamePhase {
    /** Players wait in the lobby. Adventure mode, no clock. */
    HUB,
    /** Active game: swap clock running, items being offered. */
    RUNNING,
    /** A winner has been decided; victory sequence then return to hub. */
    ENDING
}
