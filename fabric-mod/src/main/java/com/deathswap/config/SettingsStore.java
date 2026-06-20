package com.deathswap.config;

import com.deathswap.DeathSwapMod;
import com.deathswap.game.GameSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the operator-configured {@link GameSettings} (lives, swap interval,
 * pvp, ...) to {@code config/deathswap-settings.json} so they survive a server
 * restart. The in-game {@code language} is deliberately excluded: it is a
 * per-game toggle that resets to English in the hub (see GameManager), not a
 * persistent rule, so it is marked {@code transient} and never written.
 */
public final class SettingsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SettingsStore() {
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("deathswap-settings.json");
    }

    /**
     * Load saved settings into {@code target}, leaving any field absent from the
     * file at its current (default) value. Writes a fresh file with defaults if
     * none exists yet.
     *
     * @throws IllegalStateException if the file holds out-of-range values
     *         (see {@link GameSettings#validate()}); parse/IO errors fall back
     *         to defaults instead.
     */
    public static void load(GameSettings target) {
        Path path = path();
        if (!Files.exists(path)) {
            save(target);
            DeathSwapMod.LOGGER.info("Wrote default game settings to {}", path);
            return;
        }
        GameSettings saved;
        try {
            saved = GSON.fromJson(Files.readString(path), GameSettings.class);
        } catch (IOException | RuntimeException e) {
            DeathSwapMod.LOGGER.warn("Failed to read game settings {} — using defaults", path, e);
            return;
        }
        if (saved != null) {
            saved.validate();
            target.maxLives = saved.maxLives;
            target.swapIntervalSeconds = saved.swapIntervalSeconds;
            target.firstSwapSeconds = saved.firstSwapSeconds;
            target.randomCycle = saved.randomCycle;
            target.pvp = saved.pvp;
            target.hunger = saved.hunger;
            target.showSwapTimer = saved.showSwapTimer;
            target.startWithBasicTools = saved.startWithBasicTools;
            target.naturalRegen = saved.naturalRegen;
            if (saved.swapWarning != null) {
                target.swapWarning = saved.swapWarning;
            }
        }
    }

    /** Write the current settings to disk. Safe to call after every change. */
    public static void save(GameSettings settings) {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(settings));
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Failed to write game settings {}", path, e);
        }
    }
}
