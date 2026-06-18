package com.deathswap.effects;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Central registry of running {@link ActiveEffect}s, keyed by player UUID then
 * effect key. Ticked once per server tick by the game loop. Replaces the
 * datapack's scattered effect timer scoreboards and {@code *_done} functions.
 */
public final class EffectManager {

    private final Map<UUID, Map<String, ActiveEffect>> effects = new HashMap<>();

    /** Apply (or refresh) an effect on a player. Replaces an existing same-key effect. */
    public void apply(ServerPlayer player, ActiveEffect effect) {
        Map<String, ActiveEffect> map = effects.computeIfAbsent(player.getUUID(), u -> new HashMap<>());
        ActiveEffect previous = map.put(effect.key, effect);
        if (previous != null) {
            previous.end(player); // clean up the old one before overwriting
        }
    }

    public boolean hasEffect(UUID uuid, String key) {
        Map<String, ActiveEffect> map = effects.get(uuid);
        return map != null && map.containsKey(key);
    }

    public void clear(ServerPlayer player, String key) {
        Map<String, ActiveEffect> map = effects.get(player.getUUID());
        if (map == null) {
            return;
        }
        ActiveEffect effect = map.remove(key);
        if (effect != null) {
            effect.end(player);
        }
    }

    /** Clear every running effect for a player, invoking each cleanup. */
    public void clearAll(ServerPlayer player) {
        Map<String, ActiveEffect> map = effects.remove(player.getUUID());
        if (map == null) {
            return;
        }
        for (ActiveEffect effect : map.values()) {
            effect.end(player);
        }
    }

    public void tick(MinecraftServer server) {
        for (Map.Entry<UUID, Map<String, ActiveEffect>> entry : effects.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Map<String, ActiveEffect> map = entry.getValue();
            Iterator<ActiveEffect> it = map.values().iterator();
            while (it.hasNext()) {
                ActiveEffect effect = it.next();
                if (player == null) {
                    // Player logged off: drop the effect silently (no cleanup possible).
                    it.remove();
                    continue;
                }
                if (effect.tick(player)) {
                    effect.end(player);
                    it.remove();
                }
            }
        }
    }
}
