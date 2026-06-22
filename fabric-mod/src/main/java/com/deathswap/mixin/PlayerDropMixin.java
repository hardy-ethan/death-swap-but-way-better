package com.deathswap.mixin;

import com.deathswap.DeathSwapMod;
import com.deathswap.game.GamePhase;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Detects a player dropping one of the offered item dyes (the datapack's
 * "drop from hotbar to select" mechanic) and routes it to the item system,
 * cancelling the real world-drop so the dye never litters the ground.
 *
 * <p>As of 26.2 the three-argument {@code drop} overload lives on
 * {@link ServerPlayer} (it was hoisted off {@code Player}); pressing Q routes
 * through {@code ServerPlayer.drop(boolean)} into this method with the dropped
 * stack already pulled from the selected slot.
 */
@Mixin(ServerPlayer.class)
public abstract class PlayerDropMixin {

    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void deathswap$onDrop(ItemStack stack, boolean throwRandomly, boolean retainOwnership,
                                  CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (DeathSwapMod.game() == null) return;
        var game = DeathSwapMod.game();
        if (game.items().isOfferStack(stack)) {
            game.items().onItemTriggered(player, stack);
            cir.setReturnValue(null); // swallow the drop; the item was "used"
            return;
        }
        // Hub items (mace + wind charges) are not droppable.
        // The item has already been removed from the slot before this method
        // is called, so we must add it back explicitly before cancelling the
        // entity spawn — otherwise it silently disappears.
        if (game.phase() == GamePhase.HUB
                && (stack.is(Items.MACE) || stack.is(Items.WIND_CHARGE))) {
            player.getInventory().add(stack);
            cir.setReturnValue(null);
        }
    }
}
