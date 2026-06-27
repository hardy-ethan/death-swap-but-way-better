package com.deathswap.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    private static final Identifier PEE_WATER = Identifier.fromNamespaceAndPath("ds", "pee_water");

    @Inject(method = "spread", at = @At("HEAD"), cancellable = true)
    private void deathswap$cancelPeeWaterFlow(ServerLevel level, BlockPos pos, BlockState state,
                                               FluidState fluidState, CallbackInfo ci) {
        level.getBiome(pos).unwrapKey().ifPresent(key -> {
            if (key.identifier().equals(PEE_WATER)) ci.cancel();
        });
    }
}
