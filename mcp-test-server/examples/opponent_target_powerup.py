"""Demo: use an OPPONENT-targeted powerup end-to-end via the MCP.

Starts a server, spawns two Carpet bots, starts a game, then has Alice arm and
use item #1 ("Give a player speed 1 billion"). Unlike a SELF item, dropping an
OPPONENT item opens a target prompt (clickable chips that run
`/deathswap target <permNo>`); permNos are shuffled at game start, so this uses
the `player_permno` tool to look up Bob's number and target him directly.

Verifies the effect lands on the TARGET (Bob's movement speed -> 5.5) and not on
the caster (Alice stays at the vanilla 0.1).

Run:  mcp-test-server/.venv/bin/python mcp-test-server/examples/opponent_target_powerup.py
"""
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server


def show(title, result):
    print(f"\n### {title}\n{result}", flush=True)


def speed(name):
    """Base movement_speed of a player (vanilla 0.1; item #1 sets it to 5.5)."""
    r = server.run_command(f"/attribute {name} minecraft:movement_speed base get")
    m = re.findall(r"[-+]?\d*\.?\d+", r)
    return (float(m[-1]) if m else None), r


def permno(name):
    m = re.search(r"permNo (\d+)", server.player_permno(name))
    return int(m.group(1)) if m else None


show("provision", server.provision())
show("server_start", server.server_start())
show("spawn Alice", server.player_spawn("Alice"))
show("spawn Bob", server.player_spawn("Bob"))
time.sleep(3)
show("set lives", server.run_command("/deathswap set lives 3"))
show("set interval", server.run_command("/deathswap set interval 30"))
show("deathswap start", server.run_command("/deathswap start"))
time.sleep(4)

# Look up the opponent's shuffled permNo via the backend command.
show("permno Alice", server.player_permno("Alice"))
show("permno Bob", server.player_permno("Bob"))
bob_no = permno("Bob")

_, ar = speed("Alice")
_, br = speed("Bob")
show("baseline Alice speed", ar)
show("baseline Bob speed", br)

# Alice arms OPPONENT item #1, drops to open the target prompt, then targets Bob.
server.run_command("/execute as Alice run deathswap give 1")
time.sleep(1)
server.player_action("Alice", "hotbar", "7")
server.player_action("Alice", "drop")
time.sleep(1)
show(f"Alice -> deathswap target {bob_no} (Bob)",
     server.run_command(f"/execute as Alice run deathswap target {bob_no}"))
time.sleep(1)

alice_final, afr = speed("Alice")
bob_final, bfr = speed("Bob")
show("FINAL Alice speed (expect ~0.1)", afr)
show("FINAL Bob speed (expect ~5.5)", bfr)
show("log: errors", server.read_log(grep="Exception|/ERROR]", lines=20, from_start=True))
show("server_stop", server.server_stop())

ok = bob_no is not None and bob_final and bob_final > 1.0 and alice_final and alice_final < 1.0
print(f"\nRESULT: {'PASS' if ok else 'FAIL'} (Bob permNo={bob_no}, Alice={alice_final}, Bob={bob_final})", flush=True)
print("DONE", flush=True)
