# Death Swap — Datapack → Fabric Mod EXACT-Output Parity Plan

Goal: make the Fabric mod (`fabric-mod/`, currently on branch
`claude/datapack-fabric-26-2-port-lfuiqe`) reproduce the **exact observable
output** of the datapack (`datapacks/deathswap/data/ds/function/…`) for every
feature, removing the approximations the port took.

The datapack is identical on both `main` and the parity branch and is the
**source of truth**. All file references below are relative to
`datapacks/deathswap/data/ds/function/` (datapack) and
`fabric-mod/src/main/java/com/deathswap/` (mod).

## What "exact same output" means here

- **Achievable exactly:** every user-facing string (English **and** Chinese),
  titles/subtitles/action bars, sounds (event + volume + pitch), block
  geometry, summoned entity types/counts/NBT, effect durations, gamerule/time
  toggles, item stacks (name/lore/enchants/components), spread distances.
- **Not bit-identical, matched behaviourally instead:** the random item draw and
  random swap order. Java's RNG can't reproduce the datapack's
  `random value`/`@r` sequences, so we match the *rules* (pool, gates,
  no-duplicates, cyclic rotation) not the exact sequence.
- **Requires shipping datapack assets** (decision needed, see WS-10/WS-11): the
  `ds:superflat` hub & dimension, the Parkour-Civilization map, and the saved
  `amethyst_geode` structure are content baked into the world/datapack, not the
  mod. True parity means bundling them; otherwise they stay approximations.

---

## WS-0 — Bring the mod onto the parity branch (prerequisite)

The `fabric-mod/` tree lives only on `claude/datapack-fabric-26-2-port-lfuiqe`.
Import it into the parity branch (cherry-pick the mod paths or merge the branch)
so the work below has a home. No code change, just repo plumbing.

---

## WS-1 — Bilingual (Lang) text framework  ⟵ foundational, do first

**Divergence:** the datapack emits Chinese text whenever `Lang Core == 2`
(see virtually every `tellraw`/`title` with `if score Lang Core matches 2`).
The mod has `GameSettings.Language` and item 72 flips it, but **no output ever
uses it** — everything is English-only.

**Plan:**
- Add a tiny localization layer: every player-facing string becomes a pair
  `{en, zh}` resolved against `settings.language` at send time. A
  `Msg`/`Loc` helper in `util/` keyed by message id, or inline `pick(en, zh)`
  in `Mc.msg/title`.
- Audit every `Mc.msg`, `Mc.title`, `announce(...)`, broadcast, and command ack
  and give it the exact EN + ZH strings copied verbatim from the matching
  datapack function. (item 72's `toggleLanguage()` already exists; it just needs
  consumers.)
- This WS touches WS-2, WS-5, WS-6, WS-8 text — build the helper first, then the
  other workstreams supply their string pairs.

---

## WS-2 — Per-item messages, titles & sounds

**Divergence:** the datapack's `items/use/<n>` functions each emit a precise
broadcast, often a title/subtitle, and a specific sound. The mod's
`ItemRegistry` omits most messages/titles and **almost all sounds**, and adds a
spurious generic sound that the datapack never plays.

**Confirmed patterns to fix:**
- **Broadcast format for targeted items** — datapack uses a fixed shape:
  `>> <self> --> <verb phrase> <target>` (e.g. use/107b, use/110b, use/100b,
  use/21b). Reproduce this exact `tellraw` structure (the `">> "` green prefix,
  `@s` selector, `" --> "`, coloured verb, target selector) instead of the
  mod's terse `"Did X to:"`.
- **Self-item confirmations** — items 77, 79, 83, 89, 90, 91, 93, 94, 101, 102,
  104, 108 (and more) print a confirmation line and/or set a title in the
  datapack; the mod is silent. Add the exact text.
- **Sounds** — datapack plays specific events with explicit volume/pitch, e.g.
  `entity.warden.agitated … 9` (105), `entity.tnt.primed … 9` (107),
  `entity.enderman.scream … 9` (110), `block.stone.break … 99` (100/85/87),
  `entity.item.pickup … 9` (self-give items), `entity.spider.ambient` (21),
  `entity.player.splash … 99 1.6` (103). **Note:** in `playsound … ~ ~ ~ 9` the
  `9` is **volume**, not pitch — the sub-agent reports mislabelled this; copy
  the real `<volume> <pitch>` pair. The mod must add each item's sound with the
  exact event/volume/pitch.
