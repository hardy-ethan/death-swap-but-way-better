package com.deathswap.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Thin wrappers over the vanilla server API. All version-sensitive calls live
 * here so that porting to a new Minecraft mapping only touches one file.
 */
public final class Mc {

    private Mc() {
    }

    // ---- chat / titles ----

    public static void msg(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    public static void msg(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    public static void title(ServerPlayer player, String title, String subtitle,
                             ChatFormatting titleColor, ChatFormatting subtitleColor) {
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(subtitle).withStyle(subtitleColor)));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(title).withStyle(titleColor)));
    }

    // ---- effects / attributes ----

    public static void effect(ServerPlayer player, Holder<MobEffect> effect, int seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, false, true, true));
    }

    public static void setAttribute(ServerPlayer player, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    public static void resetAttribute(ServerPlayer player, Holder<Attribute> attribute, double defaultValue) {
        setAttribute(player, attribute, defaultValue);
    }

    // ---- items ----

    public static void give(ServerPlayer player, Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ---- world ----

    /** The server-side level an entity is in. {@code serverLevel()} was removed in 26.x. */
    public static ServerLevel level(Entity entity) {
        return (ServerLevel) entity.level();
    }

    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        ServerLevel level = level(player);
        Vec3 p = player.position();
        level.playSound(null, p.x, p.y, p.z, sound, SoundSource.MASTER, volume, pitch);
    }

    /** Teleport within the player's current dimension. */
    public static void teleport(ServerPlayer player, double x, double y, double z) {
        teleportTo(player, level(player), x, y, z, player.getYRot(), player.getXRot());
    }

    /**
     * Cross-dimension capable teleport. Centralised here because the exact
     * teleport signature is the most version-sensitive call in the mod.
     */
    public static void teleportTo(ServerPlayer player, ServerLevel level,
                                  double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false);
        player.fallDistance = 0.0f;
    }

    // ---- entities / blocks ----

    public static <T extends Entity> T summon(ServerLevel level, EntityType<T> type, double x, double y, double z) {
        return type.spawn(level, BlockPos.containing(x, y, z), EntitySpawnReason.COMMAND);
    }

    public static void setBlock(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    /** Fill an axis-aligned box centred on {@code center} (inclusive radii) with a block. */
    public static void fillBox(ServerLevel level, BlockPos center, int rx, int ry, int rz,
                               Block block, boolean replaceSolidOnly) {
        BlockState state = block.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                for (int dz = -rz; dz <= rz; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (replaceSolidOnly && level.getBlockState(cursor).isAir()) {
                        continue;
                    }
                    level.setBlockAndUpdate(cursor, state);
                }
            }
        }
    }

    public static void explode(ServerPlayer at, float radius) {
        ServerLevel level = level(at);
        Vec3 p = at.position();
        level.explode(null, p.x, p.y, p.z, radius, Level.ExplosionInteraction.TNT);
    }
}
