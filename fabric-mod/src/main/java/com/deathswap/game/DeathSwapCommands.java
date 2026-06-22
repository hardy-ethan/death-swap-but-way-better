package com.deathswap.game;

import com.deathswap.config.DeathSwapConfig;
import com.deathswap.config.DiscordWebhook;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
        LiteralArgumentBuilder<CommandSourceStack> root = buildCommandTree(game);
        // /deathswap
        dispatcher.register(root);
        // /ds as a convenient alias
        dispatcher.register(Commands.literal("ds").redirect(root.build()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree(GameManager game) {
        return Commands.literal("deathswap")
                // ---- lobby / flow ----
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            if (game.phase() != GamePhase.HUB ) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "Game is already in progress. Stop it before starting a new one."));
                                return 0;
                            }

                            if (game.startGame()) {
                                return 1;
                            }
                            ctx.getSource().sendFailure(Component.literal("No players to start a game."));
                            return 0;
                        }))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            game.broadcast(">> Game stopped by an operator. <<", ChatFormatting.RED);
                            game.forceReturnToHub();
                            return 1;
                        }))
                // ---- free region files + re-roll the seed (shuts the server
                //      down; an external restart wrapper brings it back on the new seed) ----
                .then(Commands.literal("resetworld")
                        .executes(ctx -> {
                            Component reason = Component.literal(
                                    "Resetting the world (new seed). The server is restarting. Please "
                                            + "reconnect in ~50 seconds.");
                            if (!game.triggerWorldReset(reason)) {
                                ctx.getSource().sendFailure(Component.literal(
                                        "Can't reset now, only from the hub with no game running "
                                                + "(or a reset is already underway)."));
                                return 0;
                            }
                            return 1;
                        }))
                // ---- admin: grant life (and resurrect / rollback if needed) ----
                .then(Commands.literal("grantlife")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> grantLife(ctx, game))))
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
                                })
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target;
                                            try {
                                                target = EntityArgument.getPlayer(ctx, "player");
                                            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                                                ctx.getSource().sendFailure(Component.literal("Player not found."));
                                                return 0;
                                            }
                                            int id = IntegerArgumentType.getInteger(ctx, "id");
                                            if (!game.items().giveById(target, id)) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "No item with id " + id + " (valid: 1.."
                                                                + game.items().maxItemId() + ")."));
                                                return 0;
                                            }
                                            return 1;
                                        }))))
                // ---- query: a player's permanent number (item targeting / tooling) ----
                .then(Commands.literal("permno")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> listPermNos(ctx, game))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> permNo(ctx, game))))
                // ---- rules ----
                .then(Commands.literal("set")
                        .then(Commands.literal("lives")
                                .then(Commands.argument("n", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> {
                                            game.settings().maxLives = IntegerArgumentType.getInteger(ctx, "n");
                                            game.persistSettings();
                                            return ack(ctx, "Lives = " + game.settings().maxLives);
                                        })))
                        .then(Commands.literal("interval")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(30, 300))
                                        .executes(ctx -> {
                                            game.settings().swapIntervalSeconds =
                                                    IntegerArgumentType.getInteger(ctx, "seconds");
                                            game.persistSettings();
                                            return ack(ctx, "Swap interval = "
                                                    + game.settings().swapIntervalSeconds + "s");
                                        })))
                        .then(Commands.literal("firstswap")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(30, 600))
                                        .executes(ctx -> {
                                            game.settings().firstSwapSeconds =
                                                    IntegerArgumentType.getInteger(ctx, "seconds");
                                            game.persistSettings();
                                            return ack(ctx, "First swap = "
                                                    + game.settings().firstSwapSeconds + "s");
                                        })))
                        .then(Commands.literal("randomcycle")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().randomCycle = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "Random cycle = " + game.settings().randomCycle);
                                        })))
                        .then(Commands.literal("pvp")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().pvp = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "PvP = " + game.settings().pvp);
                                        })))
                        .then(Commands.literal("hunger")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().hunger = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "Hunger = " + game.settings().hunger);
                                        })))
                        .then(Commands.literal("warning")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 60))
                                        .executes(ctx -> {
                                            int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                            game.settings().swapWarning = nearestWarning(s);
                                            game.persistSettings();
                                            return ack(ctx, "Swap warning = "
                                                    + game.settings().swapWarning.seconds + "s");
                                        })))
                        .then(Commands.literal("showtimer")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().showSwapTimer = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "Show swap timer = " + game.settings().showSwapTimer);
                                        })))
                        .then(Commands.literal("starttools")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().startWithBasicTools = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "Start with basic tools = "
                                                            + game.settings().startWithBasicTools);
                                        })))
                        .then(Commands.literal("naturalregen")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().naturalRegen = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "Natural regen = " + game.settings().naturalRegen);
                                        })))
                        .then(Commands.literal("keepinventory")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            game.settings().keepInventory = BoolArgumentType.getBool(ctx, "on");
                                            game.persistSettings();
                                            return ack(ctx, "Keep inventory = " + game.settings().keepInventory);
                                        })))
                        .then(Commands.literal("language")
                                .then(Commands.argument("lang", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String lang = StringArgumentType.getString(ctx, "lang");
                                            if (lang.equalsIgnoreCase("chinese") || lang.equalsIgnoreCase("zh")) {
                                                    game.settings().language = GameSettings.Language.CHINESE;
                                            } else {
                                                    game.settings().language = GameSettings.Language.ENGLISH;
                                            }
                                            game.persistSettings();
                                            return ack(ctx, "Language = " + game.settings().language);
                                        }))))
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
                                })))
                // ---- player: report to the Discord webhook ----
                .then(Commands.literal("report")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> report(ctx,
                                        StringArgumentType.getString(ctx, "message")))));
    }

    private static int grantLife(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                  GameManager game) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }
        if (!game.addLife(target)) {
            ctx.getSource().sendFailure(Component.literal(
                    "No game is in progress (or the game has already ended and returned to the hub)."));
            return 0;
        }
        return 1;
    }

    /**
     * Report one player's permanent number. The number is also returned as the
     * command result, so {@code /execute store result ...} (or an RCON caller)
     * can capture it directly instead of parsing chat. permNos are assigned at
     * game start and are 1..N, so 0 cleanly signals "no number yet".
     */
    private static int permNo(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                              GameManager game) {
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(ctx, "player");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            ctx.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }
        int no = game.data(target).permPNo;
        if (no <= 0) {
            ctx.getSource().sendFailure(Component.literal(
                    target.getScoreboardName() + " has no permNo (no game in progress)."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                target.getScoreboardName() + " permNo " + no).withStyle(ChatFormatting.AQUA), false);
        return no;
    }

    /** List every alive player with their permanent number; returns the count. */
    private static int listPermNos(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                   GameManager game) {
        java.util.List<ServerPlayer> alive = game.alivePlayers();
        if (alive.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No alive players (no game in progress)."));
            return 0;
        }
        for (ServerPlayer p : alive) {
            int no = game.data(p).permPNo;
            ctx.getSource().sendSuccess(() -> Component.literal(
                    p.getScoreboardName() + " permNo " + no).withStyle(ChatFormatting.AQUA), false);
        }
        return alive.size();
    }

    private static int report(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                                 String message) {
        CommandSourceStack source = ctx.getSource();
        if (DeathSwapConfig.discordWebhookUrl().isBlank()) {
            source.sendFailure(Component.literal(
                    "Reporting is not configured (set discordWebhookUrl in config/deathswap.json)."));
            return 0;
        }

        String reporter = source.getTextName();
        DiscordWebhook.send("From `" + reporter + "`:\n" + message)
                // The webhook completes off-thread; bounce back onto the server
                // thread before touching the command source.
                .thenAccept(ok -> source.getServer().execute(() -> {
                    if (ok) {
                        source.sendSuccess(() -> Component.literal("Report sent. Thanks!")
                                .withStyle(ChatFormatting.GREEN), false);
                    } else {
                        source.sendFailure(Component.literal(
                                "Failed to send report. Please try again later."));
                    }
                }));
        return 1;
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