- **Remove the spurious sound:** `ItemManager.afterUse()` plays
  `PLAYER_LEVELUP 0.5/1.5` which is **not** in `items/after_use.mcfunction`
  (that function plays nothing). Delete it; sounds belong to each item.

**Deliverable:** every item entry carries its exact `{en,zh}` message(s),
optional title/subtitle, and sound spec, applied at fire time.

---

## WS-3 — Offered-item display parity

Datapack `items/items/all.mcfunction` + `items/items/*` build a dyed item in
hotbar slots 6/7/8 with `custom_data={deathswapitem:true}`, a coloured
non-italic `custom_name`, a gray lore line, and `enchantment_glint_override`.

- **`custom_data`:** mod writes `{ds_item_id:N}`. For exact NBT output, also
  write `deathswapitem:true` (keep the internal id alongside it).
- **Lore/names:** verify each item's display name + colour + lore string against
  `items/items/*` and `items/detect_item.mcfunction`. Names/colours largely
  match; lore lines (e.g. "Throwback 2014", "POV: You are Neil Armstrong",
  "Severance in Minecraft") must match verbatim — audit all 110.
- **Offer text/sound** (`items/give_items.mcfunction`): title `" "` + subtitle
  `>> New items! <<` green (+ ZH), the long green/bold tellraw (+ ZH),
  `entity.item.pickup … 9`. Mod matches sound + EN text; **add ZH**.

---

## WS-4 — Item draw: pool, no-duplicates, availability gates

Datapack `give_items` draws 3 via `random_item` (1..110) then up to 45
`re-random_item` passes that (a) avoid duplicates among the 3 and (b) skip items
that aren't currently legal. The mod shuffles the whole pool and takes 3 (no
dups — OK) and has some `availableWhen` predicates.

**Plan — match the gating rules exactly** (verify each against
`re-random_item.mcfunction`):
- 47 (shorten cycle) excluded unless `randomCycle` off / relevant.
- 73/74/75 (set difficulty) excluded when already at that difficulty.
- 13/57/68 conditional (steak/notch-apple/extra-hearts one-shots).
- 3/31/97 (shields) excluded while already shielded.
- 78 (crash) handling.
Implement these as `availableWhen` predicates so the offered pool matches.
Exact RNG sequence is explicitly out of scope (see principle above).

---

## WS-5 — Swap clock: cumulative warnings + on-screen timer

**Divergence:** `game/clock.mcfunction` fires warnings **cumulatively** by
`warnLvl`:
- `warnLvl>=4`: 1-minute warning **and** 30s **and** 10s **and** 5→1 countdown.
- `warnLvl>=3`: 30s + 10s + 5→1.
- `warnLvl>=2`: 10s + 5→1.
- `warnLvl>=1`: 5→1 countdown only.

Each warning is title `>> Swapping <<` gold + subtitle (`"In 1 minute"`,
`"In 30 seconds"`, `"In 10 seconds"`, then `"In 5"/"In 4"/"In 3"` yellow,
`"In 2"` gold, `"In 1"` red) with `block.anvil.land … 9 0`. The mod only
announces the single configured threshold + a generic 1–5s countdown.

**Plan:** replace `tickSwapClock`'s single-threshold logic with the cumulative
warnLvl ladder and the exact subtitles/colours/sound above.

**On-screen timer:** datapack `showTimer==1` renders an action bar
`>> Swap: M:SS <<` (green/gold) every tick. `settings.showSwapTimer` exists but
nothing renders it — add the exact action bar.

---

## WS-6 — Swap mechanic exactness

`game/swap.mcfunction` (+ `reassign_pno`):
- Clears title/subtitle, then action bar `>> Swapped! <<` gold (+ ZH
  `>> 球员互换! <<`). Mod shows a title "Swapped!" — change to action bar + ZH.
- **Cyclic rotation by `PNo`:** P1→P2's spot, …, last→P1, via a marker at P1's
  location. The mod shuffles `alive` then rotates — behaviourally a random
  cyclic rotation; keep, but drive ordering from `reassign_pno` semantics.
