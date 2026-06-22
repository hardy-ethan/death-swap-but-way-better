#!/usr/bin/env python3
"""MCP server for validating Death Swap mod patches.

Wraps `./gradlew runServer` (Loom's dev server, which loads the mod + Fabric API
on the classpath) and exposes tools to:
  * provision a test server (world, server.properties, eula, Carpet)
  * start / stop / inspect the server
  * run console commands over RCON
  * spawn / drive / despawn server-side Carpet fake players
  * tail the server log

Player bots use Fabric Carpet's `/player` command (server-side fake players,
driven over RCON). mineflayer was not viable: node-minecraft-protocol does not
support MC 26.2's protocol.

Run standalone for a smoke check:  python3 server.py --selfcheck
Otherwise it speaks MCP over stdio (see ../.mcp.json).
"""

from __future__ import annotations

import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rcon import RconClient, RconError  # noqa: E402

# --------------------------------------------------------------------------
# Paths & config
# --------------------------------------------------------------------------
HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent
FABRIC = REPO_ROOT / "fabric-mod"
RUN = FABRIC / "run"
WORLD = RUN / "world"
MODS = RUN / "mods"
LOG_FILE = RUN / "logs" / "latest.log"
GRADLE_LOG = RUN / "mcp-gradle.log"

RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASSWORD = "deathswap"
SERVER_PORT = 25565
MC_VERSION = "26.2"

# Fabric Carpet provides the server-side `/player` fake-player command.
CARPET_SLUG = "carpet"
MODRINTH_API = "https://api.modrinth.com/v2"

# World files (repo root) that make up the playable map / hub lobby.
WORLD_PARTS = [
    "data", "dimensions", "generated", "players",
    "level.dat", "level.dat_old", "session.lock",
]

START_TIMEOUT = 900  # first run downloads MC/Fabric; be generous
READY_MARKERS = ('Done (', 'For help, type "help"')
FAIL_MARKERS = ("BUILD FAILED", "FAILURE: Build failed", "A problem occurred")


# --------------------------------------------------------------------------
# Runtime state (the MCP process is long-lived for the session)
# --------------------------------------------------------------------------
class State:
    server_proc: subprocess.Popen | None = None
    rcon: RconClient | None = None
    log_cursor: int = 0


S = State()


# --------------------------------------------------------------------------
# Helpers
# --------------------------------------------------------------------------
def _write_server_properties() -> None:
    props = {
        "online-mode": "false",
        "enable-rcon": "true",
        "rcon.password": RCON_PASSWORD,
        "rcon.port": str(RCON_PORT),
        "server-port": str(SERVER_PORT),
        "level-name": "world",
        "max-players": "20",
        "spawn-protection": "0",
        "sync-chunk-writes": "false",
        "view-distance": "8",
        "motd": "Death Swap MCP test server",
    }
    text = "\n".join(f"{k}={v}" for k, v in props.items()) + "\n"
    (RUN / "server.properties").write_text(text)


def _copy_world() -> str:
    notes = []
    WORLD.mkdir(parents=True, exist_ok=True)
    for part in WORLD_PARTS:
        src = REPO_ROOT / part
        if not src.exists():
            notes.append(f"skip {part} (not in repo)")
            continue
        dst = WORLD / part
        if src.is_dir():
            shutil.copytree(src, dst, dirs_exist_ok=True)
        else:
            shutil.copy2(src, dst)
    return "; ".join(notes) if notes else "all world parts copied"


def _ensure_carpet() -> str:
    """Ensure a Fabric Carpet jar (matching MC_VERSION) is in run/mods/.

    Carpet supplies the `/player` fake-player command used for bots. Downloaded
    from Modrinth on first call. Returns a human-readable note; never raises on
    network failure (the rest of the server still works without bots).
    """
    MODS.mkdir(parents=True, exist_ok=True)
    existing = list(MODS.glob("fabric-carpet*.jar"))
    if existing:
        return f"present ({existing[0].name})"
    try:
        q = urllib.parse.quote(f'["{MC_VERSION}"]')
        url = f"{MODRINTH_API}/project/{CARPET_SLUG}/version?game_versions={q}&loaders=%5B%22fabric%22%5D"
        with urllib.request.urlopen(url, timeout=30) as r:
            versions = json.load(r)
        if not versions:
            return f"NOT FOUND on Modrinth for MC {MC_VERSION} — bots unavailable"
        file = versions[0]["files"][0]
        dest = MODS / urllib.parse.unquote(Path(file["url"]).name)
        with urllib.request.urlopen(file["url"], timeout=120) as r, open(dest, "wb") as f:
            shutil.copyfileobj(r, f)
        return f"downloaded {dest.name}"
    except Exception as e:  # noqa: BLE001 - network/parse; degrade gracefully
        return f"download FAILED ({e}) — bots unavailable"


