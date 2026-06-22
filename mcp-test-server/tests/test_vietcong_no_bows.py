"""Regression test: the Viet Cong ambush (item #70) spawns bow-free mobs.

Item #70 ("Launch a Viet Cong ambush") rings the target with ~48 PARCHED mobs
(the desert skeleton). PARCHED spawns holding a bow by default, so
ItemRegistry clears the mainhand before the entity enters the world
(ItemRegistry.java, `setItemSlot(MAINHAND, EMPTY)`) to stop a client-side
desync that showed a bow the server didn't have.

This test fires item #70 at a bot and asserts that none of the spawned Viet
Cong hold a bow -- and, more strictly, that none hold any mainhand item at all.
MC 26.2 stores mob gear under the `equipment` NBT compound, so a clear mainhand
means the `equipment.mainhand` key is simply absent.

Exit code 0 on PASS, 1 on FAIL. Run:
  mcp-test-server/.venv/bin/python mcp-test-server/tests/test_vietcong_no_bows.py
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server

# Selectors evaluated at Bob's position (the ambush is rung around the target).
ALL_VIETCONG = '@e[type=minecraft:parched,distance=..60]'
WITH_BOW = '@e[type=minecraft:parched,distance=..60,nbt={equipment:{mainhand:{id:"minecraft:bow"}}}]'
WITH_ANY_MAINHAND = '@e[type=minecraft:parched,distance=..60,nbt={equipment:{mainhand:{}}}]'


def log(msg):
    print(msg, flush=True)


def count(selector):
    """How many entities match `selector`, via `execute if entity` at Bob."""
    r = server.run_command(f"/execute as Bob at @s run execute if entity {selector}")
    m = re.search(r"Count:\s*(\d+)", r)
    return int(m.group(1)) if m else 0


def main():
    log(server.provision())
    log(server.server_start())
    server.player_spawn("Alice")
    server.player_spawn("Bob")
    time.sleep(3)
    server.run_command("/deathswap set lives 3")
    server.run_command("/deathswap set interval 30")
    server.run_command("/deathswap start")
    time.sleep(4)

    # Item #70 is OPPONENT-targeted; look up Bob's shuffled permNo to aim it.
    m = re.search(r"permNo (\d+)", server.player_permno("Bob"))
    assert m, "could not read Bob's permNo (is a game running?)"
    bob_no = m.group(1)

    # Alice arms the Viet Cong ambush and uses it on Bob.
    server.run_command("/execute as Alice run deathswap give 70")
    time.sleep(1)
    server.player_action("Alice", "hotbar", "7")
    server.player_action("Alice", "drop")
    # The drop is processed on the server thread and arms the target prompt
    # asynchronously; give it a beat before sending the target selection.
    time.sleep(1)
    server.run_command(f"/execute as Alice run deathswap target {bob_no}")
    time.sleep(3)

    total = count(ALL_VIETCONG)
    with_bow = count(WITH_BOW)
    with_any_mainhand = count(WITH_ANY_MAINHAND)

    log(f"\nViet Cong spawned near Bob   : {total}")
    log(f"...holding a bow             : {with_bow}")
    log(f"...holding any mainhand item : {with_any_mainhand}")

    errors = server.read_log(grep="Exception|/ERROR]", lines=20, from_start=True)
    if errors and "no matching" not in errors.lower():
        log(f"\nserver errors during ambush:\n{errors}")

    server.server_stop()

    ok = total > 0 and with_bow == 0 and with_any_mainhand == 0
    log(f"\nRESULT: {'PASS' if ok else 'FAIL'}")
    if not ok:
        if total == 0:
            log("  FAIL: no Viet Cong spawned -- the test never exercised the spawn path")
        if with_bow:
            log(f"  FAIL: {with_bow} Viet Cong are holding a bow (regression)")
        if with_any_mainhand:
            log(f"  FAIL: {with_any_mainhand} Viet Cong have a mainhand item")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
