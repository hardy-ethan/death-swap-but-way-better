package com.deathswap;

import com.deathswap.game.DeathSwapCommands;
import com.deathswap.game.GameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
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

        ServerLifecycleEvents.SERVER_STARTED.register(GAME::onServerStarted);

        ServerTickEvents.END_SERVER_TICK.register(server -> GAME.tick());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                GAME.onPlayerJoin(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                GAME.onPlayerLeave(handler.player));

        // Intercept player deaths to run the lives/elimination logic.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                return GAME.onAllowDeath(player);
            }
            return true;
        });

        // Re-give the starter kit if a player respawns with an empty inventory.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                GAME.onPlayerRespawn(newPlayer));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                DeathSwapCommands.register(dispatcher, GAME));

        LOGGER.info("Death Swap ready. Use /deathswap settings then /deathswap start.");
    }
}
