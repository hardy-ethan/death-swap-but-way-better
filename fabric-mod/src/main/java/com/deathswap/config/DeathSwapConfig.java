package com.deathswap.config;

import com.deathswap.DeathSwapMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mod configuration loaded from {@code config/deathswap.json}. Currently holds
 * the Discord webhook used by {@code /deathswap report} to forward reports.
 * The file is created with defaults on first run if it does not exist.
 */
public final class DeathSwapConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Discord webhook URL reports are POSTed to. Empty disables reporting. */
    private static String discordWebhookUrl = "";

    private DeathSwapConfig() {
    }

    public static String discordWebhookUrl() {
        return discordWebhookUrl;
    }

    /** Load (or create) the config file. Safe to call once at mod init. */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("deathswap.json");
        if (!Files.exists(path)) {
            save(path);
            DeathSwapMod.LOGGER.info("Wrote default config to {}", path);
            return;
        }
        try {
            String json = Files.readString(path);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root != null && root.has("discordWebhookUrl") && root.get("discordWebhookUrl").isJsonPrimitive()) {
                discordWebhookUrl = root.get("discordWebhookUrl").getAsString();
            }
        } catch (IOException | RuntimeException e) {
            DeathSwapMod.LOGGER.warn("Failed to read config {} — using defaults", path, e);
        }
    }

    private static void save(Path path) {
        JsonObject root = new JsonObject();
        root.addProperty("discordWebhookUrl", discordWebhookUrl);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Failed to write config {}", path, e);
        }
    }
}
