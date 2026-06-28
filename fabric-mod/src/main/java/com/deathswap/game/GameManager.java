package com.deathswap.game;

import com.deathswap.DeathSwapMod;
import com.deathswap.config.SettingsStore;
import com.deathswap.config.WinsStore;
import com.deathswap.effects.EffectManager;
import com.deathswap.items.ItemManager;
import com.deathswap.util.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;

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

    /**
     * Allowed phase transitions.  Every edge in the state machine must appear here;
     * {@link #transitionTo} warns loudly if anything tries to cross an unlisted edge.
     *
     * <pre>
     *   HUB ──(enterRunning)──▶ RUNNING
     *   RUNNING ──(enterEnding)──▶ ENDING
     *   RUNNING ──(enterHub)──▶ HUB
     *   ENDING ──(rollbackToRunning)──▶ RUNNING
     *   ENDING ──(enterHub)──▶ HUB
     * </pre>
     */
    private static final java.util.Map<GamePhase, java.util.EnumSet<GamePhase>> VALID_TRANSITIONS;
    static {
        var m = new java.util.EnumMap<GamePhase, java.util.EnumSet<GamePhase>>(GamePhase.class);
        m.put(GamePhase.HUB,     java.util.EnumSet.of(GamePhase.RUNNING));
        m.put(GamePhase.RUNNING, java.util.EnumSet.of(GamePhase.ENDING, GamePhase.HUB));
        m.put(GamePhase.ENDING,  java.util.EnumSet.of(GamePhase.HUB,    GamePhase.RUNNING));
        VALID_TRANSITIONS = java.util.Collections.unmodifiableMap(m);
    }

    /** Minimum spread radius at game start (datapack uses 10,000). */
    private static final int SPREAD_MIN = 10_000;
    /**
     * Maximum spread radius. The datapack uses 29,999,000 which is brutal on
     * world-gen; we cap lower by default for playability. Tune as needed.
     */
    private static final int SPREAD_MAX = 250_000;

    /**
     * Length of the pre-game freeze: after the spread, players are blinded and
     * held in place for this long before the swap clock starts, so everyone gets
     * a moment to load in without being attacked or wandering off.
     */
    private static final int FREEZE_TICKS = 7 * 20;

    /**
     * How long the server must sit empty (no players online) before an automatic
     * world reset is triggered. The reset frees the region files and re-rolls the
     * seed (see {@link WorldReset}), so the next session starts on a fresh, smaller
     * world.
     *
     * <p>This is a wall-clock delay run off the game tick loop on purpose: a
     * dedicated server stops ticking once it has been empty for
     * {@code pause-when-empty-seconds} (default 60), so a tick-based timer would
     * freeze and never fire. We arm a real-time scheduler on disconnect instead.
     */
    private static final long IDLE_RESET_SECONDS = 5;

    private final GameSettings settings = new GameSettings();
    private boolean languageToggledByItem = false;
    private final EffectManager effects = new EffectManager();
    private final ScoreboardDisplay scoreboard = new ScoreboardDisplay();
    /** Lifetime per-player win tallies, persisted across server restarts. */
    private final WinsStore winsStore = new WinsStore();
    private final ItemManager items;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<GravelTower> gravelTowers = new ArrayList<>();
    private final List<java.util.function.BooleanSupplier> buildJobs = new ArrayList<>();
    private final java.util.Random random = new java.util.Random();
    /** Pre-generated spread destinations, filled while idling in the hub. */
    private final ChunkCache chunkCache = new ChunkCache();
    /** Snapshots block changes during a game so the world is restored when it ends. */
    private final WorldRollback worldRollback = new WorldRollback();

    private MinecraftServer server;
    private GamePhase phase = GamePhase.HUB;

    private int swapTicksRemaining;
    private int itemTicksRemaining;
    private int endingTicksRemaining;
    /** True while the game is operator-paused (see {@link #pauseGame()}). */
    private boolean paused;
    /** Real-time ticks elapsed since the current pause began, for the on-screen clock. */
    private int pausedTicks;
    /** Display name of whoever ran /deathswap pause, shown on the overlay. */
    private String pausedByName = "";
    /** Where each alive player stood when the pause began, so they're held in place. */
    private final Map<UUID, Vec3> pausePositions = new HashMap<>();
    /** Pre-game freeze countdown; while > 0 players are blinded and held in place. */
    private int freezeTicksRemaining;
    /** Ticks elapsed since the current game started (freeze + active play). */
    private int gameTicksElapsed;
    /** Last spread-cache figures broadcast to chat, so the hub only logs on change. */
    private int lastCacheReadyLogged = -1;
    private int lastCachePendingLogged = -1;
    private int startingPlayerCount;
    /** Tracks which swap-warning thresholds have already fired this cycle. */
    private int lastWarnSecondAnnounced = -1;
    /**
     * Real-time scheduler for the empty-server idle reset, run off the game tick
     * loop so it still fires after the server pauses ticking when empty. A single
     * daemon thread; the armed task is held in {@link #pendingReset}.
     */
    private final java.util.concurrent.ScheduledExecutorService idleResetScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DeathSwap-idle-reset");
                t.setDaemon(true);
                return t;
            });
    /** The armed idle-reset task, or null when none is pending. Guarded by {@code this}. */
    private java.util.concurrent.ScheduledFuture<?> pendingReset;
    /**
     * Set once the idle timer commits to a reset: the server has been told to shut
     * down, and the {@code SERVER_STOPPED} hook should free the region files and
     * change the seed once the world is closed. Volatile because it's written from
     * the server thread and read by the shutdown hook.
     */
    private volatile boolean resetPending;
    /** Cached dry lobby column near the origin, resolved on first use. */
    private net.minecraft.core.BlockPos hubSpawn;

    public GameManager() {
        this.items = new ItemManager(this);
    }

    // ---- lifecycle ----

    public void onServerStarted(MinecraftServer server) {
        this.server = server;
        SettingsStore.load(settings);
        applyGameRules();
        resetAndFreezeTimeAndWeather();
        winsStore.load();
        items.registerAll();
        scoreboard.init(server, nl());
        scoreboard.startHub(server, nl());
    }

    /** Persist the current game settings so they survive a server restart. */
    public void persistSettings() {
        SettingsStore.save(settings);
    }

    /**
     * Reset the keepInventory and naturalRegen gamerules to the current settings.
     * Called on server start and whenever a game starts or ends, so the world's
     * rules always reflect the operator's configured DeathSwap settings.
     */
    private void applyGameRules() {
        server.getGameRules().set(GameRules.KEEP_INVENTORY, settings.keepInventory, server);
        server.getGameRules().set(GameRules.NATURAL_HEALTH_REGENERATION, settings.naturalRegen, server);
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

    /**
     * Advance the state machine to {@code next}.  Crashes the server on an
     * illegal edge — a bug that must never reach production.
     */
    private void transitionTo(GamePhase next) {
        java.util.EnumSet<GamePhase> allowed = VALID_TRANSITIONS.get(phase);
        if (allowed == null || !allowed.contains(next)) {
            throw new IllegalStateException("Illegal phase transition: " + phase + " → " + next);
        }
        DeathSwapMod.LOGGER.info("Phase: {} → {}", phase, next);
        phase = next;
    }

    /** True while the game is operator-paused (read by the damage event and mixins). */
    public boolean isPaused() {
        return paused;
    }

    public PlayerData data(ServerPlayer player) {
        playerNames.put(player.getUUID(), player.getScoreboardName());
        return playerData.computeIfAbsent(player.getUUID(), uuid -> {
            PlayerData data = new PlayerData(uuid);
            data.wins = winsStore.get(uuid); // seed lifetime wins from disk
            return data;
        });
    }

    private boolean nl() {
        return settings.isDutch() ^ languageToggledByItem;
    }

    public PlayerData dataIfPresent(UUID uuid) {
        return playerData.get(uuid);
    }

    // ---- player connection ----

    public void onPlayerJoin(ServerPlayer player) {
        // Someone is online again — call off any pending empty-server reset.
        cancelIdleReset();
        if (phase != GamePhase.RUNNING && phase != GamePhase.ENDING) {
            sendToHub(player);
            updateHubScoreboard(); // show their win tally on the lobby HUD
            return;
        }
        // PlayerData survives disconnects (see onPlayerLeave), so an existing entry
        // means this is someone reconnecting to a game they were already part of —
        // not a fresh late joiner.
        PlayerData existing = dataIfPresent(player.getUUID());
        if (existing != null && (existing.playing || existing.eliminated)) {
            // Reconnecting participant: drop them straight back into the game if they
            // were still alive, otherwise keep them spectating as they already were.
            player.setGameMode(existing.playing && !existing.eliminated
                    ? GameType.SURVIVAL
                    : GameType.SPECTATOR);
            return;
        }
        // Genuine late joiner: spectate the ongoing game (extra/make_newbie_spec.mcfunction).
        PlayerData data = data(player);
        data.playing = false;
        player.setGameMode(GameType.SPECTATOR);
        broadcast(Messages.joinedMidGame(nl(), player.getDisplayName()));
        Mc.titleRaw(player, Messages.spectateTitle(nl()), Messages.spectateSubtitle(nl()));
    }

    public void onPlayerLeave(ServerPlayer player) {
        if (phase == GamePhase.RUNNING) {
            checkWinCondition();
        }
        // A player just left: start the empty-server idle clock. The timer
        // re-checks that the server is actually empty (and in the hub) before it
        // commits, so arming it while others are still online is harmless.
        armIdleReset();
    }

    private void sendToHub(ServerPlayer player) {
        PlayerData data = data(player);
        data.playing = true;
        data.eliminated = false;
        data.winner = false;
        player.setGameMode(GameType.ADVENTURE);
        effects.clearAll(player);
        resetPlayerStats(player);
        Mc.infiniteEffect(player, MobEffects.SATURATION, 254);
        ItemStack mace = new ItemStack(net.minecraft.world.item.Items.MACE, 1);
        mace.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        // Use setItem directly so the items land in known slots without going
        // through the add→drop fallback that the PlayerDropMixin now intercepts.
        player.getInventory().setItem(0, mace);
        player.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.WIND_CHARGE, 16));
        teleportToWorldSpawn(player);
    }

    /**
     * Wipe everything that an item could have changed and that doesn't belong in
     * a fresh game/lobby: max health (items 68/69), current health, food &
     * saturation, fire/fall, and the inventory + ender chest. Attribute-based
     * effects clean themselves up via {@code effects.clearAll}; max health is a
     * permanent change, so it must be reset here.
     */
    private void resetPlayerStats(ServerPlayer player) {
        var maxHp = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.setBaseValue(20.0);
        }
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
        player.totalExperience = 0;
        player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        player.clearFire();
        player.fallDistance = 0.0f;
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
    }

    // ---- main tick ----

    public void tick() {
        if (server == null) {
            return;
        }
        if (paused) {
            tickPaused();
            return;
        }
        runScheduled();
        // Pre-generate spread destinations whenever a game isn't in progress — at
        // server startup and any time we're not running or ending — so the cache is
        // filling during all the idle ticks before the next game starts.
        if (phase != GamePhase.RUNNING && phase != GamePhase.ENDING) {
            tickChunkCache();
        }
        switch (phase) {
            case RUNNING -> tickRunning();
            case ENDING -> tickEnding();
            case HUB -> tickHub();
        }
        if (server.getTickCount() % 20 == 0) {
            updateTabListFooter();
        }
    }

    private void tickHub() {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int have = player.getInventory().countItem(net.minecraft.world.item.Items.WIND_CHARGE);
            if (have < 16) {
                Mc.give(player, net.minecraft.world.item.Items.WIND_CHARGE, 16 - have);
            }
            if (player.getInventory().countItem(net.minecraft.world.item.Items.MACE) == 0) {
                ItemStack mace = new ItemStack(net.minecraft.world.item.Items.MACE, 1);
                mace.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
                Mc.giveStack(player, mace);
            }
        }
    }

    /**
     * Idle work for when no game is running: use the spare ticks to pre-generate
     * far-away spread destinations for the next game's start, and report the
     * cache's progress to chat whenever it changes (debug visibility).
     */
    private void tickChunkCache() {
        chunkCache.tick(server.overworld(), random, SPREAD_MIN, SPREAD_MAX);

        int ready = chunkCache.readyCount();
        int pending = chunkCache.pendingChunkCount();
        if (ready == lastCacheReadyLogged && pending == lastCachePendingLogged) {
            return; // nothing changed since the last line; don't spam chat
        }
        lastCacheReadyLogged = ready;
        lastCachePendingLogged = pending;
        String status = "Chunk cache: " + ready + "/" + chunkCache.capacity()
                + " ready" + (pending > 0 ? " (building, " + pending + " chunks left)" : "");
        // Console log so progress is visible from server startup, before any player
        // is online to receive the chat broadcast.
        DeathSwapMod.LOGGER.info(status);
        Mc.broadcast(server, "[DeathSwap] " + status, ChatFormatting.DARK_GRAY);
    }

    /**
     * Arm the idle-reset timer: after {@link #IDLE_RESET_SECONDS} of real time the
     * server is checked and, if still empty and idling in the hub, shut down for a
     * world reset. Called whenever a player disconnects; the timer only ever runs
     * after someone has been online, so a server nobody joins never resets.
     *
     * <p>Cancels any previously-armed timer first, so the delay always counts from
     * the most recent disconnect, and is a no-op once a reset is already committed.
     */
    private synchronized void armIdleReset() {
        if (resetPending || server == null) {
            return;
        }
        cancelIdleReset();
        pendingReset = idleResetScheduler.schedule(this::onIdleResetTimeout,
                IDLE_RESET_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Cancel any armed idle-reset timer (e.g. a player reconnected). */
    private synchronized void cancelIdleReset() {
        if (pendingReset != null) {
            pendingReset.cancel(false);
            pendingReset = null;
        }
    }

    /**
     * Idle timer elapsed (on the scheduler thread). Hop onto the server thread to
     * re-check state with correct visibility — and because that task queue is still
     * drained every tick even while the server is paused empty — then, if the
     * server really is still empty and in the hub, begin a clean shutdown for the
     * reset. The region-file deletion and seed change happen once the world is
     * saved and closed, in the {@code SERVER_STOPPED} hook (see {@link #resetPending()}
     * / {@link WorldReset}); an external restart wrapper then brings the server back
     * up on the new seed.
     */
    private void onIdleResetTimeout() {
        MinecraftServer s = server;
        if (s == null) {
            return;
        }
        s.execute(() -> {
            if (resetPending || !s.getPlayerList().getPlayers().isEmpty() || phase != GamePhase.HUB) {
                return;
            }
            resetPending = true;
            DeathSwapMod.LOGGER.info(
                    "Server empty for {}s — shutting down to free region files and change the seed.",
                    IDLE_RESET_SECONDS);
            s.halt(false);
        });
    }

    /**
     * Whether shutdown was initiated for an automatic world reset, meaning the
     * {@code SERVER_STOPPED} hook should run {@link WorldReset} once the world is
     * closed.
     */
    public boolean resetPending() {
        return resetPending;
    }

    /**
     * Trigger the world reset on demand (the {@code /deathswap resetworld} command),
     * shutting down now instead of waiting for the empty-server idle timer. Takes
     * the same path as {@link #onIdleResetTimeout()}: flag the reset and halt, so the
     * {@code SERVER_STOPPED} hook frees the region files and re-rolls the seed once
     * the world is closed, and the restart wrapper boots back up on the new seed.
     *
     * <p>Refused while a game is running (so an operator can't wipe the world out
     * from under an active round) and when a reset is already pending. Returns true
     * if the shutdown was initiated. Must be called on the server thread.
     *
     * @param reason the disconnect message shown to players as they're kicked
     */
    public boolean triggerWorldReset(Component reason) {
        if (server == null || resetPending || phase != GamePhase.HUB) {
            return false;
        }
        // Call off any armed idle timer; we're shutting down right now.
        cancelIdleReset();
        resetPending = true;
        DeathSwapMod.LOGGER.info(
                "World reset requested — shutting down to free region files and change the seed.");
        // Kick everyone with our own reason first; otherwise the halt below
        // disconnects them with the generic "Server closed" message.
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            player.connection.disconnect(reason);
        }
        server.halt(false);
        return true;
    }

    private void tickRunning() {
        if (freezeTicksRemaining > 0) {
            tickFreeze();
            return;
        }
        gameTicksElapsed++;
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

        tickGravelTowers();
        tickBuildJobs();

        // Item offer clock. The datapack hands items to ONE player per interval,
        // round-robin, not to everyone at once.
        if (--itemTicksRemaining <= 0) {
            items.offerNext();
            itemTicksRemaining = itemOfferIntervalTicks();
        }

        // Swap clock + warnings.
        tickSwapClock();

        checkWinCondition();
    }

    /**
     * Pre-game freeze: hold every player at their spread location with no velocity
     * so they can't walk, fall or be knocked away while blinded. When the window
     * elapses, hand control back and start the swap/item clocks.
     */
    private void tickFreeze() {
        for (ServerPlayer player : alivePlayers()) {
            PlayerData data = data(player);
            if (data.spawnPos == null) {
                continue;
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            Vec3 spawnVec = new Vec3(data.spawnPos.getX() + 0.5, data.spawnPos.getY(), data.spawnPos.getZ() + 0.5);
            if (!player.position().closerThan(spawnVec, 0.1)) {
                Mc.teleport(player, spawnVec.x, spawnVec.y, spawnVec.z);
            }
        }
        if (--freezeTicksRemaining <= 0) {
            startClocksAfterFreeze();
        }
    }

    /** Start the swap and item clocks once the pre-game freeze has elapsed. */
    private void startClocksAfterFreeze() {
        // First swap uses the configured opening delay, regardless of cycle settings.
        swapTicksRemaining = settings.firstSwapSeconds * 20;
        lastWarnSecondAnnounced = -1;
        itemTicksRemaining = 20 * 46; // first items after ~46s, per datapack
    }

    private void tickSwapClock() {
        int secondsLeft = (swapTicksRemaining + 19) / 20;

        // Persistent action-bar countdown (above the hotbar), shown when the swap
        // timer setting is on. The action bar fades after ~3s, so refresh it each second.
        if (settings.showSwapTimer && swapTicksRemaining % 20 == 0) {
            showSwapCountdown(secondsLeft);
        }

        // Announce on the datapack's cumulative warning ladder (clock.mcfunction).
        if (secondsLeft != lastWarnSecondAnnounced
                && shouldWarnAt(secondsLeft, settings.swapWarning.level())) {
            announceSwapWarning(secondsLeft);
            lastWarnSecondAnnounced = secondsLeft;
        }

        if (--swapTicksRemaining <= 0) {
            doSwap();
            resetSwapClock();
        }
    }

    private static final java.time.ZoneId TZ_DETROIT = java.time.ZoneId.of("America/Detroit");
    private static final java.time.ZoneId TZ_LA      = java.time.ZoneId.of("America/Los_Angeles");
    private static final java.time.ZoneId TZ_TAIWAN  = java.time.ZoneId.of("Asia/Taipei");
    private static final java.time.format.DateTimeFormatter CLOCK_FMT =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a");

    private void updateTabListFooter() {
        String detroit = java.time.ZonedDateTime.now(TZ_DETROIT).format(CLOCK_FMT);
        String la      = java.time.ZonedDateTime.now(TZ_LA).format(CLOCK_FMT);
        String taiwan  = java.time.ZonedDateTime.now(TZ_TAIWAN).format(CLOCK_FMT);
        String clocks  = "Detroit " + detroit + "  |  LA " + la + "  |  Taiwan " + taiwan;

        Component footer;
        if (phase == GamePhase.RUNNING) {
            String gameTime = formatClock(gameTicksElapsed / 20);
            footer = Component.literal(clocks).withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("\nGame Duration: " + gameTime).withStyle(ChatFormatting.DARK_GREEN));
        } else {
            footer = Component.literal(clocks).withStyle(ChatFormatting.GREEN);
        }

        ClientboundTabListPacket packet = new ClientboundTabListPacket(Component.empty(), footer);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    private void showSwapCountdown(int secondsLeft) {
        int mins = secondsLeft / 60;
        int secs = secondsLeft % 60;
        String time = mins > 0
                ? String.format("%d:%02d", mins, secs)
                : secs + "s";
        ChatFormatting color = secondsLeft <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW;
        // Show to everyone online (players and spectators alike); this only runs
        // during the RUNNING phase, so there are no lobby players to confuse.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Mc.actionBar(player, "Swap in " + time, color);
        }
    }

    /**
     * The datapack's cumulative warning ladder (clock.mcfunction): a higher
     * {@code warnLvl} announces its own threshold <em>and</em> all lower ones. So
     * "1 minute" also shows the 30s, 10s and 5..1 countdowns.
     */
    private boolean shouldWarnAt(int secondsLeft, int warnLvl) {
        return switch (secondsLeft) {
            case 60 -> warnLvl >= 4;
            case 30 -> warnLvl >= 3;
            case 10 -> warnLvl >= 2;
            case 5, 4, 3, 2, 1 -> warnLvl >= 1;
            default -> false;
        };
    }

    private void announceSwapWarning(int secondsLeft) {
        // clock.mcfunction: ">> Swapping <<" gold title + countdown subtitle, plus
        // playsound minecraft:block.anvil.land master @s ~ ~ ~ 9 0 (volume 9, pitch 0).
        for (ServerPlayer player : alivePlayers()) {
            Mc.titleRaw(player, Messages.swappingTitle(nl()), Messages.swappingSubtitle(nl(), secondsLeft));
            Mc.playSound(player, SoundEvents.ANVIL_LAND, 9.0f, 0.0f);
        }
    }

    private void tickEnding() {
        if (--endingTicksRemaining <= 0) {
            enterHub();
        }
    }

    // ---- pause / unpause ----

    /**
     * Operator pause: completely freeze a running game. Freezes the world via the
     * vanilla tick-rate manager (the engine behind {@code /tick freeze}: mobs, block
     * entities, weather, random/scheduled/fluid block updates), stops every game
     * clock by short-circuiting {@link #tick()}, and holds players in place. Player
     * input, damage and per-player effect ticking are frozen by the damage event and
     * the {@code ServerPlayer.tick}/container mixins, which all read {@link #isPaused()}.
     *
     * <p>Only valid for a running game; refused from the hub/ending or when already
     * paused. Returns true on success.
     */
    public boolean pauseGame(String pauserName) {
        if (phase != GamePhase.RUNNING || paused) {
            return false;
        }
        paused = true;
        pausedTicks = 0;
        pausedByName = pauserName;
        server.tickRateManager().setFrozen(true);
        // Snapshot where everyone is standing so tickPaused() can hold them there.
        pausePositions.clear();
        for (ServerPlayer player : alivePlayers()) {
            pausePositions.put(player.getUUID(), player.position());
        }
        broadcast(">> Game paused by " + pauserName + ". <<", ChatFormatting.YELLOW);
        showPauseOverlay();
        return true;
    }

    /** Resume a paused game. Refused when the game isn't paused. Returns true on success. */
    public boolean unpauseGame() {
        if (!paused) {
            return false;
        }
        paused = false;
        pausePositions.clear();
        server.tickRateManager().setFrozen(false);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Mc.clearTitles(player);
        }
        broadcast(">> Game resumed. <<", ChatFormatting.GREEN);
        return true;
    }

    /**
     * Paused-state tick: hold every player at the spot they paused on and keep the
     * "PAUSED m:ss" overlay refreshed. The world is already frozen by the tick-rate
     * manager; players aren't (vanilla excludes them), so we zero their velocity and
     * snap them back if they drift, mirroring the pre-game {@link #tickFreeze()}.
     */
    private void tickPaused() {
        pausedTicks++;
        for (ServerPlayer player : alivePlayers()) {
            Vec3 held = pausePositions.get(player.getUUID());
            if (held == null) {
                continue;
            }
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0f;
            if (!player.position().closerThan(held, 0.1)) {
                Mc.teleport(player, held.x, held.y, held.z);
            }
        }
        // Titles fade after their stay time, so re-send the overlay each second with
        // the updated elapsed clock.
        // if (pausedTicks % 20 == 0) {
            showPauseOverlay();
            updateTabListFooter();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (var effect : player.getActiveEffects()) {
                    player.connection.send(new ClientboundUpdateMobEffectPacket(player.getId(), effect, false));
                }
            }
        // }
    }

    /** Show the centered "Paused by <name> - m:ss" title to everyone online. */
    private void showPauseOverlay() {
        Component title = Component.literal("Paused by " + pausedByName + " - " + formatClock(pausedTicks / 20))
                .withStyle(ChatFormatting.RED);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Long stay so the title persists between the per-second refreshes.
            Mc.titleTimes(player, 0, 40, 0);
            Mc.titleOnly(player, title);
        }
    }

    /** Format whole seconds as {@code m:ss} (or {@code h:mm:ss} past an hour). */
    private static String formatClock(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                : String.format("%d:%02d", minutes, seconds);
    }
}
