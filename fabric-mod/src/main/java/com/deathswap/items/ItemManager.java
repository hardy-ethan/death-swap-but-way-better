package com.deathswap.items;

import com.deathswap.game.GameManager;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Offers the "choice of 3 items" to players, detects the drop that selects one,
 * resolves the target, and dispatches the effect. Reimplements
 * {@code items/give_items}, {@code items/*detect_item}, the {@code select}
 * trigger flow and {@code items/after_use}.
 */
public final class ItemManager {

    private static final String NBT_ITEM_ID = "ds_item_id";
    private static final String NBT_LOCKED = "ds_locked";
    private static final int[] HOTBAR_SLOTS = {6, 7, 8};

    private final GameManager game;
    private final ItemRegistry registry = new ItemRegistry();

    /** Round-robin pointer over alive players for the "one offer per interval" clock. */
    private int rotation = 0;

    public ItemManager(GameManager game) {
        this.game = game;
    }

    public void registerAll() {
        registry.registerAll();
    }

    public void tick() {
        // Keep the three powerup slots locked with filler whenever a player isn't
        // mid-choice, so they can't be used for storage and the offer always lands
        // in a predictable place.
        for (ServerPlayer player : game.alivePlayers()) {
            maintainLockedSlots(player);
        }
    }

    // ---- offering ----

    /**
     * Hand a fresh set of items to the next player in rotation. The datapack
     * offers to exactly one player per interval (round-robin), not everyone at
     * once — offering to all is what made items feel far too frequent.
     */
    public void offerNext() {
        List<ServerPlayer> alive = new ArrayList<>(game.alivePlayers());
        if (alive.isEmpty()) {
            return;
        }
        alive.sort(java.util.Comparator.comparingInt(p -> game.data(p).permPNo));
        rotation = (rotation + 1) % alive.size();
        ServerPlayer player = alive.get(rotation);
        if (game.effects().hasEffect(player.getUUID(), "blockedItems")) {
            Mc.msg(player, "** Items blocked!", ChatFormatting.RED);
            return;
        }
        offer(player);
    }

    public void offer(ServerPlayer player) {
        PlayerData data = game.data(player);
        List<DeathSwapItem> picks = registry.pickThree(player, player.getRandom().nextLong());
        if (picks.size() < 3) {
            return;
        }
        data.offeredItems = picks.toArray(new DeathSwapItem[0]);
        data.choosingItem = true;
        data.pendingTargetItem = null;

        for (int i = 0; i < HOTBAR_SLOTS.length; i++) {
            player.getInventory().setItem(HOTBAR_SLOTS[i], buildDye(picks.get(i)));
        }
        Mc.title(player, " ", ">> New items! <<", ChatFormatting.WHITE, ChatFormatting.GREEN);
        Mc.msg(player, "<< You got a new set of items! They will expire in 45 seconds if "
                + "you don't use one of them! You can only use one! >>", ChatFormatting.GREEN);
        Mc.playSound(player, SoundEvents.ITEM_PICKUP, 9.0f, 1.0f);
    }

    // ---- locked powerup slots ----

    /**
     * Force the three powerup slots to hold an immovable filler item whenever the
     * player isn't actively choosing from an offer, and remove any filler that
     * leaked into another slot. Re-running every tick is what makes it "immovable":
     * anything the player drops in is overwritten, and the filler always returns.
     */
    private void maintainLockedSlots(ServerPlayer player) {
        boolean choosing = game.data(player).choosingItem;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPowerupSlot(slot)) {
                // While choosing, the dye offer occupies these slots — leave it be.
                if (!choosing && !isLocked(stack)) {
                    inventory.setItem(slot, buildLockedFiller());
                }
            } else if (isLocked(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isPowerupSlot(int slot) {
        for (int s : HOTBAR_SLOTS) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocked(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(NBT_LOCKED, false);
    }

    /** The useless, immovable placeholder that sits in the powerup slots. */
    private ItemStack buildLockedFiller() {
        ItemStack stack = new ItemStack(Items.BARRIER);
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal("Powerup slot — items appear here")
                        .withStyle(ChatFormatting.DARK_GRAY).withStyle(s -> s.withItalic(false)));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(NBT_LOCKED, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /**
     * Admin helper: give a player a single death-swap item by its id, ready to
     * use. Places the dyed display stack in the first offer slot and arms the
     * choosing state so dropping it fires the effect (or opens target selection).
     * Returns false if no item has that id.
     */
    public boolean giveById(ServerPlayer player, int id) {
        DeathSwapItem item = registry.byId(id);
        if (item == null) {
            return false;
        }
        PlayerData data = game.data(player);
        data.offeredItems = new DeathSwapItem[]{item};
        data.choosingItem = true;
        data.pendingTargetItem = null;
        player.getInventory().setItem(HOTBAR_SLOTS[0], buildDye(item));
        Mc.msg(player, "Given item #" + id + " (" + item.name + ") -- drop it to use.",
                ChatFormatting.GREEN);
        Mc.playSound(player, SoundEvents.ITEM_PICKUP, 9.0f, 1.0f);
        return true;
    }

    /** Highest registered item id (ids run 1..N). */
    public int maxItemId() {
        return registry.size();
    }

    /** Build the dyed display item exactly as the datapack's items/items/* do. */
    private ItemStack buildDye(DeathSwapItem item) {
        ItemStack stack = new ItemStack(Items.DYE.asList().get(item.dye.ordinal()));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(item.name).withStyle(item.nameColor).withStyle(s -> s.withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal(item.lore).withStyle(ChatFormatting.GRAY))));
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
        // A dropped filler is a no-op: swallow it so the powerup slot just refills.
        if (isLocked(stack)) {
            return true;
        }
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
        Mc.msg(player, Component.literal("\n>> Click on which player you want to use this item on: ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(item.name).withStyle(ChatFormatting.AQUA)));

        // Every alive player is listed by their permanent number (yourself
        // included, as in the datapack's select_template). Shielded players are
        // shown struck-through but are NOT offered as a clickable option.
        MutableComponent line = Component.literal("");
        MutableComponent shieldedNote = Component.literal("");
        boolean anyShielded = false;
        for (ServerPlayer p : game.alivePlayers()) {
            boolean shielded = game.effects().hasEffect(p.getUUID(), "shield");
            MutableComponent chip = Component.literal("[ " + p.getName().getString() + " ]  ");
            if (shielded) {
                chip.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
                if (anyShielded) {
                    shieldedNote.append(Component.literal(", "));
                }
                shieldedNote.append(Component.literal(p.getName().getString()));
                anyShielded = true;
            } else {
                chip.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/deathswap target " + game.data(p).permPNo)));
            }
            line.append(chip);
        }
        Mc.msg(player, line);
        if (anyShielded) {
            Mc.msg(player, shieldedNote.withStyle(ChatFormatting.ITALIC)
                    .append(Component.literal(" is/are shielded from items!").withStyle(ChatFormatting.YELLOW)));
        }
    }

    /** Invoked by the /deathswap target command (the clicked chip). */
    public void onTargetSelected(ServerPlayer player, int permNo) {
        PlayerData data = game.data(player);
        DeathSwapItem item = data.pendingTargetItem;
        if (item == null) {
            return;
        }
        ServerPlayer target = game.playerByPermNo(permNo);
        // Shielded/eliminated players aren't offered as options; guard anyway and
        // keep the item pending so a valid chip can still be clicked.
        if (target == null || game.effects().hasEffect(target.getUUID(), "shield")) {
            Mc.msg(player, ">> That player can't be targeted. <<", ChatFormatting.RED);
            return;
        }
        data.pendingTargetItem = null;
        fire(item, player, target);
        afterUse(player);
    }

    // ---- cleanup ----

    private void clearOfferStacks(ServerPlayer player) {
        // Restore the locked filler immediately rather than leaving the slots
        // empty. If we cleared them to EMPTY, an effect that gives items (via
        // Inventory.add) would drop those items into these now-free slots, and
        // the next maintainLockedSlots tick would overwrite them with filler —
        // silently erasing the powerup's reward. Re-locking synchronously keeps
        // the slots occupied so given items land elsewhere.
        for (int slot : HOTBAR_SLOTS) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (itemIdOf(stack) >= 0) {
                player.getInventory().setItem(slot, buildLockedFiller());
            }
        }
    }

    private void afterUse(ServerPlayer player) {
        PlayerData data = game.data(player);
        data.clearOffer();
        Mc.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.5f);
    }

    public boolean isOfferStack(ItemStack stack) {
        return itemIdOf(stack) >= 0 || isLocked(stack);
    }
}
