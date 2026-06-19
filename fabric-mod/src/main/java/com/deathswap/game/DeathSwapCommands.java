package com.deathswap.game;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The {@code /deathswap} command tree. Replaces the datapack's in-world buttons
 * and {@code /trigger} based settings menu.
 */
public final class DeathSwapCommands {

    private DeathSwapCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, GameManager game) {
        dispatcher.register(Commands.literal("deathswap")
                // ---- admin: lobby / flow ----
                .then(Commands.literal("settings")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            game.enterSettings();
                            return 1;
                        }))
                .then(Commands.literal("start")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            if (game.startGame()) {
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("No players to start a game."));
                            return 0;
                        }))
                .then(Commands.literal("stop")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            game.broadcast(">> Game stopped by an operator. <<", ChatFormatting.RED);
                            game.forceReturnToHub();
                            return 1;
                        }))
                // ---- admin: give an item by id ----
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    if (player == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("Only a player can receive an item."));
                                        return 0;
                                    }
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    if (!game.items().giveById(player, id)) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "No item with id " + id + " (valid: 1.."
                                                        + game.items().maxItemId() + ")."));
                                        return 0;
                                    }
                                    return 1;
                                })))
                // ---- admin: rules ----
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("lives")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            game.settings().maxLives = IntegerArgumentType.getInteger(ctx, "n");
                                            return ack(ctx, "Lives = " + game.settings().maxLives);
                                        })))
                        .then(Commands.literal("interval")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(30, 300))
                                        .executes(ctx -> {
                                            game.settings().swapIntervalSeconds =
                                                    IntegerArgumentType.getInteger(ctx, "seconds");
                                            return ack(ctx, "Swap interval = "
                                                    + game.settings().swapIntervalSeconds + "s");
                                        })))
                        .then(Commands.literal("randomcycle")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().randomCycle = BoolArgumentType.getBool(ctx, "on");
                                            return ack(ctx, "Random cycle = " + game.settings().randomCycle);
                                        })))
                        .then(Commands.literal("pvp")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().pvp = BoolArgumentType.getBool(ctx, "on");
                                            return ack(ctx, "PvP = " + game.settings().pvp);
                                        })))
                        .then(Commands.literal("hunger")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().hunger = BoolArgumentType.getBool(ctx, "on");
                                            return ack(ctx, "Hunger = " + game.settings().hunger);
                                        })))
                        .then(Commands.literal("warning")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 60))
                                        .executes(ctx -> {
                                            int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                            game.settings().swapWarning = nearestWarning(s);
                                            return ack(ctx, "Swap warning = "
                                                    + game.settings().swapWarning.seconds + "s");
                                        }))))
                // ---- player: emergency teleport ----
                .then(Commands.literal("tpaway")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player == null) {
                                return 0;
                            }
                            PlayerData data = game.data(player);
                            if (!data.canTpAway) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "You can't teleport away right now."));
                                return 0;
                            }
                            data.canTpAway = false;
                            game.spreadFarAway(player, true);
                            return 1;
                        }))
                // ---- player: item target selection ----
                .then(Commands.literal("target")
                        .then(Commands.argument("permNo", IntegerArgumentType.integer(1, 12))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    if (player == null) {
                                        return 0;
                                    }
                                    game.items().onTargetSelected(player,
                                            IntegerArgumentType.getInteger(ctx, "permNo"));
                                    return 1;
                                }))));
    }

    private static int ack(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static GameSettings.SwapWarning nearestWarning(int seconds) {
        GameSettings.SwapWarning best = GameSettings.SwapWarning.TEN_SECONDS;
        int bestDiff = Integer.MAX_VALUE;
        for (GameSettings.SwapWarning w : GameSettings.SwapWarning.values()) {
            int diff = Math.abs(w.seconds - seconds);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = w;
            }
        }
        return best;
    }
}
