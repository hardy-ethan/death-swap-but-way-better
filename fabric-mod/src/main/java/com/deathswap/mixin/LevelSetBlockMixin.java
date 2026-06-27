package com.deathswap.mixin;

import com.deathswap.game.WorldRollback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Captures every server-side block change into the active {@link WorldRollback}
 * so the world can be restored when a game ends. {@code setBlock(pos, state, flags)}
 * and {@code setBlockAndUpdate} both funnel through this four-argument overload, so
 * hooking it here catches all live block changes — player edits, explosions, fire,
 * fluids, the game's own gravel towers and builds — from a single point.
 *
 * <p>The snapshot is taken at HEAD, before the write lands, so the level still
 * holds the pre-change state. When no game is recording ({@link WorldRollback#active()}
 * is null) this is a single volatile read and returns immediately.
 */
@Mixin(Level.class)
public abstract class LevelSetBlockMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void deathswap$captureBlockChange(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                              CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.isClientSide()) {
            return; // only the server's world is rolled back
        }
        WorldRollback rollback = WorldRollback.active();
        if (rollback != null) {
            rollback.record(level, pos);
        }
    }
}