def _gradlew_cmd() -> list[str]:
    gradlew = FABRIC / "gradlew"
    if gradlew.exists():
        os.chmod(gradlew, 0o755)
        return ["./gradlew"]
    return ["gradle"]  # fall back to gradle on PATH


def _find_jdk25() -> str | None:
    """Locate a JDK 25 home. The mod compiles with `release = 25` and runs MC
    26.2, so Gradle/Loom must run on a JDK >= 25.

    Search order: $DEATHSWAP_JDK25 → $JAVA_HOME (if 25) → bundled .jdk/ →
    common /usr/lib/jvm locations.
    """
    candidates: list[Path] = []
    for env in ("DEATHSWAP_JDK25", "JAVA_HOME"):
        if os.environ.get(env):
            candidates.append(Path(os.environ[env]))
    candidates.extend(sorted((HERE / ".jdk").glob("jdk-25*")))
    candidates.extend(sorted(Path("/usr/lib/jvm").glob("*25*")) if Path("/usr/lib/jvm").exists() else [])
    for home in candidates:
        java = home / "bin" / "java"
        if not java.exists():
            continue
        try:
            out = subprocess.run([str(java), "-version"], capture_output=True, text=True)
            ver = (out.stderr or out.stdout)
            if re.search(r'version "(?:25|2[5-9]|[3-9]\d)', ver):
                _ensure_jdk_truststore(home)
                return str(home)
        except Exception:
            continue
    return None


# System Java truststore (Debian) — trusts this environment's TLS proxy CA, which
# a freshly-downloaded JDK does not. We copy it into a JDK we manage under .jdk/
# so Loom's HTTPS downloads (Minecraft, assets) succeed behind the proxy.
SYSTEM_CACERTS = Path("/etc/ssl/certs/java/cacerts")


def _ensure_jdk_truststore(jdk_home: Path) -> None:
    if not SYSTEM_CACERTS.exists():
        return
    # only touch JDKs we manage, never a user-provided JAVA_HOME
    try:
        jdk_home.relative_to(HERE / ".jdk")
    except ValueError:
        return
    dest = jdk_home / "lib" / "security" / "cacerts"
    marker = jdk_home / ".cacerts-synced"
    if dest.exists() and not marker.exists():
        shutil.copy2(SYSTEM_CACERTS, dest)
        marker.write_text("synced from " + str(SYSTEM_CACERTS) + "\n")


def _server_running() -> bool:
    return S.server_proc is not None and S.server_proc.poll() is None


def _ensure_rcon() -> RconClient:
    if S.rcon is None:
        c = RconClient(RCON_HOST, RCON_PORT, RCON_PASSWORD)
        c.connect()
        S.rcon = c
    return S.rcon


def _drop_rcon() -> None:
    if S.rcon is not None:
        try:
            S.rcon.close()
        finally:
            S.rcon = None


def _read_new_log(reset: bool = False) -> str:
    if reset:
        S.log_cursor = 0
    if not LOG_FILE.exists():
        return ""
    with open(LOG_FILE, "r", encoding="utf-8", errors="replace") as f:
        f.seek(S.log_cursor)
        data = f.read()
        S.log_cursor = f.tell()
    return data


def _tail_file(path: Path, n: int) -> str:
    if not path.exists():
        return "(no file)"
    return "\n".join(path.read_text(encoding="utf-8", errors="replace").splitlines()[-n:])


# --------------------------------------------------------------------------
# Tool implementations (plain functions; registered with FastMCP below)
# --------------------------------------------------------------------------
def provision(fresh: bool = False) -> str:
    """Prepare the test server: world, server.properties, eula, Carpet.

    fresh=True starts from an empty world instead of copying the repo map.
    Idempotent; safe to call repeatedly.
    """
    RUN.mkdir(parents=True, exist_ok=True)
    (RUN / "eula.txt").write_text("eula=true\n")
    _write_server_properties()

    if fresh:
        if WORLD.exists():
            shutil.rmtree(WORLD)
        world_note = "fresh empty world (repo map not copied)"
    elif WORLD.exists():
        world_note = "world already present (left as-is)"
    else:
        world_note = _copy_world()

    carpet_note = _ensure_carpet()

    return (
        "Provisioned test server.\n"
        f"  run dir:          {RUN}\n"
        f"  server.properties: online-mode=false, rcon :{RCON_PORT}, port :{SERVER_PORT}\n"
        f"  world:            {world_note}\n"
        f"  carpet (bots):    {carpet_note}\n"
    )


