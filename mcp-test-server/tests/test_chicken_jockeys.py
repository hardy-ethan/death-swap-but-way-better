"""Integration test: item 111 spawns baby zombies riding chickens.

Item #111 ("Spawn 20 chicken jockeys on someone") uses the native entity API
(summonRelSafe + startRiding + setBaby) to spawn up to 20 chicken jockeys
around the target. This test validates three things:

  1. Chickens are spawned near the target (native summonRelSafe path works).
  2. Every chicken has a zombie passenger (startRiding linked the pairs).
  3. Every zombie rider is a baby (setBaby(true) took effect).

Exit code 0 on PASS, 1 on FAIL. Run:
  mcp-test-server/.venv/bin/python mcp-test-server/tests/test_chicken_jockeys.py
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server

# All selectors are evaluated at Bob's position (the item targets Bob).
ALL_CHICKENS     = '@e[type=minecraft:chicken,distance=..30]'
JOCKEY_CHICKENS  = '@e[type=minecraft:chicken,distance=..30,nbt={Passengers:[{id:"minecraft:zombie"}]}]'
BABY_ZOMBIES     = '@e[type=minecraft:zombie,distance=..30,nbt={IsBaby:1b}]'
RIDING_ZOMBIES   = '@e[type=minecraft:zombie,distance=..30,nbt={Vehicle:{id:"minecraft:chicken"}}]'


def log(msg):
    print(msg, flush=True)


def count(selector):
    """Count entities matching `selector`, executed at Bob's position."""
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

    # Item #111 is OPPONENT-targeted; resolve Bob's shuffled permNo to aim it.
    m = re.search(r"permNo (\d+)", server.player_permno("Bob"))
    assert m, "could not read Bob's permNo (is a game running?)"
    bob_no = m.group(1)

    # Give Alice item 111, select it, drop to use, then target Bob.
    server.run_command("/execute as Alice run deathswap give 111")
    time.sleep(1)
    server.player_action("Alice", "hotbar", "7")
    server.player_action("Alice", "drop")
    time.sleep(1)
    server.run_command(f"/execute as Alice run deathswap target {bob_no}")
    time.sleep(4)  # extra beat: 20 entity pairs need a tick to mount

    chickens       = count(ALL_CHICKENS)
    jockey_chicks  = count(JOCKEY_CHICKENS)
    baby_zombies   = count(BABY_ZOMBIES)
    riding_zombies = count(RIDING_ZOMBIES)

    log(f"\nChickens near Bob              : {chickens}")
    log(f"Chickens with zombie passenger : {jockey_chicks}")
    log(f"Baby zombies near Bob          : {baby_zombies}")
    log(f"Zombies riding a chicken       : {riding_zombies}")

    errors = server.read_log(grep="Exception|/ERROR]", lines=20, from_start=True)
    if errors and "no matching" not in errors.lower():
        log(f"\nServer errors during item use:\n{errors}")

    server.server_stop()

    # Pass conditions:
    #   - At least one chicken spawned (native entity path exercised)
    #   - All chickens have a zombie passenger (startRiding worked)
    #   - All zombie riders are babies (setBaby worked)
    spawned     = chickens >= 1
    all_mounted = jockey_chicks == chickens
    all_babies  = baby_zombies == jockey_chicks

    ok = spawned and all_mounted and all_babies
    log(f"\nRESULT: {'PASS' if ok else 'FAIL'}")
    if not ok:
        if not spawned:
            log("  FAIL: no chickens spawned — native entity path not exercised")
        if not all_mounted:
            log(f"  FAIL: {jockey_chicks}/{chickens} chickens have a zombie rider"
                " — startRiding may not have worked")
        if not all_babies:
            log(f"  FAIL: {baby_zombies}/{jockey_chicks} riders are baby zombies"
                " — setBaby may not have taken effect")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