- **Per-player "warped to" line:** datapack tellraws each player
  `* You warped to: <next player>` (green/bold) (+ ZH single line). Mod omits —
  add.
- **Fall damage:** datapack does `gamerule fall_damage false` for 1 tick
  (`end_fall_dam_prot`), not slow-falling. Replace the mod's
  `SLOW_FALLING`+`fallDistance=0` with the gamerule toggle to match behaviour.
- **Post-swap housekeeping:** `tp_away` reset + `give_back_tp` after 12s; end
  `96spying` on swap. Mod resets `canTpAway=true` immediately — change to the
  12s `give_back_tp` schedule and wire the 96spying end.

---

## WS-7 — Spread distance parity

Datapack spreads use `spreadplayers 0 0 <min> 29999000 false …`. The mod caps
`SPREAD_MAX = 250_000`. Set `SPREAD_MAX = 29_999_000` and use the correct
per-call min:
- game start (`prep/spread/*`): `10000`.
- item 6 (far away), item 81 (reshuffle): `10000`.
- item 76 (superflat): `10` (inside `ds:superflat`).
Note this reintroduces the world-gen lag the datapack README warns about — it is
the faithful behaviour.

---

## WS-8 — Death / immunity / elimination / winner

Confirmed exact values from `game/player_died`, `can_die_again`,
`player_eliminated`, `extra/post_death`, `extra/winner_fireworks`, `game/winner`:

- **Immunity window is ~8 s, not 40 s.** `no_death` increments +5 per tick and
  `can_die_again` triggers at `>=800` → 160 ticks = **8 s**. The mod sets
  `deathImmunityTicks = 20*40 = 800` (it treated the 800 *score* as ticks).
  **Fix: 160 ticks.**
- **Death broadcast:** `>> <self> > DIED! Lost a life!` (dark_red bold prefix,
  selector, red bold) — not the mod's "died and lost a life! (N left)". (+ no ZH
  broadcast in datapack.)
- **Death title/subtitle:** `>> YOU DIED! <<` red (+ ZH `>> 你死了! <<`),
  subtitle `-1 Life!` gold — **no remaining-lives count** (mod adds "(N left)").
- **Death resistance:** `resistance 10 5` = duration 10s, amplifier 5
  (**Resistance VI**). Mod uses amplifier 4 → fix to 5. The death `growl` sound
  is commented out in the datapack — **do not** play a death sound here;
  `post_death` is the source of the wither/post sound (replicate `game/post_death`).
- **Elimination** (`player_eliminated`): `entity.ender_dragon.growl … 9 1.2`
  (volume 9, pitch 1.2 — mod uses 1.0/1.0), subtitle `>> ELIMINATED! <<` red
  (+ ZH `>> 被淘汰! <<`) **only** (no title), join Eliminated team, spectator,
  clear PNo/itemPNo/permPNo, **add `Shield` tag**, `eliminations++`. Mod sets a
  title + "You're out of lives." subtitle — change to subtitle-only exact text.
- **Winner** (`prep_winner` → `winner` → `winner_fireworks`), exact values now
  confirmed:
  - `prep_winner`: glowing `6 1` (level 2), `effect clear @a`, resistance `20 5`
    (**Resistance VI**), saturation `20 5` (**Saturation VI**), totem in the
    **offhand** (`item replace entity @s weapon.offhand`), `title times 0 80 5`.
  - `winner`: glowing `12 1`, title `>> <winner> Won! <<` green + subtitle
    `They survived the way better death swap!` aqua (+ ZH), broadcast
    `\n>>> <winner> survived the longest and won the game! <<<\n` green/bold
    (+ ZH), `entity.ender_dragon.death … 99`, `Wins += 1`, then
    `prep_back_to_hub` after 10s.
  - `winner_fireworks` (triggered by the `-1 50 -15` command-block loop): summon
    `firework_rocket` `LifeTime:16` with `shape:"star", has_twinkle:true,
    has_trail:true, colors:[2424626,2420991,2739252,2871039]`.
  - Mod gaps: missing fireworks + saturation, totem given to inventory (should be
    offhand), resistance amplifier 4 (should be 5), glowing amplifier 0 (should
    be 1), and English-only text.

---

## WS-9 — Settings & defaults parity