def server_start() -> str:
    """Start the dev server (`./gradlew runServer`) and wait until it is ready.

    First run is slow: Loom downloads Minecraft/Fabric. Returns readiness plus
    any startup warnings/errors found in the log.
    """
    if _server_running():
        return "Server already running (pid %d)." % S.server_proc.pid

    if not (RUN / "server.properties").exists():
        provision()

    jdk25 = _find_jdk25()
    if jdk25 is None:
        return (
            "Cannot start: no JDK 25 found (the mod compiles with release 25 and "
            "runs Minecraft 26.2). Install a JDK 25 and set $DEATHSWAP_JDK25 (or "
            "$JAVA_HOME) to it, or drop it under mcp-test-server/.jdk/. See README."
        )
    env = dict(os.environ)
    env["JAVA_HOME"] = jdk25
    env["PATH"] = f"{jdk25}/bin:" + env.get("PATH", "")

    # Remove the previous run's latest.log so the readiness scan can't match a
    # stale "Done (" from an earlier boot (the server recreates it on startup).
    try:
        LOG_FILE.unlink()
    except FileNotFoundError:
        pass
    GRADLE_LOG.parent.mkdir(parents=True, exist_ok=True)
    gradle_out = open(GRADLE_LOG, "w")
    S.log_cursor = 0

    # --no-daemon: a reused daemon caches its JVM truststore from first launch,
    # so a daemon started before the cacerts sync would keep failing TLS. A fresh
    # JVM per boot picks up the synced truststore (and isolates runs).
    S.server_proc = subprocess.Popen(
        _gradlew_cmd() + ["runServer", "--console=plain", "--stacktrace", "--no-daemon"],
        cwd=FABRIC,
        env=env,
        stdout=gradle_out,
        stderr=subprocess.STDOUT,
        stdin=subprocess.DEVNULL,
    )

    deadline = time.time() + START_TIMEOUT
    while time.time() < deadline:
        if S.server_proc.poll() is not None:
            tail = _tail_file(GRADLE_LOG, 40)
            return f"Server process exited during startup (code {S.server_proc.returncode}).\n--- gradle log tail ---\n{tail}"
        # build failure?
        glog = _tail_file(GRADLE_LOG, 200)
        if any(m in glog for m in FAIL_MARKERS):
            return "Gradle build failed before the server started.\n--- gradle log tail ---\n" + _tail_file(GRADLE_LOG, 60)
        # ready?
        if LOG_FILE.exists():
            log = LOG_FILE.read_text(encoding="utf-8", errors="replace")
            if any(m in log for m in READY_MARKERS):
                rcon_note = "RCON not available."
                for _ in range(10):  # RCON listener starts just after "Done ("
                    try:
                        _ensure_rcon()
                        rcon_note = "RCON connected."
                        break
                    except (RconError, OSError):
                        time.sleep(1)
                warnings = "\n".join(
                    l for l in log.splitlines()
                    if ("/ERROR]" in l or "/WARN]" in l or "Exception" in l)
                )[-1500:]
                S.log_cursor = len(log.encode("utf-8"))
                return (
                    "Server ready.\n"
                    f"  {rcon_note}\n"
                    + ("  startup warnings/errors:\n" + warnings if warnings.strip()
                       else "  no startup warnings/errors.\n")
                )
        time.sleep(2)

    return f"Timed out after {START_TIMEOUT}s waiting for the server to become ready."


def server_stop() -> str:
    """Stop the server cleanly (fake players disconnect with it)."""
    if not _server_running():
        _drop_rcon()
        return "Server was not running."

    stopped = False
    try:
        _ensure_rcon().command("stop")
        stopped = True
    except Exception:
        pass
    _drop_rcon()

    try:
        S.server_proc.wait(timeout=60)
    except subprocess.TimeoutExpired:
        S.server_proc.terminate()
        try:
            S.server_proc.wait(timeout=15)
        except subprocess.TimeoutExpired:
            S.server_proc.kill()
    code = S.server_proc.returncode
    S.server_proc = None
    return f"server stopped via {'rcon' if stopped else 'signal'} (exit {code})"


