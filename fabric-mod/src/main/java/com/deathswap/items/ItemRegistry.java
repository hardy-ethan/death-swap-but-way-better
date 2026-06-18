package com.deathswap.items;

import com.deathswap.effects.ActiveEffect;
import com.deathswap.game.GameSettings;
import com.deathswap.util.Mc;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Catalogue of death-swap items. Each entry mirrors one {@code items/use/<n>}
 * function from the datapack. A representative set spanning every category is
 * implemented here; remaining IDs follow the same {@code add(...)} pattern.
 *
 * <p>Effect durations use the datapack convention loosely: the original
 * scoreboards count in 1/100s units; here they are plain server ticks (20/s).
 */
public final class ItemRegistry {

    private final Map<Integer, DeathSwapItem> items = new HashMap<>();

    public DeathSwapItem byId(int id) {
        return items.get(id);
    }

    public int size() {
        return items.size();
    }

    private void add(DeathSwapItem item) {
        items.put(item.id, item);
    }

    /** Pick three distinct, currently-available items for a player. */
    public List<DeathSwapItem> pickThree(ServerPlayer player, GameSettings settings, long seed) {
        List<DeathSwapItem> pool = new ArrayList<>();
        for (DeathSwapItem item : items.values()) {
            if (item.isAvailableFor(player)) {
                pool.add(item);
            }
        }
        Collections.shuffle(pool, new Random(seed));
        return pool.size() <= 3 ? pool : pool.subList(0, 3);
    }

    public void registerAll() {
        registerSelfBuffs();
        registerOpponentDebuffs();
        registerSummons();
        registerWorldEffects();
        registerUtility();
    }

    // ============================ SELF BUFFS ============================

    private void registerSelfBuffs() {
        add(DeathSwapItem.builder(1, "Speed Billion").chinese("十亿速度")
                .target(ItemTarget.SELF)
                .effect((ctx, self, t) -> {
                    Mc.effect(self, MobEffects.SPEED, 40, 4);
                    Mc.setAttribute(self, Attributes.MOVEMENT_SPEED, 0.9);
                    ctx.effects().apply(self, new ActiveEffect("mega_speed", 40 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.MOVEMENT_SPEED, 0.1)));
                }).build());

