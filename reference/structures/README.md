# Extracted hub structures (reference)

Produced by `scripts/mca_read.py` from the saved overworld
(`dimensions/minecraft/overworld/region`). These are vanilla **structure NBT**
files (gzipped), loadable by a structure block, `/place template`, or a mod.
Block states **and** block entities (signs with their text + click commands,
command blocks with their `Command`, chests, etc.) round-trip exactly.

The hub is three clusters in the overworld around the origin:

| File | World box (x y z → x y z) | Size | Contents |
|---|---|---|---|
| `hub_lobby.nbt` | `-8 110 -8` → `8 117 8` | 17×8×17 | The player-facing lobby: circular `lime_terracotta` platform at y≈111, the clickable settings / JOIN / BEGIN wall-signs, birch start button at `0 112 7`. 29 signs. |
| `hub_computer.nbt` | `-22 48 -34` → `22 57 21` | 45×10×56 | The underground "computer": the 44 redstone-triggered command blocks (start/winner/firework/crash/nuke logic), the duplicate settings sign panel (x18–20), and surrounding bedrock/stone. Mostly solid (it's underground). |
| `hub_launchpad.nbt` | `-50 100 -34` → `-44 110 -22` | 7×11×13 | The spread / respawn-anchor pad at `-47,104,-32`: `redo_respawn_anchor` command blocks, light, the tp pad, and the two "If you did not get teleported away…" signs (EN + ZH). |

## Key world anchors (from the datapack functions)

- Lobby gather point: `tp @a 0 111 0`; `setworldspawn 2 111 -2 14 0`.
- Start button: birch button at `0 112 7` (swapped to an oak "confirm" button
  after the warning/checklist).
- BEGIN GAME sign: `0 114 6`. PRIMARY/MINOR settings sign headers: `0 113 -3` /
  `-3 112 0`.
- Redstone triggers (set by functions, power command-block chains):
  start `-20 50 -17` then `-19 50 -19`; winner `-17 50 -15`, `-11 50 -12`,
  `-1 50 -15`; crash `18 50 19`; nuke alarm `19 50 15`.
- Spread launch worldspawn: `setworldspawn -47 107 -32 90 1`.

## How the clusters map to the parity work

- **Lobby** → place this structure at the hub origin on world load (it is what
  players see and interact with). Sign click-commands reference `ds:settings/*`
  functions; the mod will reimplement those as commands/handlers.
- **Computer** → the command-block logic is being **reimplemented in Java**
  (see `PARITY_PLAN.md` WS-10 and `reference/hub_command_blocks.txt`). This
  structure is kept as a faithful backup / cross-check of that logic; it does
  not need to be placed if the logic is recoded.
- **Launchpad** → the spread + random-world-spawn behaviour is reimplemented
  (`game/warping_all`, `extra/redo_respawn_anchor`).

## Parser usage

```
python3 scripts/mca_read.py <region_dir> cmd                  # command blocks
python3 scripts/mca_read.py <region_dir> signs                # signs + text
python3 scripts/mca_read.py <region_dir> be-ids               # block-entity counts
python3 scripts/mca_read.py <region_dir> slice X1 Z1 X2 Z2 Y  # ASCII block map
python3 scripts/mca_read.py <region_dir> nonair X1 Y1 Z1 X2 Y2 Z2
python3 scripts/mca_read.py <region_dir> export X1 Y1 Z1 X2 Y2 Z2 out.nbt [--no-air]
```

Boxes are inclusive. `export` writes a gzipped structure NBT; omit `--no-air`
to include air (so placement clears the volume).
