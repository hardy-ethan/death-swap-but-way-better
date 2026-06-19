package com.deathswap.items;

import com.deathswap.effects.ActiveEffect;
import com.deathswap.util.Mc;
import com.deathswap.util.Mc.FillMode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static net.minecraft.world.item.DyeColor.*;

/**
 * Catalogue of all 110 death-swap items. Each entry reproduces the observable
 * effect of the matching {@code items/use/<n>} (and helper) functions from the
 * datapack, plus the dyed display item from {@code items/items/*}.
 *
 * <p>Durations are taken straight from the datapack scoreboards: an effect score
 * of N (decremented by 5 per tick in {@code active_items}) lasts N/100 seconds.
 */
public final class ItemRegistry {

    // Vanilla attribute base defaults, used to undo temporary changes.
    private static final double DEF_SPEED = 0.1, DEF_JUMP = 0.42, DEF_SCALE = 1.0,
            DEF_INTERACT = 4.5, DEF_BREAK = 1.0, DEF_GRAVITY = 0.08, DEF_FALL = 1.0, DEF_CAMERA = 4.0;

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

    /** Pick three distinct items (the datapack rolls 3 of 1..110, avoiding repeats). */
    public List<DeathSwapItem> pickThree(ServerPlayer player, long seed) {
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
        register1to30();
        register31to60();
        register61to90();
        register91to110();
    }

    // ---- shared helpers ----

    private static void announce(com.deathswap.game.GameManager game, ServerPlayer self,
                                 String verb, ServerPlayer target, ChatFormatting color) {
        // Use the scoreboard name (always the plain username) rather than
        // getName(), which can render blank. Show the target whenever one is given
        // — even when it's the user themselves — so the log never drops the "who".
        String who = target == null ? "" : " " + target.getScoreboardName();
        game.broadcast(">> " + self.getScoreboardName() + " --> " + verb + who, color);
    }

    /** Origin block for relative fills/builds (the executing player's feet). */
    private static BlockPos at(ServerPlayer p) {
        return p.blockPosition();
    }

    /** Native equivalent of {@code effect clear @a minecraft:night_vision}. */
    private static void clearNightVisionAll(ItemContext ctx) {
        for (ServerPlayer p : ctx.server().getPlayerList().getPlayers()) {
            p.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /** The block tags {@code extra/all_to_stone} converts (the union it replaces). */
    private static final String[] ALL_TO_STONE_TAG_NAMES = {
            "all_signs", "ancient_city_replaceable", "anvil", "azalea_grows_on", "banners",
            "base_stone_nether", "base_stone_overworld", "beacon_base_blocks", "beds", "buttons",
            "cauldrons", "cave_vines", "corals", "diamond_ores", "dragon_immune", "emerald_ores",
            "fences", "gold_ores", "ice", "inside_step_sound_blocks", "lapis_ores",
            "lava_pool_stone_cannot_replace", "leaves", "logs", "mineable/axe", "mineable/pickaxe",
            "mineable/hoe", "mineable/shovel", "overworld_carver_replaceables"
    };
    private static List<TagKey<Block>> allToStoneTags;

    private static List<TagKey<Block>> allToStoneTags() {
        if (allToStoneTags == null) {
            List<TagKey<Block>> tags = new ArrayList<>();
            for (String name : ALL_TO_STONE_TAG_NAMES) {
                tags.add(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", name)));
            }
            allToStoneTags = tags;
        }
        return allToStoneTags;
    }

    /**
     * Native equivalent of {@code extra/all_to_stone}: replace every block in the
     * box that belongs to any of the ~29 vanilla tags with {@code to}. Uses a
     * no-neighbour-update set to stay light on large boxes.
     */
    private static boolean isAllToStoneTagged(BlockState s) {
        for (TagKey<Block> tag : allToStoneTags()) {
            if (s.is(tag)) return true;
        }
        return false;
    }

    private static void replaceTagged(ServerLevel level, BlockPos c1, BlockPos c2, BlockState to) {
        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    cur.set(x, y, z);
                    if (isAllToStoneTagged(level.getBlockState(cur))) {
                        level.setBlock(cur, to, Block.UPDATE_CLIENTS);
                    }
                }
    }

    /** A flat single-Y rectangular fill, spread across ticks (for the huge sun-blocking ceiling). */
    private static java.util.function.BooleanSupplier layerJob(ServerLevel level, int minX, int maxX,
                                                               int y, int minZ, int maxZ, BlockState state) {
        final int[] cur = {minX, minZ};
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return () -> {
            int budget = 20000;
            while (budget-- > 0) {
                if (cur[0] > maxX) {
                    return true;
                }
                pos.set(cur[0], y, cur[1]);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                if (++cur[1] > maxZ) {
                    cur[1] = minZ;
                    cur[0]++;
                }
            }
            return cur[0] > maxX;
        };
    }

    /**
     * Exact {@code misc/parkour_civ}: light up the 20 wall planes (z = ±1..±19)
     * over the full world column, replacing tagged terrain with {@code light[1]}.
     * Returned as a build job that processes a budget of positions per tick so the
     * ~600k-position sweep never freezes the server.
     */
    private static java.util.function.BooleanSupplier parkourJob(ServerLevel level, int baseX, int baseZ) {
        final int[] zPlanes = {19, 17, 15, 13, 11, 9, 7, 5, 3, 1, -1, -3, -5, -7, -9, -11, -13, -15, -17, -19};
        final int yMin = -63, yMax = 317, xMin = -29, xMax = 29;
        final BlockState lightState = Mc.light(1);
        final int[] cur = {0, xMin, yMin}; // planeIndex, dx, y
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return () -> {
            int budget = 20000;
            while (budget-- > 0) {
                if (cur[0] >= zPlanes.length) {
                    return true;
                }
                pos.set(baseX + cur[1], cur[2], baseZ + zPlanes[cur[0]]);
                if (isAllToStoneTagged(level.getBlockState(pos))) {
                    level.setBlock(pos, lightState, Block.UPDATE_CLIENTS);
                }
                if (++cur[2] > yMax) {
                    cur[2] = yMin;
                    if (++cur[1] > xMax) {
                        cur[1] = xMin;
                        cur[0]++;
                    }
                }
            }
            return cur[0] >= zPlanes.length;
        };
    }

    // ============================ ITEMS 1-30 ============================

    private void register1to30() {
        add(DeathSwapItem.of(1, LIGHT_BLUE, ChatFormatting.AQUA,
                "Give a player speed 1 billion: 40 secs", "Even the Flash can't keep up with this level of speed")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.effect(t, MobEffects.SPEED, 42, 0);
                    Mc.setAttribute(t, Attributes.MOVEMENT_SPEED, 5.5);
                    ctx.effects().apply(t, new ActiveEffect("mega_speed", 41 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.MOVEMENT_SPEED, DEF_SPEED)));
                    announce(ctx.game(), self, "Gave extremely fast, un-navigable speed to", t, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(2, GRAY, ChatFormatting.AQUA,
                "Give yourself materials to build a wither", "Suddenly Minecraft Storymode...")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.SOUL_SAND, 4);
                    Mc.give(self, Items.WITHER_SKELETON_SKULL, 3);
                }).build());

        add(DeathSwapItem.of(3, ORANGE, ChatFormatting.GOLD,
                "Shield yourself from negative items: 2 mins", "Nobody can use any items on you for 2 minutes!")
                .effect((ctx, self, t) -> shield(ctx, self, 122)).build());

