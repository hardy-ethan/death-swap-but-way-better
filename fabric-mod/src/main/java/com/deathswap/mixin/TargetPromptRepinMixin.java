package com.deathswap.mixin;

import com.deathswap.DeathSwapMod;
import com.deathswap.game.GameManager;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-pins the target-selection prompt to the bottom of chat whenever a new
 * message (system or player chat) arrives while a player is waiting to pick a
 * target. The guard flag on PlayerData prevents the prompt's own send calls
 * from triggering a recursive re-send.
 */
@Mixin(ServerPlayer.class)
public abstract class TargetPromptRepinMixin {

    @Inject(
            method = "sendSystemMessage(Lnet/minecraft/network/chat/Component;Z)V",
            at = @At("TAIL")
    )
    private void deathswap$repinAfterSystemMessage(Component component, boolean overlay, CallbackInfo ci) {
        if (overlay) return;
        repinIfPending();
    }

    @Inject(
            method = "sendChatMessage(Lnet/minecraft/network/chat/OutgoingChatMessage;ZLnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("TAIL")
    )
    private void deathswap$repinAfterChatMessage(OutgoingChatMessage message, boolean filterMaskEnabled,
                                                  ChatType.Bound params, CallbackInfo ci) {
        repinIfPending();
    }

    private void repinIfPending() {
        GameManager game = DeathSwapMod.game();
        if (game == null) return;
        game.items().repromptTarget((ServerPlayer) (Object) this);
    }
}