Align `GameSettings` + `DeathSwapCommands` with the datapack's `Core` constants
(from `prep/reset_game.mcfunction` and the settings buttons):
- Lives options the datapack actually exposes (1/3/5) vs mod's 1..6.
- `timeCycle` 60..300; `warnLvl` 1..4 mapped to 5s/10s/30s/1min (keep enum but
  treat as cumulative per WS-5).
- **Basic-tools start** (`game_start`, `basicTools==1`): give stone
  sword/pickaxe/axe/shovel **+ crafting table**. The mod's `giveBasicTools`
  gives the four stone tools **+ 16 bread and no crafting table** — fix to match.
- Defaults: datapack `keep_inventory` default **on**, `noHunger` semantics
  (mod's `hunger=true` is the inverse — verify the default), natural regen,
  immediate respawn, send_command_feedback off, fall_damage on, pvp off.
- Hunger top-up: `repeats.mcfunction` gives `saturation 1 5 true` to players with
  `hunger<=17/18`; mod hard-sets food to 20. Match the saturation approach for
  identical behaviour, or document the deviation.

---

## WS-10 — Hub / lobby & start flow  ⟵ architectural (CORRECTED)

**Correction to an earlier assumption:** the hub is **not** in `ds:superflat`.
It is a hand-built map in the **overworld** at roughly `0,111,0`
(`back_to_hub`: `setworldspawn 2 111 -2 14 0`, `tp @a 0 111 0`;
`place_start_button`: birch button at `0 112 7`). The hub blocks are baked into
the four overworld region files (`dimensions/minecraft/overworld/region/r.{-1,0}.{-1,0}.mca`).
`ds:superflat` is **only** item 76's punishment destination (see WS-11).

**Bigger finding — the datapack is not self-contained in functions.** Part of the
game logic lives in **command blocks pre-placed in the saved overworld** near
origin (y ≈ 50), kicked off by `setblock … minecraft:redstone_block` at fixed
coordinates:
- start: birch button → `-20 50 -17`; `prep_game` → `-19 50 -19`
- winner: `prep_winner` → `-17 50 -15`; `winner` → `-1 50 -15`
- crash (item 78): `18 50 19`; nuke alarm (item 107): `19 50 15`

These command blocks orchestrate the spread/`game_start` start sequence, the
winner/`winner_fireworks` loop, the crash contraption and the nuke siren.

**RESOLVED:** I wrote a region/NBT parser (`scripts/mca_read.py`) and dumped all
55 hub command blocks to `reference/hub_command_blocks.txt`. The flows are now
known exactly:

*Start:* birch button → `start_game_button` sets redstone `-20 50 -17` → chain:
remove button, `entity.arrow.hit_player 9 0`, `>> WARNING! <<`, "Read chat before
starting!" + the long "Before you start…" checklist tellraw (+ ZH), then place an
**oak button** at `0 112 7`. Oak button → `prep/prep_game` → redstone
`-19 50 -19` → chain: "Are you ready??", "3"/"2"/"1" with `block.anvil.land 9 2`,
then `schedule ds:game/warping_all 1s`. `warping_all` freezes players, shows
">> Spreading players… <<" (+ ZH), sets Lives from `maxLives`,
`gamerule respawn_radius 1`, `setworldspawn -47 107 -32 90 1`, schedules
`prep/spread/p1..p12` at 2t…24t (each `spreadplayers 0 0 10000 29999000 false @s`
by `permPNo`), optional `random_cycle`, then `schedule ds:game/game_start 5s`.

*Winner:* `prep_winner` sets redstone `-17 50 -15` → chain: `>> GAME OVER! <<`
(+ ZH), "But who is the winner??" (+ ZH), `note_block.snare 9 0.9`, "And the
winner is…" (+ ZH), `setblock -11 50 -12 redstone` + `schedule ds:game/winner
3s`. `winner` shows the win title/broadcast, `ender_dragon.death 99`, `Wins++`,
sets redstone `-1 50 -15` → **seven** `ds:extra/winner_fireworks` blocks fire at
once, then `schedule prep_back_to_hub 10s`.

*Always-on hub repeaters:* `kill @e[type=enderman,distance=..40]` and
`kill @e[type=item,distance=..39]` at `0 50 0`. *Crash/nuke alarm:*
`note_block.pling` loops for `@a[tag=game_crashed]` / `@a[tag=107alarm]`.
*Respawn-anchor demo:* the `-48..-46 101` cluster runs
`extra/redo_respawn_anchor` (tp to one of 40 far world-spawns).

Only the hub **blocks** still need extracting as a structure (next step); the
logic above is straightforward to reimplement in Java.

`game/game_start` (the real start) does, exactly: reset speed/jump, clear
powder_snow around players, title `>> D.S. But Way Better! <<` gold + subtitle
`Created by Jerries!` yellow, `event.raid.horn … 99 1`, set `gameOn/clockRunning`,
`TimeS Items += 46`, sidebar→Lives, survival, pvp per setting, `reassign_pno` +
`assign_pno`, spawnpoint at feet, late-joiners→spectator + tp to a random
player, the "Map created by Jerries (Map version 1.0.3)" + TheWorfer27/Melumi11
credits (+ ZH), and `basicTools` → stone sword/pickaxe/axe/shovel **+ crafting
table** (NOT bread — the mod's `giveBasicTools` gives 16 bread and no crafting
table; fix it).

**To reproduce the hub exactly, the mod must:**
1. Place the hub build at `~0,111,0` in the overworld — extract it from the
   world save as a structure (`.nbt`) and have the mod place it on first load
   (or ship the region). A pure-code rebuild would need every block hand-coded.
2. Reimplement the command-block orchestration in Java (start → spread →
   `game_start`; winner → `winner_fireworks` loop; crash; nuke alarm), driven by
   the same triggers/timing.
3. Reproduce `make_newbie_spec`, `setworldspawn`/random world-spawn table
   (`extra/setworldspawn`, 40 entries), the start button, and the hub return
   sequence (`prep_back_to_hub` → 2s blindness → `back_to_hub` stats screen).

This is the largest workstream and the only part that cannot be "just code"
without shipping map data. Recommend extracting the hub as a structure + JSON
dimensions and reimplementing the command-block logic; flag the `.mca`
command-block read as a required sub-task.

---

## WS-11 — Custom worlds & structure decoration

- **Structure items 93 (stronghold), 101 (trial chamber), 108 (ancient city),
  86 (stalagmite):** datapack digs a light-lit shaft, places a ladder + a sign,
  prints `> Go down the ladder … (around y = 30)` (+ ZH), and **rotates the
  player 90°**. Mod just runs `/place`. Reproduce the shaft/ladder/sign/message/
  rotation around the `/place` call.
- **102 (mansion):** add the 90° rotation + exact message (+ ZH) + sound.
- **82 (amethyst geode):** datapack loads the **saved structure**
  `minecraft:amethyst_geode` via a structure block (the `.nbt` is in
  `generated/minecraft/structure/`); mod uses `/place feature` (different shape).
  Ship/track the saved structure and load it for exact output.
- **76 (superflat):** teleports the target into the **`ds:superflat`** dimension
  via `spreadplayers 0 0 10 29999000`. That dimension is **JSON-defined**
  (`data/ds/dimension/superflat.json` + `dimension_type/type_superflat.json`:
  flat plains, layers bedrock/stone/stone/grass, `structure_overrides:[villages]`,
  height 352, min_y -64) and generated on demand — its region folder is empty.
  Exact parity = register this dimension in the mod (or ship the dimension JSON)
  and spread the target into it, instead of the current grass-platform hack.
- **95 (parkour civilization):** CORRECTION — this is **not** a bundled custom
  world. The datapack hand-builds it with fills (the `parkour_civ` helper: iron-bar
  walls + a water moat over two Y-ranges). Reproduce those exact fills; the mod's
  single quartz cell is wrong.
- **Hotbar placeholders:** `inventory/resetitems.mcfunction` fills empty slots
  6/7/8 with placeholder `command_block` items named "Item Slot #1/2/3" (gray),
  `item_model=gray_stained_glass_pane`, `max_stack_size=1`,
  `custom_data={deathswapitem:true}`. The mod shows nothing in idle slots — add
  these placeholders for exact display.

---

## WS-12 — Per-item effect corrections (geometry / entities / counts)

High-confidence, verified divergences to fix in `ItemRegistry`:

| Item | Divergence | Fix |
|---|---|---|
| 40 | mod summons 2 lightning + sets fire | datapack: **1** bolt, no fire block |
| 49 | junk list 25 items | datapack lists **46** specific items (incl. pale_oak_fence_gate, red_sandstone_stairs, yellow_bed, infested_cobblestone, music_disc_ward, blue_carpet, resin_brick, red_banner, suspicious_sand, spruce_leaves, activator_rail, dirt_path, horn_coral, gray_concrete, writable_book, diorite_stairs, exposed_copper, bamboo_shelf, cake, …) — copy the full set |
| 59/60 | mod uses HUSK at offset (0,0,1) | datapack summons the `parched` mob at `^ ^ ^1` (face-relative) |
| 61 | missing chest + torch | add the diamond-pickaxe chest + torch placement |
| 70 | 12 HUSK | datapack: **56** `parched` w/ custom head texture + attack-damage attribute |
| 71 | netherite, no enchants | datapack: `diamond_sword` `damage=1560`, name "One-hit-kill -- ONE USE!", enchants fire_aspect 1/knockback 2/looting 3/sharpness 5, `attack_damage +999999`; + msg + `entity.item.pickup 9` |
| 100 | 1 scaled zombie + max_health 100 | datapack: **two** constructs — zombie carrying a `giant` passenger at `~4 ~3 ~4` **and** a scale-16 zombie at `~-5 ~4 ~-5`, both leather helmets; no max_health override |
| 105 | warden digs immediately | set Brain `dig_cooldown ttl:1200L` like datapack |
| 107 | no alarm/siren | datapack sets redstone block + scheduled `end_107alarm` siren; subtitle text "RUN TF AWAY!!" (mod truncates to "RUN!!") |
| 110 | spawns 105 | match datapack count (score 26 → verify exact total) |
| 8/36 | hordes over-spawn (105/101) | match exact 100 |
| 12/17/20/109 | fill geometry/fill-mode differs | match datapack fill volumes & replace-filters (e.g. 109 uses layered air+light→fire, not one AIR_ONLY box) |
| 77/104 | items given directly | datapack places a **chest** with specific slots; reproduce chest delivery + "Look down!" title (+ ZH) |
| 88 | silent direct swap | add marker swap + forceload + "you warped" tell to target + sound |
| 96 | no exit UI | add the clickable `[CLICK HERE TO EXIT SPY MODE!]` tellraw (+ ZH) |
| 65/85 | fill-mode mismatch | match tag-based `replace #minecraft:…` filters |

**Durations: audit, don't assume bugs.** Several sub-agent "wrong duration"
claims were false — they compared the *description text* ("45 seconds") to the
real `use/Nb` score (`4600` = 46 s) which the mod already matches (items 21, 80,
89, 90, 91, 92, 96, 97, 98, 99, 103 verified MATCH). Still, sweep every timed
item: datapack score `N` ⇒ `N/100` seconds ⇒ `N/100*20` ticks. Flag only true
mismatches.

---

## WS-13 — Shield gating completeness

The mod gates OPPONENT/RANDOM targeting on `shield`, but ALL_OTHERS/EVERYONE
items don't skip shielded players. Datapack `Shield` tag blocks all incoming
negative items. Ensure shielded players are skipped for ALL_OTHERS and any
all-targets negative item, and that eliminated players (who get `Shield`) are
excluded too.

---

## Verification

1. `gradle build` in `fabric-mod/` after each workstream (26.2, no-remap loom).
2. Manual smoke test per subsystem on a 26.2 server: hub→start→swap warnings→
   swap→item offer→each target mode→death→immunity→elimination→winner+fireworks.
3. Spot-check a sample of items per category (self-give, opponent, all-others,
   everyone, structure, timed-effect) against the datapack side-by-side,
   confirming identical chat/title/sound/blocks in both EN and ZH.

## Suggested order

WS-0 → WS-1 (localization spine) → WS-8 (correctness bugs: immunity, fireworks)
→ WS-5/WS-6 (swap UX) → WS-2/WS-3 (text+sound+display) → WS-4 (draw gates) →
WS-7 (spread) → WS-12/WS-13 (item fixes) → WS-9 (settings) → WS-11 (structure
decoration) → WS-10 (hub, after the fidelity decision).
