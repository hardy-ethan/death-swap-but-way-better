package com.deathswap.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;
import java.util.function.ToIntFunction;

/**
 * Drives the in-game scoreboard HUD: a sidebar listing every participant's
 * remaining lives, and a tab-list column showing each player's health as hearts.
 *
 * <p>The health column uses the vanilla {@link ObjectiveCriteria#HEALTH} criterion,
 * which the server keeps in sync with each player's health automatically, so only
 * the lives sidebar needs to be pushed by hand.
 */
final class ScoreboardDisplay {

    private static final String LIVES_OBJECTIVE = "ds_lives";
    private static final String HEALTH_OBJECTIVE = "ds_health";

    private MinecraftServer server;

    /** Create the objectives (fresh each game) and bind them to their display slots. */
    void start(MinecraftServer server) {
        this.server = server;
        Scoreboard board = server.getScoreboard();

        Objective lives = recreate(board, LIVES_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal("Lives").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER);
        board.setDisplayObjective(DisplaySlot.SIDEBAR, lives);

        Objective health = recreate(board, HEALTH_OBJECTIVE,
                ObjectiveCriteria.HEALTH,
                Component.literal("Health").withStyle(ChatFormatting.RED),
                ObjectiveCriteria.RenderType.HEARTS);
        board.setDisplayObjective(DisplaySlot.LIST, health);
    }

    /** Push current life counts for every participant onto the sidebar. */
    void updateLives(Collection<ServerPlayer> participants, ToIntFunction<ServerPlayer> livesOf) {
        if (server == null) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        Objective lives = board.getObjective(LIVES_OBJECTIVE);
        if (lives == null) {
            return;
        }
        for (ServerPlayer player : participants) {
            board.getOrCreatePlayerScore(player, lives).set(livesOf.applyAsInt(player));
        }
    }

    /** Drop a player's sidebar row (e.g. on disconnect). */
    void removePlayer(ServerPlayer player) {
        if (server == null) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        Objective lives = board.getObjective(LIVES_OBJECTIVE);
        if (lives != null) {
            board.resetSinglePlayerScore(player, lives);
        }
    }

    /** Tear the HUD down when returning to the hub. */
    void stop() {
        if (server == null) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        remove(board, LIVES_OBJECTIVE);
        remove(board, HEALTH_OBJECTIVE);
    }

    private static Objective recreate(Scoreboard board, String name, ObjectiveCriteria criteria,
                                      Component displayName, ObjectiveCriteria.RenderType render) {
        remove(board, name);
        return board.addObjective(name, criteria, displayName, render, false, null);
    }

    private static void remove(Scoreboard board, String name) {
        Objective existing = board.getObjective(name);
        if (existing != null) {
            board.removeObjective(existing);
        }
    }
}
