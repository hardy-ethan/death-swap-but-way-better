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
- Lives, the 8 s post-death immunity window, elimination → spectator, and the
  last-player-standing win condition with the winner reward (offhand totem,
  glowing 12 1, resistance/saturation 20 5).
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
- **Superflat (76)** now teleports the target into the bundled `ds:superflat`
  dimension (spread up to 29,999,000 like the datapack — cheap because the world
  is flat).
- All other one-shot builders are translated exactly natively: end-portal pad
  (4), nether portal (12), 7×7×7 `air destroy` drop (15), y=-60 pad + torch (16),
  void hole (17), falling anvils (32), bells (34), lava (35), water (42, already
  matched), gravel-everyone (46, matched), the obsidian sun ceiling (48 — its
  353×353 layer spread over ticks), cobwebs (51, matched), the stalagmite trap
  (86 — both lit tuff segments, ladders and the dripstone tip/frustum), chunk
  delete (87) and the layered fire field (109).
- **`fillbiome`** biome repaints (nether-world, peeing puddle) are skipped — only
  the block changes are reproduced.
- **Hub/lobby**: modelled as a *state* (adventure at world spawn) driven by
  `/deathswap` commands rather than the datapack's hand-built button hub.
- **Spread radius**: capped at `GameManager.SPREAD_MAX` (250,000) instead of
  29,999,000 for world-gen sanity — raise it for full fidelity.

## Bilingual + message/sound parity

The core game-flow text now matches the datapack verbatim in **both** languages
(`Lang Core` 1/2), built in `game/Messages.java` and emitted from `GameManager`:

- Swap (`>> Swapped! <<` action bar, the "You warped to: \<name\>" line + the
  Chinese-only follow-up, **no** swap sound — the datapack has none).
- Swap countdown (`>> Swapping <<` + "In 1 minute/30 seconds/.../In 1", the
  cumulative warn-level ladder, and `block.anvil.land` at volume 9 / pitch 0).
- Game start (`>> D.S. But Way Better! <<` + `event.raid.horn` 99/1 + the map
  credits), player spreading (`>> Spreading players... <<`).
- Death (`>> YOU DIED! <<` + `-1 Life!` + the broadcast line, resistance 10 5),
  elimination (`entity.ender_dragon.growl` 9/1.2 + `>> ELIMINATED! <<` subtitle).
- Winner (`prep_winner`+`winner`: clear effects, resistance/saturation 20 5,
  glowing 12 1, offhand totem, title times 0 140 5, `ender_dragon.death` 99, and
  the green broadcast line).
- Language switch item 72 (`lang_chinese`/`lang_english`: title + subtitle +
  `ui.button.click` 9 + banner + Chinese translator note + the switched line).
- Late-joiner spectate announcement (`make_newbie_spec`).

Fixed along the way: post-death immunity is **8 s** (no_death 800 @ 5/tick = 160
ticks), not 40 s; the extra teleport/"game begun"/"eliminated" chat lines were
removed; swap is an action bar, not a title.

## Not yet ported

- Chinese **item names/lore** in the hotbar (item *effects* and all game-flow
  text are bilingual; only the dyed item display strings are still English).
- Per-item activation broadcasts beyond item 72 (each `items/use/<n>` tellraw).

## 26.2 mapping notes

The mod **compiles and builds against Minecraft 26.2** (verified with
`gradle build`). 26.x ships unobfuscated, so there is no `mappings` line and the
non-remapping `net.fabricmc.fabric-loom` plugin is used. Most vanilla interaction
is funnelled through `util/Mc.java`. Notable renames/relocations encountered
versus the older Mojang names (useful when porting further):

- `ServerPlayer.serverLevel()` → use `(ServerLevel) entity.level()` (`Mc.level`).
- `ServerPlayer.teleportTo(...)` now takes `Set<Relative>` + a boolean.
- `net.minecraft.resources.ResourceLocation` → `net.minecraft.resources.Identifier`;
  the factory is `Identifier.fromNamespaceAndPath(ns, path)` (no `Identifier.of`).
- `Structure#generate(...)` is an instance method taking `(Holder<Structure>,
  ResourceKey<Level>, RegistryAccess, ChunkGenerator, BiomeSource, RandomState,
  StructureTemplateManager, long, ChunkPos, int, LevelHeightAccessor,
  Predicate<Holder<Biome>>)`; `StructureStart#placeInChunk` takes
  `level.structureManager()` (the `StructureManager`, **not** the template mgr).
  `Mc.placeStructure` mirrors `PlaceCommand.placeStructure` exactly.
- Pointed-dripstone thickness enum `DripstoneThickness` → `SpeleothemThickness`
  and the property `DRIPSTONE_THICKNESS` → `SPELEOTHEM_THICKNESS`.
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
