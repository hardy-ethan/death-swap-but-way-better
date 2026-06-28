"""Integration test: the hub platform clears everything above it and tidies drops.

When the first player joins, GameManager.buildHubPlatform() lays a 41x41 stone
floor at the overworld origin and then, per the `hub-platform-clear-blocks`
feature:

  1. Clears every block from the platform surface up to the world top across the
     whole footprint, so no terrain (trees, grass, ocean water, ...) is left
     floating above the lobby.
  2. Discards the item entities that pop out when that terrain is removed (e.g. a
     flower losing its support block when the dirt under it becomes stone), so
     the hub isn't littered with drifting drops.

To exercise both halves deterministically (independent of which biome the random
fresh world rolls), we plant our own scenario at known footprint columns *before*
the first player joins:

  * stone obstructions high above the platform  -> must be cleared to air
  * a dandelion on a dirt block                 -> popped + dropped when its
                                                    support is cleared, then the
                                                    dropped item must be discarded

The natural terrain at origin is cleared on top of this, reinforcing the checks.

Exit code 0 on PASS, 1 on FAIL. Run:
  mcp-test-server/.venv/bin/python mcp-test-server/tests/test_hub_platform_clears_blocks.py
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server

# Footprint is -20..20 in x/z around the origin. Pick a few interior/edge columns
# to obstruct and to probe. Y=200 is safely above any natural surface yet below
# the world top, so it falls inside the cleared range [feetY, worldTop).
OBSTRUCTIONS = [(15, 200, 15), (-18, 200, -18), (12, 200, -12)]
FLOWER_DIRT = (8, 200, 8)            # support block, cleared during the build
FLOWER_POS = (8, 201, 8)             # dandelion sitting on the dirt above


def log(msg):
    print(msg, flush=True)


def passed(resp):
    """True when an `/execute if ...` reported a match ('Test passed')."""
    return "passed" in resp.lower()


def block_is(x, y, z, block):
    """True if the block at (x,y,z) is minecraft:<block>."""
    return passed(server.run_command(f"/execute if block {x} {y} {z} minecraft:{block}"))


def find_floor_y():
    """Y of the platform floor at the origin column.

    The build clears everything above the stone floor up to the world top, so the
    topmost non-air block at (0, *, 0) is the floor. We scan down from above any
    plausible surface; this is independent of where the (fake) player ends up.
    """
    for y in range(160, -64, -1):
        if not block_is(0, y, 0, "air"):
            return y
    return None


def item_count_in_flower_column(floor_y):
    """Count item entities in the column above our planted flower (x/z 6..10).

    Clearing the flower's dirt support pops the dandelion, which drops an item the
    build must discard. We scope the check to that column rather than a wide radius
    so unrelated natural drops elsewhere in the fresh world can't make this flaky;
    a *non*-discarded drop would fall straight down the cleared column to the floor,
    so we span from the floor up past where the flower sat.
    """
    r = server.run_command(
        "/execute if entity "
        f"@e[type=minecraft:item,x=6,dx=4,y={floor_y},dy=160,z=6,dz=4]"
    )
    m = re.search(r"Count:\s*(\d+)", r)
    return int(m.group(1)) if m else 0


def main():
    # fresh=True regenerates natural terrain at the origin, so the build has real
    # blocks (and plant drops) to clear — a pre-cleared repo map would be vacuous.
    log(server.provision(fresh=True))
    log(server.server_start())

    # Keep the footprint chunks resident so our pre-placement and post-checks land,
    # then plant the deterministic scenario *before* anyone joins.
    server.run_command("/forceload add -20 -20 20 20")
    for (x, y, z) in OBSTRUCTIONS:
        server.run_command(f"/setblock {x} {y} {z} minecraft:stone")
    fx, fy, fz = FLOWER_DIRT
    server.run_command(f"/setblock {fx} {fy} {fz} minecraft:dirt")
    px, py, pz = FLOWER_POS
    server.run_command(f"/setblock {px} {py} {pz} minecraft:dandelion")
    time.sleep(1)

    # First join triggers buildHubPlatform(): floor + clear-above + discard-drops.
    server.player_spawn("Alice")
    time.sleep(4)

    # Locate the platform floor from the world itself (the stone floor is laid one
    # block below the sampled surface, flat across the whole footprint).
    floor_y = find_floor_y()
    assert floor_y is not None, "no platform floor found in the origin column"
    surface_y = floor_y + 1  # the cleared, walkable layer players stand on
    log(f"\nHub floor at y={floor_y} (walkable surface y={surface_y})")

    # --- Assertion 1: deliberate obstructions above the platform were cleared ---
    obstructions_cleared = []
    for (x, y, z) in OBSTRUCTIONS:
        air = passed(server.run_command(f"/execute if block {x} {y} {z} minecraft:air"))
        obstructions_cleared.append(air)
        log(f"  obstruction ({x},{y},{z}) cleared to air : {air}")
    all_obstructions_cleared = all(obstructions_cleared)

    # The flower's dirt support and the flower itself must also be gone.
    flower_dirt_cleared = passed(
        server.run_command(f"/execute if block {fx} {fy} {fz} minecraft:air"))
    flower_cleared = passed(
        server.run_command(f"/execute if block {px} {py} {pz} minecraft:air"))
    log(f"  flower support ({fx},{fy},{fz}) cleared    : {flower_dirt_cleared}")
    log(f"  flower ({px},{py},{pz}) cleared            : {flower_cleared}")

    # --- Assertion 2: the platform footprint is stone at the floor, air above.
    # Probe the centre and the four corners of the 41x41 footprint.
    corners = [(0, 0), (20, 20), (-20, 20), (20, -20), (-20, -20)]
    air_above = []
    stone_floor = []
    for (cx, cz) in corners:
        s = block_is(cx, floor_y, cz, "stone")
        a = block_is(cx, surface_y, cz, "air")
        stone_floor.append(s)
        air_above.append(a)
        log(f"  column ({cx},{cz}): stone@{floor_y}={s}  air@{surface_y}={a}")
    all_air_above = all(air_above)
    all_stone_floor = all(stone_floor)

    # --- Assertion 3: the flower's drop was discarded (none left in its column) --
    items = item_count_in_flower_column(floor_y)
    log(f"\nItem entities in planted-flower column: {items}")

    errors = server.read_log(grep="Exception|/ERROR]", lines=20, from_start=True)
    if errors and "no matching" not in errors.lower():
        log(f"\nServer errors during hub build:\n{errors}")

    server.server_stop()

    ok = (all_obstructions_cleared and flower_dirt_cleared and flower_cleared
          and all_air_above and all_stone_floor and items == 0)
    log(f"\nRESULT: {'PASS' if ok else 'FAIL'}")
    if not ok:
        if not all_obstructions_cleared:
            log("  FAIL: a stone obstruction above the platform was not cleared")
        if not (flower_dirt_cleared and flower_cleared):
            log("  FAIL: the planted flower/dirt above the platform was not cleared")
        if not all_air_above:
            log("  FAIL: a footprint column still has a non-air block at floor level")
        if not all_stone_floor:
            log("  FAIL: the stone floor was not laid across the whole footprint")
        if items:
            log(f"  FAIL: {items} item entit(y/ies) left in the flower column — drop not discarded")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
