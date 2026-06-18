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
- A representative catalogue (~50 of the datapack's 110 items) spanning every
  category: self-buffs, shields, opponent debuffs (attributes/effects), summons,
  world manipulation (fills/explosions/fire/portals), and utility/chaos items.

## Approximated or simplified

- **Hub/lobby**: the datapack ships a hand-built superflat hub with physical
  buttons. A mod can't carry that world build, so the lobby is modelled as a
  *state* (adventure mode at world spawn) driven by commands instead of buttons.
- **Spread radius**: the datapack uses up to 29,999,000 blocks, which is brutal
  on world-gen. `GameManager.SPREAD_MAX` defaults to 250,000 for playability —
  raise it for full fidelity.
- **Item interval / count scaling** follows the datapack's player-count table
  but item *durations* are expressed in plain server ticks rather than the
  datapack's 1/100-second scoreboard units.
- A few items are behavioural stand-ins where a faithful version needs a
  dedicated mixin (e.g. "block crafting/smelting", curse-of-binding armour).

## Not yet ported

- The remaining ~60 item IDs. They slot directly into `ItemRegistry` using the
  existing `DeathSwapItem.builder(...)` pattern — add the effect lambda and it is
  automatically included in the 3-item offer pool.
- Bilingual UI for every string (item *names* carry EN + 中文; broadcast/title
  text is currently English only).
- Cosmetic structure-spawn items (woodland mansion, trial chamber, ancient city)
  — easy to add with `StructureTemplate`/`/place`-equivalent calls.

## Version-sensitive call sites (verify against 26.2 mappings)

All vanilla interaction is funnelled through `util/Mc.java` to make porting
mechanical. The calls most likely to need a tweak on the exact 26.2 mappings:

1. **`Mc.teleportTo`** — `ServerPlayer.teleportTo(ServerLevel, x, y, z, yaw, pitch)`.
   Recent versions are migrating to `Entity.teleport(TeleportTransition)`. If the
   convenience overload is gone, reimplement this one method.
2. **`Mc.title`** — `ClientboundSetTitleTextPacket` / `ClientboundSetSubtitleTextPacket`.
3. **`ClickEvent.RunCommand`** (in `ItemManager`) — the record-style click event
   API introduced around 1.21.6.
4. **NBT** — `CompoundTag.getIntOr(key, default)`, the Optional-based accessor.
5. **`EntityType.spawn(ServerLevel, BlockPos, EntitySpawnReason)`** — `EntitySpawnReason`
   replaced `MobSpawnType`.
6. **`MobEffects` / `Attributes` constants** are `Holder<…>` references.

Because this environment has no Minecraft 26.2 artifacts, the project could not
be compiled here; the code targets the documented 26.2 / Mojang-mappings API
surface. Run `./gradlew build` against the real toolchain to surface any
remaining mapping mismatches (expected to be localized to `Mc.java`).
