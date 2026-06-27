"""Test the optional player argument added to /deathswap give.

Covers two forms:
  /deathswap give <id>          -- gives item to the executing player (Alice)
  /deathswap give <id> <player> -- gives item to Bob directly

Exit code 0 on PASS, 1 on FAIL. Run:
  mcp-test-server/.venv/bin/python mcp-test-server/tests/test_give_player_arg.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server


def log(msg):
    print(msg, flush=True)


def hotbar_is_occupied(player, slot):
    """Return True if the player has an item in the given hotbar slot (1-9)."""
    r = server.run_command(
        f"/execute as {player} run data get entity @s Inventory"
    )
    return f'"Slot": {slot - 1}b' in r or f"Slot:{slot - 1}b" in r or "choosingItem" in server.read_log(grep=player, lines=5)


def main():
    log(server.provision())
    log(server.server_start())
    server.player_spawn("Alice")
    server.player_spawn("Bob")
    time.sleep(2)
    server.run_command("/op Alice")
    server.run_command("/deathswap set lives 1")
    server.run_command("/deathswap set interval 30")
    server.run_command("/deathswap start")
    time.sleep(3)

    errors_before = server.read_log(grep="Exception|/ERROR]", lines=200, from_start=True)

    # --- Test 1: /deathswap give <id>  (no player arg — gives to Alice) ---
    log("\n[1] /execute as Alice run deathswap give 1")
    r1 = server.run_command("/execute as Alice run deathswap give 1")
    log(f"    RCON response: {r1!r}")
    time.sleep(1)
    log1 = server.read_log(grep="choosingItem|item|gave|Exception|ERROR", lines=15)
    log(f"    log: {log1.strip()}")

    # --- Test 2: /deathswap give <id> <player>  (give to Bob) ---
    log("\n[2] /deathswap give 2 Bob")
    r2 = server.run_command("/deathswap give 2 Bob")
    log(f"    RCON response: {r2!r}")
    time.sleep(1)
    log2 = server.read_log(grep="choosingItem|item|gave|Exception|ERROR", lines=15)
    log(f"    log: {log2.strip()}")

    errors_after = server.read_log(grep="Exception|/ERROR]", lines=200, from_start=True)

    server.server_stop()

    new_errors = set(errors_after.splitlines()) - set(errors_before.splitlines())
    if new_errors:
        log(f"\nErrors found in log:\n" + "\n".join(new_errors))

    pass1 = "Only a player" not in r1 and "No item" not in r1
    pass2 = "Only a player" not in r2 and "No item" not in r2 and "Player not found" not in r2
    no_exceptions = not new_errors

    log(f"\n/deathswap give <id>          : {'PASS' if pass1 else 'FAIL'}")
    log(f"/deathswap give <id> <player> : {'PASS' if pass2 else 'FAIL'}")
    log(f"No new exceptions             : {'PASS' if no_exceptions else 'FAIL'}")

    ok = pass1 and pass2 and no_exceptions
    log(f"\nRESULT: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
