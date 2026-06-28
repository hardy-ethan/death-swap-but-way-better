package com.deathswap.game;

import com.deathswap.DeathSwapMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

/**
 * Frees the world's region files and re-rolls the seed so the next session starts
 * on a fresh, smaller world. The Java equivalent of {@code clear_world.sh}, but run
 * automatically by the mod instead of by hand.
 *
 * <p>This must only be called while the server is <em>stopped</em> — from the
 * {@code SERVER_STOPPED} lifecycle event, after every level has been saved and
 * closed and the region-file handles released. Deleting {@code .mca} files or
 * rewriting the worldgen settings out from under a running server would corrupt
 * the save; the new seed also only takes effect when the server next loads the
 * world, so a restart (via an external wrapper) is required for it to apply.
 *
 * <p>Only the vanilla {@code minecraft:} dimensions are wiped — the {@code ds:}
 * datapack dimension (the superflat lobby) is never touched. The wipe is total
 * (the origin/lobby region is deleted too): the lobby regenerates on the new seed
 * and {@link GameManager} rebuilds its hub platform at spawn on first use.
 */
public final class WorldReset {

    private WorldReset() {
    }

    /** Dimension subdirectories that hold deletable chunk data. */
    private static final String[] CHUNK_DIRS = {"region", "poi", "entities"};
    /** The vanilla dimensions to wipe, relative to {@code dimensions/}. */
    private static final String[] DIMENSIONS = {
            "minecraft/overworld", "minecraft/the_nether", "minecraft/the_end"};

    /**
     * Delete all vanilla-dimension chunk data and re-roll the world seed. Never
     * throws: any failure is logged and the rest of the reset still runs, so a
     * problem here can't wedge server shutdown.
     */
    public static void resetWorld(Path worldRoot) {
        DeathSwapMod.LOGGER.info("Auto-reset: freeing region files and changing the world seed.");

        Path dimensions = worldRoot.resolve("dimensions");
        for (String dim : DIMENSIONS) {
            for (String sub : CHUNK_DIRS) {
                deleteMcaIn(dimensions.resolve(dim).resolve(sub));
            }
        }

        // Reset the dragon fight / raids so the End and its boss start clean.
        Path endData = dimensions.resolve("minecraft/the_end/data/minecraft");
        deleteIfPresent(endData.resolve("ender_dragon_fight.dat"));
        deleteIfPresent(endData.resolve("raids.dat"));

        changeSeed(worldRoot.resolve("data/minecraft/world_gen_settings.dat"));

        DeathSwapMod.LOGGER.info("Auto-reset complete. The new seed applies on the next server start.");
    }

    /** Delete every {@code *.mca} file in a dimension subdirectory; no-op if absent. */
    private static void deleteMcaIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.mca")) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Auto-reset: failed to clear region files in {}", dir, e);
        }
    }

    private static void deleteIfPresent(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Auto-reset: failed to delete {}", file, e);
        }
    }

    /**
     * Re-roll the seed in {@code world_gen_settings.dat} (gzipped NBT, the seed
     * living under the {@code data} compound). Skips with a warning if the file or
     * the expected tags are missing rather than failing the reset.
     */
    private static void changeSeed(Path settings) {
        if (!Files.isRegularFile(settings)) {
            DeathSwapMod.LOGGER.warn("Auto-reset: {} not found; seed left unchanged.", settings);
            return;
        }
        try {
            CompoundTag root = NbtIo.readCompressed(settings, NbtAccounter.unlimitedHeap());
            Optional<CompoundTag> data = root.getCompound("data");
            if (data.isEmpty() || data.get().getLong("seed").isEmpty()) {
                DeathSwapMod.LOGGER.warn("Auto-reset: no data/seed tag in {}; seed left unchanged.", settings);
                return;
            }
            long newSeed = new Random().nextLong();
            data.get().putLong("seed", newSeed);
            NbtIo.writeCompressed(root, settings);
            DeathSwapMod.LOGGER.info("Auto-reset: world seed changed to {}.", newSeed);
        } catch (IOException e) {
            DeathSwapMod.LOGGER.warn("Auto-reset: failed to change the world seed in {}", settings, e);
        }
    }
}