        add(DeathSwapItem.of(4, PURPLE, ChatFormatting.LIGHT_PURPLE,
                "Teleport someone to the End", "Lets beat Minecraft!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    // Datapack 4b: an end_portal pad at fixed overworld coords; the target is
                    // dropped on it and falls through to the End.
                    ServerLevel ow = ctx.server().overworld();
                    Mc.fillState(ow, new BlockPos(-3, 79, -11), new BlockPos(1, 77, -7), Mc.light(15), FillMode.ALL);
                    Mc.fill(ow, new BlockPos(-3, 76, -11), new BlockPos(1, 76, -7), Blocks.END_PORTAL, FillMode.ALL);
                    Mc.teleportTo(t, ow, -1, 77, -9, 180, 0);
                    announce(ctx.game(), self, "Teleported to the End dimension:", t, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(5, YELLOW, ChatFormatting.YELLOW,
                "Swap all players right NOW!", "Makes the swap happen half a second after dropping")
                .effect((ctx, self, t) -> {
                    ctx.game().instantSwap();
                    announce(ctx.game(), self, "Made the swap happen right NOW!", null, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(6, PINK, ChatFormatting.LIGHT_PURPLE,
                "Teleport someone really far away", "Is someones trap too good? Teleport them away from it!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.game().spreadFarAway(t, true);
                    announce(ctx.game(), self, "Teleported far, far away:", t, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(7, LIGHT_BLUE, ChatFormatting.AQUA,
                "Give yourself a super fast shovel", "In case someone tries to do the boring-old gravel/sand trap")
                .effect((ctx, self, t) -> {
                    ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
                    Mc.enchant(self, shovel, Enchantments.EFFICIENCY, 5);
                    shovel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            Component.literal("Super-fast shovel").withStyle(ChatFormatting.AQUA));
                    Mc.giveStack(self, shovel);
                }).build());

        add(DeathSwapItem.of(8, BROWN, ChatFormatting.GOLD,
                "Spawn 100 villagers on someone", "It's my party and I'll cry if I want too...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) ->
                        spawnHorde(ctx, self, t, EntityTypes.VILLAGER, "Spawned 100 villagers at")).build());

        add(DeathSwapItem.of(9, LIGHT_GRAY, ChatFormatting.GOLD,
                "Summon an 300 block-tall gravel tower", "You can never go wrong with the basics! ...right?")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    // misc/gravel_base — the lit obsidian launch pad and the seed gravel block.
                    Mc.setBlock(lvl, o.offset(3, -1, 0), Blocks.OBSIDIAN);
                    Mc.fillState(lvl, o.offset(3, 0, 0), o.offset(3, 1, 0), Mc.light(12), FillMode.ALL);
                    Mc.setBlock(lvl, o.offset(3, 2, 0), Blocks.DIRT);
                    Mc.setBlock(lvl, o.offset(3, 3, 0), Blocks.GRAVEL);
                    Mc.fill(lvl, o.offset(2, 3, 0), o.offset(2, 4, 0), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(4, 3, 0), o.offset(4, 4, 0), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 3, 1), o.offset(3, 4, 1), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 3, -1), o.offset(3, 4, -1), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(2, 0, 0), o.offset(1, 1, 0), Mc.light(5), FillMode.ALL);
                    Mc.fill(lvl, o.offset(4, 0, 0), o.offset(4, 2, 0), Blocks.OBSIDIAN, FillMode.ALL);
                    // misc/gravel_up grows the column upward to the build limit over time.
                    ctx.game().addGravelTower(lvl, o.getX() + 3, o.getY() + 3, o.getZ());
                    Mc.setYaw(self, -90);
                    clearNightVisionAll(ctx);
                    Mc.msg(self, "A gravel tower was placed right in front of you!", ChatFormatting.WHITE);
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 99f, 1.0f);
                }).build());

        add(DeathSwapItem.of(10, BLUE, ChatFormatting.BLUE,
                "Teleport someone to the middle of the ocean", "If Tom Hanks could survive it, then anyone can")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    int[][] oceans = {{-20380498, -22881232}, {589330, 6311587}, {-7891432, 608764},
                            {169416, -8009858}, {-9885188, 75470}, {1999709, -4887449}, {-6077095, 3556330},
                            {17969202, 11749377}, {-13862972, 7667282}};
                    int[] c = oceans[t.getRandom().nextInt(oceans.length)];
                    Mc.teleportTo(t, ctx.server().overworld(), c[0], 64, c[1], t.getYRot(), t.getXRot());
                    announce(ctx.game(), self, "Teleported to the middle of an ocean:", t, ChatFormatting.BLUE);
                }).build());

        add(DeathSwapItem.of(11, LIME, ChatFormatting.GREEN,
                "Disable a player's ability to jump: 60 secs", "Must've eaten too much McDonald's")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.JUMP_STRENGTH, 0.0);
                    ctx.effects().apply(t, new ActiveEffect("jump_disabled", 61 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.JUMP_STRENGTH, DEF_JUMP)));
                    Mc.title(t, " ", "You can't jump: 1 minute", ChatFormatting.WHITE, ChatFormatting.GREEN);
                    announce(ctx.game(), self, "Disabled the jump of", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(12, RED, ChatFormatting.RED,
                "Place a Nether Portal next to you", "Skip the pro-speedrun strat")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    Mc.fill(lvl, o.offset(3, 0, 0), o.offset(3, 0, 3), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 4, 0), o.offset(3, 4, 3), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 0, 0), o.offset(3, 4, 0), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 0, 3), o.offset(3, 4, 3), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(3, 1, 1), o.offset(3, 3, 2), Mc.netherPortal(Direction.Axis.Z), FillMode.ALL);
                    Mc.fill(lvl, o.offset(1, 0, 0), o.offset(2, 2, 2), Blocks.AIR, FillMode.ALL);
                    Mc.setYaw(self, -90);
                    Mc.msg(self, ">> A nether portal was placed in front of you! <<", ChatFormatting.RED);
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 99f, 1.0f);
                    Mc.playSound(self, SoundEvents.BLAZE_SHOOT, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(13, BROWN, ChatFormatting.GOLD,
                "Give yourself 32 steak", "Isn't hunger just an annoying problem in general?")
                .effect((ctx, self, t) -> Mc.give(self, Items.COOKED_BEEF, 32)).build());

        add(DeathSwapItem.of(14, RED, ChatFormatting.GOLD,
                "Spawn TNT on someone", "They won't expect it!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    primeTnt(Mc.level(t), t.position(), 0, 1, 0, (byte) 85);
                    Mc.title(t, "RUN away!", "", ChatFormatting.RED, ChatFormatting.WHITE);
                    announce(ctx.game(), self, "Spawned ignited TNT on", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(15, WHITE, ChatFormatting.WHITE,
                "Place a 7x7x7 cube of air on someone", "All blocks in a 3-block radius of them turns to air!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos o = at(t);
                    Mc.destroyBox(Mc.level(t), o.offset(3, 3, 3), o.offset(-3, -3, -3));
                    announce(ctx.game(), self, "Placed a 7x7x7 cube of air, deleting all blocks on", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(16, ORANGE, ChatFormatting.YELLOW,
                "Teleport someone to y= -60", "Way down, Hadestown...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    Mc.fill(lvl, new BlockPos(o.getX() + 2, -62, o.getZ() + 2),
                            new BlockPos(o.getX() - 2, -62, o.getZ() - 2), Blocks.BEDROCK, FillMode.ALL);
                    Mc.fillState(lvl, new BlockPos(o.getX() + 2, -61, o.getZ() + 2),
                            new BlockPos(o.getX() - 2, -59, o.getZ() - 2), Mc.light(1), FillMode.ALL);
                    Mc.fill(lvl, new BlockPos(o.getX() + 2, -58, o.getZ() + 2),
                            new BlockPos(o.getX() - 2, -58, o.getZ() - 2), Blocks.DEEPSLATE, FillMode.ALL);
                    Mc.teleportTo(t, lvl, t.getX(), -61, t.getZ(), t.getYRot(), t.getXRot());
                    Mc.setBlock(lvl, new BlockPos(o.getX(), -61, o.getZ()), Blocks.TORCH);
                    clearNightVisionAll(ctx);
                    announce(ctx.game(), self, "Teleported to Y = -60 (bedrock layer):", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(17, GRAY, ChatFormatting.YELLOW,
                "Spawn a hole that goes to the void", "They might end up in the End!")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    Mc.fill(lvl, new BlockPos(o.getX() + 3, o.getY() + 2, o.getZ()),
                            new BlockPos(o.getX() + 6, -64, o.getZ() + 2), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(1, 0, 0), o.offset(5, 1, 0), Blocks.AIR, FillMode.ALL);
                    Mc.setYaw(self, -90);
                    Mc.msg(self, ">> A hole to the void summoned in front of you! <<", ChatFormatting.WHITE);
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 99f, 1.0f);
                }).build());

        add(DeathSwapItem.of(18, MAGENTA, ChatFormatting.AQUA,
                "Enter creative mode for 10 seconds", "You can get ANYTHING you want or need!")
                .effect((ctx, self, t) -> {
                    self.setGameMode(GameType.CREATIVE);
                    ctx.effects().apply(self, new ActiveEffect("creative_mode", (int) (10.5 * 20), null,
                            p -> p.setGameMode(GameType.SURVIVAL)));
                    Mc.msg(self, ">>> You are in CREATIVE MODE for 10 seconds! <<<", ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(19, RED, ChatFormatting.AQUA,
                "Give yourself 4 pieces of TNT", "You can be like J.D. from Heathers!")
                .effect((ctx, self, t) -> Mc.give(self, Items.TNT, 4)).build());

        add(DeathSwapItem.of(20, WHITE, ChatFormatting.WHITE,
                "Trap a player in a barrier block cage", "To say they'll be pissed is a gross understatement")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos o = at(t);
                    Mc.fill(Mc.level(t), o.offset(4, 3, 4), o.offset(-4, -2, -4), Blocks.BARRIER, FillMode.HOLLOW);
                    Mc.setBlock(Mc.level(t), o.offset(0, -1, 0), Blocks.TORCH);
                    announce(ctx.game(), self, "Trapped in an annoying barrier block cage:", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(21, GREEN, ChatFormatting.GREEN,
                "Give a player motion sickness: 30 seconds", "Bro drove through the Rocky Mountains")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("motion_sick", 46 * 20,
                            p -> Mc.rotateRelative(p, 10f, 0.1f), null));
                    announce(ctx.game(), self, "Gave motion sickness to", t, ChatFormatting.DARK_GREEN);
                }).build());

        add(DeathSwapItem.of(22, LIGHT_GRAY, ChatFormatting.WHITE,
                "Turn all nearby blocks of a player to stone", "The stone age comes to Minecraft")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    replaceTagged(lvl, o.offset(12, 6, 12), o.offset(-1, -6, -12), Blocks.STONE.defaultBlockState());
                    replaceTagged(lvl, o.offset(12, -2, 12), o.offset(-12, -6, -12), Blocks.STONE.defaultBlockState());
                    announce(ctx.game(), self, "Changed all nearby blocks to stone for", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(23, BROWN, ChatFormatting.GOLD,
                "Put a curse of binding leather chestplate on someone", "Thanks Mojang for adding curse of binding...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
                    Mc.enchant(t, chest, Enchantments.BINDING_CURSE, 1);
                    chest.set(net.minecraft.core.component.DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
                    t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, chest);
                    announce(ctx.game(), self, "Put a curse-of-binding leather tunic on", t, ChatFormatting.GOLD);
                }).build());

        add(DeathSwapItem.of(24, ORANGE, ChatFormatting.RED,
                "/clear a RANDOM person's inventory (could be you!!)",
                "You WON'T get to choose who gets cleared -- the game decides, and it could be you!")
                .effect((ctx, self, t) -> {
                    List<ServerPlayer> all = ctx.game().alivePlayers();
                    ServerPlayer victim = all.get(self.getRandom().nextInt(all.size()));
                    victim.getInventory().clearContent();
                    Mc.title(victim, " ", self.getScoreboardName() + " cleared your inventory!",
                            ChatFormatting.WHITE, ChatFormatting.RED);
                    // Always name the unlucky player explicitly (it can be the user).
                    ctx.game().broadcast(">> " + self.getScoreboardName()
                            + " --> Cleared " + victim.getScoreboardName()
                            + "'s inventory! (randomly chosen)", ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(25, GRAY, ChatFormatting.WHITE,
                "Make someone leave a bedrock trail: 40 secs", "Wherever they walk, the blocks below them turn to bedrock.")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos[] last = {null};
                    ctx.effects().apply(t, new ActiveEffect("bedrock_trail", 72 * 20, p -> {
                        // Replace the block the player is standing on (datapack ~-0.9),
                        // without triggering neighbour/light updates — those are what
                        // made a 36s-long per-tick trail tank the server's tick rate.
                        ServerLevel lvl = Mc.level(p);
                        BlockPos cur = BlockPos.containing(p.getX(), p.getY() - 0.9, p.getZ());
                        // Fill any gap since the previous placement so the player can't
                        // outrun the trail (and fall through it) when the server lags.
                        if (last[0] != null) {
                            int steps = Math.max(Math.abs(cur.getX() - last[0].getX()),
                                    Math.abs(cur.getZ() - last[0].getZ()));
                            if (steps > 0 && steps <= 8) { // skip the jump after a swap/teleport
                                for (int i = 1; i < steps; i++) {
                                    double f = (double) i / steps;
                                    Mc.setBlockFast(lvl, new BlockPos(
                                            (int) Math.round(last[0].getX() + (cur.getX() - last[0].getX()) * f),
                                            (int) Math.round(last[0].getY() + (cur.getY() - last[0].getY()) * f),
                                            (int) Math.round(last[0].getZ() + (cur.getZ() - last[0].getZ()) * f)),
                                            Blocks.BEDROCK);
                                }
                            }
                        }
                        Mc.setBlockFast(lvl, cur, Blocks.BEDROCK);
                        last[0] = cur;
                    }, null));
                    Mc.title(t, " ", "Look below you!", ChatFormatting.WHITE, ChatFormatting.WHITE);
                    announce(ctx.game(), self, "Made a bedrock trail follow", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(26, GRAY, ChatFormatting.WHITE,
                "Give someone blindness & darkness: 40 secs", "Hey, who turned out the lights??")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.effect(t, MobEffects.DARKNESS, 41, 5);
                    Mc.effect(t, MobEffects.BLINDNESS, 41, 5);
                    announce(ctx.game(), self, "Gave blindness & darkness to", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(27, LIGHT_GRAY, ChatFormatting.WHITE,
                "Give yourself one ravager spawn egg", "You can't mess this one up...")
                .effect((ctx, self, t) -> Mc.give(self, Items.RAVAGER_SPAWN_EGG, 1)).build());

        add(DeathSwapItem.of(28, RED, ChatFormatting.RED,
                "Give yourself an elytra & fireworks", "Because fall damage-based traps are just too lame")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.ELYTRA, 1);
                    Mc.give(self, Items.FIREWORK_ROCKET, 8);
                }).build());

        add(DeathSwapItem.of(29, WHITE, ChatFormatting.WHITE,
                "Give yourself a milk bucket & 2 golden apples", "Insert 2016 'he needs some milk' meme here")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.MILK_BUCKET, 1);
                    Mc.give(self, Items.GOLDEN_APPLE, 2);
                }).build());

        add(DeathSwapItem.of(30, ORANGE, ChatFormatting.GOLD,
                "Give yourself a fire resistance potion", "Because the Nether exists, yunno?")
                .effect((ctx, self, t) -> Mc.givePotion(self, Potions.LONG_FIRE_RESISTANCE)).build());
    }

    // ============================ ITEMS 31-60 ===========================

    private void register31to60() {
        add(DeathSwapItem.of(31, ORANGE, ChatFormatting.GOLD,
                "Shield yourself from negative items: 3 mins", "Nobody can use any items on you for 3 minutes!")
                .effect((ctx, self, t) -> shield(ctx, self, 182)).build());

        add(DeathSwapItem.of(32, LIGHT_GRAY, ChatFormatting.WHITE,
                "Spawn falling anvils above someone", "A worse concussion than in NFL Football")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    Mc.fill(lvl, o, o.offset(0, 1, 0), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(1, 2, 1), o.offset(-1, 2, -1), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(2, 3, 2), o.offset(-2, 3, -2), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 4, 3), o.offset(-3, 8, -3), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(3, 6, 3), o.offset(-3, 8, -3), Blocks.ANVIL, FillMode.ALL);
                    Mc.title(t, ">> HEADS UP!! <<", "", ChatFormatting.RED, ChatFormatting.WHITE);
                    announce(ctx.game(), self, "Spawned falling anvils above", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(33, CYAN, ChatFormatting.AQUA,
                "Put someone in adventure mode: 40 secs", "Minecraft without the 'mine' part")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.BLOCK_INTERACTION_RANGE, 0.0);
                    ctx.effects().apply(t, new ActiveEffect("no_interaction", 31 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.BLOCK_INTERACTION_RANGE, DEF_INTERACT)));
                    Mc.title(t, " ", ">> You're in adventure mode for 60 secs! <<", ChatFormatting.WHITE, ChatFormatting.AQUA);
                    announce(ctx.game(), self, "Disabled block interaction for", t, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(34, YELLOW, ChatFormatting.AQUA,
                "Place bells all around someone", "Ring Ring Ring Ring Ring Bing Ring Ring Ring (x1000)")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos o = at(t);
                    Mc.fill(Mc.level(t), o.offset(14, 6, 14), o.offset(-14, -6, -14), Blocks.BELL, FillMode.AIR_ONLY);
                    clearNightVisionAll(ctx);
                    announce(ctx.game(), self, "Replaced all air around with bells for", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(35, ORANGE, ChatFormatting.GOLD,
                "Place a layer of lava above someone", "La-la-la lava...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    Mc.fill(lvl, o, o.offset(0, 1, 0), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(1, 2, 1), o.offset(-1, 12, -1), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(28, 9, 28), o.offset(-28, 10, -28), Blocks.LAVA, FillMode.ALL);
                    announce(ctx.game(), self, "Spawned a layer of lava above", t, ChatFormatting.GOLD);
                }).build());

        add(DeathSwapItem.of(36, YELLOW, ChatFormatting.GOLD,
                "Spawn a horde of bees around someone", "Good thing bee allergies are rare.. right?")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.summonRel(t, EntityTypes.BEE, 0, 0, 0);
                    spawnOverTime(ctx, t, EntityTypes.BEE, 25, 4);
                    announce(ctx.game(), self, "Summoned a horde of bees around", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(37, LIGHT_BLUE, ChatFormatting.AQUA,
                "Give yourself full diamond armor & tools", "The Pro's shortcut")
                .effect((ctx, self, t) -> {
                    for (var it : new net.minecraft.world.item.Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                            Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE,
                            Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE}) {
                        Mc.give(self, it, 1);
                    }
                    Mc.give(self, Items.DIAMOND, 1);
                }).build());

        add(DeathSwapItem.of(38, PURPLE, ChatFormatting.LIGHT_PURPLE,
                "Teleport to someone (& surprise attack them?)", "It would indeed be a deadly swap")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.teleportTo(self, Mc.level(t), t.getX(), t.getY(), t.getZ(), self.getYRot(), self.getXRot());
                    announce(ctx.game(), self, "Teleported to", t, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(39, CYAN, ChatFormatting.LIGHT_PURPLE,
                "Give yourself 9 obsidian", "Almost enough to box someone up.")
                .effect((ctx, self, t) -> Mc.give(self, Items.OBSIDIAN, 9)).build());

        add(DeathSwapItem.of(40, ORANGE, ChatFormatting.GOLD,
                "Strike someone with lightning", "Statistically unlikely to survive")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.summonRel(t, EntityTypes.LIGHTNING_BOLT, 0, 0, 0);
                    Mc.summonRel(t, EntityTypes.LIGHTNING_BOLT, 0, 2, 0);
                    Mc.setBlock(Mc.level(t), at(t), Blocks.FIRE);
                    announce(ctx.game(), self, "Struck with lightning:", t, ChatFormatting.GOLD);
                }).build());

        add(DeathSwapItem.of(41, ORANGE, ChatFormatting.YELLOW,
                "Put a pumpkin head on someone: 1 min", "Experience Minecraft like Jack from Nightmare Before Xmas")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    t.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                    ctx.effects().apply(t, new ActiveEffect("pumpkin_head", 61 * 20, null,
                            p -> p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY)));
                    announce(ctx.game(), self, "Put a pumpkin head on", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(42, CYAN, ChatFormatting.AQUA,
                "Drown someone with a flood of water", "Waterloo, I was defeated & you won the war...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos o = at(t);
                    Mc.fill(Mc.level(t), o.offset(20, 12, 20), o.offset(-20, -1, -20), Blocks.WATER, FillMode.AIR_ONLY);
                    Mc.fill(Mc.level(t), o.offset(20, -2, 20), o.offset(-20, -6, -20), Blocks.WATER, FillMode.AIR_ONLY);
                    announce(ctx.game(), self, "Drowned with a flood of water:", t, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(43, BROWN, ChatFormatting.RED,
                "Make someone extremely tiny", "The official Ant-Man mod in Minecraft")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.SCALE, 0.0625);
                    ctx.effects().apply(t, new ActiveEffect("tiny_scale", 80 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.SCALE, DEF_SCALE)));
                    Mc.title(t, " ", ">> You are very smol! <<", ChatFormatting.WHITE, ChatFormatting.RED);
                    announce(ctx.game(), self, "Made extremely tiny:", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(44, LIME, ChatFormatting.GREEN,
                "Make someone extremely huge", "Alice in Minecraft-Land")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.SCALE, 16.0);
                    Mc.effect(t, MobEffects.JUMP_BOOST, 51, 3);
                    ctx.effects().apply(t, new ActiveEffect("huge_scale", 60 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.SCALE, DEF_SCALE)));
                    Mc.title(t, " ", ">> You are very beeg! <<", ChatFormatting.WHITE, ChatFormatting.GREEN);
                    announce(ctx.game(), self, "Made extremely huge:", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(45, YELLOW, ChatFormatting.GOLD,
                "Give yourself a totem of undying", "Alice in Minecraft-Land")
                .effect((ctx, self, t) -> Mc.give(self, Items.TOTEM_OF_UNDYING, 1)).build());

        add(DeathSwapItem.of(46, LIGHT_GRAY, ChatFormatting.WHITE,
                "Place a layer of gravel above EVERYONE except you", "Cloudy with a chance of gravel")
                .effect((ctx, self, t) -> {
                    for (ServerPlayer p : ctx.game().alivePlayers()) {
                        if (p == self || ctx.effects().hasEffect(p.getUUID(), "shield")) continue;
                        BlockPos o = at(p);
                        Mc.fill(Mc.level(p), o, o.offset(0, 13, 0), Blocks.AIR, FillMode.ALL);
                        Mc.fill(Mc.level(p), o.offset(20, 12, 20), o.offset(-20, 12, -20), Blocks.GRAVEL, FillMode.ALL);
                    }
                    announce(ctx.game(), self, "Placed a layer of gravel above everyone else", null, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(47, PURPLE, ChatFormatting.RED,
                "Make the swap cycle length 30 seconds shorter", "More frequent swaps means more chaos!")
                .effect((ctx, self, t) -> {
                    ctx.game().shortenSwapTimer(30);
                    announce(ctx.game(), self, "Reduced the time between swaps by 30 seconds", null, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(48, GRAY, ChatFormatting.WHITE,
                "Block out the sun for someone", "Ehh, Vitamin D is overrated anyways")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    Mc.fill(lvl, new BlockPos(o.getX() + 64, 317, o.getZ() + 64),
                            new BlockPos(o.getX() - 64, 317, o.getZ() - 64), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, new BlockPos(o.getX() + 16, 316, o.getZ() + 16),
                            new BlockPos(o.getX() - 16, 316, o.getZ() - 16), Blocks.OBSIDIAN, FillMode.ALL);
                    // The 353x353 layer at y318 is built across ticks (forceload is implicit: setBlock loads chunks).
                    ctx.game().addBuildJob(layerJob(lvl, o.getX() - 176, o.getX() + 176, 318,
                            o.getZ() - 176, o.getZ() + 176, Blocks.OBSIDIAN.defaultBlockState()));
                    clearNightVisionAll(ctx);
                    announce(ctx.game(), self, "Blocked out the sun with an obsidian ceiling for", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(49, LIME, ChatFormatting.YELLOW,
                "Fill up someone's inventory with junk", "Jason Momoa's Garbage Man has joined the server")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    net.minecraft.world.item.Item[] junk = {Items.CLAY_BALL, Items.PODZOL, Items.BIG_DRIPLEAF,
                            Items.PURPUR_PILLAR, Items.FIRE_CORAL_FAN, Items.SHORT_GRASS, Items.TINTED_GLASS,
                            Items.POISONOUS_POTATO, Items.GHAST_TEAR, Items.FLOWER_POT, Items.ITEM_FRAME,
                            Items.TERRACOTTA, Items.HONEYCOMB, Items.PUMPKIN_SEEDS, Items.CACTUS, Items.DEAD_BUSH,
                            Items.WET_SPONGE, Items.GRAVEL, Items.HEAVY_CORE, Items.CRAFTING_TABLE,
                            Items.BEETROOT_SEEDS, Items.CRIMSON_NYLIUM, Items.BELL, Items.NAME_TAG, Items.IRON_DOOR};
                    for (net.minecraft.world.item.Item it : junk) Mc.give(t, it, 64);
                    announce(ctx.game(), self, "Filled the inventory with useless junk:", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(50, RED, ChatFormatting.RED,
                "Transform the world around someone into the Nether", "Just like the A Minecraft Movie (2025)!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("nether_world", 52 * 20, p -> {
                        BlockPos o = p.blockPosition();
                        Mc.fill(Mc.level(p), o.offset(3, 5, 3), o.offset(-3, -2, -3), Blocks.NETHERRACK, FillMode.NATURAL_ONLY);
                    }, null));
                    announce(ctx.game(), self, "Turned the world into the Nether around", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(51, WHITE, ChatFormatting.WHITE,
                "Spawn a bunch of cobwebs on someone", "Now that's what I call a sticky situation!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos o = at(t);
                    Mc.fill(Mc.level(t), o.offset(2, 3, 2), o.offset(-2, 0, -2), Blocks.COBWEB, FillMode.AIR_ONLY);
                    announce(ctx.game(), self, "Placed a bunch of cobwebs on", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(52, PURPLE, ChatFormatting.AQUA,
                "Give yourself 3 ender pearls", "Fall damage death traps are boring anyway")
                .effect((ctx, self, t) -> Mc.give(self, Items.ENDER_PEARL, 3)).build());

        add(DeathSwapItem.of(53, BROWN, ChatFormatting.YELLOW,
                "Spawn a village right where you are", "Villages are overpowered anyway")
                .effect((ctx, self, t) -> {
                    Mc.placeStructure(Mc.level(self), at(self), "village_plains");
                    Mc.msg(self, "You now have access to a village! (IMPORTANT: If you are underground, "
                            + "it spawned up on the surface!)", ChatFormatting.YELLOW);
                    Mc.playSound(self, SoundEvents.ITEM_PICKUP, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(54, ORANGE, ChatFormatting.YELLOW,
                "Spawn a desert temple right where you are", "Yunno, for that classic TNT trap we all love...")
                .effect((ctx, self, t) -> {
                    Mc.placeStructure(Mc.level(self), at(self), "desert_pyramid");
                    Mc.msg(self, "You now have access to a desert temple/pyramid! Good job! (Note: If you "
                            + "don't see it on the surface it may have spawned underground)", ChatFormatting.YELLOW);
                    Mc.playSound(self, SoundEvents.ITEM_PICKUP, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(55, GRAY, ChatFormatting.YELLOW,
                "Summon a California earthquake on EVERYONE except you", "Please excuse my trauma")
                .effect((ctx, self, t) -> {
                    for (ServerPlayer p : ctx.game().alivePlayers()) {
                        if (p == self || ctx.effects().hasEffect(p.getUUID(), "shield")) continue;
                        earthquake(ctx, p);
                    }
                    announce(ctx.game(), self, "Summoned a California-level earthquake on the world", null, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(56, BROWN, ChatFormatting.YELLOW,
                "Block someone from using any items: 3 mins", "The whole gimmick of this map, the items, they can't even use")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("blockedItems", 155 * 20, null, null));
                    ctx.game().data(t).clearOffer();
                    Mc.title(t, " ", ">> You can't use items for 3 minutes! <<", ChatFormatting.WHITE, ChatFormatting.RED);
                    announce(ctx.game(), self, "Blocked item usage for", t, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(57, YELLOW, ChatFormatting.YELLOW,
                "Give yourself an enchanted golden apple", "Notch would be proud of you")
                .effect((ctx, self, t) -> Mc.give(self, Items.ENCHANTED_GOLDEN_APPLE, 1)).build());

        add(DeathSwapItem.of(58, GREEN, ChatFormatting.GREEN,
                "Force a player to look down for 45 secs", "Always look where you're stepping!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("look_down", 45 * 20,
                            p -> Mc.rotateRelative(p, 0f, 8f), null));
                    announce(ctx.game(), self, "Forced a downward look for 45 seconds:", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(59, WHITE, ChatFormatting.GREEN,
                "Bombard someone with a ton of ghasts", "WHEWW!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    double[][] off = {{0, 5, 0}, {0, 5, 0}, {0, 7, 0}, {0, 9, 0}, {6, 5, 0}, {-6, 5, 0},
                            {0, 5, 10}, {-3, 5, -10}, {0, 4, 12}, {0, 4, -12}, {12, 4, 0}, {12, 4, 0}};
                    for (double[] d : off) Mc.summonRel(t, EntityTypes.GHAST, d[0], d[1], d[2]);
                    announce(ctx.game(), self, "Bombarded with a heck-ton of ghasts:", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(60, LIME, ChatFormatting.RED,
                "Jumpscare someone", "Life-threatening scariness")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> jumpscare(ctx, self, t)).build());
    }

    // ============================ ITEMS 61-90 ===========================

    private void register61to90() {
        add(DeathSwapItem.of(61, RED, ChatFormatting.RED,
                "Lock someone into a prison", "Experience the prison-industrial complex for yourself")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    // Bedrock floor & ceiling.
                    Mc.fill(lvl, o.offset(10, -1, 6), o.offset(-10, -1, -6), Blocks.BEDROCK, FillMode.ALL);
                    Mc.fill(lvl, o.offset(10, 2, 6), o.offset(-10, 2, -6), Blocks.BEDROCK, FillMode.ALL);
                    // Lower wall ring: obsidian. Upper ring: crying obsidian (datapack).
                    Mc.fill(lvl, o.offset(10, 0, 6), o.offset(-10, 0, 6), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(10, 0, -6), o.offset(-10, 0, -6), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(10, 0, 6), o.offset(10, 0, -6), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(-10, 0, 6), o.offset(-10, 0, -6), Blocks.OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(10, 1, 6), o.offset(-10, 1, 6), Blocks.CRYING_OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(10, 1, -6), o.offset(-10, 1, -6), Blocks.CRYING_OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(10, 1, 6), o.offset(10, 1, -6), Blocks.CRYING_OBSIDIAN, FillMode.ALL);
                    Mc.fill(lvl, o.offset(-10, 1, 6), o.offset(-10, 1, -6), Blocks.CRYING_OBSIDIAN, FillMode.ALL);
                    // A torch for light, and a chest with a worn diamond pickaxe (slot 13,
                    // damage 1500) so the prisoner can slowly mine their way out.
                    Mc.setBlock(lvl, o, Blocks.TORCH);
                    ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
                    pick.set(net.minecraft.core.component.DataComponents.DAMAGE, 1500);
                    Mc.chestWithItem(lvl, o.offset(2, 0, 1), 13, pick);
                    announce(ctx.game(), self, "Trapped inside of a prison:", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(62, CYAN, ChatFormatting.AQUA,
                "Give yourself a water bucket", "Avoid the hassle of infinite water sources")
                .effect((ctx, self, t) -> Mc.give(self, Items.WATER_BUCKET, 1)).build());

        add(DeathSwapItem.of(63, ORANGE, ChatFormatting.GOLD,
                "Give yourself a lava bucket", "Sometimes ,the best traps are the simplest")
                .effect((ctx, self, t) -> Mc.give(self, Items.LAVA_BUCKET, 1)).build());

        add(DeathSwapItem.of(64, ORANGE, ChatFormatting.GOLD,
                "Give yourself a long fire resistance potion", "Because the Nether exists, yunno?")
                .effect((ctx, self, t) -> Mc.givePotion(self, Potions.LONG_FIRE_RESISTANCE)).build());

        add(DeathSwapItem.of(65, CYAN, ChatFormatting.AQUA,
                "Liquidate all blocks surrounding a player", "Because the Nether exists, yunno?")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    replaceTagged(lvl, o.offset(12, 6, 12), o.offset(-12, -1, -12), Blocks.WATER.defaultBlockState());
                    replaceTagged(lvl, o.offset(12, -2, 12), o.offset(-12, -6, -12), Blocks.WATER.defaultBlockState());
                    announce(ctx.game(), self, "Liquidated all nearby blocks into water:", t, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(66, WHITE, ChatFormatting.GRAY,
                "Set time to midnight (or mid-day if it's night)", "Minecraft")
                .effect((ctx, self, t) -> {
                    ctx.game().toggleTime();
                    announce(ctx.game(), self, ctx.game().isNight() ? "Set the time to midnight" : "Set the time to day",
                            null, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(67, LIME, ChatFormatting.GREEN,
                "Spawn a giant slime on someone", "That's the biggest slime I've ever seen!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Entity e = Mc.summonRel(t, EntityTypes.SLIME, 4, 6, 2);
                    if (e instanceof net.minecraft.world.entity.monster.cubemob.Slime slime) slime.setSize(8, true);
                    announce(ctx.game(), self, "Spawned a giant slime on", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(68, RED, ChatFormatting.RED,
                "Give yourself 4 extra hearts for health", "More harts!")
                .effect((ctx, self, t) -> {
                    Mc.addMaxHealth(self, 8.0);
                    Mc.msg(self, "+4 Hearts!", ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(69, RED, ChatFormatting.RED,
                "Take away 2 hearts from someone (leave them w/8)", "Less harts!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.addMaxHealth(t, -4.0);
                    announce(ctx.game(), self, "Removed 2 hearts from", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(70, GREEN, ChatFormatting.GREEN,
                "Launch a Viet Cong ambush on someone", "Queue the Rolling Stones music")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    double[][] ring = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {8, 2}, {-8, 2},
                            {2, 8}, {2, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}};
                    for (double[] d : ring) {
                        // Find a vertical gap so the husks don't suffocate in walls/hills.
                        Entity z = Mc.summonRelSafe(t, EntityTypes.HUSK, d[0], d[1]);
                        if (z instanceof Monster m) {
                            m.setCustomName(Component.literal("The Việt Cộng"));
                            m.setPersistenceRequired();
                        }
                    }
                    announce(ctx.game(), self, "Ambushed with a Viet Cong attack:", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(71, LIGHT_BLUE, ChatFormatting.AQUA,
                "Give yourself a one-hit-kill sword", "One hit, one kill, just in case...")
                .effect((ctx, self, t) -> {
                    // Datapack gives a diamond sword at 1 durability (damage=1560 of
                    // 1561) so it breaks after a single swing — "ONE USE".
                    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                    Mc.enchant(self, sword, Enchantments.SHARPNESS, 5);
                    Mc.enchant(self, sword, Enchantments.FIRE_ASPECT, 1);
                    Mc.enchant(self, sword, Enchantments.KNOCKBACK, 2);
                    Mc.enchant(self, sword, Enchantments.LOOTING, 3);
                    sword.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            Component.literal("One-hit-kill -- ONE USE!").withStyle(ChatFormatting.AQUA));
                    ItemAttributeModifiers mods = ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                                    Identifier.fromNamespaceAndPath("deathswap", "one_hit"),
                                    999999.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                            .build();
                    sword.set(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, mods);
                    // Leave exactly one point of durability so it shatters on use.
                    sword.set(net.minecraft.core.component.DataComponents.DAMAGE, sword.getMaxDamage() - 1);
                    // Irreparable: no material/anvil can top it back up.
                    sword.set(net.minecraft.core.component.DataComponents.REPAIRABLE,
                            new net.minecraft.world.item.enchantment.Repairable(
                                    net.minecraft.core.HolderSet.empty()));
                    Mc.giveStack(self, sword);
                }).build());

        add(DeathSwapItem.of(72, LIME, ChatFormatting.LIGHT_PURPLE,
                "Switch game's language to Chinese 中文", "For the culture!!!")
                .effect((ctx, self, t) -> {
                    ctx.game().toggleLanguage();
                    // use/72a.mcfunction: a per-using-player broadcast after the switch.
                    ctx.game().broadcast(com.deathswap.game.Messages.langSwitched(
                            ctx.game().settings().isChinese(), self.getDisplayName()));
                }).build());

        add(DeathSwapItem.of(73, GRAY, ChatFormatting.WHITE,
                "Set /difficulty to hard", "How Notch intended Death Swap to be played")
                .effect((ctx, self, t) -> {
                    ctx.server().setDifficulty(Difficulty.HARD, true);
                    announce(ctx.game(), self, "Set the difficulty to hard!", null, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(74, ORANGE, ChatFormatting.GOLD,
                "Set /difficulty to normal", "How Notch intended Death Swap to be played")
                .effect((ctx, self, t) -> {
                    ctx.server().setDifficulty(Difficulty.NORMAL, true);
                    announce(ctx.game(), self, "Set the difficulty to normal", null, ChatFormatting.GOLD);
                }).build());

        add(DeathSwapItem.of(75, LIME, ChatFormatting.GREEN,
                "Set /difficulty to easy", "How Jeb intended Death Swap to be played")
                .effect((ctx, self, t) -> {
                    ctx.server().setDifficulty(Difficulty.EASY, true);
                    announce(ctx.game(), self, "Set the difficulty to easy", null, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(76, GREEN, ChatFormatting.GREEN,
                "Teleport someone to a superflat world", "Throwback 2014")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    // Datapack 76b: spread the target into the bundled ds:superflat dimension.
                    ServerLevel flat = ctx.server().getLevel(
                            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("ds", "superflat")));
                    if (flat != null) {
                        double angle = t.getRandom().nextDouble() * Math.PI * 2;
                        double radius = 10 + t.getRandom().nextDouble() * (29_999_000 - 10);
                        int x = (int) (Math.cos(angle) * radius);
                        int z = (int) (Math.sin(angle) * radius);
                        int y = flat.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        Mc.teleportTo(t, flat, x + 0.5, y, z + 0.5, t.getYRot(), t.getXRot());
                    }
                    announce(ctx.game(), self, "Teleported to a superflat world:", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(77, BROWN, ChatFormatting.GREEN,
                "Give yourself a crafting table, furnace, & materials", "In case you can't get the essentials")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.OAK_PLANKS, 8);
                    Mc.give(self, Items.COBBLESTONE, 8);
                    Mc.give(self, Items.COAL, 8);
                    Mc.give(self, Items.CRAFTING_TABLE, 1);
                    Mc.give(self, Items.FURNACE, 1);
                    Mc.give(self, Items.BLAST_FURNACE, 1);
                }).build());

        add(DeathSwapItem.of(78, BROWN, ChatFormatting.YELLOW,
                "Crash somebody's Minecraft game", "Breaking the fourth.. er, actually all the walls")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    // We do NOT actually crash a client; reproduce the in-game warning + heavy disorientation.
                    Mc.title(t, ">>> WARNING!! <<<", "Your game will lag heavily!", ChatFormatting.RED, ChatFormatting.RED);
                    Mc.effect(t, MobEffects.NAUSEA, 12, 0);
                    Mc.effect(t, MobEffects.BLINDNESS, 6, 0);
                    announce(ctx.game(), self, "Crashed the Minecraft game of", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(79, BROWN, ChatFormatting.WHITE,
                "Turn OFF Keep Inventory (or ON if it's off)", "Now you REALLY don't wanna die!")
                .effect((ctx, self, t) -> {
                    var rules = ctx.server().getGameRules();
                    boolean next = !rules.get(GameRules.KEEP_INVENTORY);
                    rules.set(GameRules.KEEP_INVENTORY, next, ctx.server());
                    announce(ctx.game(), self, next ? "Turned keep_inventory BACK ON!" : "Turned keep_inventory OFF!",
                            null, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(80, WHITE, ChatFormatting.WHITE,
                "Put someone into spectator mode: 20 secs", "Life as a floating ghostly head!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    t.setGameMode(GameType.SPECTATOR);
                    ctx.effects().apply(t, new ActiveEffect("specMode", 21 * 20, null,
                            p -> p.setGameMode(GameType.SURVIVAL)));
                    announce(ctx.game(), self, "Put into spectator mode for 20 seconds:", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(81, PINK, ChatFormatting.AQUA,
                "Re-shuffle everyone around the world EXCEPT YOU", "Everyone needs a reset sometimes")
                .effect((ctx, self, t) -> {
                    for (ServerPlayer p : ctx.game().alivePlayers()) {
                        if (p != self) ctx.game().spreadFarAway(p);
                    }
                    announce(ctx.game(), self, "Re-shuffled everybody around the world except themself!", null, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(82, MAGENTA, ChatFormatting.LIGHT_PURPLE,
                "Place an Amethyst Geode where you are", "Your favorite 1.21 addition")
                .effect((ctx, self, t) -> {
                    // Loads the bundled saved structure (data/minecraft/structure/amethyst_geode.nbt),
                    // exactly like the datapack's structure_block[mode=load] at ~ ~ ~1.
                    Mc.placeTemplate(Mc.level(self), at(self).offset(0, 0, 1), "amethyst_geode");
                    Mc.msg(self, "You placed an Amethyst Geode", ChatFormatting.LIGHT_PURPLE);
                    Mc.playSound(self, SoundEvents.ITEM_PICKUP, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(83, RED, ChatFormatting.AQUA,
                "Turn OFF /gamerule natural regeneration (or ON if it's off)", "Everyone needs a reset sometimes")
                .effect((ctx, self, t) -> {
                    var rules = ctx.server().getGameRules();
                    boolean next = !rules.get(GameRules.NATURAL_HEALTH_REGENERATION);
                    rules.set(GameRules.NATURAL_HEALTH_REGENERATION, next, ctx.server());
                    announce(ctx.game(), self, next ? "Turned natural_regeneration BACK ON!"
                            : "Turned natural_regeneration OFF!", null, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(84, WHITE, ChatFormatting.WHITE,
                "Build a Quartz maze around someone", "Severance in Minecraft")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    BlockPos o = at(t);
                    int y = o.getY();
                    // misc/quartz_pillars ly/uy, chosen by the target's Y band (84b).
                    int ly = y <= -55 ? -63 : y - 10;
                    int uy = y >= 296 ? 319 : y + 24;
                    quartzMaze(Mc.level(t), o, ly, uy);
                    announce(ctx.game(), self, "Built a Quartz maze around", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(85, GRAY, ChatFormatting.GRAY,
                "Turn all blocks near someone to obsidian", "Just in case you need a portal")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    replaceTagged(Mc.level(t), at(t).offset(5, 3, 5), at(t).offset(-5, -2, -5),
                            Blocks.OBSIDIAN.defaultBlockState());
                    announce(ctx.game(), self, "Turned all nearby blocks to obsidian for", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(86, LIGHT_GRAY, ChatFormatting.GOLD,
                "Build a Stalagmite trap next to you", "A classic death swap trap")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    BlockState ladderN = Blocks.LADDER.defaultBlockState();
                    Mc.setBlock(lvl, o.offset(0, -4, 4), Blocks.TUFF);
                    // upper segment (y 0..12)
                    Mc.fill(lvl, o.offset(0, 0, 3), o.offset(0, 12, 3), Blocks.TUFF, FillMode.ALL);
                    Mc.fill(lvl, o.offset(0, 0, 5), o.offset(0, 12, 5), Blocks.TUFF, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(0, 0, 4), o.offset(0, 12, 4), Mc.light(15), FillMode.ALL);
                    Mc.fill(lvl, o.offset(1, 0, 4), o.offset(1, 12, 4), Blocks.TUFF, FillMode.ALL);
                    Mc.fill(lvl, o.offset(-1, 0, 4), o.offset(-1, 12, 4), Blocks.TUFF, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(0, 0, 2), o.offset(0, 12, 2), ladderN, FillMode.ALL);
                    // lower segment (y -3..0)
                    Mc.fill(lvl, o.offset(0, -3, 3), o.offset(0, 0, 3), Blocks.TUFF, FillMode.ALL);
                    Mc.fill(lvl, o.offset(0, -3, 5), o.offset(0, 0, 5), Blocks.TUFF, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(0, -3, 4), o.offset(0, 0, 4), Mc.light(15), FillMode.ALL);
                    Mc.fill(lvl, o.offset(1, -3, 4), o.offset(1, 0, 4), Blocks.TUFF, FillMode.ALL);
                    Mc.fill(lvl, o.offset(-1, -3, 4), o.offset(-1, 0, 4), Blocks.TUFF, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(0, -3, 2), o.offset(0, 0, 2), ladderN, FillMode.ALL);
                    Mc.setState(lvl, o.offset(0, -3, 4),
                            Mc.dripstone(net.minecraft.world.level.block.state.properties.SpeleothemThickness.FRUSTUM, Direction.UP));
                    Mc.setState(lvl, o.offset(0, -2, 4),
                            Mc.dripstone(net.minecraft.world.level.block.state.properties.SpeleothemThickness.TIP, Direction.UP));
                    Mc.fill(lvl, o, o.offset(0, 1, 1), Blocks.AIR, FillMode.ALL);
                    Mc.fill(lvl, o.offset(0, 13, 2), o.offset(0, 14, 4), Blocks.AIR, FillMode.ALL);
                    Mc.setYaw(self, 0);
                    clearNightVisionAll(ctx);
                    Mc.msg(self, "You built a Stalagmite trap in front of you!", ChatFormatting.GOLD);
                }).build());

        add(DeathSwapItem.of(87, RED, ChatFormatting.LIGHT_PURPLE,
                "Delete the Chunk someone's standing in", "That's a 16x16 hole in the world, BTW")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    Mc.fill(lvl, new BlockPos(o.getX() + 7, -63, o.getZ() + 10),
                            new BlockPos(o.getX() - 8, -63, o.getZ() - 5), Blocks.WATER, FillMode.ALL);
                    Mc.fill(lvl, new BlockPos(o.getX() + 7, -62, o.getZ() + 10),
                            new BlockPos(o.getX() - 8, 319, o.getZ() - 5), Blocks.AIR, FillMode.ALL);
                    clearNightVisionAll(ctx);
                    announce(ctx.game(), self, "Deleted the chunk standing in:", t, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(88, YELLOW, ChatFormatting.YELLOW,
                "Switch places with someone", "Like a sub-death swap")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Vec3 a = self.position(), b = t.position();
                    ServerLevel la = Mc.level(self), lb = Mc.level(t);
                    Mc.teleportTo(self, lb, b.x, b.y, b.z, self.getYRot(), self.getXRot());
                    Mc.teleportTo(t, la, a.x, a.y, a.z, t.getYRot(), t.getXRot());
                    announce(ctx.game(), self, "Switched places with", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(89, LIGHT_BLUE, ChatFormatting.AQUA,
                "Mine blocks faster for 90 seconds", "You could mine the whole world!")
                .effect((ctx, self, t) -> {
                    Mc.setAttribute(self, Attributes.BLOCK_BREAK_SPEED, 4.0);
                    ctx.effects().apply(self, new ActiveEffect("mine_faster", 121 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.BLOCK_BREAK_SPEED, DEF_BREAK)));
                    Mc.title(self, " ", "3x Faster mining: 120 seconds", ChatFormatting.WHITE, ChatFormatting.AQUA);
                }).build());

        add(DeathSwapItem.of(90, PURPLE, ChatFormatting.LIGHT_PURPLE,
                "Make the game low-gravity: 3 minutes", "POV: You are Neil Armstrong")
                .effect((ctx, self, t) -> {
                    for (ServerPlayer p : ctx.game().alivePlayers()) {
                        Mc.setAttribute(p, Attributes.GRAVITY, 0.008);
                        Mc.setAttribute(p, Attributes.FALL_DAMAGE_MULTIPLIER, 0.0);
                        ctx.effects().apply(p, new ActiveEffect("low_grav", 182 * 20, null, q -> {
                            Mc.resetAttribute(q, Attributes.GRAVITY, DEF_GRAVITY);
                            Mc.resetAttribute(q, Attributes.FALL_DAMAGE_MULTIPLIER, DEF_FALL);
                        }));
                    }
                    announce(ctx.game(), self, "Turned the game into a low-gravity environment", null, ChatFormatting.LIGHT_PURPLE);
                }).build());
    }

    // ============================ ITEMS 91-110 ==========================

    private void register91to110() {
        add(DeathSwapItem.of(91, LIME, ChatFormatting.YELLOW,
                "Take NO fall damage for the next 5 minutes", "A pretty OP item, as the kids say")
                .effect((ctx, self, t) -> {
                    Mc.setAttribute(self, Attributes.FALL_DAMAGE_MULTIPLIER, 0.0);
                    ctx.effects().apply(self, new ActiveEffect("no_fall_dam", 301 * 20,
                            p -> p.fallDistance = 0.0f,
                            p -> Mc.resetAttribute(p, Attributes.FALL_DAMAGE_MULTIPLIER, DEF_FALL)));
                    Mc.title(self, " ", ">> NO FALL DAMAGE! 5 Mins <<", ChatFormatting.WHITE, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(92, PURPLE, ChatFormatting.RED,
                "Make someone's ears bleed (HEADPHONE WARNING!)", "WHAT DID YOU SAY???!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("ears_bleed", 45 * 20, p -> {
                        Mc.playSound(p, SoundEvents.ENDER_DRAGON_GROWL, 99f, 1.0f);
                        Mc.playSound(p, SoundEvents.WITHER_AMBIENT, 99f, 1.0f);
                    }, null));
                    Mc.title(t, " ", ">> YOU CAN'T HEAR ANYTHING! 45 secs <<", ChatFormatting.WHITE, ChatFormatting.RED);
                    announce(ctx.game(), self, "Made the ears bleed for 45 seconds:", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(93, MAGENTA, ChatFormatting.GREEN,
                "Summon a Stronghold below you", "Because the nearest regular one is 20 million blocks away")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    Mc.fillReplace(lvl, new BlockPos(o.getX() - 20, 8, o.getZ() - 20),
                            new BlockPos(o.getX() + 20, 35, o.getZ() + 20), Mc.light(4), Blocks.STONE);
                    Mc.fillReplace(lvl, new BlockPos(o.getX() - 20, -25, o.getZ() - 20),
                            new BlockPos(o.getX() + 20, -10, o.getZ() + 20), Mc.light(4), Blocks.DEEPSLATE);
                    Mc.placeStructure(lvl, o, "stronghold");
                    Mc.fillState(lvl, new BlockPos(o.getX() - 3, o.getY() + 1, o.getZ()),
                            new BlockPos(o.getX() - 4, -60, o.getZ() + 1), Blocks.AIR.defaultBlockState(), FillMode.ALL);
                    Mc.fillState(lvl, new BlockPos(o.getX() - 4, o.getY() - 1, o.getZ()),
                            new BlockPos(o.getX() - 4, -60, o.getZ() + 1), Mc.ladder(Direction.EAST), FillMode.ALL);
                    Mc.fillState(lvl, o.offset(-1, 0, 0), o.offset(-4, 1, 0), Mc.light(8), FillMode.ALL);
                    Mc.birchSign(lvl, o.offset(-5, 0, 0), 12,
                            new String[]{"Stronghold 据点", "| | | |", "| | | |", "V V V V"});
                    Mc.setState(lvl, o.offset(-5, 1, 0), Blocks.AIR.defaultBlockState());
                    Mc.setYaw(self, 90);
                    clearNightVisionAll(ctx);
                    Mc.msg(self, Component.literal("> Go down the ladder to reach the stronghold! (It's around y = at 30)")
                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)));
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(94, ORANGE, ChatFormatting.WHITE,
                "Give yourself a flint & steel & iron ingot", "Light it up, up up, light it up, up, up")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.FLINT_AND_STEEL, 1);
                    Mc.give(self, Items.IRON_INGOT, 1);
                }).build());

        add(DeathSwapItem.of(95, GREEN, ChatFormatting.GREEN,
                "Put someone into Parkour Civilization (from Youtube)", "Evbo, noooooooo!")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    BlockState bars = Blocks.IRON_BARS.defaultBlockState();
                    // One-shot: the bedrock-layer water floor and the iron-bar cell around the player.
                    Mc.fill(lvl, new BlockPos(o.getX() + 26, -63, o.getZ() + 26),
                            new BlockPos(o.getX() - 26, -63, o.getZ() - 26), Blocks.WATER, FillMode.ALL);
                    Mc.fillState(lvl, o.offset(1, 0, 1), o.offset(1, 0, -1), bars, FillMode.AIR_ONLY);
                    Mc.fillState(lvl, o.offset(-1, 0, 1), o.offset(-1, 0, -1), bars, FillMode.AIR_ONLY);
                    Mc.fillState(lvl, o.offset(0, 0, 1), o.offset(0, 0, 1), bars, FillMode.AIR_ONLY);
                    Mc.fillState(lvl, o.offset(0, 0, -1), o.offset(0, 0, -1), bars, FillMode.AIR_ONLY);
                    clearNightVisionAll(ctx);
                    // Exact light-wall cage, built across ticks so it never freezes the server.
                    ctx.game().addBuildJob(parkourJob(lvl, o.getX(), o.getZ()));
                    announce(ctx.game(), self, "Spawned inside of Parkour Civilization:", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(96, CYAN, ChatFormatting.GREEN,
                "Secretly spy on what someone's currently doing", "Cold War shenanigans in Minecraft")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> spy(ctx, self, t)).build());

        add(DeathSwapItem.of(97, ORANGE, ChatFormatting.GOLD,
                "Shield yourself from negative items: 2.5 mins", "Nobody can use any items on you for 2.5 minutes!")
                .effect((ctx, self, t) -> shield(ctx, self, 151)).build());

        add(DeathSwapItem.of(98, BROWN, ChatFormatting.GOLD,
                "Block someone from using crafting tables: 90 sec", "Minecraft without the -craft")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("no_craft", 110 * 20, p -> {
                        BlockPos o = p.blockPosition();
                        ServerLevel lvl = Mc.level(p);
                        for (net.minecraft.world.level.block.Block b : new net.minecraft.world.level.block.Block[]{
                                Blocks.CRAFTING_TABLE, Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.CRAFTER}) {
                            replaceBlock(lvl, o.offset(-7, -1, -7), o.offset(7, 7, 7), b, Blocks.AIR);
                        }
                    }, null));
                    Mc.title(t, " ", "You can't use crafting tables & furnaces: 90 secs!", ChatFormatting.WHITE, ChatFormatting.YELLOW);
                    announce(ctx.game(), self, "Blocked crafting tables & furnaces for", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(99, MAGENTA, ChatFormatting.AQUA,
                "Gain a monster-proof forcefield: 3 mins", "Minus well just be peaceful mode for you")
                .effect((ctx, self, t) -> {
                    ctx.effects().apply(self, new ActiveEffect("mob_forcefield", 185 * 20, p -> {
                        ServerLevel lvl = Mc.level(p);
                        AABB box = p.getBoundingBox().inflate(3.2);
                        for (Monster m : lvl.getEntitiesOfClass(Monster.class, box)) {
                            m.discard();
                        }
                        // Purple dust ring so the forcefield is visible (datapack particle).
                        Vec3 c = p.position();
                        int color = 0x7519FF;
                        Mc.dust(lvl, c.x + 2.2, c.y + 1, c.z, color, 1.2f, 1, 0.1, 0.1, 1.2);
                        Mc.dust(lvl, c.x - 2.2, c.y + 1, c.z, color, 1.2f, 1, 0.1, 0.1, 1.2);
                        Mc.dust(lvl, c.x, c.y + 1, c.z + 2.2, color, 1.2f, 1, 1.2, 0.1, 0.1);
                        Mc.dust(lvl, c.x, c.y + 1, c.z - 2.2, color, 1.2f, 1, 1.2, 0.1, 0.1);
                    }, null));
                    Mc.title(self, " ", ">> Forcefield: 3 minutes! <<", ChatFormatting.WHITE, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(100, GREEN, ChatFormatting.GREEN,
                "Spawn a Giant zombie on someone", "These Giants were real mobs until around 1.8")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Entity giant = Mc.summonRel(t, EntityTypes.ZOMBIE, -5, 4, -5);
                    if (giant instanceof LivingEntity le && le.getAttribute(Attributes.SCALE) != null) {
                        le.getAttribute(Attributes.SCALE).setBaseValue(16.0);
                        le.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
                        le.setHealth(100.0f);
                    }
                    announce(ctx.game(), self, "Summoned a Giant zombie on", t, ChatFormatting.GREEN);
                }).build());

        add(DeathSwapItem.of(101, LIME, ChatFormatting.GREEN,
                "Place a Trial Chamber below you", "This is why Lena Raine joined Mojang")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    Mc.fillReplace(lvl, new BlockPos(o.getX() - 20, 8, o.getZ() - 20),
                            new BlockPos(o.getX() + 20, 35, o.getZ() + 20), Mc.light(4), Blocks.STONE);
                    Mc.fillReplace(lvl, new BlockPos(o.getX() - 20, -16, o.getZ() - 20),
                            new BlockPos(o.getX() + 20, -10, o.getZ() + 20), Mc.light(4), Blocks.DEEPSLATE);
                    Mc.placeStructure(lvl, o, "trial_chambers");
                    Mc.fillState(lvl, new BlockPos(o.getX() - 3, o.getY() + 1, o.getZ()),
                            new BlockPos(o.getX() - 4, -60, o.getZ() + 1), Blocks.AIR.defaultBlockState(), FillMode.ALL);
                    Mc.fillState(lvl, new BlockPos(o.getX() - 4, o.getY() - 1, o.getZ()),
                            new BlockPos(o.getX() - 4, -60, o.getZ() + 1), Mc.ladder(Direction.EAST), FillMode.ALL);
                    Mc.fillState(lvl, o.offset(-1, 0, 0), o.offset(-4, 1, 0), Mc.light(8), FillMode.ALL);
                    Mc.birchSign(lvl, o.offset(-5, 0, 0), 12,
                            new String[]{"Trial Chamber", "审判分庭", "| | | |", "V V V V"});
                    Mc.setState(lvl, o.offset(-5, 1, 0), Blocks.AIR.defaultBlockState());
                    Mc.setYaw(self, 90);
                    clearNightVisionAll(ctx);
                    Mc.msg(self, Component.literal("> Go down the ladder and you'll find the Trial Chamber! (It's around y = at 30)")
                            .withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)));
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(102, BROWN, ChatFormatting.YELLOW,
                "Build a Woodland Mansion where you are", "The #1 target for arsonists")
                .effect((ctx, self, t) -> {
                    Mc.placeStructure(Mc.level(self), at(self), "mansion");
                    Mc.setYaw(self, 90);
                    clearNightVisionAll(ctx);
                    Mc.msg(self, ">> A Woodland Mansion was built near you!", ChatFormatting.YELLOW);
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(103, YELLOW, ChatFormatting.YELLOW,
                "Make someone continuously pee: 1 min", "How much did they have to drink??")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("is_peeing", 61 * 20,
                            p -> Mc.playSound(p, SoundEvents.PLAYER_SPLASH, 0.4f, 2.0f), null));
                    Mc.title(t, " ", "You are peeing...", ChatFormatting.WHITE, ChatFormatting.YELLOW);
                    announce(ctx.game(), self, "Made continuously pee for 1 minute:", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(104, MAGENTA, ChatFormatting.LIGHT_PURPLE,
                "Get an enchanting table, lapis, & XP", "The full enchanting set!")
                .effect((ctx, self, t) -> {
                    Mc.give(self, Items.ENCHANTING_TABLE, 1);
                    Mc.give(self, Items.LAPIS_LAZULI, 32);
                    Mc.give(self, Items.EXPERIENCE_BOTTLE, 32);
                }).build());

        add(DeathSwapItem.of(105, CYAN, ChatFormatting.LIGHT_PURPLE,
                "Summon the Warden on someone", "Deep Dark Edition")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Entity w = Mc.summonRel(t, EntityTypes.WARDEN, 0, 0, 0);
                    if (w instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
                    Mc.title(t, " ", ">> warden.. <<", ChatFormatting.WHITE, ChatFormatting.DARK_AQUA);
                    announce(ctx.game(), self, "Summoned the Warden on", t, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(106, RED, ChatFormatting.RED,
                "Teleport someone directly to the Nether", "A Throwback to 1.16")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel nether = ctx.server().getLevel(Level.NETHER);
                    if (nether != null) {
                        double nx = t.getX() / 8.0, nz = t.getZ() / 8.0;
                        BlockPos p = BlockPos.containing(nx, 64, nz);
                        Mc.fill(nether, p.offset(-2, -1, -2), p.offset(2, -1, 2), Blocks.NETHERRACK, FillMode.ALL);
                        Mc.teleportTo(t, nether, nx, 64.5, nz, t.getYRot(), t.getXRot());
                    }
                    announce(ctx.game(), self, "Teleported directly to the Nether:", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(107, RED, ChatFormatting.RED,
                "Summon Oppenheimer's NUCLEAR bomb on someone", "Now you are become death, destroyer of Minecrafts")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    // The datapack nuke is TNT with explosion_power:40 (the entity's
                    // power field has no setter, so summon via command like the datapack
                    // does — a plain primed-TNT entity only explodes at power 4).
                    String nbt = "{fuse:180,explosion_power:40,Tags:[\"ent\"]}";
                    for (String pos : new String[]{"~ ~1 ~", "~2 ~1 ~", "~-2 ~1 ~", "~ ~1 ~2", "~ ~1 ~-2"}) {
                        Mc.runAt(t, "summon tnt " + pos + " " + nbt);
                    }
                    Mc.title(t, ">> NUKE!!! <<", "Explodes in 12 secs -- RUN!!", ChatFormatting.GOLD, ChatFormatting.RED);
                    announce(ctx.game(), self, "Summoned the Oppenheimer nuclear bomb on", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(108, CYAN, ChatFormatting.WHITE,
                "Summon an Ancient City below you", "Now you are become death, destroyer of Minecrafts")
                .effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(self);
                    BlockPos o = at(self);
                    int x = o.getX(), z = o.getZ();
                    Mc.fillReplace(lvl, new BlockPos(x - 48, -52, z - 48), new BlockPos(x + 48, -48, z + 48), Mc.light(4), Blocks.DEEPSLATE);
                    Mc.fillReplace(lvl, new BlockPos(x - 48, -47, z - 48), new BlockPos(x + 48, -45, z + 48), Mc.light(4), Blocks.DEEPSLATE);
                    Mc.fillReplace(lvl, new BlockPos(x - 48, -44, z - 48), new BlockPos(x + 48, -42, z + 48), Mc.light(4), Blocks.DEEPSLATE);
                    Mc.fillReplace(lvl, new BlockPos(x - 48, -41, z - 48), new BlockPos(x + 48, -38, z + 48), Mc.light(4), Blocks.DEEPSLATE);
                    Mc.placeStructure(lvl, o, "ancient_city");
                    Mc.fillState(lvl, new BlockPos(x - 3, o.getY() + 1, z), new BlockPos(x - 4, 0, z + 1), Blocks.AIR.defaultBlockState(), FillMode.ALL);
                    Mc.fillState(lvl, new BlockPos(x - 3, -1, z), new BlockPos(x - 4, -54, z + 1), Blocks.AIR.defaultBlockState(), FillMode.ALL);
                    Mc.fillState(lvl, new BlockPos(x - 4, o.getY() - 1, z), new BlockPos(x - 4, 0, z + 1), Mc.ladder(Direction.EAST), FillMode.ALL);
                    Mc.fillState(lvl, new BlockPos(x - 4, -1, z), new BlockPos(x - 4, -54, z + 1), Mc.ladder(Direction.EAST), FillMode.ALL);
                    Mc.fillState(lvl, o.offset(-1, 0, 0), o.offset(-4, 1, 0), Mc.light(8), FillMode.ALL);
                    Mc.birchSign(lvl, o.offset(-5, 0, 0), 12,
                            new String[]{"Ancient City", "古城", "| | | |", "V V V V"});
                    Mc.setState(lvl, o.offset(-5, 1, 0), Blocks.AIR.defaultBlockState());
                    Mc.setYaw(self, 90);
                    clearNightVisionAll(ctx);
                    Mc.msg(self, Component.literal("> Go down the ladder and you'll find the Ancient City! (May not have spawned if in the Nether or the End!)")
                            .withStyle(s -> s.withColor(ChatFormatting.YELLOW).withBold(true)));
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(109, ORANGE, ChatFormatting.YELLOW,
                "Set everything around someone on fire", "We didn't start the fire...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ServerLevel lvl = Mc.level(t);
                    BlockPos o = at(t);
                    BlockState fire = Blocks.FIRE.defaultBlockState();
                    // Four side quadrants (leaving the player's column), y -1..8, replacing air then light.
                    Mc.fillReplace(lvl, o.offset(-10, -1, -10), o.offset(-2, 8, 10), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(2, -1, -10), o.offset(10, 8, 10), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(-2, -1, -10), o.offset(2, 8, -2), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(-2, -1, 2), o.offset(2, 8, 10), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(-10, -1, -10), o.offset(-2, 8, 10), fire, Blocks.LIGHT);
                    Mc.fillReplace(lvl, o.offset(2, -1, -10), o.offset(10, 8, 10), fire, Blocks.LIGHT);
                    Mc.fillReplace(lvl, o.offset(-2, -1, -10), o.offset(2, 8, -2), fire, Blocks.LIGHT);
                    Mc.fillReplace(lvl, o.offset(-2, -1, 2), o.offset(2, 8, 10), fire, Blocks.LIGHT);
                    // Lower band y -5..8 (air) then y -5..-2 (light).
                    Mc.fillReplace(lvl, o.offset(-10, -5, -10), o.offset(-2, 8, 10), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(2, -5, -10), o.offset(10, 8, 10), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(-2, -5, -10), o.offset(2, 8, -2), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(-2, -5, 2), o.offset(2, 8, 10), fire, Blocks.AIR);
                    Mc.fillReplace(lvl, o.offset(-10, -5, -10), o.offset(-2, -2, 10), fire, Blocks.LIGHT);
                    Mc.fillReplace(lvl, o.offset(2, -5, -10), o.offset(10, -2, 10), fire, Blocks.LIGHT);
                    Mc.fillReplace(lvl, o.offset(-2, -5, -10), o.offset(2, -2, -2), fire, Blocks.LIGHT);
                    Mc.fillReplace(lvl, o.offset(-2, -5, 2), o.offset(2, -2, 10), fire, Blocks.LIGHT);
                    clearNightVisionAll(ctx);
                    announce(ctx.game(), self, "Set everything on fire around", t, ChatFormatting.YELLOW);
                }).build());

        add(DeathSwapItem.of(110, PURPLE, ChatFormatting.LIGHT_PURPLE,
                "Spawn 100 Endermen on someone", "We didn't start the fire...")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) ->
                        spawnHorde(ctx, self, t, EntityTypes.ENDERMAN, "Spawned 100 Endermen at")).build());
    }

    // ---- effect helpers ----

    private static void shield(ItemContext ctx, ServerPlayer self, int seconds) {
        ctx.effects().apply(self, new ActiveEffect("shield", seconds * 20, null,
                p -> Mc.msg(p, "Your shield wore off. Others can target you again.", ChatFormatting.GRAY)));
        ctx.game().broadcast(">> " + self.getName().getString()
                + " shielded themself from negative items! <<", ChatFormatting.GOLD);
    }

    /** Spawn a primed TNT at default explosion power (4); higher-power nukes are summoned via command. */
    private static void primeTnt(ServerLevel level, Vec3 p, double dx, double dy, double dz, byte fuse) {
        var tnt = Mc.summon(level, EntityTypes.TNT, p.x + dx, p.y + dy, p.z + dz);
        if (tnt != null) {
            tnt.setFuse(fuse);
        }
    }

    private static void spawnHorde(ItemContext ctx, ServerPlayer self, ServerPlayer t,
                                   EntityType<?> type, String verb) {
        Mc.summonRel(t, type, 0, 0, 0);
        spawnOverTime(ctx, t, type, 26, 4);
        announce(ctx.game(), self, verb, t, ChatFormatting.GOLD);
    }

    /** Spawn {@code perTick} entities around the target each tick for {@code ticks} ticks. */
    private static void spawnOverTime(ItemContext ctx, ServerPlayer t, EntityType<?> type, int ticks, int perTick) {
        ctx.effects().apply(t, new ActiveEffect("horde_" + type.hashCode() + "_" + t.getUUID(), ticks, p -> {
            for (int i = 0; i < perTick; i++) {
                double a = Math.random() * Math.PI * 2;
                Mc.summonRel(p, type, Math.cos(a), 0, Math.sin(a));
            }
        }, null));
    }

    private static void jumpscare(ItemContext ctx, ServerPlayer self, ServerPlayer t) {
        Entity scare = Mc.summonRel(t, EntityTypes.HUSK, 0, 0, 1);
        if (scare instanceof net.minecraft.world.entity.Mob m) {
            m.setNoAi(true);
            m.setInvulnerable(true);
            m.setPersistenceRequired();
        }
        ctx.effects().apply(t, new ActiveEffect("jumpscare", 30 * 20, p -> {
            if (scare.isAlive()) {
                Vec3 look = p.getLookAngle();
                scare.snapTo(p.getX() + look.x * 0.74, p.getEyeY() - 0.5, p.getZ() + look.z * 0.74,
                        p.getYRot() + 180f, 0f);
            }
        }, p -> scare.discard()));
        announce(ctx.game(), self, "Jumpscared", t, ChatFormatting.RED);
    }

    private static void spy(ItemContext ctx, ServerPlayer self, ServerPlayer t) {
        ServerLevel originLevel = Mc.level(self);
        Vec3 origin = self.position();
        float yaw = self.getYRot(), pitch = self.getXRot();
        self.setGameMode(GameType.SPECTATOR);
        Mc.teleportTo(self, Mc.level(t), t.getX(), t.getY(), t.getZ(), self.getYRot(), self.getXRot());
        ctx.effects().apply(self, new ActiveEffect("spying", 20 * 20, null, p -> {
            Mc.teleportTo(p, originLevel, origin.x, origin.y, origin.z, yaw, pitch);
            p.setGameMode(GameType.SURVIVAL);
        }));
        Mc.title(self, " ", t.getName().getString() + " can't see you!", ChatFormatting.WHITE, ChatFormatting.GREEN);
    }

    private static void earthquake(ItemContext ctx, ServerPlayer victim) {
        int[] counter = {0};
        ctx.effects().apply(victim, new ActiveEffect("earthquake", 55 * 20, p -> {
            counter[0]++;
            Mc.rotateRelative(p, counter[0] % 4 < 2 ? 0.4f : -0.4f, 0f);
            if (counter[0] % 20 == 0) {
                BlockPos o = p.blockPosition();
                Mc.fill(Mc.level(p), o.offset(10, 8, 10), o.offset(-10, -1, -10), Blocks.GRAVEL, FillMode.NATURAL_ONLY);
                Mc.playSound(p, SoundEvents.STONE_BREAK, 9f, 1.0f);
            }
            if (counter[0] % 10 == 0) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.NAUSEA, 30, 0, false, false));
            }
        }, p -> Mc.msg(p, ">> The earthquake has concluded! You are safe now! <<", ChatFormatting.YELLOW)));
    }

    /** Blocks the datapack's {@code #minecraft:pillars_blocks} tag covers. */
    private static final Block[] PILLARS_BLOCKS = {
            Blocks.STONE, Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.DEEPSLATE, Blocks.SAND,
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.AIR, Blocks.WATER, Blocks.LAVA,
            Blocks.END_STONE, Blocks.NETHERRACK, Blocks.SOUL_SAND, Blocks.ANDESITE,
            Blocks.DIORITE, Blocks.SNOW, Blocks.GRANITE
    };

    private static boolean isPillarsBlock(BlockState s) {
        for (Block b : PILLARS_BLOCKS) {
            if (s.is(b)) return true;
        }
        return false;
    }

    /** A vertical quartz-brick column placed only over {@code pillars_blocks}, like {@code quartz_pillars}. */
    private static void quartzColumn(ServerLevel level, int x, int z, int y1, int y2) {
        BlockState q = Blocks.QUARTZ_BRICKS.defaultBlockState();
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            cur.set(x, y, z);
            if (isPillarsBlock(level.getBlockState(cur))) {
                level.setBlock(cur, q, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Exact {@code misc/quartz_pillars}: concentric square pillar rings (radius 2..14) + lit centre. */
    private static void quartzMaze(ServerLevel level, BlockPos o, int ly, int uy) {
        quartzColumn(level, o.getX(), o.getZ(), 2, uy);                   // centre column (abs y=2..uy)
        level.setBlock(o.offset(0, -1, 0), Blocks.QUARTZ_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
        Mc.fillState(level, o, o.offset(0, 1, 0), Mc.light(15), FillMode.ALL);
        for (int r = 2; r <= 14; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        quartzColumn(level, o.getX() + dx, o.getZ() + dz, ly, uy);
                    }
                }
            }
        }
    }

    private static void replaceBlock(ServerLevel level, BlockPos c1, BlockPos c2,
                                     net.minecraft.world.level.block.Block from,
                                     net.minecraft.world.level.block.Block to) {
        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    cur.set(x, y, z);
                    if (level.getBlockState(cur).is(from)) {
                        level.setBlockAndUpdate(cur, to.defaultBlockState());
                    }
                }
    }
}
