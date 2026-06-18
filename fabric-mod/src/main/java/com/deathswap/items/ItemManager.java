package com.deathswap.items;

import com.deathswap.game.GameManager;
import com.deathswap.game.GameSettings;
import com.deathswap.game.PlayerData;
import com.deathswap.util.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offers the "choice of 3 items" to players, detects the drop that selects one,
 * resolves the target, and dispatches the effect. Reimplements
 * {@code items/give_items}, {@code items/*detect_item}, the {@code select}
 * trigger flow and {@code items/after_use}.
 */
public final class ItemManager {

    private static final String NBT_ITEM_ID = "ds_item_id";
    private static final int[] HOTBAR_SLOTS = {6, 7, 8};
    private static final Item[] DYE_BY_SLOT = {Items.LIGHT_BLUE_DYE, Items.LIME_DYE, Items.PINK_DYE};

    private final GameManager game;
    private final ItemRegistry registry = new ItemRegistry();

    public ItemManager(GameManager game) {
        this.game = game;
    }

    public void registerAll() {
        registry.registerAll();
    }

    public void tick() {
        // Reserved for per-tick item bookkeeping (currently effects self-manage).
    }

    // ---- offering ----

    public void offerToAll() {
        for (ServerPlayer player : game.alivePlayers()) {
            offer(player);
        }
    }

    public void offer(ServerPlayer player) {
        PlayerData data = game.data(player);
        List<DeathSwapItem> picks = registry.pickThree(player, game.settings(), player.getRandom().nextLong());
        if (picks.size() < 3) {
            return;
        }
        data.offeredItems = picks.toArray(new DeathSwapItem[0]);
        data.choosingItem = true;
        data.pendingTargetItem = null;

        for (int i = 0; i < HOTBAR_SLOTS.length; i++) {
            player.getInventory().setItem(HOTBAR_SLOTS[i], buildDye(picks.get(i), i, game.settings()));
        }
        Mc.title(player, " ", ">> New items! <<", ChatFormatting.WHITE, ChatFormatting.GREEN);
        Mc.msg(player, "<< You got 3 new items! Drop one from your hotbar to use it. >>",
                ChatFormatting.GREEN);
        Mc.playSound(player, SoundEvents.ITEM_PICKUP, 1.0f, 1.0f);
    }

    private ItemStack buildDye(DeathSwapItem item, int slot, GameSettings settings) {
        ItemStack stack = new ItemStack(DYE_BY_SLOT[slot % DYE_BY_SLOT.length]);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(item.displayName(settings)).withStyle(ChatFormatting.AQUA));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_ITEM_ID, item.id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static int itemIdOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return -1;
        }
        // 26.2 uses the Optional-based NBT API; getIntOr returns a primitive with a default.
        return data.copyTag().getIntOr(NBT_ITEM_ID, -1);
    }

    // ---- selection (drop) ----

    /**
     * Called from the drop mixin. Returns true if the stack was a death-swap
     * offer item (in which case the real drop is cancelled).
     */
    public boolean onItemDropped(ServerPlayer player, ItemStack stack) {
        int id = itemIdOf(stack);
        if (id < 0) {
            return false;
        }
        PlayerData data = game.data(player);
        DeathSwapItem item = registry.byId(id);
        if (item == null || !data.choosingItem) {
            clearOfferStacks(player);
            return true;
        }
        clearOfferStacks(player);
        data.choosingItem = false;

        switch (item.target) {
            case SELF, EVERYONE -> {
                applyEveryoneOrSelf(item, player);
                afterUse(player);
            }
            case ALL_OTHERS -> {
                for (ServerPlayer other : game.alivePlayers()) {
                    if (other != player) {
                        fire(item, player, other);
                    }
                }
                afterUse(player);
            }
            case RANDOM_OPPONENT -> {
                ServerPlayer victim = randomOpponent(player);
                if (victim != null) {
                    fire(item, player, victim);
                }
                afterUse(player);
            }
            case OPPONENT -> {
                data.pendingTargetItem = item;
                promptForTarget(player, item);
            }
        }
        return true;
    }

    private void applyEveryoneOrSelf(DeathSwapItem item, ServerPlayer player) {
        if (item.target == ItemTarget.EVERYONE) {
            for (ServerPlayer p : game.alivePlayers()) {
                fire(item, player, p);
            }
        } else {
            fire(item, player, player);
        }
    }

    private void fire(DeathSwapItem item, ServerPlayer self, ServerPlayer target) {
        item.effect.apply(new ItemContext(game.server(), game, game.effects()), self, target);
    }

    private ServerPlayer randomOpponent(ServerPlayer self) {
        List<ServerPlayer> opponents = new ArrayList<>(game.alivePlayers());
        opponents.remove(self);
        opponents.removeIf(p -> game.effects().hasEffect(p.getUUID(), "shield"));
        if (opponents.isEmpty()) {
            return null;
        }
        return opponents.get(self.getRandom().nextInt(opponents.size()));
    }

    // ---- targeting (opponent items) ----

    private void promptForTarget(ServerPlayer player, DeathSwapItem item) {
        Mc.msg(player, Component.literal("\n>> Click the player to hit with: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(item.displayName(game.settings())).withStyle(ChatFormatting.AQUA)));
        MutableComponent line = Component.literal("");
        boolean any = false;
        for (ServerPlayer opponent : game.alivePlayers()) {
            if (opponent == player) {
                continue;
            }
            boolean shielded = game.effects().hasEffect(opponent.getUUID(), "shield");
            int permNo = game.data(opponent).permPNo;
            MutableComponent chip = Component.literal("[ " + opponent.getName().getString() + " ] ");
            if (shielded) {
                chip.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
            } else {
                chip.withStyle(Style.EMPTY
                        .withColor(ChatFormatting.DARK_AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/deathswap target " + permNo)));
                any = true;
            }
            line.append(chip);
        }
        if (!any) {
            Mc.msg(player, "Everyone else is shielded! Item wasted.", ChatFormatting.RED);
            game.data(player).pendingTargetItem = null;
            afterUse(player);
            return;
        }
        Mc.msg(player, line);
    }

    /** Invoked by the /deathswap target command. */
    public void onTargetSelected(ServerPlayer player, int permNo) {
        PlayerData data = game.data(player);
        DeathSwapItem item = data.pendingTargetItem;
        if (item == null) {
            return;
        }
        ServerPlayer target = game.playerByPermNo(permNo);
        if (target == null) {
            Mc.msg(player, "That player is no longer available. Pick another.", ChatFormatting.RED);
            return;
        }
        if (game.effects().hasEffect(target.getUUID(), "shield")) {
            Mc.msg(player, "That player is shielded! Pick another.", ChatFormatting.RED);
            return;
        }
        data.pendingTargetItem = null;
        fire(item, player, target);
        afterUse(player);
    }

    // ---- cleanup ----

    private void clearOfferStacks(ServerPlayer player) {
        for (int slot : HOTBAR_SLOTS) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (itemIdOf(stack) >= 0) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private void afterUse(ServerPlayer player) {
        PlayerData data = game.data(player);
        data.clearOffer();
        Mc.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    public boolean isOfferStack(ItemStack stack) {
        return itemIdOf(stack) >= 0;
    }
}
