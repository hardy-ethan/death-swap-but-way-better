package com.deathswap.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Pre-generates far-away spread destinations while no game is running (from
 * server startup onward) so the world-gen cost isn't paid all at once at game
 * start, when every player is spread simultaneously.
 *
 * <p>Each queued destination is a validated dry-land surface column whose whole
 * landing area — a {@link #PREGEN_RADIUS}-chunk square around the column — has
 * already been generated to disk. Generating the surroundings (not just the
 * centre column) matters for how the spread <em>looks</em>: when a player is
 * teleported onto freshly-generated terrain the server has to run world-gen
 * before it can stream the chunks, so the client only receives the chunk shapes
 * (the outline) and renders the blocks several seconds later. Pre-generating the
 * area ahead of time means the teleport only triggers a fast disk load, so the
 * blocks stream in promptly and the client has the entire pre-game freeze to
 * render them before the player is unblinded.
 *
 * <p>Destinations are handed out one at a time, and {@link #next} removes a
 * destination from the queue as it hands it out, so a pre-generated area is used
 * at most once and never reused. Unused destinations persist across games — they
 * were never handed out, so they're still valid — and the queue is never cleared
 * wholesale. The generated chunks are <em>not</em> kept loaded (no forceload
 * ticket is added) — they unload naturally and are reloaded from disk (no re-gen)
 * when a player is sent there.
 *
 * <p>The cache is transparent to callers: {@link #next} simply rolls a location
 * live (cold, single-chunk) when nothing is ready, exactly as if the cache
 * didn't exist.
 */
final class ChunkCache {

    /** How many destinations to keep queued up. */
    private static final int CAPACITY = 5 * 4; // 5 players * 4 swaps
    /**
     * Maximum chunk generations per tick. Pre-generating a destination's landing
     * area is spread across many ticks at this rate so the work never stalls the
     * server tick, no matter how large {@link #PREGEN_RADIUS} is.
     */
    private static final int CHUNK_GENS_PER_TICK = 2;
    /**
     * Chunks to pre-generate in each direction around a destination column
     * (Chebyshev radius): {@code PREGEN_RADIUS = 3} pre-generates a 7x7 area. Big
     * enough to cover the player's immediate surroundings so they don't land
     * looking at un-rendered terrain; larger just takes longer to fill the queue.
     */
    private static final int PREGEN_RADIUS = 5;

    /** Destinations whose whole landing area is generated and ready to hand out. */
    private final Deque<BlockPos> ready = new ArrayDeque<>();

    /** Destination currently being pre-generated, plus the chunks still to do for it. */
    private BlockPos pendingPos;
    /** Remaining landing-area chunks for {@link #pendingPos}, packed as {@code (x<<32)|z}. */
    private final Deque<Long> pendingChunks = new ArrayDeque<>();

    /**
     * Make progress generating spread destinations this tick, bounded to
     * {@link #CHUNK_GENS_PER_TICK} chunk generations. Should only be called while
     * no game is running (not during the running/ending phases).
     */
    void tick(ServerLevel level, Random random, int minRadius, int maxRadius) {
        int budget = CHUNK_GENS_PER_TICK;

        // Finish pre-generating the in-progress destination's landing area first.
        while (budget > 0 && !pendingChunks.isEmpty()) {
            long packed = pendingChunks.poll();
            level.getChunk((int) (packed >> 32), (int) packed); // force to FULL, generating if needed
            budget--;
        }
        if (pendingPos != null && pendingChunks.isEmpty()) {
            ready.add(pendingPos);
            pendingPos = null;
        }

        // Start the next destination once the previous one's area is fully done.
        if (budget > 0 && pendingPos == null && ready.size() < CAPACITY) {
            BlockPos pos = rollColumn(level, random, minRadius, maxRadius);
            if (isDryLand(level, pos)) {
                pendingPos = pos;
                queueLandingArea(pos);
            }
        }
    }

    /** Number of fully pre-generated destinations ready to hand out. */
    int readyCount() {
        return ready.size();
    }

    /** Target queue depth — the most destinations the cache will keep ready. */
    int capacity() {
        return CAPACITY;
    }

    /** Landing-area chunks still to generate for the in-progress destination, if any. */
    int pendingChunkCount() {
        return pendingChunks.size();
    }

    /**
     * Take a spread destination. Returns a pre-generated one when the queue has
     * any (removing it, so it is used at most once); otherwise rolls one live,
     * exactly as if the cache didn't exist. Either way the caller gets a valid
     * dry-land column and can't tell which path produced it — the cache being
     * empty is transparent (the live roll just won't have its area pre-generated).
     */
    BlockPos next(ServerLevel level, Random random, int minRadius, int maxRadius) {
        BlockPos ready = this.ready.poll();
        return ready != null ? ready : rollLive(level, random, minRadius, maxRadius);
    }

    /**
     * Roll a destination live, re-rolling until we find dry land so players don't
     * spawn in an ocean/lake. Falls back to the last pick after a cap.
     */
    private static BlockPos rollLive(ServerLevel level, Random random, int minRadius, int maxRadius) {
        BlockPos pos = rollColumn(level, random, minRadius, maxRadius);
        for (int attempt = 1; attempt < 32 && !isDryLand(level, pos); attempt++) {
            pos = rollColumn(level, random, minRadius, maxRadius);
        }
        return pos;
    }

    /**
     * Queue the chunks of a destination's landing area for pre-generation. The
     * centre chunk is already generated by {@link #rollColumn} (to sample the
     * heightmap), so only the surrounding ring chunks need queueing.
     */
    private void queueLandingArea(BlockPos pos) {
        int ccx = pos.getX() >> 4;
        int ccz = pos.getZ() >> 4;
        for (int dx = -PREGEN_RADIUS; dx <= PREGEN_RADIUS; dx++) {
            for (int dz = -PREGEN_RADIUS; dz <= PREGEN_RADIUS; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // centre already generated by rollColumn
                }
                pendingChunks.add(((long) (ccx + dx) << 32) | ((ccz + dz) & 0xffffffffL));
            }
        }
    }

    /**
     * Roll one random far-away surface column, forcing its chunk to generate
     * before sampling the heightmap. Far-away chunks aren't loaded yet, and
     * getHeight() on an ungenerated chunk returns the world minimum (the void),
     * so the chunk must be generated first.
     */
    private static BlockPos rollColumn(ServerLevel level, Random random, int minRadius, int maxRadius) {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = minRadius + random.nextDouble() * (maxRadius - minRadius);
        int x = (int) (Math.cos(angle) * radius);
        int z = (int) (Math.sin(angle) * radius);
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    /**
     * True when the column is dry land. The surface block sits just below the
     * spawn height; if it (or the block we'd stand in) holds a fluid it's
     * water/lava, which would drop the player into a lake or lava.
     */
    private static boolean isDryLand(ServerLevel level, BlockPos feet) {
        return level.getFluidState(feet.below()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }
}
