package com.deathswap.mixin;

import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link ScatteredFeaturePiece#heightPosition}. Desert/jungle/swamp temples
 * normally snap their Y to the lowest ground height across their footprint the first
 * time {@code postProcess} runs (the {@code heightPosition < 0} branch), which buries
 * them on uneven terrain or sinks them into water. Setting this to a non-negative value
 * marks the height as already resolved so the snap is skipped and the piece builds
 * wherever its bounding box has been placed.
 */
@Mixin(ScatteredFeaturePiece.class)
public interface ScatteredFeaturePieceAccessor {

    @Accessor("heightPosition")
    void deathswap$setHeightPosition(int heightPosition);
}
