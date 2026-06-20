package com.deathswap;

import com.deathswap.config.DeathSwapConfig;
import com.deathswap.game.DeathSwapCommands;
import com.deathswap.game.GameManager;
import com.deathswap.game.WorldReset;
import com.deathswap.items.ItemManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric entrypoint. Wires the {@link GameManager} into the server tick loop,
 * player connection events, the death event and the command tree.
 */
public final class DeathSwapMod implements ModInitializer {

    public static final String MOD_ID = "deathswap";
    public static final Logger LOGGER = LoggerFactory.getLogger("DeathSwap");

    private static final GameManager GAME = new GameManager();

    public static GameManager game() {
        return GAME;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Death Swap But Way Better — initialising");

        DeathSwapConfig.load();

        ServerLifecycleEvents.SERVER_STARTED.register(GAME::onServerStarted);

        // When the server shuts down for an automatic world reset (triggered after
        // it sits empty in the hub), free the region files and change the seed now
        // that every level is saved and closed. An external restart wrapper brings
        // the server back up on the new seed.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            if (GAME.resetPending()) {
                WorldReset.resetWorld(server.getWorldPath(LevelResource.ROOT));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> GAME.tick());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                GAME.onPlayerJoin(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GAME.onPlayerLeave(handler.player));

        // Intercept player deaths to run the lives/elimination logic.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                return GAME.onAllowDeath(player, source);
            }
            return true;
        });

        // Re-give the starter kit if a player respawns with an empty inventory.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                GAME.onPlayerRespawn(newPlayer));

        // The powerup slots hold a barrier as filler, which is a placeable block.
        // Cancel any attempt to place it so players can't build with it.
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (ItemManager.isLocked(player.getItemInHand(hand))) {
                // FAIL cancels server-side placement, but the client has already
                // predicted the use and emptied the slot. Resync the inventory so
                // the barrier filler stays in the hotbar.
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.containerMenu.sendAllDataToRemote();
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DeathSwapCommands.register(dispatcher, GAME));

        LOGGER.info("Death Swap ready. Use /deathswap settings then /deathswap start.");
    }
}
