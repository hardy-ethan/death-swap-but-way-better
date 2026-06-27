# Death Swap But Way Better — Fabric Mod

A Java/Fabric reimplementation of the **Death Swap But Way Better** datapack,
targeting **Minecraft 26.2** with the **Fabric** toolchain. The datapack's 329
`mcfunction` files are replaced by a structured Java game engine plus an
extensible powerful-items framework.

## Toolchain

| Component | Version |
|-----------|---------|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.149.2+26.2 |
| Fabric Loom | 1.17-SNAPSHOT |
| Gradle | 9.5.1 |
| Java | 25 |
| Mappings | None — MC 26.1+ ships unobfuscated (non-remapping `net.fabricmc.fabric-loom`) |

## Building

This project uses the Gradle wrapper, but the binary `gradle-wrapper.jar` is not
committed. Generate the wrapper once, then build:

```bash
cd fabric-mod
gradle wrapper --gradle-version 9.5.1   # one-time, creates ./gradlew + wrapper jar
./gradlew build                         # produces build/libs/deathswap-2.0.0.jar
```

Drop the resulting jar (and Fabric API) into your server's `mods/` folder.

## How to play

The datapack drove everything through in-world buttons and `/trigger`. As a mod,
the flow is command-driven:

1. Players connect — they land in the lobby (adventure mode at world spawn).
2. An operator configures the game:
   ```
   /deathswap set lives 3
   /deathswap set interval 120        # seconds between swaps
   /deathswap set randomcycle true
   /deathswap set pvp true
   /deathswap set hunger true
   /deathswap set warning 60          # pre-swap warning seconds
   ```
3. `/deathswap start` — everyone is spread far apart and the swap clock begins.
4. Every ~45s (scaled by player count) each player is handed **3 powerful
   items** in hotbar slots 7–9. **Drop one** to use it; opponent-targeted items
   then prompt you to click a target.
5. Every interval, players cyclically **swap positions**. Build traps!
6. Lose all your lives → spectator. Last player standing **wins**.
7. `/deathswap tpaway` relocates you once per cycle if your area is laggy.
8. `/deathswap stop` aborts back to the lobby.

## Architecture

```
com.deathswap
├── DeathSwapMod            entrypoint: events, tick loop, commands, death hook
├── game
│   ├── GamePhase           HUB / SETTINGS / RUNNING / ENDING state machine
│   ├── GameSettings        configurable rules (lives, interval, pvp, …)
│   ├── PlayerData          per-player state (replaces per-player scoreboards/tags)
│   ├── GameManager         the engine: clock, swap, lives, elimination, winner
│   └── DeathSwapCommands   the /deathswap command tree
├── items
│   ├── ItemTarget          SELF / OPPONENT / ALL_OTHERS / RANDOM / EVERYONE
│   ├── ItemEffect          functional effect interface
│   ├── ItemContext         services handed to an effect
│   ├── DeathSwapItem       item definition + availability gate (builder)
│   ├── ItemRegistry        the catalogue of item effects
│   └── ItemManager         offering, drop-to-select, targeting, dispatch
├── effects
│   ├── ActiveEffect        one timed status effect (tick + cleanup)
│   └── EffectManager       central timer registry (replaces effect scoreboards)
├── util
│   └── Mc                  thin wrappers over version-sensitive vanilla calls
└── mixin
    └── PlayerDropMixin     detects the "drop to select" action
```

See [PORTING.md](PORTING.md) for what is/isn't covered and the call sites most
likely to need adjustment against the exact 26.2 mappings.
