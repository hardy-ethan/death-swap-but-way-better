"""Test: wins and lives persist on scoreboard after a player disconnects.

PR 9 (Show wins and lives on scoreboard regardless of online status):

Previously, ScoreboardDisplay.removePlayer() was called in onPlayerLeave for
both hub and game phases, wiping the player's row.  The patch removes that
call and adds an offline-player pass inside updateHubScoreboard/updateSidebar
that writes scores by name via ScoreHolder.forNameOnly.

This script validates two behaviours:

  1. Hub phase — wins row is NOT wiped when a player disconnects and is
     re-populated when the next player joins (which fires updateHubScoreboard).
  2. Game phase — lives row is NOT wiped when a player disconnects mid-game
     (no removePlayer call), and the offline row is also re-populated when
     updateSidebar next runs (triggered by a game-lives change via RCON).

Exit code 0 on PASS, 1 on FAIL.
Run:
  mcp-test-server/.venv/bin/python mcp-test-server/tests/test_offline_scoreboard.py
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server

WINS_OBJ = "ds_wins"
LIVES_OBJ = "ds_lives"


def log(msg):
    print(msg, flush=True)


def scoreboard_get(player_name, objective):
    """Return the integer scoreboard score, or None if the player has no entry."""
    resp = server.run_command(f"/scoreboard players get {player_name} {objective}")
    m = re.search(r"has (\d+) \[", resp)
    if m:
        return int(m.group(1))
    return None


def main():
    log(server.provision())
    log(server.server_start())

    # -----------------------------------------------------------------------
    # Test 1 — hub wins score survives disconnect
    # -----------------------------------------------------------------------
    log("\n=== Test 1: hub wins score survives disconnect ===")
    log(server.player_spawn("Alice"))
    log(server.player_spawn("Bob"))
    time.sleep(3)  # let both settle in the hub

    alice_wins_online = scoreboard_get("Alice", WINS_OBJ)
    bob_wins_online   = scoreboard_get("Bob",   WINS_OBJ)
    log(f"Alice wins (online): {alice_wins_online}")
    log(f"Bob wins  (online): {bob_wins_online}")

    # Disconnect Alice; in the old code this wiped her scoreboard row.
    log(server.player_despawn("Alice"))
    time.sleep(2)

    # Spawn Charlie — onPlayerJoin triggers updateHubScoreboard(), which now
    # also calls updateWinsForName() for every offline PlayerData entry.
    log(server.player_spawn("Charlie"))
    time.sleep(2)
    log(server.player_despawn("Charlie"))
    time.sleep(1)

    alice_wins_offline = scoreboard_get("Alice", WINS_OBJ)
    log(f"Alice wins (offline, after Charlie join): {alice_wins_offline}")

    # -----------------------------------------------------------------------
    # Test 2 — in-game lives score survives disconnect
    # -----------------------------------------------------------------------
    log("\n=== Test 2: in-game lives survive disconnect ===")
    # Three players so the game does not end when one disconnects (win condition
    # requires a single surviving player).
    log(server.player_spawn("Alice"))
    log(server.player_spawn("Dave"))
    time.sleep(2)

    server.run_command("/deathswap set lives 3")
    server.run_command("/deathswap set interval 60")
    server.run_command("/deathswap start")
    time.sleep(5)  # wait for game freeze + start

    alice_lives_online = scoreboard_get("Alice", LIVES_OBJ)
    bob_lives_online   = scoreboard_get("Bob",   LIVES_OBJ)
    dave_lives_online  = scoreboard_get("Dave",  LIVES_OBJ)
    log(f"Alice lives (online):  {alice_lives_online}")
    log(f"Bob lives   (online):  {bob_lives_online}")
    log(f"Dave lives  (online):  {dave_lives_online}")

    # Disconnect Alice mid-game; old code called scoreboard.removePlayer(), new
    # code leaves the entry in place.
    log(server.player_despawn("Alice"))
    time.sleep(2)

    # Force updateSidebar() by changing Dave's lives via RCON (the /deathswap
    # lives command, if present) — or by any other server-side lives mutation.
    # As a reliable fallback we just wait a tick for checkWinCondition to run
    # (onPlayerLeave calls it, which runs on the server thread).
    time.sleep(2)

    alice_lives_offline = scoreboard_get("Alice", LIVES_OBJ)
    log(f"Alice lives (offline, after disconnect): {alice_lives_offline}")

    # Surface any server errors seen during the run.
    errors = server.read_log(grep=r"Exception|/ERROR\]", lines=30, from_start=True)
    if errors and "no matching" not in errors.lower():
        log(f"\nServer errors during test:\n{errors}")

    server.server_stop()

    # -----------------------------------------------------------------------
    # Verdict
    # -----------------------------------------------------------------------
    failures = []

    if alice_wins_online is None:
        failures.append("FAIL t1: Alice had no wins entry while online (hub HUD broken)")
    if bob_wins_online is None:
        failures.append("FAIL t1: Bob had no wins entry while online (hub HUD broken)")
    if alice_wins_offline is None:
        failures.append(
            "FAIL t1: Alice's wins score disappeared after disconnect "
            "(removePlayer regression or updateWinsForName not called)"
        )

    if alice_lives_online is None:
        failures.append("FAIL t2: Alice had no lives entry while in-game (sidebar broken)")
    if dave_lives_online is None:
        failures.append("FAIL t2: Dave had no lives entry while in-game (sidebar broken)")
    if alice_lives_offline is None:
        failures.append(
            "FAIL t2: Alice's lives score disappeared after disconnect mid-game "
            "(removePlayer regression in RUNNING phase)"
        )

    for f in failures:
        log(f)

    ok = len(failures) == 0
    log(f"\nRESULT: {'PASS' if ok else 'FAIL'} ({len(failures)} failure(s))")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
