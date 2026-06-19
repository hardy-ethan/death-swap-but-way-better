package com.deathswap.game;

import com.deathswap.effects.EffectManager;
import com.deathswap.items.ItemManager;
import com.deathswap.util.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
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
    private final ScoreboardDisplay scoreboard = new ScoreboardDisplay();
    private final ItemManager items;
    private final Map<UUID, PlayerData> playerData = new HashMap<>();
    private final List<Scheduled> scheduled = new ArrayList<>();
    private final List<GravelTower> gravelTowers = new ArrayList<>();
    private final List<java.util.function.BooleanSupplier> buildJobs = new ArrayList<>();
    private final java.util.Random random = new java.util.Random();

    private MinecraftServer server;
    private GamePhase phase = GamePhase.HUB;

    private int swapTicksRemaining;
    private int itemTicksRemaining;
    private int endingTicksRemaining;
    private int startingPlayerCount;
    /** Tracks which swap-warning thresholds have already fired this cycle. */
    private int lastWarnSecondAnnounced = -1;
    /** Whether the world was last set to night by item 66. */
    private boolean isNight = false;
    /** Cached dry lobby column near the origin, resolved on first use. */
    private net.minecraft.core.BlockPos hubSpawn;

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

    /** True when the game language is Chinese ({@code Lang Core} 2). */
    private boolean zh() {
        return settings.isChinese();
    }

    public PlayerData dataIfPresent(UUID uuid) {
        return playerData.get(uuid);
    }

    // ---- player connection ----

    public void onPlayerJoin(ServerPlayer player) {
        PlayerData data = data(player);
        if (phase == GamePhase.RUNNING || phase == GamePhase.ENDING) {
            // Late joiner: spectate the ongoing game (extra/make_newbie_spec.mcfunction).
            data.playing = false;
            player.setGameMode(GameType.SPECTATOR);
            broadcast(Messages.joinedMidGame(player.getDisplayName()));
            Mc.titleRaw(player, Messages.spectateTitle(), Messages.spectateSubtitle());
        } else {
            sendToHub(player);
        }
    }

    public void onPlayerLeave(ServerPlayer player) {
        // Keep their PlayerData so wins persist if they reconnect this session.
        if (phase == GamePhase.RUNNING) {
            scoreboard.removePlayer(player);
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

    private void showSwapCountdown(int secondsLeft) {
        int mins = secondsLeft / 60;
        int secs = secondsLeft % 60;
        String time = mins > 0
                ? String.format("%d:%02d", mins, secs)
                : secs + "s";
        ChatFormatting color = secondsLeft <= 5 ? ChatFormatting.RED : ChatFormatting.YELLOW;
        for (ServerPlayer player : alivePlayers()) {
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
            Mc.titleRaw(player, Messages.swappingTitle(), Messages.swappingSubtitle(secondsLeft));
            Mc.playSound(player, SoundEvents.ANVIL_LAND, 9.0f, 0.0f);
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
            player.getEnderChestInventory().clearContent(); // don't carry stashes between rounds
            if (settings.startWithBasicTools) {
                giveStarterKit(player);
            }
            // Spread far away and set the player's spawn at the destination, so a
            // death/relog returns them there rather than the world origin.
            spreadFarAway(player, true);
            // warping_all.mcfunction: ">> Spreading players... <<" action bar.
            Mc.actionbar(player, Messages.spreadingActionbar(zh()));
        }

        // Assign permanent slot numbers after the per-player reset (which zeroes them).
        assignPermanentNumbers(participants);

        phase = GamePhase.RUNNING;
        scoreboard.start(server);
        updateSidebar();
        // First swap is always 3 minutes, regardless of cycle settings.
        swapTicksRemaining = 180 * 20;
        lastWarnSecondAnnounced = -1;
        itemTicksRemaining = 20 * 46; // first items after ~46s, per datapack

        // game_start.mcfunction runs ~5s after the spread: the title card, the raid
        // horn (volume 99, pitch 1) and the map credits.
        schedule(20 * 5, () -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Mc.titleRaw(p, Messages.startTitle(), Messages.startSubtitle());
                Mc.playSound(p, SoundEvents.RAID_HORN, 99.0f, 1.0f);
                Mc.msg(p, Messages.mapCredit(zh()));
                Mc.msg(p, Messages.additionalCredit());
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
            // There is no swap sound in the datapack.
            Mc.titleRaw(player, Component.empty(), Component.empty());
            Mc.actionbar(player, Messages.swapActionbar(zh()));
            ServerPlayer warpedToPlayer = alive.get(destIndex);
            Mc.msg(player, Messages.warpedTo(warpedToPlayer.getDisplayName()));
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
        isNight = !isNight;
        Mc.runServer(server, isNight ? "time set midnight" : "time set noon");
    }

    public boolean isNight() {
        return isNight;
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
        // no_death immunity: the datapack adds 5/tick and re-allows death at 800,
        // i.e. 160 ticks = 8 seconds (not 40s).
        data.deathImmunityTicks = 160;

        // player_died.mcfunction (runs for every death, including the fatal one):
        // broadcast line, ">> YOU DIED! <<" title and the "-1 Life!" subtitle.
        broadcast(Messages.diedBroadcast(player.getDisplayName()));
        Mc.titleRaw(player, Messages.diedTitle(zh()), Messages.diedSubtitle());

        if (data.lives <= 0) {
            eliminate(player);
        } else {
            survive(player);
        }
        updateSidebar();
        return false; // we always handle death ourselves
    }

    /**
     * Re-supply the starter kit when an active participant respawns empty-handed,
     * so a death/relog that wiped their inventory doesn't leave them defenseless.
     */
    public void onPlayerRespawn(ServerPlayer player) {
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

            // prep_winner.mcfunction: clear effects on everyone, then resistance 20 5
            // and saturation 20 5 for all; the winner gets glowing (12 1 after
            // winner.mcfunction overrides) and a totem of undying in the offhand.
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                effects.clearAll(p);
                Mc.effect(p, MobEffects.RESISTANCE, 20, 5);
                Mc.effect(p, MobEffects.SATURATION, 20, 5);
            }
            Mc.effect(winner, MobEffects.GLOWING, 12, 1);
            winner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));

            // winner.mcfunction: title times 0 140 5, the green win title/subtitle,
            // the dragon-death sound (volume 99) and the broadcast line.
            Component winnerName = winner.getDisplayName();
            broadcast(Messages.winnerBroadcast(zh(), winnerName));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Mc.titleTimes(p, 0, 140, 5);
                Mc.titleRaw(p, Messages.winnerTitle(winnerName), Messages.winnerSubtitle(zh()));
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

    private void returnEveryoneToHub() {
        phase = GamePhase.HUB;
        scoreboard.stop();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendToHub(player);
        }
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
        int x = 0, z = 0, y = 0;
        // Re-roll the location until we find dry land, so players don't spawn in
        // the middle of an ocean/lake. Falls back to the last pick after a cap.
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = SPREAD_MIN + random.nextDouble() * (SPREAD_MAX - SPREAD_MIN);
            x = (int) (Math.cos(angle) * radius);
            z = (int) (Math.sin(angle) * radius);
            // Force the destination chunk to generate before sampling the heightmap.
            // Far-away chunks aren't loaded yet, and getHeight() on an ungenerated
            // chunk returns the world's minimum build height (the void), which would
            // drop the player into the void to their death.
            level.getChunk(x >> 4, z >> 4);
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            // The surface block sits just below the spawn height. If it (or the
            // block we'd stand in) holds a fluid, it's water/lava — try again.
            net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(x, y, z);
            if (level.getFluidState(feet.below()).isEmpty()
                    && level.getFluidState(feet).isEmpty()) {
                break;
            }
        }
        Mc.teleportTo(player, level, x + 0.5, y, z + 0.5, player.getYRot(), player.getXRot());
        if (setSpawn) {
            // Give the player their own spawn point at the destination, so a
            // death/relog returns them here rather than at the world origin
            // (datapack: spawnpoint @s ~ ~ ~ in game_start.mcfunction).
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
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
            hubSpawn = findDryHubColumn(level);
        }
        Mc.teleportTo(player, level, hubSpawn.getX() + 0.5, hubSpawn.getY(), hubSpawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    /**
     * Find a dry surface column near the origin for the lobby. Scans outward in a
     * square spiral so everyone gathers on land rather than bobbing in an ocean.
     * Falls back to the origin column if nothing dry is found within range.
     */
    private net.minecraft.core.BlockPos findDryHubColumn(ServerLevel level) {
        for (int radius = 0; radius <= 64; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 8) {
                for (int dz = -radius; dz <= radius; dz += 8) {
                    // Only inspect the ring at this radius; inner rings were checked already.
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    // Force the chunk to generate before sampling the heightmap; on an
                    // ungenerated chunk getHeight() returns the world minimum (the void).
                    level.getChunk(dx >> 4, dz >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
                    net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(dx, y, dz);
                    if (level.getFluidState(feet.below()).isEmpty()
                            && level.getFluidState(feet).isEmpty()) {
                        return feet;
                    }
                }
            }
        }
        level.getChunk(0, 0);
        return new net.minecraft.core.BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
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
