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


def item_count_near_hub():
    """Count item entities anywhere in the hub column (full 3D radius from feet)."""
    r = server.run_command(
        "/execute as Alice at @s run execute if entity "
        "@e[type=minecraft:item,distance=..256]"
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

    # The player is teleported onto the platform, so their Y is the floor surface.
    pos = server.run_command("/data get entity Alice Pos")
    nums = re.findall(r"-?\d+\.\d+", pos)
    assert len(nums) >= 2, f"could not read Alice's position: {pos!r}"
    feet_y = int(float(nums[1]))
    log(f"\nHub floor surface (Alice feet Y): {feet_y}")

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

    # --- Assertion 2: the platform footprint is air above the floor, stone below.
    # Probe the centre and the four corners of the 41x41 footprint.
    corners = [(0, 0), (20, 20), (-20, 20), (20, -20), (-20, -20)]
    air_above = []
    stone_floor = []
    for (cx, cz) in corners:
        a = passed(server.run_command(
            f"/execute if block {cx} {feet_y} {cz} minecraft:air"))
        s = passed(server.run_command(
            f"/execute if block {cx} {feet_y - 1} {cz} minecraft:stone"))
        air_above.append(a)
        stone_floor.append(s)
        log(f"  column ({cx},{cz}): air@{feet_y}={a}  stone@{feet_y - 1}={s}")
    all_air_above = all(air_above)
    all_stone_floor = all(stone_floor)

    # --- Assertion 3: no item entities lingering anywhere in the hub column ------
    items = item_count_near_hub()
    log(f"\nItem entities near hub: {items}")

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
            log(f"  FAIL: {items} item entit(y/ies) left near the hub — drops not discarded")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
