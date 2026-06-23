package com.deathswap.mixin;

import com.deathswap.DeathSwapMod;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freezes a player's per-tick state while the game is operator-paused. The vanilla
 * tick-rate freeze ({@code /tick freeze}) deliberately keeps players ticking, so we
 * cancel {@link ServerPlayer#tick()} ourselves: this halts potion-effect duration
 * countdown, food/saturation, air, fire and survival block-break progress (its game
 * mode is ticked from here). Player movement still arrives via packets, but the
 * game's hold-in-place loop snaps it back, so the player is effectively frozen.
 *
 * <p>When no game is paused ({@link com.deathswap.game.GameManager#isPaused()} is
 * false) this is a single boolean read and the tick proceeds normally.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTickMixin {

    @Inject(method = "doTick", at = @At("HEAD"), cancellable = true)
    private void deathswap$freezeWhilePaused(CallbackInfo ci) {
        if (DeathSwapMod.game() != null && DeathSwapMod.game().isPaused()) {
            ci.cancel();
        }
    }
}
