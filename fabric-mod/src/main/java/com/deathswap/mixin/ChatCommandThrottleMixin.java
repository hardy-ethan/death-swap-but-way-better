package com.deathswap.mixin;

import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents a spam-kick when a player rapidly clicks a "/deathswap target"
 * click-event button in chat. Each click fires a ServerboundChatCommandPacket;
 * Minecraft's flood-protection kicks the player if too many arrive at once.
 * We drop duplicate packets at the network layer before MC counts them.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatCommandThrottleMixin {

    private static final Map<String, Long> LAST_TARGET_MS = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 750;

    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleChatCommand(Lnet/minecraft/network/protocol/game/ServerboundChatCommandPacket;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void deathswap$throttleTargetCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        String command = packet.command();
        String prefix = null;
        if (command.startsWith("deathswap target ")) {
            prefix = "deathswap target ";
        } else if (command.startsWith("ds target ")) {
            prefix = "ds target ";
        }
        if (prefix == null) return;

        String permNo = command.substring(prefix.length()).strip();
        String key = player.getUUID() + ":" + permNo;
        long now = System.currentTimeMillis();
        Long last = LAST_TARGET_MS.get(key);
        if (last != null && now - last < COOLDOWN_MS) {
            ci.cancel();
            return;
        }
        LAST_TARGET_MS.put(key, now);
    }
}