def server_status() -> str:
    """Report whether the server/RCON are up, who is online, and Carpet state."""
    running = _server_running()
    lines = [f"server running: {running}"]
    if running:
        lines.append(f"  pid: {S.server_proc.pid}")
    rcon_ok = False
    if running:
        try:
            who = _ensure_rcon().command("list")
            rcon_ok = True
            lines.append(f"  rcon: ok — {who.strip()}")
        except Exception as e:
            lines.append(f"  rcon: unavailable ({e})")
    lines.append(f"rcon connected: {rcon_ok}")
    carpet = list(MODS.glob("fabric-carpet*.jar")) if MODS.exists() else []
    lines.append(f"carpet (bots): {'installed: ' + carpet[0].name if carpet else 'not installed'}")
    return "\n".join(lines)


def run_command(command: str) -> str:
    """Run one server console command over RCON and return its response."""
    if not _server_running():
        return "Server is not running. Call server_start first."
    cmd = command.lstrip("/")
    try:
        resp = _ensure_rcon().command(cmd)
    except Exception as e:
        _drop_rcon()
        return f"RCON error: {e}"
    return resp if resp.strip() else "(no output)"


def read_log(grep: str | None = None, lines: int = 100, from_start: bool = False) -> str:
    """Return new server-log output since the last call (incremental tail).

    grep: optional regex to filter lines. from_start: re-read the whole log.
    """
    data = _read_new_log(reset=from_start)
    if not data:
        return "(no new log output)"
    out = data.splitlines()
    if grep:
        try:
            pat = re.compile(grep)
            out = [l for l in out if pat.search(l)]
        except re.error as e:
            return f"bad regex: {e}"
    if not out:
        return "(no matching log lines)"
    return "\n".join(out[-lines:])


def player_spawn(name: str) -> str:
    """Spawn a Carpet server-side fake player named `name` (joins the lobby)."""
    if not _server_running():
        return "Server is not running. Call server_start first."
    return run_command(f"player {name} spawn")


def player_action(name: str, action: str, args: str = "") -> str:
    """Drive a Carpet fake player via `/player <name> <action> <args>`.

    Common actions: use [<ticks>] | attack [<ticks>] | jump | sneak | sprint |
    drop | dropStack | swapHands | hotbar <1-9> | move <forward|backward|left|
    right> | turn <left|right|back> | look <direction|yaw pitch>. `drop` (after
    `hotbar <slot>`) triggers the drop-to-select mechanic (PlayerDropMixin).
    """
    return run_command(f"player {name} {action} {args}".strip())


def player_despawn(name: str) -> str:
    """Remove a fake player via `/player <name> kill`."""
    return run_command(f"player {name} kill")


def players_online() -> str:
    """List players currently connected (via `/list` over RCON)."""
    return run_command("list")


def player_permno(name: str) -> str:
    """Fetch a player's permanent number (`permPNo`) for item targeting.

    permNos are shuffled at game start and held only in memory, so this is the
    reliable way to find which number `/deathswap target <permNo>` should use
    for a given opponent. Wraps `/deathswap permno <name>` (gamemaster command,
    available over RCON). Call with no game running and it reports that there is
    no permNo yet.
    """
    return run_command(f"/deathswap permno {name}")


# --------------------------------------------------------------------------
# MCP wiring
# --------------------------------------------------------------------------
def build_mcp():
    from mcp.server.fastmcp import FastMCP

    mcp = FastMCP("deathswap-test")
    for fn in (provision, server_start, server_stop, server_status,
               run_command, read_log, player_spawn, player_action,
               player_despawn, players_online, player_permno):
        mcp.tool()(fn)
    return mcp


def _selfcheck() -> int:
    print("paths:")
    print("  repo:", REPO_ROOT)
    print("  fabric:", FABRIC, "exists:", FABRIC.exists())
    print("  gradlew:", (FABRIC / "gradlew").exists())
    carpet = list(MODS.glob("fabric-carpet*.jar")) if MODS.exists() else []
    print("  carpet:", carpet[0].name if carpet else "(not yet downloaded)")
    print("  jdk25:", _find_jdk25() or "(not found)")
    try:
        build_mcp()
        print("FastMCP import + tool registration: ok")
    except Exception as e:
        print("FastMCP setup FAILED:", e)
        return 1
    return 0


if __name__ == "__main__":
    if "--selfcheck" in sys.argv:
        sys.exit(_selfcheck())

    def _cleanup(*_):
        sys.exit(0)

    signal.signal(signal.SIGTERM, _cleanup)
    build_mcp().run()
