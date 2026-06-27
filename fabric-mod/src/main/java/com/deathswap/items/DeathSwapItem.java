package com.deathswap.items;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;

import java.util.function.Predicate;

/**
 * Definition of one of the 110 death-swap items: its identity, the dyed display
 * item shown in the hotbar (colour + name + lore, matching the datapack's
 * {@code items/items/*} definitions), who it targets, an optional availability
 * gate, and its effect.
 */
public final class DeathSwapItem {

    public final int id;
    public final DyeColor dye;
    public final ChatFormatting nameColor;
    public final String name;
    public final String lore;
    public final ItemTarget target;
    public final ItemEffect effect;
    private final Predicate<ServerPlayer> available;

    private DeathSwapItem(Builder b) {
        this.id = b.id;
        this.dye = b.dye;
        this.nameColor = b.nameColor;
        this.name = b.name;
        this.lore = b.lore;
        this.target = b.target;
        this.effect = b.effect;
        this.available = b.available;
    }

    public boolean isAvailableFor(ServerPlayer player) {
        return available == null || available.test(player);
    }

    public static Builder of(int id, DyeColor dye, ChatFormatting nameColor, String name, String lore) {
        return new Builder(id, dye, nameColor, name, lore);
    }

    public static final class Builder {
        private final int id;
        private final DyeColor dye;
        private final ChatFormatting nameColor;
        private final String name;
        private final String lore;
        private ItemTarget target = ItemTarget.SELF;
        private ItemEffect effect = (ctx, self, t) -> {};
        private Predicate<ServerPlayer> available;

        private Builder(int id, DyeColor dye, ChatFormatting nameColor, String name, String lore) {
            this.id = id;
            this.dye = dye;
            this.nameColor = nameColor;
            this.name = name;
            this.lore = lore;
        }

        public Builder target(ItemTarget t) {
            this.target = t;
            return this;
        }

        public Builder effect(ItemEffect e) {
            this.effect = e;
            return this;
        }

        public Builder availableWhen(Predicate<ServerPlayer> predicate) {
            this.available = predicate;
            return this;
        }

        public DeathSwapItem build() {
            return new DeathSwapItem(this);
        }
    }
}
