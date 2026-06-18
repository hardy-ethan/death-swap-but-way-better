package com.deathswap.game;

import com.deathswap.effects.EffectManager;
import com.deathswap.items.ItemManager;
import com.deathswap.util.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the whole game: the {@link GamePhase} state machine, the swap clock,
 * the lives/elimination system and the win condition. Reimplements
 * {@code repeats.mcfunction}, {@code game/clock.mcfunction}, {@code game/swap.mcfunction},
 * {@code game/player_died.mcfunction}, {@code game/player_eliminated.mcfunction}
 * and {@code game/prep_winner.mcfunction}.
 */
public final class GameManager {

    /** Minimum spread radius at game start (datapack uses 10,000). */
    private static final int SPREAD_MIN = 10_000;
    /**
     * Maximum spread radius. The datapack uses 29,999,000 which is brutal on
     * world-gen; we cap lower by default for playability. Tune as needed.
     */
    private static final int SPREAD_MAX = 250_000;

    private final GameSettings settings = new GameSettings();
    private final EffectManager effects = new EffectManager();
    private final ItemManager items;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final java.util.Random random = new java.util.Random();

    private MinecraftServer server;
    private GamePhase phase = GamePhase.HUB;

    private int swapTicksRemaining;
    private int itemTicksRemaining;
    private int endingTicksRemaining;
    private int startingPlayerCount;
    /** Tracks which swap-warning thresholds have already fired this cycle. */
    private int lastWarnSecondAnnounced = -1;

    public GameManager() {
        this.items = new ItemManager(this);
    }

