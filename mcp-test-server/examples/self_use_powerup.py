"""Demo: use a SELF-targeted powerup end-to-end via the MCP.

Starts a server, spawns two Carpet bots, starts a game, then has Alice arm and
use item #2 ("Give yourself materials to build a wither") and verifies the
soul sand + wither skulls land in her own inventory.

Run:  mcp-test-server/.venv/bin/python mcp-test-server/examples/self_use_powerup.py
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import server


def show(title, result):
    print(f"\n### {title}\n{result}", flush=True)


show("provision", server.provision())
show("server_start", server.server_start())
show("spawn Alice", server.player_spawn("Alice"))
show("spawn Bob", server.player_spawn("Bob"))
time.sleep(3)
show("players_online", server.players_online())
show("set lives", server.run_command("/deathswap set lives 3"))
show("set interval", server.run_command("/deathswap set interval 30"))
show("deathswap start", server.run_command("/deathswap start"))
time.sleep(3)

# Use SELF powerup #2 ("Give yourself materials to build a wither"): arming places
# the dyed item in hotbar slot 7 (slot index 6); dropping it fires the effect.
show("baseline skull count", server.run_command("/clear Alice minecraft:wither_skeleton_skull 0"))
show("arm item 2 on Alice", server.run_command("/execute as Alice run deathswap give 2"))
time.sleep(1)
show("Alice select slot (hotbar 7)", server.player_action("Alice", "hotbar", "7"))
show("Alice drop -> USE powerup", server.player_action("Alice", "drop"))
time.sleep(2)

# The SELF item gives Alice 3 wither skulls + 4 soul sand.
show("VERIFY wither skulls (expect 3)", server.run_command("/clear Alice minecraft:wither_skeleton_skull 0"))
show("VERIFY soul sand (expect 4)", server.run_command("/clear Alice minecraft:soul_sand 0"))
show("log: errors", server.read_log(grep="Exception|/ERROR]", lines=20, from_start=True))
show("server_stop", server.server_stop())
print("\nDONE", flush=True)
