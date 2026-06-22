package com.deathswap.game;

import com.deathswap.util.Translator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
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
    private static final String WINS_OBJECTIVE = "ds_wins";

    private MinecraftServer server;

    /**
     * Hub HUD: show each player's lifetime win count both on the sidebar and
     * below their name above their head. Clears the running-game objectives so
     * only the wins tally is visible while idling in the lobby.
     */
    void startHub(MinecraftServer server, boolean zh) {
        this.server = server;
        Scoreboard board = server.getScoreboard();
        remove(board, LIVES_OBJECTIVE);
        remove(board, HEALTH_OBJECTIVE);

        Objective wins = recreate(board, WINS_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal(Translator.translate(zh, "Wins")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER);
        board.setDisplayObjective(DisplaySlot.SIDEBAR, wins);
        board.setDisplayObjective(DisplaySlot.BELOW_NAME, wins);
    }

    /** Push current win counts for every online player onto the hub HUD. */
    void updateWins(Collection<ServerPlayer> players, ToIntFunction<ServerPlayer> winsOf) {
        if (server == null) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        Objective wins = board.getObjective(WINS_OBJECTIVE);
        if (wins == null) {
            return;
        }
        for (ServerPlayer player : players) {
            board.getOrCreatePlayerScore(player, wins).set(winsOf.applyAsInt(player));
        }
    }

    /** Create the objectives (fresh each game) and bind them to their display slots. */
    void start(MinecraftServer server, boolean zh) {
        this.server = server;
        Scoreboard board = server.getScoreboard();
        // The wins tally (sidebar + below-name) belongs to the hub; clear it so the
        // game's lives/health HUD takes over cleanly.
        remove(board, WINS_OBJECTIVE);

        Objective lives = recreate(board, LIVES_OBJECTIVE,
                ObjectiveCriteria.DUMMY,
                Component.literal(Translator.translate(zh, "Lives")).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                ObjectiveCriteria.RenderType.INTEGER);
        board.setDisplayObjective(DisplaySlot.SIDEBAR, lives);

        Objective health = recreate(board, HEALTH_OBJECTIVE,
                ObjectiveCriteria.HEALTH,
                Component.literal(Translator.translate(zh, "Health")).withStyle(ChatFormatting.RED),
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

    /** Set the lives score for an offline player by name. */
    void updateLivesForName(String name, int lives) {
        if (server == null) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        Objective obj = board.getObjective(LIVES_OBJECTIVE);
        if (obj == null) {
            return;
        }
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name), obj).set(lives);
    }

    /** Set the wins score for an offline player by name. */
    void updateWinsForName(String name, int wins) {
        if (server == null) {
            return;
        }
        Scoreboard board = server.getScoreboard();
        Objective obj = board.getObjective(WINS_OBJECTIVE);
        if (obj == null) {
            return;
        }
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name), obj).set(wins);
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
