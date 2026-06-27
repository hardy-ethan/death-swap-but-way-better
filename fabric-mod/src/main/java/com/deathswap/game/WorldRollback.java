package com.deathswap.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Records every block change made while a game is running so the world can be
 * restored to exactly how it looked before the game when the game ends — players
 * mine, build, blow things up and set fires during a round, and none of that
 * should permanently scar the shared world.
 *
 * <p>Capture is block-level rather than whole-chunk: the first time a position is
 * modified during a game its pre-change state (and block-entity NBT, if any) is
 * snapshotted, and only that snapshot is restored at the end. First-write-wins
 * means a position touched many times during a game costs one snapshot and is
 * restored to what it was at game start, not to some intermediate state.
 *
 * <p>The capturing instance is published through {@link #active()} so the
 * {@code Level.setBlock} mixin — which runs for every block change on the server —
 * can find it with a single volatile read and skip all work when no game is
 * recording. Snapshotting reads the <em>old</em> state at the mixin's HEAD, before
 * the write lands.
 */
public final class WorldRollback {

    /**
     * Flags used when restoring blocks: tell clients about the change and treat
     * the shape as known, but deliberately omit {@code UPDATE_NEIGHBORS} so
     * restoring one block doesn't trigger neighbour physics (water flowing, blocks
     * popping off) that would record nothing and leave the world subtly wrong.
     * Drops are suppressed so a restored container doesn't spit its contents.
     */
    private static final int RESTORE_FLAGS =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    /** The instance currently capturing changes, or null when no game is recording. */
    private static volatile WorldRollback active;

    /** Per-dimension snapshots, keyed by {@link BlockPos#asLong()} so first-write-wins. */
    private final Map<ResourceKey<Level>, Map<Long, Saved>> snapshots = new HashMap<>();
    /** While true (during a rollback) our own restore writes are not re-recorded. */
    private boolean restoring;

    /** The capturing instance, or null when nothing is recording. Read by the mixin. */
    public static WorldRollback active() {
        return active;
    }

    /** Start capturing block changes for a new game, discarding any stale snapshots. */
    public void begin() {
        snapshots.clear();
        restoring = false;
        active = this; // publish last, after the map is cleared, so the mixin sees a clean slate
    }

    /**
     * Snapshot the pre-change state of a position the first time it is modified in
     * a game. Called from the {@code Level.setBlock} mixin's HEAD, so the level
     * still holds the old state here. No-op while a rollback is in progress.
     */
    public void record(Level level, BlockPos pos) {
        if (restoring) {
            return;
        }
        if (level.dimension() != Level.END) {
            return; // only the End is rolled back; leave overworld/nether changes alone
        }
        Map<Long, Saved> map = snapshots.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long key = pos.asLong();
        if (map.containsKey(key)) {
            return; // already snapshotted at its game-start state; keep that one
        }
        BlockState old = level.getBlockState(pos);
        CompoundTag nbt = null;
        if (old.hasBlockEntity()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                nbt = be.saveWithFullMetadata(level.registryAccess());
            }
        }
        map.put(key, new Saved(old, nbt));
    }

    /**
     * Restore every snapshotted position to its game-start state and stop
     * capturing. Returns the number of blocks restored. Safe to call when nothing
     * was recorded (returns 0).
     */
    public int rollback(MinecraftServer server) {
        active = null; // stop the mixin recording before we start writing restored blocks
        restoring = true;
        int restored = 0;
        for (Map.Entry<ResourceKey<Level>, Map<Long, Saved>> dim : snapshots.entrySet()) {
            ServerLevel level = server.getLevel(dim.getKey());
            if (level == null) {
                continue; // dimension no longer loaded; nothing we can restore there
            }
            for (Map.Entry<Long, Saved> e : dim.getValue().entrySet()) {
                BlockPos pos = BlockPos.of(e.getKey());
                Saved saved = e.getValue();
                level.setBlock(pos, saved.state, RESTORE_FLAGS);
                if (saved.nbt != null) {
                    BlockEntity be = BlockEntity.loadStatic(pos, saved.state, saved.nbt, level.registryAccess());
                    if (be != null) {
                        level.setBlockEntity(be);
                    }
                }
                restored++;
            }
        }
        snapshots.clear();
        restoring = false;
        return restored;
    }

    /** A position's pre-game block state and, for block entities, its full NBT. */
    private record Saved(BlockState state, CompoundTag nbt) {
    }
}
