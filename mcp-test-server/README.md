# Death Swap — MCP Test Server

An [MCP](https://modelcontextprotocol.io) server that lets Claude (or any MCP
client) **run the mod live and validate patches**, instead of only
compile-checking. It wraps Loom's `./gradlew runServer` (which loads the mod +
Fabric API into a real Minecraft 26.2 dedicated server), talks to it over RCON,
tails the log, and drives **Fabric Carpet fake players** (`/player`) so the
multi-player swap/item/elimination logic can actually be exercised.

> **Why Carpet, not mineflayer?** mineflayer's `node-minecraft-protocol` maxes
> out at MC 1.21.x and cannot speak the 26.2 protocol, so client bots can't
> connect. Carpet spawns *server-side* fake players, which need no client
> protocol at all — they're driven entirely over RCON.

## Prerequisites

- **Java 25** — `runServer` runs Minecraft 26.2. The server auto-discovers a
  JDK 25 (`$DEATHSWAP_JDK25`, `$JAVA_HOME`, `mcp-test-server/.jdk/`, or
  `/usr/lib/jvm`); install one if none is present.
- **Python 3.10+** and the `mcp` package (installed into a venv below).
- **Network** on first run — Loom downloads Minecraft/Fabric and `provision`
  downloads Carpet from Modrinth.

The first `server_start` is slow: Loom downloads Minecraft/Fabric.

## Install

```bash
cd mcp-test-server
python3 -m venv .venv                         # isolated env (avoids system pkg conflicts)
./.venv/bin/pip install -r requirements.txt   # mcp SDK
./.venv/bin/python server.py --selfcheck      # sanity check paths + MCP wiring
```

`.mcp.json` (repo root) points at `mcp-test-server/.venv/bin/python`, so the
venv is used automatically and the server loads in a Claude Code session.
`.venv/`, `.jdk/`, and `__pycache__/` are gitignored.

## Tools

| Tool | Purpose |
|------|---------|
| `provision(fresh=False)` | Build `fabric-mod/run/`: copy the repo map into `run/world/` (or `fresh` empty), write `server.properties` (offline-mode, RCON) + `eula.txt`, download Carpet into `run/mods/`. |
| `server_start()` | Launch `./gradlew runServer` (on a JDK 25), wait until ready, connect RCON, report startup warnings/errors. |
| `server_stop()` | Stop the server cleanly (fake players disconnect with it). |
| `server_status()` | Server / RCON health, who is online, Carpet jar state. |
| `run_command(command)` | Run any console command over RCON (e.g. `/deathswap start`). |
| `read_log(grep=None, lines=100, from_start=False)` | Incremental tail of `run/logs/latest.log`; `grep` is a regex filter. |
| `player_spawn(name)` | Spawn a Carpet server-side fake player as `name`. |
| `player_action(name, action, args="")` | Drive a fake player via `/player <name> <action> <args>`: `use`, `attack`, `jump`, `sneak`, `sprint`, `drop`, `dropStack`, `swapHands`, `hotbar <1-9>`, `move <forward\|backward\|left\|right>`, `turn ...`, `look ...`. **`drop`** (after `hotbar <slot>`) triggers the drop-to-select mechanic (`PlayerDropMixin`). |
| `player_despawn(name)` | Remove a fake player (`/player <name> kill`). |
| `players_online()` | `/list` over RCON. |
| `player_permno(name)` | A player's permanent number (`permPNo`) for opponent targeting — wraps the `/deathswap permno` command. permNos are shuffled at game start and in-memory only, so this is the reliable way to find which number `/deathswap target <permNo>` should use. |

Everything runs under `fabric-mod/run/` (gitignored), so nothing here mutates
committed map data.

## Typical validation loop

```text
provision()
server_start()                              # ready, no stack traces
player_spawn("Alice"); player_spawn("Bob")
players_online()                            # both connected
run_command("/op Alice")
run_command("/deathswap set lives 1")
run_command("/deathswap set interval 30")   # min interval is 30
run_command("/deathswap start")
read_log(grep="swap|spread|Death Swap")     # game started, players spread
player_action("Alice", "hotbar", "8")       # select an offered item slot...
player_action("Alice", "drop")              # ...and drop to use it
read_log(grep="Exception|ERROR")            # <-- the patch-validation signal
server_stop()
```

## Examples

Runnable end-to-end demos live in `examples/` (each starts a server, spawns two
bots, plays a powerup, verifies the result, and stops):

```bash
.venv/bin/python examples/self_use_powerup.py        # SELF item #2: caster gets wither materials
.venv/bin/python examples/opponent_target_powerup.py # OPPONENT item #1: target gets speed (uses player_permno)
```

## Notes / limits

- Fake players are server-side (Carpet); `online-mode=false` is set anyway so a
  real client could also join for manual inspection.
- `/player` requires Carpet to be loaded. If Carpet can't be downloaded,
  `provision` says so and bot tools fail with a clear message — the server +
  RCON + log validation path still works.
- State (server process, RCON connection, log cursor) lives in the running MCP
  process for the session.
