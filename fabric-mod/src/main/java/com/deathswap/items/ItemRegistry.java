package com.deathswap.items;

import com.deathswap.effects.ActiveEffect;
import com.deathswap.util.Mc;
import com.deathswap.util.Mc.FillMode;
import com.deathswap.util.Translator;
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
import net.minecraft.world.phys.shapes.VoxelShape;
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

    // ---- shared helpers ----

    private static void announce(com.deathswap.game.GameManager game, ServerPlayer self,
                                 String verb, ServerPlayer target, ChatFormatting color) {
        // Use the scoreboard name (always the plain username) rather than
        // getName(), which can render blank. Show the target whenever one is given
        // — even when it's the user themselves — so the log never drops the "who".
        String who = target == null ? "" : " " + target.getScoreboardName();
        String localized = Translator.translate(game.settings().isDutch(), verb);
        game.broadcast(">> " + self.getScoreboardName() + " --> " + localized + who, color);
    }

    /** Localize an in-game effect string for the current game language. */
    private static String translate(ItemContext ctx, String en) {
        return Translator.translate(ctx.game().settings().isDutch(), en);
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

    private static final TagKey<Block> SAND_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "sand"));
    private static final TagKey<Block> LEAVES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "leaves"));
    private static final TagKey<Block> LOGS_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "logs"));
    private static final TagKey<Block> COAL_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "coal_ores"));
    private static final TagKey<Block> COPPER_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "copper_ores"));
    private static final TagKey<Block> IRON_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "iron_ores"));
    private static final TagKey<Block> LAPIS_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "lapis_ores"));
    private static final TagKey<Block> REDSTONE_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "redstone_ores"));
    private static final TagKey<Block> EMERALD_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "emerald_ores"));
    private static final TagKey<Block> GOLD_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "gold_ores"));
    private static final TagKey<Block> DIAMOND_ORES_TAG = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "diamond_ores"));

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
        final int yMin = -63, yMax = 317, min = -19, max = 19;
        final BlockState lightState = Mc.light(1);
        // The cage is a light-wall grid: a wall block goes wherever dx or dz is odd, leaving the
        // even/even offsets as open cells. This slices the whole ±19 square on both horizontal axes.
        final int[] cur = {min, min, yMin}; // dx, dz, y
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return () -> {
            int budget = 20000;
            while (budget-- > 0) {
                if (cur[0] > max) {
                    return true;
                }
                if (((cur[0] & 1) | (cur[1] & 1)) != 0) {
                    pos.set(baseX + cur[0], cur[2], baseZ + cur[1]);
                    if (isAllToStoneTagged(level.getBlockState(pos))) {
                        level.setBlock(pos, lightState, Block.UPDATE_CLIENTS);
                    }
                }
                if (++cur[2] > yMax) {
                    cur[2] = yMin;
                    if (++cur[1] > max) {
                        cur[1] = min;
                        cur[0]++;
                    }
                }
            }
            return cur[0] > max;
        };
    }

    public void registerAll() {
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
                    ServerLevel end = ctx.server().getLevel(Level.END);
                    if (end != null) {
                        double posX = 100.5, posY = 49, posZ = 0.5;
                        // Force the destination chunk to load before teleporting, else the
                        // client hangs on "Loading terrain..."
                        BlockPos p = BlockPos.containing(posX, posY, posZ);
                        end.getChunk(p.getX() >> 4, p.getZ() >> 4);
                        Mc.teleportTo(t, end, posX, posY, posZ, t.getYRot(), t.getXRot());
                    }
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
                    // A punishment teleport must NOT move the player's spawn point — only
                    // the initial game-start spread does that, so a later death still
                    // returns them to their original spread location.
                    ctx.game().spreadFarAway(t);
                    announce(ctx.game(), self, "Teleported far, far away:", t, ChatFormatting.LIGHT_PURPLE);
                }).build());

        add(DeathSwapItem.of(7, LIGHT_BLUE, ChatFormatting.AQUA,
                "Give yourself a super fast shovel", "In case someone tries to do the boring-old gravel/sand trap")
                .effect((ctx, self, t) -> {
                    // Datapack 7a: diamond_shovel[enchantments={efficiency:5}] with this
                    // exact name + lore — same power, just the lore line was missing.
                    ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
                    Mc.enchant(self, shovel, Enchantments.EFFICIENCY, 5);
                    shovel.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            Component.literal("Super-fast shovel")
                                    .withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)));
                    shovel.set(net.minecraft.core.component.DataComponents.LORE,
                            new net.minecraft.world.item.component.ItemLore(List.of(
                                    Component.literal("To use against the classic falling sand/gravel trap"))));
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
                    Mc.msg(self, translate(ctx, "A gravel tower was placed right in front of you!"), ChatFormatting.WHITE);
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 99f, 1.0f);
                }).build());

        add(DeathSwapItem.of(10, BLUE, ChatFormatting.BLUE,
                "Teleport someone to the middle of the ocean", "If Tom Hanks could survive it, then anyone can")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    // The datapack hard-codes far coordinates that aren't guaranteed to be
                    // ocean; instead locate a real ocean biome (native /locate biome).
                    ServerLevel ow = ctx.server().overworld();
                    BlockPos ocean = Mc.findOcean(ow, t.getRandom());
                    Mc.teleportTo(t, ow, ocean.getX() + 0.5, ocean.getY(), ocean.getZ() + 0.5,
                            t.getYRot(), t.getXRot());
                    announce(ctx.game(), self, "Teleported to the middle of an ocean:", t, ChatFormatting.BLUE);
                }).build());

        add(DeathSwapItem.of(11, LIME, ChatFormatting.GREEN,
                "Disable a player's ability to jump: 60 secs", "Must've eaten too much McDonald's")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.JUMP_STRENGTH, 0.0);
                    ctx.effects().apply(t, new ActiveEffect("jump_disabled", 61 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.JUMP_STRENGTH, DEF_JUMP)));
                    Mc.title(t, " ", translate(ctx, "You can't jump: 1 minute"), ChatFormatting.WHITE, ChatFormatting.GREEN);
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
                    Mc.msg(self, translate(ctx, ">> A nether portal was placed in front of you! <<"), ChatFormatting.RED);
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
                    Mc.title(t, translate(ctx, "RUN away!"), "", ChatFormatting.RED, ChatFormatting.WHITE);
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
                    Mc.msg(self, translate(ctx, ">> A hole to the void summoned in front of you! <<"), ChatFormatting.WHITE);
                    Mc.playSound(self, SoundEvents.STONE_BREAK, 99f, 1.0f);
                }).build());

        add(DeathSwapItem.of(18, MAGENTA, ChatFormatting.AQUA,
                "Enter creative mode for 10 seconds", "You can get ANYTHING you want or need!")
                .effect((ctx, self, t) -> {
                    self.setGameMode(GameType.CREATIVE);
                    ctx.effects().apply(self, new ActiveEffect("creative_mode", (int) (10.5 * 20), null,
                            p -> p.setGameMode(GameType.SURVIVAL)));
                    Mc.msg(self, translate(ctx, ">>> You are in CREATIVE MODE for 10 seconds! <<<"), ChatFormatting.AQUA);
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
                    List<ServerPlayer> all = new ArrayList<>(ctx.game().alivePlayers());
                    all.removeIf(p -> ctx.effects().hasEffect(p.getUUID(), "shield"));
                    if (all.isEmpty()) {
                        Mc.msg(self, translate(ctx, ">> Everyone is shielded! Item had no effect. <<"), ChatFormatting.RED);
                        return;
                    }
                    ServerPlayer victim = all.get(self.getRandom().nextInt(all.size()));
                    // /clear wipes everything EXCEPT the three powerup slots (6,7,8):
                    // the datapack clears only real items, never the offer/filler that
                    // lives in those slots. Save and restore them around the wipe.
                    var inv = victim.getInventory();
                    ItemStack[] kept = {inv.getItem(6), inv.getItem(7), inv.getItem(8)};
                    inv.clearContent();
                    inv.setItem(6, kept[0]);
                    inv.setItem(7, kept[1]);
                    inv.setItem(8, kept[2]);
                    boolean nl = ctx.game().settings().isDutch();
                    Mc.title(victim, " ", self.getScoreboardName()
                                    + Translator.translate(nl, " cleared your inventory!"),
                            ChatFormatting.WHITE, ChatFormatting.RED);
                    // Always name the unlucky player explicitly (it can be the user).
                    ctx.game().broadcast(">> " + self.getScoreboardName()
                            + Translator.translate(nl, " --> Cleared ") + victim.getScoreboardName()
                            + Translator.translate(nl, "'s inventory! (randomly chosen)"), ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(25, GRAY, ChatFormatting.WHITE,
                "Make someone leave a bedrock trail: 40 secs", "Wherever they walk, the blocks below them turn to bedrock.")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    ctx.effects().apply(t, new ActiveEffect("bedrock_trail", 72 * 20, p -> {
                        // Lay one bedrock block directly below the player's feet, without
                        // neighbour/light updates (a per-tick full update is what tanked the
                        // tick rate). Only run while the player is actually standing on the
                        // ground so the trail forms where they walk rather than mid-jump.
                        // Use the block directly beneath the block their feet occupy: this is
                        // always strictly below the player, so it can never be placed inside
                        // them — even when onGround() reports true at a fractional feet Y
                        // (e.g. just after landing), which the old `getY() - 0.9` floor could
                        // round back onto the feet block, clipping them into the trail.
                        if (!p.onGround()) {
                            return;
                        }
                        ServerLevel lvl = Mc.level(p);
                        BlockPos below = p.blockPosition().below();
                        // Don't fill genuine gaps: if there's no floor at all below the player
                        // (e.g. they walked off a ledge while onGround() still read true for a
                        // tick) leave it empty so they keep falling rather than having bedrock
                        // spawned into them.
                        VoxelShape shape = lvl.getBlockState(below).getCollisionShape(lvl, below);
                        if (shape.isEmpty()) {
                            return;
                        }
                        Mc.setBlockFast(lvl, below, Blocks.BEDROCK);
                        // If the old floor was a partial block (slab, carpet, snow layer, ...)
                        // the player's feet can sit below the top of the new full bedrock
                        // block. Lift them onto its top face so the trail can't clip them.
                        double bedrockTop = below.getY() + 1;
                        if (p.getY() < bedrockTop) {
                            Mc.teleport(p, p.getX(), bedrockTop, p.getZ());
                        }
                    }, null));
                    Mc.title(t, " ", translate(ctx, "Look below you!"), ChatFormatting.WHITE, ChatFormatting.WHITE);
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
                // Datapack 30a gives a plain fire_resistance potion; item 64 is the
                // *long* variant. (They were both handing out LONG before.)
                .effect((ctx, self, t) -> Mc.givePotion(self, Potions.FIRE_RESISTANCE)).build());
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
                    Mc.title(t, translate(ctx, ">> HEADS UP!! <<"), "", ChatFormatting.RED, ChatFormatting.WHITE);
                    announce(ctx.game(), self, "Spawned falling anvils above", t, ChatFormatting.WHITE);
                }).build());

        add(DeathSwapItem.of(33, CYAN, ChatFormatting.AQUA,
                "Put someone in adventure mode: 40 secs", "Minecraft without the 'mine' part")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.BLOCK_INTERACTION_RANGE, 0.0);
                    ctx.effects().apply(t, new ActiveEffect("no_interaction", 40 * 20, null,
                            p -> Mc.resetAttribute(p, Attributes.BLOCK_INTERACTION_RANGE, DEF_INTERACT)));
                    Mc.title(t, " ", translate(ctx, ">> You're in adventure mode for 40 secs! <<"), ChatFormatting.WHITE, ChatFormatting.AQUA);
                    announce(ctx.game(), self, "Put into adventure mode for 40 seconds:", t, ChatFormatting.AQUA);
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
                            Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, Items.DIAMOND_SPEAR}) {
                        Mc.give(self, it, 1);
                    }
                    // The single diamond is part of the datapack chest (slot 10), so it's faithful.
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
                    Mc.title(t, " ", translate(ctx, ">> You are very smol! <<"), ChatFormatting.WHITE, ChatFormatting.RED);
                    announce(ctx.game(), self, "Made extremely tiny:", t, ChatFormatting.RED);
                }).build());

        add(DeathSwapItem.of(44, LIME, ChatFormatting.GREEN,
                "Make someone extremely huge", "Alice in Minecraft-Land")
                .target(ItemTarget.OPPONENT).effect((ctx, self, t) -> {
                    Mc.setAttribute(t, Attributes.SCALE, 16.0);
                    Mc.effect(t, MobEffects.JUMP_BOOST, 51, 3);
                    ctx.effects().apply(t, new ActiveEffect("huge_scale", 60 * 20,
                            // Keep fall distance pinned while giant, and clear it when the
                            // scale reverts — otherwise shrinking back from 16x dealt the
                            // player fall damage from the distance fallen while huge.
                            p -> p.fallDistance = 0.0f,
                            p -> {
                                Mc.resetAttribute(p, Attributes.SCALE, DEF_SCALE);
                                p.fallDistance = 0.0f;
                            }));
                    Mc.title(t, " ", translate(ctx, ">> You are very beeg! <<"), ChatFormatting.WHITE, ChatFormatting.GREEN);
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
                    int[] ticksLeft = {52 * 20};
                    ctx.effects().apply(t, new ActiveEffect("nether_world", 52 * 20, p -> {
                        int remaining = ticksLeft[0]--;
                        boolean isCrimson = remaining > 420;
                        ServerLevel lvl = Mc.level(p);
                        BlockPos o = p.blockPosition();
                        BlockPos c1 = o.offset(3, 5, 3);
                        BlockPos c2 = o.offset(-3, -2, -3);
                        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
                        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
                        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
                        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    cur.set(x, y, z);
                                    BlockState existing = lvl.getBlockState(cur);
                                    if (existing.isAir() || existing.is(Blocks.BEDROCK) || existing.is(Blocks.BARRIER) || existing.is(Blocks.LIGHT)) {
                                        continue;
                                    }
                                    if (existing.is(Blocks.NETHERRACK)
                                            || existing.is(Blocks.CRIMSON_NYLIUM)
                                            || existing.is(Blocks.WARPED_NYLIUM)
                                            || existing.is(Blocks.SOUL_SAND)
                                            || existing.is(Blocks.SOUL_SOIL)
                                            || existing.is(Blocks.NETHER_WART_BLOCK)
                                            || existing.is(Blocks.WARPED_WART_BLOCK)
                                            || existing.is(Blocks.CRIMSON_STEM)
                                            || existing.is(Blocks.WARPED_STEM)
                                            || existing.is(Blocks.NETHER_QUARTZ_ORE)
                                            || existing.is(Blocks.NETHER_GOLD_ORE)
                                            || existing.is(Blocks.LAVA)
                                            || existing.is(Blocks.WEEPING_VINES)
                                            || existing.is(Blocks.WEEPING_VINES_PLANT)
                                            || existing.is(Blocks.TWISTING_VINES)
                                            || existing.is(Blocks.TWISTING_VINES_PLANT)) {
                                        continue;
                                    }
                                    BlockState toState = null;
                                    if (existing.is(Blocks.WATER)) {
                                        toState = Blocks.LAVA.defaultBlockState();
                                    } else if (existing.is(Blocks.GRASS_BLOCK)) {
                                        toState = (isCrimson ? Blocks.CRIMSON_NYLIUM : Blocks.WARPED_NYLIUM).defaultBlockState();
                                    } else if (existing.is(SAND_TAG)) {
                                        toState = (isCrimson ? Blocks.SOUL_SAND : Blocks.WARPED_NYLIUM).defaultBlockState();
                                    } else if (existing.is(LEAVES_TAG)) {
                                        toState = (isCrimson ? Blocks.NETHER_WART_BLOCK : Blocks.WARPED_WART_BLOCK).defaultBlockState();
                                    } else if (existing.is(LOGS_TAG)) {
                                        toState = (isCrimson ? Blocks.CRIMSON_STEM : Blocks.WARPED_STEM).defaultBlockState();
                                    } else if (existing.is(COAL_ORES_TAG)
                                            || existing.is(COPPER_ORES_TAG)
                                            || existing.is(IRON_ORES_TAG)
                                            || existing.is(LAPIS_ORES_TAG)
                                            || existing.is(REDSTONE_ORES_TAG)
                                            || existing.is(EMERALD_ORES_TAG)) {
                                        toState = Blocks.NETHER_QUARTZ_ORE.defaultBlockState();
                                    } else if (existing.is(GOLD_ORES_TAG)
                                            || existing.is(DIAMOND_ORES_TAG)) {
                                        toState = Blocks.NETHER_GOLD_ORE.defaultBlockState();
                                    } else if (existing.is(Blocks.VINE) || existing.is(Blocks.CAVE_VINES) || existing.is(Blocks.CAVE_VINES_PLANT)) {
                                        toState = (isCrimson ? Blocks.WEEPING_VINES : Blocks.TWISTING_VINES).defaultBlockState();
                                    } else if (existing.is(Blocks.END_STONE) || isAllToStoneTagged(existing)) {
                                        toState = Blocks.NETHERRACK.defaultBlockState();
                                    }

                                    if (toState != null) {
                                        lvl.setBlock(cur, toState, Block.UPDATE_CLIENTS);
                                    }
                                }
                            }
                        }
                    }, p -> Mc.msg(p, translate(ctx, ">> The world has finished transforming into the Nether! <<"), ChatFormatting.RED)));
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
                    Mc.msg(self, translate(ctx, "You now have access to a village! (IMPORTANT: If you are underground, "
                            + "it spawned up on the surface!)"), ChatFormatting.YELLOW);
                    Mc.playSound(self, SoundEvents.ITEM_PICKUP, 9f, 1.0f);
                }).build());

        add(DeathSwapItem.of(54, ORANGE, ChatFormatting.YELLOW,
                "Spawn a desert temple right where you are", "Yunno, for that classic TNT trap we all love...")
                .effect((ctx, self, t) -> {
                    // Place one block offset so that it doesn't clip into the player
                    Mc.placeDesertTemple(Mc.level(self), at(self).north(-1).west(-1));
                    Mc.msg(self, translate(ctx, "You now have access to a desert temple/pyramid! Good job!"), ChatFormatting.YELLOW);
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

    }

    // ---- effect helpers ----

    private static void shield(ItemContext ctx, ServerPlayer self, int seconds) {
        ctx.effects().apply(self, new ActiveEffect("shield", seconds * 20, null,
                p -> Mc.msg(p, translate(ctx, "Your shield wore off. Others can target you again."), ChatFormatting.GRAY)));
        ctx.game().broadcast(">> " + self.getName().getString()
                + translate(ctx, " shielded themself from negative items! <<"), ChatFormatting.GOLD);
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
        // Datapack 60b: summon a parched (the desert skeleton), invulnerable + NoAI,
        // then each tick keep it 0.74 blocks in front of the target's face.
        Entity scare = Mc.summonRel(t, EntityTypes.PARCHED, 0, 0, 1);
        if (scare instanceof net.minecraft.world.entity.Mob m) {
            m.setNoAi(true);
            m.setInvulnerable(true);
            m.setPersistenceRequired();
        }
        // entity.ghast.scream on cast (datapack plays it for everyone at the target).
        Mc.playSound(t, SoundEvents.GHAST_SCREAM, 9f, 1.0f);
        ctx.effects().apply(t, new ActiveEffect("jumpscare", 30 * 20, p -> {
            if (scare.isAlive()) {
                // `^ ^ ^0.74 facing entity @s feet`: 0.74 forward at the player's feet
                // level, looking back at them.
                Vec3 look = p.getLookAngle();
                scare.snapTo(p.getX() + look.x * 0.74, p.getY(), p.getZ() + look.z * 0.74,
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
        Mc.title(self, " ", t.getName().getString()
                + translate(ctx, " can't see you!"),
                ChatFormatting.WHITE, ChatFormatting.GREEN);
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
        }, p -> Mc.msg(p, translate(ctx, ">> The earthquake has concluded! You are safe now! <<"), ChatFormatting.YELLOW)));
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

    /** Exact {@code misc/quartz_pillars}: concentric square pillar rings (radius 2..26) + lit centre. */
    private static void quartzMaze(ServerLevel level, BlockPos o, int ly, int uy) {
        quartzColumn(level, o.getX(), o.getZ(), 2, uy);                   // centre column (abs y=2..uy)
        level.setBlock(o.offset(0, -1, 0), Blocks.QUARTZ_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
        Mc.fillState(level, o, o.offset(0, 1, 0), Mc.light(15), FillMode.ALL);
        for (int r = 2; r <= 26; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        quartzColumn(level, o.getX() + dx, o.getZ() + dz, ly, uy);
                    }
                }
            }
        }
    }

    private static boolean hasBeneficialEffect(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.alchemy.PotionContents contents =
            stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                net.minecraft.world.item.alchemy.PotionContents.EMPTY);
        for (net.minecraft.world.effect.MobEffectInstance e : contents.getAllEffects()) {
            if (e.getEffect().value().isBeneficial()) return true;
        }
        return false;
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
