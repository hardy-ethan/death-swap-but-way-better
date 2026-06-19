package com.deathswap.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;

/**
 * Thin wrappers over the vanilla server API. All version-sensitive calls and a
 * few "reach into a vanilla subsystem" shortcuts live here so the item effects
 * read declaratively.
 */
public final class Mc {

    private Mc() {
    }

    /** How a {@link #fill} call treats existing blocks. */
    public enum FillMode {
        /** Replace every block in the box (datapack default + {@code destroy}). */
        ALL,
        /** Only replace blocks that are currently air ({@code replace air}). */
        AIR_ONLY,
        /** Replace any "natural" block, i.e. not air/bedrock/barrier ({@code replace #tag}). */
        NATURAL_ONLY,
        /** Only the outer shell of the box ({@code hollow}). */
        HOLLOW
    }

    // ---- chat / titles ----

    public static void msg(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    public static void msg(ServerPlayer player, Component component) {
        player.sendSystemMessage(component);
    }

    public static void broadcast(MinecraftServer server, String text, ChatFormatting color) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            msg(p, text, color);
        }
    }

    public static void title(ServerPlayer player, String title, String subtitle,
                             ChatFormatting titleColor, ChatFormatting subtitleColor) {
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(subtitle).withStyle(subtitleColor)));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal(title).withStyle(titleColor)));
    }

    /** Show text on the action bar (above the hotbar). */
    public static void actionBar(ServerPlayer player, String text, ChatFormatting color) {
        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal(text).withStyle(color)));
    }

    /** Set the player's forced respawn point (so deaths/relogs return them here). */
    public static void setSpawn(ServerPlayer player, ServerLevel level, BlockPos pos,
                                float yaw, float pitch) {
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                new LevelData.RespawnData(GlobalPos.of(level.dimension(), pos), yaw, pitch),
                true), false);
    }

    /** Send a title + subtitle built from arbitrary {@link Component}s (bilingual/styled text). */
    public static void titleRaw(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Set only the subtitle line (the next title packet shows it). */
    public static void subtitleRaw(ServerPlayer player, Component subtitle) {
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    /** Set only the title line. */
    public static void titleOnly(ServerPlayer player, Component title) {
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Action bar message (native {@code title @a actionbar ...}). */
    public static void actionbar(ServerPlayer player, Component text) {
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    /** Native {@code title @a times <fadeIn> <stay> <fadeOut>} (ticks). */
    public static void titleTimes(ServerPlayer player, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
    }

    // ---- effects / attributes ----

    public static void effect(ServerPlayer player, Holder<MobEffect> effect, int seconds, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, false, true, true));
    }

    public static void clearEffect(ServerPlayer player, Holder<MobEffect> effect) {
        player.removeEffect(effect);
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
        giveStack(player, new ItemStack(item, count));
    }

    public static void giveStack(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    public static void givePotion(ServerPlayer player, Holder<Potion> potion) {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
        giveStack(player, stack);
    }

    public static void enchant(ServerPlayer player, ItemStack stack, ResourceKey<Enchantment> enchantment, int level) {
        Holder<Enchantment> holder = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        stack.enchant(holder, level);
    }

    // ---- world: sounds ----

    public static void playSound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        ServerLevel level = level(player);
        Vec3 p = player.position();
        level.playSound(null, p.x, p.y, p.z, sound.value(), SoundSource.MASTER, volume, pitch);
    }

    public static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        ServerLevel level = level(player);
        Vec3 p = player.position();
        level.playSound(null, p.x, p.y, p.z, sound, SoundSource.MASTER, volume, pitch);
    }

    // ---- world: teleport ----

    public static ServerLevel level(Entity entity) {
        return (ServerLevel) entity.level();
    }

    /**
     * Nudge a player's look direction and push the new rotation to their client.
     * This is the equivalent of the datapack's {@code /rotate @s ~dyaw ~dpitch}:
     * unlike {@code setXRot}/{@code setYRot} (server-side only), it actually moves
     * the player's camera. Used by the look-down / motion-sickness items.
     */
    public static void rotateRelative(ServerPlayer player, float deltaYaw, float deltaPitch) {
        player.forceSetRotation(deltaYaw, true, deltaPitch, true);
    }

    public static void teleport(ServerPlayer player, double x, double y, double z) {
        teleportTo(player, level(player), x, y, z, player.getYRot(), player.getXRot());
    }

    public static void teleportTo(ServerPlayer player, ServerLevel level,
                                  double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, java.util.Set.of(), yaw, pitch, false);
        player.fallDistance = 0.0f;
    }

    // ---- world: entities ----

    public static <T extends Entity> T summon(ServerLevel level, EntityType<T> type, double x, double y, double z) {
        return type.spawn(level, BlockPos.containing(x, y, z), EntitySpawnReason.COMMAND);
    }

    /** Summon relative to a reference entity's precise position. */
    public static <T extends Entity> T summonRel(Entity ref, EntityType<T> type, double dx, double dy, double dz) {
        Vec3 p = ref.position();
        return summon(level(ref), type, p.x + dx, p.y + dy, p.z + dz);
    }

    /**
     * Summon at a horizontal offset, but nudge the spawn to a vertical gap with
     * head-room so the mob doesn't immediately suffocate inside terrain/walls.
     * Searches near the reference's feet level (out to ±6 blocks) for two stacked
     * non-suffocating blocks. Used for the Viet Cong ambush, which previously
     * spawned husks straight into hillsides.
     */
    public static <T extends Entity> T summonRelSafe(Entity ref, EntityType<T> type, double dx, double dz) {
        ServerLevel level = level(ref);
        Vec3 p = ref.position();
        double x = p.x + dx, z = p.z + dz;
        int baseY = (int) Math.floor(p.y);
        int[] order = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6};
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int dy : order) {
            int y = baseY + dy;
            cur.set((int) Math.floor(x), y, (int) Math.floor(z));
            boolean feetClear = !level.getBlockState(cur).isSuffocating(level, cur);
            cur.setY(y + 1);
            boolean headClear = !level.getBlockState(cur).isSuffocating(level, cur);
            if (feetClear && headClear) {
                return summon(level, type, x, y, z);
            }
        }
        return summon(level, type, x, baseY, z);
    }

    // ---- world: blocks ----

    public static void setBlock(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, block.defaultBlockState());
    }

    /**
     * Place a block without triggering neighbour/observer updates. Used for the
     * per-tick bedrock trail, where a full update each tick is what makes it lag.
     */
    public static void setBlockFast(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState(),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    /** Place a chest and drop a single stack into it (e.g. a prison escape kit). */
    public static void placeChestWithItem(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.Container container
                && container.getContainerSize() > 0) {
            container.setItem(0, stack);
        }
    }

    /** Spawn coloured dust particles, visible to nearby players (forcefield, etc.). */
    public static void dust(ServerLevel level, double x, double y, double z, int rgb, float scale,
                            int count, double spreadX, double spreadY, double spreadZ) {
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(rgb, scale),
                x, y, z, count, spreadX, spreadY, spreadZ, 0.0);
    }

    /** Fill the inclusive box between two corners with {@code block}, honouring {@code mode}. */
    public static void fill(ServerLevel level, BlockPos c1, BlockPos c2, Block block, FillMode mode) {
        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
        BlockState state = block.defaultBlockState();
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cur.set(x, y, z);
                    BlockState existing = level.getBlockState(cur);
                    switch (mode) {
                        case AIR_ONLY -> {
                            if (!existing.isAir()) continue;
                        }
                        case NATURAL_ONLY -> {
                            if (!isNatural(existing)) continue;
                        }
                        case HOLLOW -> {
                            boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                            if (!shell) continue;
                        }
                        case ALL -> { /* always */ }
                    }
                    level.setBlockAndUpdate(cur, state);
                }
            }
        }
    }

    /** A block the datapack's "replace #tag" conversions would touch (not air/bedrock/barrier). */
    private static boolean isNatural(BlockState state) {
        if (state.isAir()) return false;
        return !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER) && !state.is(Blocks.LIGHT);
    }

    public static void explodeAt(ServerLevel level, double x, double y, double z, float radius) {
        level.explode(null, x, y, z, radius, Level.ExplosionInteraction.TNT);
    }

    // ---- max health ----

    public static void addMaxHealth(ServerPlayer player, double delta) {
        AttributeInstance attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr != null) {
            double v = Math.max(2.0, attr.getBaseValue() + delta);
            attr.setBaseValue(v);
            if (delta > 0) {
                player.setHealth((float) v);
            } else if (player.getHealth() > v) {
                player.setHealth((float) v);
            }
        }
    }

    // ---- vanilla subsystem shortcut ----

    /**
     * Run a vanilla command as the server (full permissions), positioned at the
     * given player. Used only for subsystems that are impractical to reimplement
     * by hand: structure placement, time, difficulty and game rules.
     */
    public static void runAt(ServerPlayer player, String command) {
        MinecraftServer server = level(player).getServer();
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level(player))
                .withPosition(player.position())
                .withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    public static void runServer(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    // ---- world: native structure placement (replaces /place structure) ----

    /**
     * Native equivalent of {@code /place structure minecraft:<path>}: generates
     * the worldgen structure with its anchor at {@code pos} and places it across
     * the affected chunks. No command dispatch.
     *
     * <p>Version-sensitive (26.2): structure registry lookup, {@code generate(..)}
     * and {@code placeInChunk(..)} signatures, and {@code randomState()}.
     */
    public static boolean placeStructure(ServerLevel level, BlockPos pos, String path) {
        java.util.Optional<Holder.Reference<Structure>> holderOpt = level.registryAccess()
                .lookupOrThrow(Registries.STRUCTURE)
                .get(Identifier.fromNamespaceAndPath("minecraft", path));
        if (holderOpt.isEmpty()) {
            return false;
        }
        Holder<Structure> holder = holderOpt.get();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        // Mirrors net.minecraft.server.commands.PlaceCommand.placeStructure (26.2).
        StructureStart start = holder.value().generate(
                holder, level.dimension(), level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), ChunkPos.containing(pos),
                0, level, biome -> true);
        if (!start.isValid()) {
            return false;
        }
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()),
                SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(cp -> start.placeInChunk(level,
                level.structureManager(), generator, level.getRandom(),
                new BoundingBox(cp.getMinBlockX(), level.getMinY(), cp.getMinBlockZ(),
                        cp.getMaxBlockX(), level.getMaxY(), cp.getMaxBlockZ()), cp));
        return true;
    }

    /**
     * Native equivalent of a {@code structure_block[mode=load]} of a saved
     * template (e.g. the bundled {@code minecraft:amethyst_geode}). Places the
     * template with its corner at {@code pos}, no rotation/mirror.
     */
    public static void placeTemplate(ServerLevel level, BlockPos pos, String path) {
        StructureTemplateManager mgr = level.getStructureManager();
        java.util.Optional<StructureTemplate> tmpl = mgr.get(Identifier.fromNamespaceAndPath("minecraft", path));
        if (tmpl.isEmpty()) {
            return;
        }
        tmpl.get().placeInWorld(level, pos, pos, new StructurePlaceSettings(),
                level.getRandom(), Block.UPDATE_CLIENTS);
    }

    // ---- world: block states ----

    public static void setState(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state);
    }

    /** Fill the inclusive box with an explicit {@link BlockState}, honouring {@code mode}. */
    public static void fillState(ServerLevel level, BlockPos c1, BlockPos c2, BlockState state, FillMode mode) {
        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    cur.set(x, y, z);
                    BlockState existing = level.getBlockState(cur);
                    switch (mode) {
                        case AIR_ONLY -> { if (!existing.isAir()) continue; }
                        case HOLLOW -> {
                            boolean shell = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                            if (!shell) continue;
                        }
                        default -> { }
                    }
                    level.setBlockAndUpdate(cur, state);
                }
    }

    /** Fill the box with {@code state} but only where the existing block equals {@code only}. */
    public static void fillReplace(ServerLevel level, BlockPos c1, BlockPos c2, BlockState state, Block only) {
        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    cur.set(x, y, z);
                    if (level.getBlockState(cur).is(only)) {
                        level.setBlockAndUpdate(cur, state);
                    }
                }
    }

    // ---- common block-state builders (match the datapack's block[...] forms) ----

    public static BlockState light(int level) {
        return Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level);
    }

    public static BlockState ladder(Direction facing) {
        return Blocks.LADDER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
    }

    public static BlockState netherPortal(Direction.Axis axis) {
        return Blocks.NETHER_PORTAL.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, axis);
    }

    public static BlockState dripstone(net.minecraft.world.level.block.state.properties.SpeleothemThickness thickness,
                                       Direction verticalDirection) {
        return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.SPELEOTHEM_THICKNESS, thickness)
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, verticalDirection);
    }

    /** Break every block in the box, dropping its items (native {@code fill ... air destroy}). */
    public static void destroyBox(ServerLevel level, BlockPos c1, BlockPos c2) {
        int minX = Math.min(c1.getX(), c2.getX()), maxX = Math.max(c1.getX(), c2.getX());
        int minY = Math.min(c1.getY(), c2.getY()), maxY = Math.max(c1.getY(), c2.getY());
        int minZ = Math.min(c1.getZ(), c2.getZ()), maxZ = Math.max(c1.getZ(), c2.getZ());
        BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    cur.set(x, y, z);
                    if (!level.getBlockState(cur).isAir()) {
                        level.destroyBlock(cur, true);
                    }
                }
    }

    /** Place a standing birch sign rotated like {@code birch_sign[rotation=N]} with front-text lines. */
    public static void birchSign(ServerLevel level, BlockPos pos, int rotation, String[] lines) {
        BlockState state = Blocks.BIRCH_SIGN.defaultBlockState()
                .setValue(BlockStateProperties.ROTATION_16, rotation);
        level.setBlockAndUpdate(pos, state);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            SignText text = sign.getFrontText();
            for (int i = 0; i < lines.length && i < 4; i++) {
                text = text.setMessage(i, Component.literal(lines[i]));
            }
            sign.setText(text, true);
            sign.setChanged();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    /** Place a chest with a single stack at {@code slot} (native {@code setblock chest{Items:[...]}}). */
    public static void chestWithItem(ServerLevel level, BlockPos pos, int slot, ItemStack stack) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(slot, stack);
        }
    }

    /** Set a player's yaw in place (native {@code rotate @s <yaw> ~}). */
    public static void setYaw(ServerPlayer player, float yaw) {
        teleportTo(player, level(player), player.getX(), player.getY(), player.getZ(), yaw, player.getXRot());
    }
}
