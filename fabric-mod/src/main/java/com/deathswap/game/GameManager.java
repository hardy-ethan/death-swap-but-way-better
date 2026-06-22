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
    private final EffectManager effects = new EffectManager();
    private final ScoreboardDisplay scoreboard = new ScoreboardDisplay();
    /** Lifetime per-player win tallies, persisted across server restarts. */
    private final WinsStore winsStore = new WinsStore();
    private final ItemManager items;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
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
        this.phase = GamePhase.HUB;
        SettingsStore.load(settings);
        applyGameRules();
        winsStore.load();
        items.registerAll();
        // Stand up the hub's wins HUD now; per-player rows are pushed as people join.
        scoreboard.startHub(server, zh());
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

    public PlayerData data(ServerPlayer player) {
        return playerData.computeIfAbsent(player.getUUID(), uuid -> {
            PlayerData data = new PlayerData(uuid);
            data.wins = winsStore.get(uuid); // seed lifetime wins from disk
            return data;
        });
    }

    /** True when the game language is Chinese ({@code Lang Core} 2). */
    private boolean zh() {
        return settings.isChinese();
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
        broadcast(Messages.joinedMidGame(zh(), player.getDisplayName()));
        Mc.titleRaw(player, Messages.spectateTitle(zh()), Messages.spectateSubtitle(zh()));
    }

    public void onPlayerLeave(ServerPlayer player) {
        // Keep their PlayerData so wins persist if they reconnect this session.
        if (phase == GamePhase.RUNNING) {
            scoreboard.removePlayer(player);
            checkWinCondition();
        } else {
            // In the hub, drop their row from the wins HUD as they leave.
            scoreboard.removePlayer(player);
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
            int totalSeconds = gameTicksElapsed / 20;
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            int seconds = totalSeconds % 60;
            String gameTime = hours > 0
                    ? String.format("%d:%02d:%02d", hours, minutes, seconds)
                    : String.format("%d:%02d", minutes, seconds);
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
            Mc.titleRaw(player, Messages.swappingTitle(zh()), Messages.swappingSubtitle(zh(), secondsLeft));
            Mc.playSound(player, SoundEvents.ANVIL_LAND, 9.0f, 0.0f);
        }
    }

    private void tickEnding() {
        if (--endingTicksRemaining <= 0) {
            returnEveryoneToHub();
        }
    }

    // ---- phase transitions ----

    /** Begin a game with everyone currently in the hub. */
    public boolean startGame() {
        settings.validate();
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
        applyGameRules();

        startingPlayerCount = participants.size();

        // Start snapshotting block changes now, before any game-driven world edits
        // (gravel towers, item builds) or player mining, so the whole game can be
        // rolled back to this point when it ends.
        worldRollback.begin();

        // Every round starts with a clean advancement book for everyone.
        clearAllAdvancements();

        Mc.runServer(server, "time set noon");

        for (ServerPlayer player : participants) {
            PlayerData data = data(player);
            data.resetForNewGame(settings.maxLives);
            player.setGameMode(GameType.SURVIVAL);
            // Full reset so nothing leaks in from a previous game: clear any running
            // effects (the mod's custom ones plus vanilla mob effects, including the
            // hub's infinite Regeneration), then reset max health/health/food/
            // saturation and wipe the inventory + ender chest before the starter kit
            // and spread.
            effects.clearAll(player);
            player.removeAllEffects();
            resetPlayerStats(player);
            if (settings.startWithBasicTools) {
                giveStarterKit(player);
            }
            // Spread far away and set the player's spawn at the destination, so a
            // death/relog returns them there rather than the world origin.
            spreadFarAway(player, true);
            // warping_all.mcfunction: ">> Spreading players... <<" action bar.
            Mc.actionbar(player, Messages.spreadingActionbar(zh()));
            // Blind everyone for the pre-game freeze window; tickFreeze() holds
            // them in place for the same duration before the swap clock begins.
            Mc.effect(player, MobEffects.BLINDNESS, FREEZE_TICKS / 20, 0);
        }

        // Assign permanent slot numbers after the per-player reset (which zeroes them).
        assignPermanentNumbers(participants);

        phase = GamePhase.RUNNING;
        gameTicksElapsed = 0;
        scoreboard.start(server, zh());
        updateSidebar();
        // Hold everyone blind and motionless first; the swap and item clocks only
        // start once the freeze ends (see tickFreeze / startClocksAfterFreeze).
        freezeTicksRemaining = FREEZE_TICKS;

        // game_start.mcfunction runs ~5s after the spread: the title card, the raid
        // horn (volume 99, pitch 1) and the map credits.
        schedule(20 * 5, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Mc.titleRaw(p, Messages.startTitle(zh()), Messages.startSubtitle(zh()));
                Mc.playSound(p, SoundEvents.RAID_HORN, 99.0f, 1.0f);
                Mc.msg(p, Messages.mapCredit(zh()));
                Mc.msg(p, Messages.additionalCredit(zh()));
            }
        });

        // Enable the emergency teleport after 32 seconds (start_tp_away.mcfunction;
        // its protip tellraw is commented out in the datapack, so no chat line).
        schedule(20 * 32, () -> {
            for (ServerPlayer p : alivePlayers()) {
                data(p).canTpAway = true;
            }
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
            int destIndex = (i + 1) % alive.size();
            Location dest = locations.get(destIndex);
            dest.apply(player);
            // Disable swap fall damage exactly like the datapack (gamerule fall_damage
            // false for a tick) — reset accumulated fall, with no extra status effects.
            player.fallDistance = 0.0f;
            // swap.mcfunction: clear the title, show the gold ">> Swapped! <<" action
            // bar, then the green "You warped to: <name>" line (Chinese adds a 2nd line).
            // There is no swap sound in the datapack. Use a real `title clear` so the
            // "Swap in 1" countdown subtitle (with its long stay time) vanishes at once
            // instead of lingering on screen.
            Mc.clearTitles(player);
            Mc.actionbar(player, Messages.swapActionbar(zh()));
            ServerPlayer warpedToPlayer = alive.get(destIndex);
            Mc.msg(player, Messages.warpedTo(zh(), warpedToPlayer.getDisplayName()));
            if (zh()) {
                Mc.msg(player, Messages.warpedToChinese());
            }
            data(player).canTpAway = true; // restore the emergency teleport each cycle
        }
    }

    /** Item 5: swap everyone almost immediately without resetting the cycle timer. */
    public void instantSwap() {
        schedule(4, this::doSwap);
    }

    /** Item 47: cut 30s off the current cycle and shorten the configured interval. */
    public void shortenSwapTimer(int seconds) {
        swapTicksRemaining = Math.max(20, swapTicksRemaining - seconds * 20);
        settings.swapIntervalSeconds = Math.max(30, settings.swapIntervalSeconds - seconds);
    }

    /** Item 66: toggle the overworld between midnight and noon. */
    public void toggleTime() {
        Mc.runServer(server, isNight() ? "time set noon" : "time set midnight");
    }

    public boolean isNight() {
        long time = server.overworld().getOverworldClockTime() % 24000;
        return time >= 13000 && time < 23000;
    }

    /**
     * Item 72: switch the game language between English and Chinese, reproducing
     * the ds:settings/lang_chinese / lang_english confirmation (title, subtitle,
     * ui.button.click sound, banner line and the Chinese translator note).
     */
    public void toggleLanguage() {
        boolean toChinese = settings.language == GameSettings.Language.ENGLISH;
        settings.language = toChinese ? GameSettings.Language.CHINESE : GameSettings.Language.ENGLISH;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Mc.titleRaw(p, Messages.langTitle(toChinese), Messages.langSubtitle(toChinese));
            Mc.playSound(p, SoundEvents.UI_BUTTON_CLICK, 9.0f, 1.0f);
            Mc.msg(p, Messages.langBanner(toChinese));
            if (toChinese) {
                Mc.msg(p, Messages.langTranslatorNote());
            }
        }
    }

    // ---- death / elimination (game/player_died, player_eliminated) ----

    /**
     * Called from the death mixin/event. Returns true if the death should be
     * allowed to proceed, false to cancel it (the player survives).
     */
    public boolean onAllowDeath(ServerPlayer player, DamageSource source) {
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

        // If the player is holding a Totem of Undying (and the damage isn't a kind
        // that bypasses it), let vanilla's death handling run: returning true keeps
        // the isDeadOrDying() branch alive in hurtServer, so checkTotemDeathProtection
        // pops the totem, restores 1 health and applies its effects. No life is lost.
        // We must not handle the death ourselves here, or the totem would be skipped.
        if (hasTotem(player, source)) {
            return true;
        }

        data.lives--;
        // no_death immunity: the datapack adds 5/tick and re-allows death at 800,
        // i.e. 160 ticks = 8 seconds (not 40s).
        data.deathImmunityTicks = 160;

        // player_died.mcfunction (runs for every death, including the fatal one):
        // broadcast line, ">> YOU DIED! <<" title and the "-1 Life!" subtitle. The
        // broadcast carries the vanilla death message so it states how they died.
        broadcast(Messages.diedBroadcast(zh(), source.getLocalizedDeathMessage(player)));
        Mc.titleRaw(player, Messages.diedTitle(zh()), Messages.diedSubtitle(zh()));

        if (data.lives <= 0) {
            eliminate(player);
        } else {
            survive(player);
        }
        updateSidebar();
        return false; // we handle this death ourselves
    }

    /**
     * Mirror vanilla {@code LivingEntity.checkTotemDeathProtection}: a death is
     * deflected by a totem only when the damage doesn't bypass invulnerability and
     * the player holds an item carrying the {@code DEATH_PROTECTION} component in
     * either hand. We don't consume the item here; vanilla does that once we let
     * the death proceed.
     */
    private boolean hasTotem(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (player.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-supply the starter kit when an active participant respawns empty-handed,
     * so a death/relog that wiped their inventory doesn't leave them defenseless.
     * In hub phase, restore the full hub loadout (mace + wind charges).
     */
    public void onPlayerRespawn(ServerPlayer player) {
        if (phase == GamePhase.HUB) {
            sendToHub(player);
            return;
        }
        if (phase != GamePhase.RUNNING || !settings.startWithBasicTools) {
            return;
        }
        PlayerData data = data(player);
        if (data.playing && !data.eliminated && player.getInventory().isEmpty()) {
            giveStarterKit(player);
        }
    }

    private void survive(ServerPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.fallDistance = 0.0f;
        // We cancel vanilla death, so the inventory is never dropped automatically.
        // Honour the keepInventory gamerule ourselves: when it's off, drop everything
        // at the death location (before we teleport the player away).
        if (!server.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            dropInventory(player);
        }
        // A vanilla respawn clears all active effects; emulate that since the death
        // was cancelled.
        player.removeAllEffects();
        // Death returns the player to their initial spread location (their spawn
        // point), just as a vanilla death respawns them at the spawn point set in
        // game_start.mcfunction (spawnpoint @s ~ ~ ~). We emulate the death rather
        // than letting it run, so teleport them there ourselves.
        PlayerData data = data(player);
        if (data.spawnPos != null) {
            Mc.teleportTo(player, server.overworld(),
                    data.spawnPos.getX() + 0.5, data.spawnPos.getY(), data.spawnPos.getZ() + 0.5,
                    data.spawnYaw, player.getXRot());
        }
        // player_died.mcfunction: effect give @s minecraft:resistance 10 5.
        Mc.effect(player, MobEffects.RESISTANCE, 10, 5);
        // Respawn-fresh hunger.
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
    }

    /**
     * Drop every item in the player's inventory at their feet, then empty it. The
     * immovable barrier filler in the powerup slots is left untouched — it's UI
     * furniture, not loot, and must stay in place for the item system.
     */
    private void dropInventory(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !ItemManager.isLocked(stack)) {
                player.drop(stack, true, false);
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private void eliminate(ServerPlayer player) {
        PlayerData data = data(player);
        data.lives = 0;
        data.eliminated = true;
        data.playing = false;
        data.clearOffer();
        effects.clearAll(player);
        player.setGameMode(GameType.SPECTATOR);
        // player_eliminated.mcfunction: dragon growl (volume 9, pitch 1.2) and the
        // ">> ELIMINATED! <<" subtitle (the "YOU DIED" title from player_died stays).
        Mc.playSound(player, SoundEvents.ENDER_DRAGON_GROWL, 9.0f, 1.2f);
        Mc.subtitleRaw(player, Messages.eliminatedSubtitle(zh()));
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
            data.wins++; // scoreboard players add @s Wins 1
            winsStore.set(winner.getUUID(), data.wins); // persist across restarts

            // prep_winner.mcfunction: clear effects on everyone, then resistance 20 5
            // and saturation 20 5 for all; the winner gets glowing (12 1 after
            // winner.mcfunction overrides) and a totem of undying in the offhand.
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                effects.clearAll(p);
                Mc.effect(p, MobEffects.RESISTANCE, 20, 5);
                Mc.effect(p, MobEffects.SATURATION, 20, 5);
            }
            Mc.effect(winner, MobEffects.GLOWING, 12, 1);

            // winner.mcfunction: title times 0 140 5, the green win title/subtitle,
            // the dragon-death sound (volume 99) and the broadcast line.
            Component winnerName = winner.getDisplayName();
            broadcast(Messages.winnerBroadcast(zh(), winnerName));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Mc.titleTimes(p, 0, 140, 5);
                Mc.titleRaw(p, Messages.winnerTitle(zh(), winnerName), Messages.winnerSubtitle(zh()));
                Mc.playSound(p, SoundEvents.ENDER_DRAGON_DEATH, 99.0f, 1.0f);
            }
        } else {
            broadcast(">> The game ended in a draw. <<", ChatFormatting.YELLOW);
        }
    }

    /** Abort the current game (operator /deathswap stop) and reset to the lobby. */
    public void forceReturnToHub() {
        returnEveryoneToHub();
    }

    /**
     * Admin command: give one life to {@code target}, resurrecting
     * them if they were eliminated and rolling the game back from the ENDING phase
     * if their death was what triggered it. Returns false if there is no game to
     * act on (hub / already done).
     */
    public boolean addLife(ServerPlayer target) {
        if (phase != GamePhase.RUNNING && phase != GamePhase.ENDING) {
            return false;
        }
        PlayerData data = data(target);

        // If the game is in the ENDING phase, undo the winner declaration first so
        // the round can continue. This covers the case where this player's death (or
        // elimination) caused the win condition to fire.
        if (phase == GamePhase.ENDING) {
            undoWinnerDeclaration();
            phase = GamePhase.RUNNING;
            broadcast(">> Game rolled back — a life was granted. <<", ChatFormatting.YELLOW);
        }

        // Resurrect an eliminated player.
        if (data.eliminated) {
            data.eliminated = false;
            data.playing = true;
            data.deathImmunityTicks = 160;
            effects.clearAll(target);
            target.setGameMode(GameType.SURVIVAL);
            target.setHealth(target.getMaxHealth());
            target.getFoodData().setFoodLevel(20);
            target.getFoodData().setSaturation(5.0f);
            // Return them to their initial spread spawn, same as a normal death.
            if (data.spawnPos != null) {
                Mc.teleportTo(target, server.overworld(),
                        data.spawnPos.getX() + 0.5, data.spawnPos.getY(), data.spawnPos.getZ() + 0.5,
                        data.spawnYaw, target.getXRot());
            }
        }

        data.lives = Math.max(data.lives, 0) + 1;
        updateSidebar();

        broadcast(target.getDisplayName().getString() + " was granted a life by an admin.", ChatFormatting.AQUA);
        return true;
    }

    /**
     * Undo a winner declaration: strip the win credit, remove the winner's special
     * effects (glowing, totem, resistance/saturation), and restore the game-time
     * effects clock so play can resume.
     */
    private void undoWinnerDeclaration() {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerData d = data(p);
            if (d.winner) {
                d.winner = false;
                d.wins = Math.max(0, d.wins - 1);
                winsStore.set(p.getUUID(), d.wins);
                p.removeEffect(MobEffects.GLOWING);
            }
            p.removeEffect(MobEffects.RESISTANCE);
            p.removeEffect(MobEffects.SATURATION);
        }
    }

    private void returnEveryoneToHub() {
        phase = GamePhase.HUB;
        applyGameRules();
        // Swap the game's lives/health HUD back to the hub's wins tally.
        scoreboard.startHub(server, zh());
        // Don't discard the cache: a destination is removed from it the moment it's
        // handed out (ChunkCache.next), so anything still queued is unused and safe
        // to carry into the next game. The hub phase tops it back up from here.
        lastCacheReadyLogged = -1; // re-log current cache state as the hub resumes
        lastCachePendingLogged = -1;
        // Language (item 72) is a per-game state, not a persistent setting: reset it
        // so the next game starts in English unless item 72 is used again.
        settings.language = GameSettings.Language.ENGLISH;

        Mc.runServer(server, "time set noon");

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToHub(player);
            Mc.setSpawn(player, server.overworld(), hubSpawn, player.getYRot(), player.getXRot());
        }
        // Refresh the wins tally now everyone is back in the lobby (the winner's
        // count has gone up).
        updateHubScoreboard();
        // Wipe everyone's advancements so nothing earned during the round carries
        // into the lobby or the next game.
        clearAllAdvancements();
        // Now that everyone is safely back in the hub, undo every block change the
        // game made so the shared world is restored to how it looked before the round.
        int restored = worldRollback.rollback(server);
        if (restored > 0) {
            DeathSwapMod.LOGGER.info("Rolled back {} block change(s) from the last game.", restored);
        }
        // Clean up the End: remove everything except the Ender Dragon and End Crystals
        // so the dimension resets to a playable state for the next game.
        cleanUpEndEntities();
        broadcast(">> Back to the lobby. Run /deathswap start for another round. <<",
                ChatFormatting.AQUA);
    }

    // ---- helpers ----

    /**
     * Refresh the lives sidebar. Shows every player taking part in the current
     * game — those still alive and those already eliminated (at 0 lives) — but
     * not late-joining spectators.
     */
    private void updateSidebar() {
        List<ServerPlayer> participants = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerData data = data(player);
            if (data.playing || data.eliminated) {
                participants.add(player);
            }
        }
        scoreboard.updateLives(participants, p -> data(p).lives);
    }

    /**
     * Refresh the hub's wins HUD (sidebar + below-name) with every online
     * player's lifetime win count. Only meaningful while in the hub, where the
     * wins objectives are the displayed ones.
     */
    private void updateHubScoreboard() {
        scoreboard.updateWins(server.getPlayerList().getPlayers(), p -> data(p).wins);
    }

    /**
     * Wipe every player's advancements, online and offline, so each game starts
     * and ends with a clean advancement book and nothing carries over between
     * rounds. Online players are cleared through their live {@link PlayerAdvancements}
     * (the change is pushed to their client and persisted on logout); offline
     * players have their saved advancement files deleted from disk, since their
     * progress only lives there while they're away.
     */
    private void clearAllAdvancements() {
        var allAdvancements = server.getAdvancements().getAllAdvancements();
        java.util.Set<UUID> online = new java.util.HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            PlayerAdvancements progress = player.getAdvancements();
            for (AdvancementHolder holder : allAdvancements) {
                for (String criterion : holder.value().criteria().keySet()) {
                    progress.revoke(holder, criterion);
                }
            }
            // Push the revocations to the client now rather than waiting for the
            // next tick's flush, so the advancement screen updates immediately.
            progress.flushDirty(player, true);
        }

        // Offline players: their advancements exist only as <uuid>.json files on
        // disk. Delete those; skip online players, whose live data (handled above)
        // would otherwise just rewrite the file on their next logout.
        java.nio.file.Path dir = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR);
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        try (var files = java.nio.file.Files.newDirectoryStream(dir, "*.json")) {
            for (java.nio.file.Path file : files) {
                String fileName = file.getFileName().toString();
                UUID uuid;
                try {
                    uuid = UUID.fromString(fileName.substring(0, fileName.length() - ".json".length()));
                } catch (IllegalArgumentException notAPlayerFile) {
                    continue;
                }
                if (!online.contains(uuid)) {
                    java.nio.file.Files.deleteIfExists(file);
                }
            }
        } catch (java.io.IOException e) {
            DeathSwapMod.LOGGER.warn("Failed to clear offline player advancements", e);
        }
    }

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
        spreadFarAway(player, false);
    }

    /**
     * Scatter a player to a random far-away surface location. When
     * {@code setSpawn} is true the player's respawn point is moved to the
     * destination too — used for the initial spread and the "teleport really
     * far away" item, but not for momentary punishment teleports.
     */
    public void spreadFarAway(ServerPlayer player, boolean setSpawn) {
        ServerLevel level = server.overworld();
        // A destination pre-generated while no game was running when one is ready (so
        // the world-gen cost isn't paid here at game start), otherwise rolled live. The
        // cache handles the fallback internally, so this call always returns a valid
        // dry-land column regardless of whether the cache had anything.
        net.minecraft.core.BlockPos pos = chunkCache.next(level, random, SPREAD_MIN, SPREAD_MAX);
        Mc.teleportTo(player, level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        if (setSpawn) {
            // Give the player their own spawn point at the destination, so a
            // death/relog returns them here rather than at the world origin
            // (datapack: spawnpoint @s ~ ~ ~ in game_start.mcfunction).
            Mc.setSpawn(player, level, pos, player.getYRot(), player.getXRot());
            // Remember it so a death sends the player back to this initial spread
            // location, mirroring the vanilla respawn-at-spawn-point behaviour.
            PlayerData data = data(player);
            data.spawnPos = pos;
            data.spawnYaw = player.getYRot();
        }
    }

    private void teleportToWorldSpawn(ServerPlayer player) {
        // The lobby isn't a built map (unlike the datapack's superflat hub), so we
        // gather everyone at one dry surface column near the overworld origin.
        ServerLevel level = server.overworld();
        if (hubSpawn == null) {
            hubSpawn = buildHubPlatform(level);
        }
        Mc.teleportTo(player, level, hubSpawn.getX() + 0.5, hubSpawn.getY(), hubSpawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /**
     * Lay the lobby's gather point at the origin column. The lobby isn't a built
     * map, so we drop a small stone platform at the surface there — this keeps the
     * hub on solid ground even when the origin sits in an ocean, so players never
     * spawn bobbing in water.
     */
    private net.minecraft.core.BlockPos buildHubPlatform(ServerLevel level) {
        net.minecraft.core.BlockPos feet = surfaceColumn(level, 0, 0);
        for (int px = -20; px <= 20; px++) {
            for (int pz = -20; pz <= 20; pz++) {
                level.setBlockAndUpdate(feet.offset(px, -1, pz), Blocks.STONE.defaultBlockState());
            }
        }
        return feet;
    }

    /** Surface (feet) position of the column at the given x/z, generating the chunk first. */
    private net.minecraft.core.BlockPos surfaceColumn(ServerLevel level, int x, int z) {
        // Force the chunk to generate before sampling the heightmap; on an
        // ungenerated chunk getHeight() returns the world minimum (the void).
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new net.minecraft.core.BlockPos(x, y, z);
    }

    private void giveStarterKit(ServerPlayer player) {
        Mc.give(player, net.minecraft.world.item.Items.STONE_SWORD, 1);
        Mc.give(player, net.minecraft.world.item.Items.STONE_AXE, 1);
        Mc.give(player, net.minecraft.world.item.Items.STONE_PICKAXE, 1);
        Mc.give(player, net.minecraft.world.item.Items.STONE_SHOVEL, 1);
        Mc.give(player, net.minecraft.world.item.Items.CRAFTING_TABLE, 1);
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
            case 7 -> 6;
            case 8, 9 -> 5;
            default -> 4;
        };
        return seconds * 20;
    }

    public void broadcast(String text, ChatFormatting color) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Mc.msg(player, text, color);
        }
    }

    public void broadcast(Component component) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Mc.msg(player, component);
        }
    }

    private void cleanUpEndEntities() {
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        List<Entity> toDiscard = new ArrayList<>();
        for (Entity entity : end.getAllEntities()) {
            if (entity instanceof Player) continue;
            if (entity.getType() == EntityTypes.ENDER_DRAGON) continue;
            if (entity.getType() == EntityTypes.END_CRYSTAL) continue;
            toDiscard.add(entity);
        }
        for (Entity entity : toDiscard) {
            entity.discard();
        }
    }

    // ---- gravel tower growth (item 9: misc/gravel_up grows the column over time) ----

    /** Start a gravel column that grows upward to the build limit, like the datapack marker. */
    public void addGravelTower(ServerLevel level, int x, int baseY, int z) {
        gravelTowers.add(new GravelTower(level, x, baseY, z));
    }

    private void tickGravelTowers() {
        for (int i = gravelTowers.size() - 1; i >= 0; i--) {
            GravelTower g = gravelTowers.get(i);
            g.level.setBlockAndUpdate(new BlockPos(g.x, (int) Math.floor(g.y), g.z),
                    Blocks.GRAVEL.defaultBlockState());
            g.y += 0.5; // marker rises 0.5/tick in misc/gravel_up
            if (g.y >= 319) {
                gravelTowers.remove(i);
            }
        }
    }

    private static final class GravelTower {
        final ServerLevel level;
        final int x;
        final int z;
        double y;

        GravelTower(ServerLevel level, int x, int baseY, int z) {
            this.level = level;
            this.x = x;
            this.z = z;
            this.y = baseY;
        }
    }

    // ---- deferred world-build jobs (large exact builds spread across ticks) ----

    /** Register a build job; it is ticked each game tick and removed when it returns true. */
    public void addBuildJob(java.util.function.BooleanSupplier job) {
        buildJobs.add(job);
    }

    private void tickBuildJobs() {
        for (int i = buildJobs.size() - 1; i >= 0; i--) {
            if (buildJobs.get(i).getAsBoolean()) {
                buildJobs.remove(i);
            }
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
