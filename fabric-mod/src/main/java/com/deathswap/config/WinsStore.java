package com.deathswap.config;

import com.deathswap.DeathSwapMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists each player's lifetime win tally (datapack {@code Wins}) to
 * {@code config/deathswap-wins.json}, keyed by player UUID, so the count shown
 * on the hub scoreboard and below players' names survives a server restart.
 *
 * <p>Loaded once at server start; written back whenever a player wins. The
 * backing map is the source of truth for new {@link com.deathswap.game.PlayerData}
 * instances, which seed their {@code wins} from here on creation.
 */
public final class WinsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final java.lang.reflect.Type MAP_TYPE =
            new TypeToken<Map<UUID, Integer>>() {}.getType();

    private final Map<UUID, Integer> wins = new HashMap<>();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("deathswap-wins.json");
    }

    /** Read the saved tallies from disk, replacing whatever is currently held. */
    public void load() {
        Path path = path();
        wins.clear();
        if (!Files.exists(path)) {
            return;
        }
        try {
            Map<UUID, Integer> saved = GSON.fromJson(Files.readString(path), MAP_TYPE);
            if (saved != null) {
                wins.putAll(saved);
            }
        } catch (IOException | RuntimeException e) {
            DeathSwapMod.LOGGER.warn("Failed to read win tallies {} — starting from zero", path, e);
        }
    }

    /** This player's recorded win count, or 0 if they have never won. */
    public int get(UUID uuid) {
        return wins.getOrDefault(uuid, 0);
    }

    /** Record a player's new win count and write the file back to disk. */
    public void set(UUID uuid, int count) {
        wins.put(uuid, count);
        save();
    }

    private void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(wins, MAP_TYPE));
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Failed to write win tallies {}", path, e);
        }
    }
}
