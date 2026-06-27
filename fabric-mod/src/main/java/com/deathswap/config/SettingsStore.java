package com.deathswap.config;

import com.deathswap.DeathSwapMod;
import com.deathswap.game.GameSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
     * Load saved settings into {@code target}. Writes a fresh file with defaults
     * if none exists yet. The file must be complete: every persisted field has to
     * be present and in range — a partial or out-of-range file is rejected rather
     * than silently merged with defaults.
     *
     * @throws IllegalStateException if the file is empty, missing a field, or
     *         holds out-of-range values (see {@link GameSettings#validate()}).
     *         An IO read failure falls back to defaults instead.
     */
    public static void load(GameSettings target) {
        Path path = path();
        if (!Files.exists(path)) {
            save(target);
            DeathSwapMod.LOGGER.info("Wrote default game settings to {}", path);
            return;
        }
        JsonObject json;
        try {
            json = GSON.fromJson(Files.readString(path), JsonObject.class);
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Failed to read game settings {} — using defaults", path, e);
            return;
        }
        requireAllFieldsPresent(json, path);

        GameSettings saved = GSON.fromJson(json, GameSettings.class);
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
        target.keepInventory = saved.keepInventory;
        target.swapWarning = saved.swapWarning;
    }

    /**
     * Verify the file contains an entry for every persisted {@link GameSettings}
     * field. Reflecting over the fields (skipping {@code static}/{@code transient},
     * exactly as Gson does) keeps this in sync with the class automatically.
     */
    private static void requireAllFieldsPresent(JsonObject json, Path path) {
        if (json == null) {
            throw new IllegalStateException("Settings file " + path + " is empty or not an object");
        }
        for (Field field : GameSettings.class.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
                continue;
            }
            if (!json.has(field.getName())) {
                throw new IllegalStateException(
                        "Settings file " + path + " is missing required field: " + field.getName());
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