        add(DeathSwapItem.builder(2, "Wither Materials").chinese("凋灵材料")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.SOUL_SAND, 4);
                    Mc.give(self, Items.WITHER_SKELETON_SKULL, 3);
                }).build());

        add(DeathSwapItem.builder(13, "32 Steak").chinese("32牛排")
                .availableWhen(p -> true)
                .effect((ctx, self, t) -> Mc.give(self, Items.COOKED_BEEF, 32)).build());

        add(DeathSwapItem.builder(19, "4 TNT").chinese("4 TNT")
                .effect((ctx, self, t) -> Mc.give(self, Items.TNT, 4)).build());

        add(DeathSwapItem.builder(28, "Elytra + Rockets").chinese("鞘翅+烟花")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.ELYTRA, 1);
                    Mc.give(self, Items.FIREWORK_ROCKET, 16);
                }).build());

        add(DeathSwapItem.builder(29, "Milk + Golden Apples").chinese("牛奶+金苹果")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.MILK_BUCKET, 1);
                    Mc.give(self, Items.GOLDEN_APPLE, 2);
                }).build());

        add(DeathSwapItem.builder(30, "Fire Resistance").chinese("抗火")
                .effect((ctx, self, t) -> Mc.effect(self, MobEffects.FIRE_RESISTANCE, 180, 0)).build());

        add(DeathSwapItem.builder(37, "Full Diamond Kit").chinese("全套钻石装备")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.DIAMOND_HELMET, 1);
                    Mc.give(self, Items.DIAMOND_CHESTPLATE, 1);
                    Mc.give(self, Items.DIAMOND_LEGGINGS, 1);
                    Mc.give(self, Items.DIAMOND_BOOTS, 1);
                    Mc.give(self, Items.DIAMOND_SWORD, 1);
                    Mc.give(self, Items.DIAMOND_PICKAXE, 1);
                    Mc.give(self, Items.DIAMOND_AXE, 1);
                    Mc.give(self, Items.DIAMOND, 5);
                }).build());

        add(DeathSwapItem.builder(39, "9 Obsidian").chinese("9黑曜石")
                .effect((ctx, self, t) -> Mc.give(self, Items.OBSIDIAN, 9)).build());

        add(DeathSwapItem.builder(45, "Totem of Undying").chinese("不死图腾")
                .effect((ctx, self, t) -> Mc.give(self, Items.TOTEM_OF_UNDYING, 1)).build());

        add(DeathSwapItem.builder(52, "3 Ender Pearls").chinese("3末影珍珠")
                .effect((ctx, self, t) -> Mc.give(self, Items.ENDER_PEARL, 3)).build());

        add(DeathSwapItem.builder(57, "Enchanted Golden Apple").chinese("附魔金苹果")
                .availableWhen(p -> true)
                .effect((ctx, self, t) -> Mc.give(self, Items.ENCHANTED_GOLDEN_APPLE, 1)).build());

        add(DeathSwapItem.builder(62, "Water Bucket").chinese("水桶")
                .effect((ctx, self, t) -> Mc.give(self, Items.WATER_BUCKET, 1)).build());

        add(DeathSwapItem.builder(63, "Lava Bucket").chinese("熔岩桶")
                .effect((ctx, self, t) -> Mc.give(self, Items.LAVA_BUCKET, 1)).build());

        add(DeathSwapItem.builder(89, "Mine 3x Faster").chinese("挖掘速度x3")
                .effect((ctx, self, t) -> {
                    Mc.setAttribute(self, Attributes.BLOCK_BREAK_SPEED, 3.0);
                    ctx.effects().apply(self, new ActiveEffect("mine_faster", 120 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.BLOCK_BREAK_SPEED, 1.0)));
                }).build());

        add(DeathSwapItem.builder(91, "No Fall Damage").chinese("无摔落伤害")
                .effect((ctx, self, t) -> ctx.effects().apply(self,
                        new ActiveEffect("no_fall_dam", 300 * 20,
                                p -> p.fallDistance = 0.0f, null))).build());

        add(DeathSwapItem.builder(94, "Flint & Steel").chinese("打火石")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.FLINT_AND_STEEL, 1);
                    Mc.give(self, Items.IRON_INGOT, 1);
                }).build());

        // Shields make the holder un-targetable while active.
        add(shield(3, "Shield (2 min)", "护盾(2分钟)", 120));
        add(shield(97, "Shield (2.5 min)", "护盾(2.5分钟)", 150));
        add(shield(31, "Shield (3 min)", "护盾(3分钟)", 180));
    }

    private DeathSwapItem shield(int id, String en, String zh, int seconds) {
        return DeathSwapItem.builder(id, en).chinese(zh)
                .target(ItemTarget.SELF)
                .availableWhen(p -> true)
                .effect((ctx, self, t) -> {
                    ctx.effects().apply(self, new ActiveEffect("shield", seconds * 20, null,
                            p -> Mc.msg(p, "Your shield wore off. Others can target you again.",
                                    ChatFormatting.GRAY)));
                    Mc.msg(self, "You are shielded for " + seconds + "s. Items can't target you.",
                            ChatFormatting.AQUA);
                }).build();
    }

    // ========================= OPPONENT DEBUFFS =========================

    private void registerOpponentDebuffs() {
        add(DeathSwapItem.builder(11, "Disable Jump").chinese("禁止跳跃")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.setAttribute(target, Attributes.JUMP_STRENGTH, 0.0);
                    ctx.effects().apply(target, new ActiveEffect("jump_disabled", 60 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.JUMP_STRENGTH, 0.42)));
                    Mc.msg(target, "You can't jump for a while!", ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.builder(21, "Motion Sickness").chinese("晕动症")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> ctx.effects().apply(target,
                        new ActiveEffect("motion_sick", 45 * 20,
                                p -> p.setYRot(p.getYRot() + 12f), null))).build());

        add(DeathSwapItem.builder(26, "Blindness & Darkness").chinese("失明与黑暗")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.effect(target, MobEffects.BLINDNESS, 40, 0);
                    Mc.effect(target, MobEffects.DARKNESS, 40, 0);
                }).build());

        add(DeathSwapItem.builder(25, "Bedrock Trail").chinese("基岩足迹")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> ctx.effects().apply(target,
                        new ActiveEffect("bedrock_trail", 120 * 20,
                                p -> Mc.setBlock(Mc.level(p), p.blockPosition().below(), Blocks.BEDROCK),
                                null))).build());

        add(DeathSwapItem.builder(33, "Can't Interact").chinese("无法交互")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.setAttribute(target, Attributes.BLOCK_INTERACTION_RANGE, 0.0);
                    ctx.effects().apply(target, new ActiveEffect("no_interaction", 60 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.BLOCK_INTERACTION_RANGE, 4.5)));
                }).build());

        add(DeathSwapItem.builder(41, "Pumpkin Head").chinese("南瓜头")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                            new net.minecraft.world.item.ItemStack(Items.CARVED_PUMPKIN));
                    ctx.effects().apply(target, new ActiveEffect("pumpkin_head", 60 * 20, null,
                            p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                                    net.minecraft.world.item.ItemStack.EMPTY)));
                }).build());

        add(DeathSwapItem.builder(43, "Tiny Scale").chinese("迷你身材")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.setAttribute(target, Attributes.SCALE, 0.0625);
                    ctx.effects().apply(target, new ActiveEffect("tiny_scale", 80 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.SCALE, 1.0)));
                }).build());

        add(DeathSwapItem.builder(44, "Huge Scale").chinese("巨大身材")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.setAttribute(target, Attributes.SCALE, 10.0);
                    Mc.effect(target, MobEffects.JUMP_BOOST, 50, 2);
                    ctx.effects().apply(target, new ActiveEffect("huge_scale", 50 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.SCALE, 1.0)));
                }).build());

        add(DeathSwapItem.builder(58, "Forced Look Down").chinese("强制低头")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> ctx.effects().apply(target,
                        new ActiveEffect("look_down", 45 * 20,
                                p -> p.setXRot(90f), null))).build());

        add(DeathSwapItem.builder(68, "Minus 4 Hearts").chinese("减少4颗心")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.setAttribute(target, Attributes.MAX_HEALTH, 12.0);
                    if (target.getHealth() > 12.0f) {
                        target.setHealth(12.0f);
                    }
                    Mc.msg(target, "You lost 4 hearts!", ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.builder(80, "Spectator (20s)").chinese("旁观者(20秒)")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    target.setGameMode(GameType.SPECTATOR);
                    ctx.effects().apply(target, new ActiveEffect("spec_mode", 20 * 20, null,
                            p -> p.setGameMode(GameType.SURVIVAL)));
                }).build());

        add(DeathSwapItem.builder(92, "Ears Bleed").chinese("爆音")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> ctx.effects().apply(target,
                        new ActiveEffect("ears_bleed", 45 * 20,
                                p -> Mc.playSound(p, SoundEvents.WARDEN_SONIC_BOOM, 4.0f, 1.0f),
                                null))).build());
    }

    // ============================= SUMMONS =============================

    private void registerSummons() {
        add(DeathSwapItem.builder(8, "30 Villagers").chinese("30村民")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> summonRing(target, EntityTypes.VILLAGER, 30)).build());

        add(DeathSwapItem.builder(36, "Bee Horde").chinese("蜜蜂群")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> summonRing(target, EntityTypes.BEE, 25)).build());

        add(DeathSwapItem.builder(59, "Ghast Bombardment").chinese("恶魂轰炸")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> summonRing(target, EntityTypes.GHAST, 8)).build());

        add(DeathSwapItem.builder(67, "Giant Slime").chinese("巨型史莱姆")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Entity e = Mc.summon(Mc.level(target), EntityTypes.SLIME,
                            target.getX() + 4, target.getY() + 6, target.getZ() + 2);
                    if (e instanceof net.minecraft.world.entity.monster.cubemob.Slime slime) {
                        slime.setSize(8, true);
                    }
                }).build());

        add(DeathSwapItem.builder(100, "Giant Zombie").chinese("巨型僵尸")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Entity e = Mc.summon(Mc.level(target), EntityTypes.ZOMBIE,
                            target.getX(), target.getY() + 3, target.getZ());
                    if (e instanceof LivingEntity living && living.getAttribute(Attributes.SCALE) != null) {
                        living.getAttribute(Attributes.SCALE).setBaseValue(12.0);
                        living.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
                        living.setHealth(200.0f);
                    }
                }).build());

        add(DeathSwapItem.builder(105, "Summon the Warden").chinese("召唤监守者")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Entity warden = Mc.summon(Mc.level(target), EntityTypes.WARDEN,
                            target.getX(), target.getY(), target.getZ() + 2);
                    if (warden instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setPersistenceRequired();
                    }
                }).build());

        add(DeathSwapItem.builder(110, "30 Endermen").chinese("30末影人")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> summonRing(target, EntityTypes.ENDERMAN, 30)).build());

        add(DeathSwapItem.builder(40, "Lightning Strike").chinese("雷击")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.summon(Mc.level(target), EntityTypes.LIGHTNING_BOLT,
                            target.getX(), target.getY(), target.getZ());
                }).build());

        add(DeathSwapItem.builder(27, "Ravager Egg").chinese("劫掠兽刷怪蛋")
                .effect((ctx, self, t) -> Mc.give(self, Items.RAVAGER_SPAWN_EGG, 1)).build());
    }

    private void summonRing(ServerPlayer target, EntityType<?> type, int count) {
        ServerLevel level = Mc.level(target);
        Vec3 c = target.position();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 / count) * i;
            double x = c.x + Math.cos(angle) * 3;
            double z = c.z + Math.sin(angle) * 3;
            Mc.summon(level, type, x, c.y + 1, z);
        }
    }

    // ========================== WORLD EFFECTS ==========================

    private void registerWorldEffects() {
        add(DeathSwapItem.builder(15, "7x7 Air Cube").chinese("7x7空气方块")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> Mc.fillBox(Mc.level(target),
                        target.blockPosition(), 3, 3, 3, Blocks.AIR, true)).build());

        add(DeathSwapItem.builder(22, "Turn Surroundings to Stone").chinese("石化周围")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> Mc.fillBox(Mc.level(target),
                        target.blockPosition(), 10, 5, 10, Blocks.STONE, true)).build());

        add(DeathSwapItem.builder(35, "Lava Ceiling").chinese("熔岩天花板")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    BlockPos above = target.blockPosition().above(8);
                    Mc.fillBox(Mc.level(target), above, 12, 0, 12, Blocks.LAVA, false);
                }).build());

        add(DeathSwapItem.builder(42, "Flood with Water").chinese("水淹")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> Mc.fillBox(Mc.level(target),
                        target.blockPosition().above(2), 8, 6, 8, Blocks.WATER, true)).build());

        add(DeathSwapItem.builder(20, "Barrier Cage").chinese("屏障笼")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    ServerLevel level = Mc.level(target);
                    BlockPos p = target.blockPosition();
                    // Hollow box: fill the shell only.
                    int r = 3;
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dy = -1; dy <= 3; dy++) {
                            for (int dz = -r; dz <= r; dz++) {
                                boolean shell = Math.abs(dx) == r || Math.abs(dz) == r || dy == -1 || dy == 3;
                                if (shell) {
                                    Mc.setBlock(level, p.offset(dx, dy, dz), Blocks.BARRIER);
                                }
                            }
                        }
                    }
                }).build());

        add(DeathSwapItem.builder(61, "Prison").chinese("监狱")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    ServerLevel level = Mc.level(target);
                    BlockPos p = target.blockPosition();
                    Mc.fillBox(level, p.below(1), 5, 0, 4, Blocks.BEDROCK, false);   // floor
                    Mc.fillBox(level, p.above(3), 5, 0, 4, Blocks.BEDROCK, false);   // ceiling
                }).build());

        add(DeathSwapItem.builder(107, "Oppenheimer's Nuke").chinese("奥本海默的核弹")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Mc.explode(target, 12.0f);
                    Mc.explode(target, 8.0f);
                }).build());

        add(DeathSwapItem.builder(109, "Everything on Fire").chinese("一切燃烧")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    ServerLevel level = Mc.level(target);
                    BlockPos p = target.blockPosition();
                    for (int dx = -8; dx <= 8; dx++) {
                        for (int dz = -8; dz <= 8; dz++) {
                            BlockPos pos = p.offset(dx, 0, dz);
                            if (level.getBlockState(pos).isAir()
                                    && !level.getBlockState(pos.below()).isAir()) {
                                Mc.setBlock(level, pos, Blocks.FIRE);
                            }
                        }
                    }
                }).build());

        add(DeathSwapItem.builder(12, "Nether Portal").chinese("下界传送门")
                .effect((ctx, self, t) -> {
                    ServerLevel level = Mc.level(self);
                    BlockPos base = self.blockPosition().offset(2, 0, 0);
                    for (int y = 0; y <= 4; y++) {
                        for (int z = 0; z <= 3; z++) {
                            boolean frame = y == 0 || y == 4 || z == 0 || z == 3;
                            Mc.setBlock(level, base.offset(0, y, z),
                                    frame ? Blocks.OBSIDIAN : Blocks.NETHER_PORTAL);
                        }
                    }
                }).build());
    }

    // ============================= UTILITY =============================

    private void registerUtility() {
        add(DeathSwapItem.builder(18, "Creative Mode (10s)").chinese("创造模式(10秒)")
                .effect((ctx, self, t) -> {
                    self.setGameMode(GameType.CREATIVE);
                    ctx.effects().apply(self, new ActiveEffect("creative_mode", 10 * 20, null,
                            p -> p.setGameMode(GameType.SURVIVAL)));
                }).build());

        add(DeathSwapItem.builder(5, "Instant Swap").chinese("立即交换")
                .target(ItemTarget.EVERYONE)
                .effect((ctx, self, t) -> ctx.game().schedule(4, ctx.game()::doSwap)).build());

        add(DeathSwapItem.builder(66, "Darkness for Everyone").chinese("全员黑暗")
                .target(ItemTarget.EVERYONE)
                .effect((ctx, self, target) -> {
                    Mc.effect(target, MobEffects.DARKNESS, 30, 0);
                    Mc.effect(target, MobEffects.BLINDNESS, 10, 0);
                }).build());

        add(DeathSwapItem.builder(81, "Scatter Everyone Else").chinese("驱散其他人")
                .target(ItemTarget.ALL_OTHERS)
                .effect((ctx, self, target) -> ctx.game().spreadFarAway(target)).build());

        add(DeathSwapItem.builder(6, "Teleport Far Away").chinese("传送到远方")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> ctx.game().spreadFarAway(target)).build());

        add(DeathSwapItem.builder(38, "Teleport to Opponent").chinese("传送到对手")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> Mc.teleport(self, target.getX(), target.getY(), target.getZ()))
                .build());

        add(DeathSwapItem.builder(88, "Swap Places").chinese("交换位置")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    Vec3 selfPos = self.position();
                    Vec3 targetPos = target.position();
                    Mc.teleport(self, targetPos.x, targetPos.y, targetPos.z);
                    Mc.teleport(target, selfPos.x, selfPos.y, selfPos.z);
                }).build());

        add(DeathSwapItem.builder(24, "Clear a Random Inventory").chinese("清空随机背包")
                .target(ItemTarget.RANDOM_OPPONENT)
                .effect((ctx, self, target) -> {
                    target.getInventory().clearContent();
                    Mc.msg(target, "Your inventory was wiped!", ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.builder(49, "Inventory Full of Junk").chinese("背包塞满垃圾")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> {
                    for (int i = 0; i < 9; i++) {
                        Mc.give(target, Items.CLAY_BALL, 64);
                        Mc.give(target, Items.PODZOL, 64);
                    }
                }).build());

        add(DeathSwapItem.builder(90, "Low Gravity").chinese("低重力")
                .target(ItemTarget.EVERYONE)
                .effect((ctx, self, target) -> {
                    Mc.setAttribute(target, Attributes.GRAVITY, 0.01);
                    ctx.effects().apply(target, new ActiveEffect("low_grav", 180 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.GRAVITY, 0.08)));
                }).build());

        add(DeathSwapItem.builder(47, "Shorten Swap Timer").chinese("缩短交换计时")
                .availableWhen(p -> true)
                .effect((ctx, self, t) -> Mc.msg(self,
                        "Swap timer shortened!", ChatFormatting.GREEN)).build());

        add(DeathSwapItem.builder(103, "Continuous Pee").chinese("持续尿尿")
                .target(ItemTarget.OPPONENT)
                .effect((ctx, self, target) -> ctx.effects().apply(target,
                        new ActiveEffect("is_peeing", 60 * 20,
                                p -> Mc.playSound(p, SoundEvents.GENERIC_SPLASH, 0.3f, 2.0f),
                                null))).build());
    }
}