    // ---- lifecycle ----

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        this.phase = GamePhase.HUB;
        items.registerAll();
    }

    public MinecraftServer server() {
        return server;
    }

    public GameSettings settings() {
        return settings;
    }

    public EffectManager effects() {
        return effects;
    }

    public ItemManager items() {
        return items;
    }

    public GamePhase phase() {
        return phase;
    }

    public PlayerData data(ServerPlayer player) {
        return playerData.computeIfAbsent(player.getUUID(), PlayerData::new);
    }

    public PlayerData dataIfPresent(UUID uuid) {
        return playerData.get(uuid);
    }

    // ---- player connection ----

    public void onPlayerJoin(ServerPlayer player) {
        PlayerData data = data(player);
        if (phase == GamePhase.RUNNING || phase == GamePhase.ENDING) {
            // Late joiner: spectate the ongoing game.
            data.playing = false;
            player.setGameMode(GameType.SPECTATOR);
            Mc.msg(player, ">> A game is in progress. You'll join the next one.", ChatFormatting.YELLOW);
        } else {
            sendToHub(player);
        }
    }

    public void onPlayerLeave(ServerPlayer player) {
        // Keep their PlayerData so wins persist if they reconnect this session.
        if (phase == GamePhase.RUNNING) {
            checkWinCondition();
        }
    }

    private void sendToHub(ServerPlayer player) {
        PlayerData data = data(player);
        data.playing = true;
        data.eliminated = false;
        data.winner = false;
        player.setGameMode(GameType.ADVENTURE);
        effects.clearAll(player);
        teleportToWorldSpawn(player);
    }

    // ---- main tick ----

    public void tick() {
        if (server == null) {
            return;
        }
        runScheduled();
        switch (phase) {
            case RUNNING -> tickRunning();
            case ENDING -> tickEnding();
            default -> {
                // HUB / SETTINGS: nothing time-driven.
            }
        }
    }

    private void tickRunning() {
        effects.tick(server);
        items.tick();

        for (ServerPlayer player : alivePlayers()) {
            PlayerData data = data(player);
            if (data.deathImmunityTicks > 0) {
                data.deathImmunityTicks--;
            }
            // No-hunger rule: keep the food bar topped up (repeats.mcfunction).
            if (!settings.hunger) {
                player.getFoodData().setFoodLevel(20);
            }
        }

        // Item offer clock.
        if (--itemTicksRemaining <= 0) {
            items.offerToAll();
            itemTicksRemaining = itemOfferIntervalTicks();
        }

        // Swap clock + warnings.
        tickSwapClock();

        checkWinCondition();
    }

    private void tickSwapClock() {
        int secondsLeft = (swapTicksRemaining + 19) / 20;

        // Announce at the configured warning threshold, plus the final 5s countdown.
        boolean shouldWarn = secondsLeft == settings.swapWarning.seconds
                || (secondsLeft >= 1 && secondsLeft <= 5);
        if (shouldWarn && secondsLeft != lastWarnSecondAnnounced) {
            announceSwapWarning(secondsLeft);
            lastWarnSecondAnnounced = secondsLeft;
        }

        if (--swapTicksRemaining <= 0) {
            doSwap();
            resetSwapClock();
        }
    }

    private void announceSwapWarning(int secondsLeft) {
        for (ServerPlayer player : alivePlayers()) {
            Mc.title(player, ">> Swapping <<", "In " + secondsLeft
                            + (secondsLeft == 1 ? " second" : " seconds"),
                    ChatFormatting.GOLD, ChatFormatting.YELLOW);
            Mc.playSound(player, SoundEvents.ANVIL_LAND, 0.5f, 1.5f);
        }
    }

    private void tickEnding() {
        if (--endingTicksRemaining <= 0) {
            returnEveryoneToHub();
        }
    }

    // ---- phase transitions ----

    public void enterSettings() {
        phase = GamePhase.SETTINGS;
        broadcast(">> Configure the game, then run /deathswap start <<", ChatFormatting.AQUA);
    }

    /** Begin a game with everyone currently in the hub. */
    public boolean startGame() {
        settings.clampToLegalValues();
        List<ServerPlayer> participants = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = data(player);
            if (data.playing && !data.eliminated) {
                participants.add(player);
            }
        }
        if (participants.isEmpty()) {
            return false;
        }

        startingPlayerCount = participants.size();

        for (ServerPlayer player : participants) {
            PlayerData data = data(player);
            data.resetForNewGame(settings.maxLives);
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.getInventory().clearContent();
            if (settings.startWithBasicTools) {
                giveBasicTools(player);
            }
            spreadFarAway(player);
            Mc.title(player, ">> Death Swap! <<", "Survive and outlast everyone!",
                    ChatFormatting.GREEN, ChatFormatting.AQUA);
        }

        // Assign permanent slot numbers after the per-player reset (which zeroes them).
        assignPermanentNumbers(participants);

        phase = GamePhase.RUNNING;
        resetSwapClock();
        itemTicksRemaining = 20 * 46; // first items after ~46s, per datapack
        broadcast(">> The game has begun! <<", ChatFormatting.GREEN);

        // Enable the emergency teleport after 32 seconds (start_tp_away.mcfunction).
        schedule(20 * 32, () -> {
            for (ServerPlayer p : alivePlayers()) {
                data(p).canTpAway = true;
            }
            broadcast("Tip: /deathswap tpaway relocates you if your area is laggy.",
                    ChatFormatting.GRAY);
        });
        return true;
    }

    private void resetSwapClock() {
        if (settings.randomCycle) {
            int minutes = random.nextInt(5); // 0..4
            int seconds = random.nextInt(60);
            if (minutes == 0 && seconds < 30) {
                seconds = 30 + random.nextInt(30); // avoid sub-30s cycles
            }
            swapTicksRemaining = (minutes * 60 + seconds) * 20;
        } else {
            swapTicksRemaining = settings.swapIntervalSeconds * 20;
        }
        lastWarnSecondAnnounced = -1;
    }

    // ---- swap (game/swap.mcfunction) ----

    public void doSwap() {
        List<ServerPlayer> alive = alivePlayers();
        if (alive.size() < 2) {
            return;
        }
        Collections.shuffle(alive, random);

        // Snapshot every player's location, then rotate: player i receives the
        // location player i+1 was standing in (a cyclic position swap).
        List<Location> locations = new ArrayList<>();
        for (ServerPlayer player : alive) {
            locations.add(Location.of(player));
        }

        for (int i = 0; i < alive.size(); i++) {
            ServerPlayer player = alive.get(i);
            Location dest = locations.get((i + 1) % alive.size());
            dest.apply(player);
            player.fallDistance = 0.0f; // disable swap fall damage (gamerule toggle in datapack)
            Mc.effect(player, MobEffects.SLOW_FALLING, 3, 0);
            Mc.title(player, ">> Swapped! <<", "", ChatFormatting.GOLD, ChatFormatting.WHITE);
            Mc.playSound(player, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
            data(player).canTpAway = true; // restore the emergency teleport each cycle
        }
    }

    // ---- death / elimination (game/player_died, player_eliminated) ----

    /**
     * Called from the death mixin/event. Returns true if the death should be
     * allowed to proceed, false to cancel it (the player survives).
     */
    public boolean onAllowDeath(ServerPlayer player) {
        if (phase != GamePhase.RUNNING) {
            return true;
        }
        PlayerData data = data(player);
        if (!data.playing || data.eliminated) {
            return true;
        }

        // Death immunity window after a recent death (no_death).
        if (data.deathImmunityTicks > 0) {
            survive(player);
            return false;
        }

        data.lives--;
        data.deathImmunityTicks = 20 * 40; // ~40s immunity, like the datapack

        if (data.lives <= 0) {
            eliminate(player);
        } else {
            survive(player);
            Mc.title(player, ">> You died! <<", "-1 life (" + data.lives + " left)",
                    ChatFormatting.RED, ChatFormatting.GOLD);
            broadcast(player.getName().getString() + " died and lost a life! ("
                    + data.lives + " left)", ChatFormatting.RED);
        }
        return false; // we always handle death ourselves
    }

    private void survive(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.fallDistance = 0.0f;
        Mc.effect(player, MobEffects.RESISTANCE, 10, 4);
        player.getFoodData().setFoodLevel(20);
    }

    private void eliminate(ServerPlayer player) {
        PlayerData data = data(player);
        data.lives = 0;
        data.eliminated = true;
        data.playing = false;
        data.clearOffer();
        effects.clearAll(player);
        player.setGameMode(GameType.SPECTATOR);
        Mc.title(player, ">> Eliminated! <<", "You're out of lives.",
                ChatFormatting.RED, ChatFormatting.GRAY);
        Mc.playSound(player, SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        broadcast(player.getName().getString() + " has been eliminated!", ChatFormatting.RED);
    }

    private void checkWinCondition() {
        if (phase != GamePhase.RUNNING) {
            return;
        }
        List<ServerPlayer> alive = alivePlayers();
        if (startingPlayerCount >= 2 && alive.size() <= 1) {
            declareWinner(alive.isEmpty() ? null : alive.get(0));
        }
    }

    private void declareWinner(ServerPlayer winner) {
        phase = GamePhase.ENDING;
        endingTicksRemaining = 20 * 10;
        if (winner != null) {
            PlayerData data = data(winner);
            data.winner = true;
            data.wins++;
            Mc.give(winner, net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1);
            Mc.effect(winner, MobEffects.RESISTANCE, 20, 4);
            Mc.effect(winner, MobEffects.GLOWING, 6, 0);
            broadcast(">>> " + winner.getName().getString()
                    + " survived the longest and won the game! <<<", ChatFormatting.GREEN);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Mc.title(p, winner.getName().getString() + " won!",
                        "They survived the way better death swap!",
                        ChatFormatting.GREEN, ChatFormatting.AQUA);
                Mc.playSound(p, SoundEvents.ENDER_DRAGON_DEATH, 1.0f, 1.0f);
            }
        } else {
            broadcast(">> The game ended in a draw. <<", ChatFormatting.YELLOW);
        }
    }

    /** Abort the current game (operator /deathswap stop) and reset to the lobby. */
    public void forceReturnToHub() {
        returnEveryoneToHub();
    }

    private void returnEveryoneToHub() {
        phase = GamePhase.HUB;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToHub(player);
        }
        broadcast(">> Back to the lobby. Run /deathswap start for another round. <<",
                ChatFormatting.AQUA);
    }

    // ---- helpers ----

    public List<ServerPlayer> alivePlayers() {
        List<ServerPlayer> alive = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = data(player);
            if (data.playing && !data.eliminated) {
                alive.add(player);
            }
        }
        return alive;
    }

    public ServerPlayer playerByPermNo(int permNo) {
        for (ServerPlayer player : alivePlayers()) {
            if (data(player).permPNo == permNo) {
                return player;
            }
        }
        return null;
    }

    private void assignPermanentNumbers(List<ServerPlayer> participants) {
        List<ServerPlayer> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, random);
        for (int i = 0; i < shuffled.size(); i++) {
            data(shuffled.get(i)).permPNo = i + 1;
        }
    }

    public void spreadFarAway(ServerPlayer player) {
        ServerLevel level = server.overworld();
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = SPREAD_MIN + random.nextDouble() * (SPREAD_MAX - SPREAD_MIN);
        int x = (int) (Math.cos(angle) * radius);
        int z = (int) (Math.sin(angle) * radius);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        Mc.teleportTo(player, level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
    }

    private void teleportToWorldSpawn(ServerPlayer player) {
        // The lobby isn't a built map (unlike the datapack's superflat hub), so we
        // just gather everyone at the overworld origin column.
        ServerLevel level = server.overworld();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        Mc.teleportTo(player, level, 0.5, y, 0.5, player.getYRot(), player.getXRot());
    }

    private void giveBasicTools(ServerPlayer player) {
        Mc.give(player, net.minecraft.world.item.Items.STONE_PICKAXE, 1);
        Mc.give(player, net.minecraft.world.item.Items.STONE_AXE, 1);
        Mc.give(player, net.minecraft.world.item.Items.STONE_SHOVEL, 1);
        Mc.give(player, net.minecraft.world.item.Items.STONE_SWORD, 1);
        Mc.give(player, net.minecraft.world.item.Items.BREAD, 16);
    }

    private int itemOfferIntervalTicks() {
        // Datapack scales the item interval with player count; roughly 45s solo
        // down to a few seconds with a full lobby (reset_time.mcfunction).
        int n = Math.max(1, alivePlayers().size());
        int seconds = switch (n) {
            case 1 -> 35;
            case 2 -> 22;
            case 3 -> 15;
            case 4 -> 11;
            case 5 -> 9;
            case 6 -> 7;
            case 7, 8, 9 -> 5;
            default -> 4;
        };
        return seconds * 20;
    }

    public void broadcast(String text, ChatFormatting color) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Mc.msg(player, text, color);
        }
    }

    // ---- tiny scheduler (replaces /schedule) ----

    public void schedule(int delayTicks, Runnable action) {
        scheduled.add(new Scheduled(delayTicks, action));
    }

    private void runScheduled() {
        for (int i = scheduled.size() - 1; i >= 0; i--) {
            Scheduled s = scheduled.get(i);
            if (--s.ticks <= 0) {
                scheduled.remove(i);
                s.action.run();
            }
        }
    }

    private static final class Scheduled {
        int ticks;
        final Runnable action;

        Scheduled(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }

    /** Immutable snapshot of a player's location, used during swaps. */
    private record Location(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        static Location of(ServerPlayer player) {
            Vec3 p = player.position();
            return new Location(Mc.level(player), p.x, p.y, p.z, player.getYRot(), player.getXRot());
        }

        void apply(ServerPlayer player) {
            Mc.teleportTo(player, level, x, y, z, yRot, xRot);
        }
    }
}
