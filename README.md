# PvPGames Demo Core

<!-- Replace OWNER/REPO with your GitHub path once pushed, e.g. omar/pvpgames-demo-core. -->
![Build](https://github.com/OWNER/REPO/actions/workflows/build.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)

A polished, professional **1v1 Duels** demo for Minecraft (Paper) that proves a reusable,
expandable competitive-PvP match framework — built as a portfolio piece for the
[oalawwa/pvpgames-demo-core](https://pvpgames.gg) development team.

> The goal isn't a full network. It's a small, clean demo that demonstrates the core systems a
> full server needs: a competitive lobby, matchmaking queues, a state-driven match engine, kits,
> arenas, persistent stats with ELO, leaderboards, spectating, and an esports-style UI — all
> organized so Capture-The-Flag, Royale, tournaments, cosmetics, and a website API can be added
> later **without rewriting the core**.

---

## Gameplay loop

```
Lobby  →  Queue  →  Countdown  →  PvP Match  →  Results Screen  →  Stats/ELO Update  →  Requeue / Lobby
```

Every step is implemented end-to-end for ranked **and** unranked 1v1 duels.

## Feature highlights

- **Reusable match framework** — `Game` interface + `GameStateEngine` + `GameManager`. Adding a
  new mode means implementing one interface and registering it; the lifecycle, scoreboard,
  spectator, and stats plumbing are all mode-agnostic.
- **1v1 Duels** — ranked + unranked queues, kit selection, automatic arena assignment, title
  countdowns, win detection, ELO changes, a clean results screen, and clickable requeue.
- **Kits** — NoDebuff, Diamond, Axe, Archer, and a Custom (sandbox) kit, all defined in
  `kits.yml`. Add new kits with zero code.
- **Arenas** — fully managed in-game (`/arena create/setspawn1/setspawn2/enable/disable/list/delete`),
  saved to `arenas.yml`, allocated so two matches never share one.
- **Statistics** — kills, deaths, wins, losses, KDR, ELO, current & best win streak, games played,
  and damage dealt. Stored in **MySQL** with an automatic **SQLite fallback**.
- **Leaderboards** — `/leaderboard elo|wins|kills`, plus optional **DecentHolograms** holographic
  top-10 boards.
- **Esports UI** — per-state sidebar scoreboards, title countdowns, action bars, sounds, clean
  chat, a built-in queue GUI, and clickable requeue prompts. Dark-gray / black / white / blue /
  cyan theme, fully editable in `messages.yml`.
- **Admin tools** — `/pvp reload|forcestart|resetstats|debug|setlobby|info`.
- **Integrations (all optional / soft-depends)** — PlaceholderAPI (`%pvpgames_*%`), LuckPerms, TAB,
  DecentHolograms, Citizens, DeluxeMenus, CombatLogX.

## Tech stack

| Tool | Version (pinned) |
|------|------------------|
| Java | 21 (toolchain) |
| Paper API | 1.21.8-R0.1-SNAPSHOT |
| Gradle | 8.11.1 (wrapper) |
| Shadow | 8.3.10 |
| HikariCP | 5.1.0 |
| MySQL Connector/J | 8.4.0 |
| SQLite JDBC | 3.46.1.0 |

All versions live in `gradle.properties` so they can be bumped in one place.

## Quick start

```bash
# 1. Build (Windows: gradlew.bat). Produces build/libs/PvPGamesDemoCore-1.0.0.jar
./gradlew build

# 2. Drop the jar into your Paper server's /plugins folder and start the server once.
#    With no MySQL configured it automatically uses a local SQLite file — zero setup.

# 3. In-game (as OP), set up a playable arena:
/pvp setlobby
/arena create classic1
#   stand on spawn point 1:
/arena setspawn1 classic1
#   stand on spawn point 2:
/arena setspawn2 classic1
/arena enable classic1

# 4. Queue and fight (open the menu, or queue directly):
/duel
/duel NoDebuff ranked
```

See **[SETUP.md](SETUP.md)** for IntelliJ import, MySQL configuration, and a full testing
checklist, and **[DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md)** for the architecture deep-dive and how
to add a new game mode.

## Project layout

```
src/main/java/gg/pvpgames/demo/
├── PvPGamesDemoCore.java     # entry point + service locator
├── api/                      # Game, GameMode  (the extension surface)
├── game/                     # GameManager + state engine
├── match/                    # DuelGame, MatchController, MatchVisuals
├── queue/                    # QueueManager + matchmaking
├── arena/                    # Arena + ArenaManager
├── kit/                      # Kit + KitManager
├── profile/                  # PlayerProfile(+Manager), PlayerState
├── stats/                    # PlayerStats, StatsManager, Elo
├── data/                     # DataStore + MySQL/SQLite implementations
├── scoreboard/               # ScoreboardManager
├── leaderboard/              # LeaderboardManager
├── spectator/                # SpectatorManager
├── hologram/                 # HologramManager (DecentHolograms, reflective)
├── hook/                     # PlaceholderAPI expansion
├── core/                     # LobbyManager
├── commands/  + commands/admin/
├── listeners/                # connection, combat, lobby protection
└── util/                     # Text, Items, Locations, Sounds, Placeholders
src/main/resources/
├── plugin.yml  config.yml  messages.yml  kits.yml  arenas.yml
└── menus/duel_menu.yml       # optional DeluxeMenus config
```

## License

Provided as a portfolio/demo project. Use freely as a learning reference.
