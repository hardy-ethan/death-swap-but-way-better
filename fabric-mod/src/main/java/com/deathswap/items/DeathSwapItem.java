package com.deathswap.items;

import com.deathswap.game.GameSettings;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/**
 * Definition of one of the 110 death-swap items: its identity, display names,
 * who it targets, an optional availability gate, and its effect.
 */
public final class DeathSwapItem {

    public final int id;
    private final String englishName;
    private final String chineseName;
    public final ItemTarget target;
    public final ItemEffect effect;
    private final Predicate<ServerPlayer> available;

    private DeathSwapItem(Builder b) {
        this.id = b.id;
        this.englishName = b.englishName;
        this.chineseName = b.chineseName != null ? b.chineseName : b.englishName;
        this.target = b.target;
        this.effect = b.effect;
        this.available = b.available;
    }

    public String displayName(GameSettings settings) {
        return settings.isChinese() ? chineseName : englishName;
    }

    /** Whether this item may be offered to the given player right now. */
    public boolean isAvailableFor(ServerPlayer player) {
        return available == null || available.test(player);
    }

    public static Builder builder(int id, String englishName) {
        return new Builder(id, englishName);
    }

    public static final class Builder {
        private final int id;
        private final String englishName;
        private String chineseName;
        private ItemTarget target = ItemTarget.SELF;
        private ItemEffect effect = (ctx, self, target) -> {};
        private Predicate<ServerPlayer> available;

        private Builder(int id, String englishName) {
            this.id = id;
            this.englishName = englishName;
        }

        public Builder chinese(String name) {
            this.chineseName = name;
            return this;
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
