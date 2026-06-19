# Porting notes: datapack → Fabric 26.2 mod

This document records how the datapack maps onto the Java mod, what is fully
ported, what is approximated, and which call sites are most version-sensitive.

## Datapack → Java mapping

| Datapack concept | Java equivalent |
|---|---|
| `repeats.mcfunction` (tick tag) | `GameManager.tick()` via `ServerTickEvents.END_SERVER_TICK` |
| `Core` scoreboard flags (`gameOn`, `settingsOn`, `clockRunning`) | `GamePhase` enum |
| Per-player scores (`Lives`, `permPNo`, `PNo`, `Wins`, `no_death`) | `PlayerData` fields |
| Effect timer scores + `*_done` functions | `EffectManager` + `ActiveEffect` |
| `settings/*` functions, start button | `DeathSwapCommands` (`/deathswap set …`, `/deathswap start`) |
| `game/clock.mcfunction` + warnings | `GameManager.tickSwapClock()` |
| `game/swap.mcfunction` (cyclic tp) | `GameManager.doSwap()` |
| `prep/spread/*` (spreadplayers far away) | `GameManager.spreadFarAway()` |
| `game/player_died` / `player_eliminated` / `prep_winner` | `onAllowDeath` / `eliminate` / `declareWinner` |
| `items/give_items`, dye items w/ `custom_data` | `ItemManager.offer()` + `buildDye()` |
| drop-from-hotbar selection | `PlayerDropMixin` → `ItemManager.onItemDropped()` |
| `select` trigger / target picking | `/deathswap target <n>` → `onTargetSelected()` |
| `items/use/<n>` effects | `ItemRegistry` entries |
| `ds:superflat` hub dimension | Replaced by lobby state at overworld spawn (see below) |

## Fully implemented

- The complete game state machine and tick loop.
- Swap clock with fixed **and** random cycle modes, configurable pre-swap
  warning, and the cyclic position swap.
- Lives, post-death immunity window, elimination → spectator, and the
  last-player-standing win condition with the winner reward (totem, glow,
  resistance).
- Player spreading at game start and the `/deathswap tpaway` emergency relocate.
- The full item lifecycle: 3-item offer, drop-to-select detection, self /
  opponent / all-others / random / everyone targeting, shield gating, and
  timer-based effect cleanup.
- All 110 items (see the item-coverage section below).

## Item coverage

**All 110 items are implemented** in `ItemRegistry`, each reproducing the
observable effect of its `items/use/<n>` function and the dyed hotbar display
(colour + name + lore) of its `items/items/*` definition. Item durations are
taken straight from the datapack scoreboards (an effect score N lasts N/100s).
Multi-tick effects (bedrock trail, motion sickness, spawn-over-time hordes,
nether-world conversion, earthquake, forcefield, ears-bleed, no-craft, jumpscare,
spying, …) run through `EffectManager`/`ActiveEffect`.

### Approximated where a 1:1 port is impractical

- **Structure items** (53 village, 54 desert pyramid, 82 amethyst geode,
  93 stronghold, 101 trial chamber, 102 mansion, 108 ancient city) are now placed
  **natively** (no command dispatch): `Mc.placeStructure` runs the same
  `Structure.generate(..)` + `placeInChunk(..)` path `/place structure` uses, and
  `Mc.placeTemplate` loads the bundled saved `minecraft:amethyst_geode` template
  (`src/main/resources/data/minecraft/structure/amethyst_geode.nbt`). The
  decorative lit ladder/sign shafts and player rotation (93/101/108/102) are
  reproduced exactly via native fills/setblocks. These `generate`/`placeInChunk`
  calls are the most version-sensitive code in the mod.
- **Tag conversions** (22 stone, 65 water, 85 obsidian) and the **quartz maze**
  (84) are translated natively (`replaceTagged` over the datapack's tag union /
  `quartz_pillars` ring pattern). **Cage** (20), **prison** (61, incl. the
  diamond-pickaxe chest) and the **gravel tower** (9, lit pad + a column that
  grows over ticks) are exact.
- **Time / difficulty / game-rules** (66, 72-75, 79, 83) use the difficulty &
  game-rule APIs directly, with `time set` via the command dispatcher.
- **Item 78 "crash someone's game"**: we deliberately do **not** crash a client.
  It reproduces the in-game warning plus heavy nausea/blindness instead.
- **Parkour Civilization (95)** is reproduced **exactly**: the water floor and
  iron-bar cell are placed immediately, and the 20 light-wall planes (the ~600k
  position sweep) run as a `GameManager` build job that processes a budget of
  positions per tick, so it never freezes the server.
- **Superflat (76)** is still spread+grass platform (its `ds:superflat` dimension
  JSON is now bundled, ready to wire).
- **`fillbiome`** biome repaints (nether-world, peeing puddle) are skipped — only
  the block changes are reproduced.
- **Hub/lobby**: modelled as a *state* (adventure at world spawn) driven by
  `/deathswap` commands rather than the datapack's hand-built button hub.
- **Spread radius**: capped at `GameManager.SPREAD_MAX` (250,000) instead of
  29,999,000 for world-gen sanity — raise it for full fidelity.

## Not yet ported

- Bilingual UI: item *names* are stored, but in-game broadcast/title text and the
  Chinese item names are English-only for now (item 72 still flips the flag).

## 26.2 mapping notes

The mod **compiles and builds against Minecraft 26.2** (verified with
`gradle build`). 26.x ships unobfuscated, so there is no `mappings` line and the
non-remapping `net.fabricmc.fabric-loom` plugin is used. Most vanilla interaction
is funnelled through `util/Mc.java`. Notable renames/relocations encountered
versus the older Mojang names (useful when porting further):

- `ServerPlayer.serverLevel()` → use `(ServerLevel) entity.level()` (`Mc.level`).
- `ServerPlayer.teleportTo(...)` now takes `Set<Relative>` + a boolean.
- `net.minecraft.resources.ResourceLocation` → `net.minecraft.resources.Identifier`.
- `GameRules` moved to `net.minecraft.world.level.gamerules`; rules are
  `GameRule<T>` with `rules.get(rule)` / `rules.set(rule, val, server)`, and
  `KEEP_INVENTORY` / `NATURAL_HEALTH_REGENERATION` constants.
- Entity-type constants moved from `EntityType` to `EntityTypes`.
- `MobEffects`: `MOVEMENT_SPEED`→`SPEED`, `JUMP`→`JUMP_BOOST`,
  `DAMAGE_RESISTANCE`→`RESISTANCE` (all `Holder<MobEffect>`).
- Dyes & concrete moved into `Items.DYE` / `Items.CONCRETE` `ColorCollection`s
  (`Items.DYE.asList().get(dyeColor.ordinal())`).
- `Slime` is now `net.minecraft.world.entity.monster.cubemob.Slime`.
- NBT uses the Optional-based API (`CompoundTag.getIntOr(key, default)`).
- `Potions.*` are `Holder<Potion>`; `DataComponents.UNBREAKABLE` is `Unit`.
