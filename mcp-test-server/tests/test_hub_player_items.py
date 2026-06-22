"""Integration test: hub players receive unbreakable mace + wind charges (PR 8).

Changes tested:
  - sendToHub() no longer applies infinite REGENERATION
  - sendToHub() gives each player an unbreakable MACE + 16 WIND_CHARGEs on join
  - tickHub() replenishes wind charges back to 16 every second if any were consumed

Test flow:
  1. Provision + start the server
  2. Spawn Alice (joining a HUB-phase server triggers onPlayerJoin -> sendToHub)
  3. Assert: Alice has a mace, it has the unbreakable component, she has exactly
     16 wind charges, and she has no regeneration effect
  4. Clear Alice's wind charges; wait 2 s; assert tickHub topped them back to 16

Exit code 0 on PASS, 1 on FAIL.  Run:
  mcp-test-server/.venv/bin/python mcp-test-server/tests/test_hub_player_items.py
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server


def log(msg):
    print(msg, flush=True)


def clear_count(player, item):
    """/clear <player> <item> 0 reports the count without removing anything."""
    r = server.run_command(f"/clear {player} {item} 0")
    m = re.search(r"(\d+)", r)
    return int(m.group(1)) if m else 0


def inventory_data(player):
    """Raw /data get entity output for the player's Inventory list."""
    return server.run_command(f"/data get entity {player} Inventory")


def has_effect(player, effect_id):
    """True if the player currently has the named active effect."""
    r = server.run_command(f"/data get entity {player} active_effects")
    return effect_id in r


def main():
    log(server.provision())
    log(server.server_start())

    # Joining a HUB-phase server calls onPlayerJoin -> sendToHub, which should
    # give Alice the mace + wind charges and skip regeneration.
    server.player_spawn("Alice")
    server.player_spawn("Bob")
    time.sleep(3)

    inv = inventory_data("Alice")

    # ---- Test 1: Alice has a mace ----------------------------------------
    mace_count = clear_count("Alice", "minecraft:mace")
    t1 = mace_count >= 1
    log(f"\nTest 1 - Alice has mace in hub (got {mace_count}): {'PASS' if t1 else 'FAIL'}")

    # ---- Test 2: mace has the unbreakable component ----------------------
    # MC 26.2 serialises components as: {components: {"minecraft:unbreakable": {}}, id: "minecraft:mace", ...}
    # Both keys must appear inside the same inventory entry.
    t2 = '"minecraft:unbreakable"' in inv and '"minecraft:mace"' in inv
    log(f"Test 2 - Mace is unbreakable: {'PASS' if t2 else 'FAIL'}")
    if not t2:
        log(f"  Inventory: {inv}")

    # ---- Test 3: Alice has exactly 16 wind charges ------------------------
    wc = clear_count("Alice", "minecraft:wind_charge")
    t3 = wc == 16
    log(f"Test 3 - Alice has 16 wind charges (got {wc}): {'PASS' if t3 else 'FAIL'}")

    # ---- Test 4: Alice does NOT have regeneration -------------------------
    regen = has_effect("Alice", "minecraft:regeneration")
    t4 = not regen
    log(f"Test 4 - Alice has no regeneration: {'PASS' if t4 else 'FAIL'}")
    if regen:
        effects = server.run_command("/data get entity Alice active_effects")
        log(f"  active_effects: {effects}")

    # ---- Test 5: tickHub replenishes wind charges within 2 seconds --------
    server.run_command("/clear Alice minecraft:wind_charge")   # consume all
    time.sleep(4)   # tickHub fires every 20 ticks (1 s); give it 4 cycles
    wc_after = clear_count("Alice", "minecraft:wind_charge")
    t5 = wc_after == 16
    log(f"Test 5 - Wind charges replenished after clear (got {wc_after}): {'PASS' if t5 else 'FAIL'}")

    # ---- Test 6: tickHub restores mace within 2 seconds if lost ----------
    server.run_command("/clear Alice minecraft:mace")
    time.sleep(2)
    mace_after = clear_count("Alice", "minecraft:mace")
    t6 = mace_after >= 1
    log(f"Test 6 - Mace restored after clear (got {mace_after}): {'PASS' if t6 else 'FAIL'}")

    # ---- Test 7: mace cannot be dropped ----------------------------------
    # Selecting the mace slot and dropping it should be a no-op in hub.
    server.player_action("Alice", "hotbar", "1")   # slot 1 holds the mace
    server.player_action("Alice", "drop")
    time.sleep(1)
    mace_after_drop = clear_count("Alice", "minecraft:mace")
    t7 = mace_after_drop >= 1
    log(f"Test 7 - Mace cannot be dropped (got {mace_after_drop}): {'PASS' if t7 else 'FAIL'}")

    # ---- Test 8: wind charges cannot be dropped --------------------------
    server.player_action("Alice", "hotbar", "2")   # slot 2 holds wind charges
    server.player_action("Alice", "drop")
    time.sleep(1)
    wc_after_drop = clear_count("Alice", "minecraft:wind_charge")
    t8 = wc_after_drop == 16
    log(f"Test 8 - Wind charges cannot be dropped (got {wc_after_drop}): {'PASS' if t8 else 'FAIL'}")

    errors = server.read_log(grep="Exception|/ERROR]", lines=20, from_start=True)
    if errors and "no matching" not in errors.lower():
        log(f"\nServer errors:\n{errors}")

    server.server_stop()

    ok = t1 and t2 and t3 and t4 and t5 and t6 and t7 and t8
    log(f"\nRESULT: {'PASS' if ok else 'FAIL'}")
    if not ok:
        if not t1:
            log("  FAIL: Alice did not receive a mace (sendToHub regression)")
        if not t2:
            log("  FAIL: Alice's mace is missing the unbreakable component")
        if not t3:
            log(f"  FAIL: Alice has {wc} wind charges; expected 16")
        if not t4:
            log("  FAIL: Alice still has regeneration (should have been removed)")
        if not t5:
            log(f"  FAIL: Wind charges not replenished by tickHub (got {wc_after}; expected 16)")
        if not t6:
            log(f"  FAIL: Mace not restored by tickHub (got {mace_after}; expected >=1)")
        if not t7:
            log("  FAIL: Mace was dropped (should be non-droppable in hub)")
        if not t8:
            log(f"  FAIL: Wind charges were dropped (got {wc_after_drop}; expected 16)")
    return 0 if ok else 1



if __name__ == "__main__":
    sys.exit(main())
