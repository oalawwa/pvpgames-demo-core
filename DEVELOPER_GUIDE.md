# Developer Guide — PvPGames Demo Core

This document explains *how the plugin is built and why*, then shows how to extend it. It's written
for a reviewer evaluating the code and for any developer who'll build on top of it.

---

## 1. Design goals

1. **Prove the core, not the content.** One mode (1v1 Duels), implemented completely, that exercises
   every system a full PvP network needs.
2. **Make modes pluggable.** Adding CTF / Royale / tournaments must mean *adding* code, not *editing*
   the match engine. That requirement drove the `Game` interface and the state engine.
3. **Separate "what we persist" from "what's in memory."** `PlayerStats` (saved) vs `PlayerProfile`
   (session). This keeps the database schema clean and the runtime state cheap.
4. **Never block the main thread on IO.** All database access is async; the main thread only ever
   touches in-memory objects.
5. **Be configurable and themeable.** Text (`messages.yml`), kits (`kits.yml`), arenas
   (`arenas.yml`), and behaviour (`config.yml`) are data, not code.

---

## 2. The big picture

```
                         ┌─────────────────────────────┐
                         │      PvPGamesDemoCore        │  entry point + service locator
                         │  (holds every manager)       │
                         └──────────────┬───────────────┘
            ┌───────────────────────────┼───────────────────────────┐
            ▼                           ▼                            ▼
   ┌────────────────┐         ┌──────────────────┐          ┌────────────────┐
   │  QueueManager  │ matched │  MatchController │  creates  │   GameManager  │
   │ (matchmaking)  │────────▶│ (arena alloc +   │──────────▶│ (registry +    │
   └────────────────┘  pair   │  builds a Game)  │  Game     │  global tick)  │
                              └──────────────────┘          └───────┬────────┘
                                                                    │ ticks each
                                                                    ▼
                                                         ┌──────────────────────┐
                                                         │   DuelGame : Game     │
                                                         │  driven by            │
                                                         │  GameStateEngine      │
                                                         └─────────┬─────────────┘
                          uses, during its lifecycle:              │
       ┌───────────────┬───────────────┬────────────────┬─────────┼───────────────┐
       ▼               ▼               ▼                ▼         ▼               ▼
   ArenaManager    KitManager   ScoreboardManager  StatsManager  SpectatorMgr  MatchVisuals
                                                       │
                                                       ▼
                                                  DataStore  ── MySQL / SQLite
```

**One sentence per box:**

- **PvPGamesDemoCore** — builds all managers in dependency order on enable; exposes them via short
  accessors (`plugin.queues()`, `plugin.stats()`, …) so the codebase reads like prose.
- **QueueManager** — holds queued players and, once per second, pairs compatible entries.
- **MatchController** — turns a matched pair into a running `Game`, owning arena allocation and the
  "no arena free" fallback.
- **GameManager** — the registry of live games and the single repeating task that ticks them all;
  also the O(1) "which game is this player in?" index.
- **DuelGame** — the concrete 1v1 mode; the reference implementation of `Game`.
- **GameStateEngine** — a tiny state machine that guarantees forward-only phase transitions and
  fires the right lifecycle callback exactly once.
- **DataStore** — the storage seam; MySQL and SQLite are interchangeable behind it.

---

## 3. The match lifecycle (the heart of the framework)

Every match is a state machine: `WAITING → COUNTDOWN → LIVE → ENDED → CLEANUP`.

`GameStateEngine` owns the current state. Concrete games **never** call their own `onLive()` etc.
directly — they ask the engine to `transition(...)`, and the engine invokes the matching callback
once. This makes illegal transitions (e.g. going back to COUNTDOWN, or running results twice)
*structurally impossible*.

```java
// GameStateEngine.transition — forward-only, fire-once
public void transition(GameState next) {
    if (next.ordinal() <= state.ordinal()) return; // ignore backwards/same
    this.state = next;
    this.entered = false;
    enter(); // calls game.onCountdown()/onLive()/... exactly once
}
```

How `DuelGame` uses each phase:

| Phase | What `DuelGame` does |
|-------|----------------------|
| `WAITING` | Mark both players `IN_MATCH`; immediately advance to `COUNTDOWN`. |
| `COUNTDOWN` | Teleport to arena spawns, set SURVIVAL, apply the kit, **freeze**, then run a 1-second recursive countdown with title + sound. |
| `LIVE` | Unfreeze, send the "FIGHT!" title, start the match timer. Win detection is now active. |
| `ENDED` | Compute the result, update stats + ELO, show the results panel + clickable requeue, then schedule `CLEANUP`. |
| `CLEANUP` | Send both players (and any spectators) back to the lobby, release the arena, unregister the game. |

`GameManager` calls `engineTick()` on every game each server tick; the engine only forwards that to
`onTick()` while `LIVE`, where `DuelGame` advances the per-second timer and enforces the time limit.

**Win detection without a death screen.** `CombatListener` intercepts lethal damage
(`EntityDamageEvent` at `HIGHEST`): if the hit would drop the victim to ≤ 0 HP, it cancels the
event, restores the victim, records the kill/death, and calls `duel.onPlayerDeath(victim)`. The
duel then decides the winner and transitions to `ENDED`. This is the standard approach competitive
servers use — no respawn screen, instant round end, full control over rewards.

---

## 4. Matchmaking

`QueueEntry` snapshots everything a match needs: player, mode, kit, ranked flag, ELO, and join time.
Two entries are compatible only if they share **mode + kit + ranked flag** (`sameBracket`).

`QueueManager.scan()` runs every second:

- **Unranked**: pair the two longest-waiting compatible players immediately.
- **Ranked**: also require both players to be inside each other's ELO band. The band starts at
  `ranked-initial-elo-range` and **grows** by `ranked-elo-range-growth-per-second` for each second a
  player has waited, capped at `ranked-max-elo-range`. So strong players get fair matches fast but
  never wait forever.

```yaml
queue:
  ranked-initial-elo-range: 100      # start within ±100 ELO
  ranked-elo-range-growth-per-second: 25
  ranked-max-elo-range: 1000
```

When a pair is found, the players are removed from the queue and handed to
`MatchController.startDuel(...)`.

---

## 5. ELO

Isolated in `Elo` (pure functions → trivially testable):

```
Ea  = 1 / (1 + 10^((Rb - Ra)/400))        // expected score for A
Ra' = Ra + K * (Sa - Ea)                  // Sa = 1 win / 0.5 draw / 0 loss
```

`StatsManager.applyMatchResult(winner, loser, ranked)` records the win/loss, and — only for ranked —
applies the K-factor-scaled delta (config `elo.k-factor`, floored at `elo.minimum`). It returns the
deltas so the results screen can show `+16 / -16`.

---

## 6. Storage (MySQL + SQLite fallback)

The whole plugin codes against the `DataStore` interface. Two implementations share all their SQL
via an abstract `SqlDataStore`; subclasses differ only in:

- how they get a `Connection` (HikariCP pool vs per-op SQLite connection), and
- the dialect-specific UPSERT (`ON DUPLICATE KEY UPDATE` vs `ON CONFLICT … DO UPDATE`).

`StorageProvider.create(...)` implements the fallback policy:

```
type = SQLITE            → SQLite
type = MYSQL             → try MySQL; if init() fails AND fallback-to-sqlite → SQLite
                           else throw (and the plugin disables itself safely)
```

Because everything is one flat `pvp_players` row per player, exposing this over a **website API**
later is a single `SELECT` — no schema gymnastics.

**Threading rule:** `DataStore` methods may block, so they're always called inside
`runTaskAsynchronously`. Results are marshalled back to the main thread before touching Bukkit
objects. See `StatsManager.loadAsync` / `topAsync` for the pattern.

---

## 7. Players: profile vs stats

- **`PlayerStats`** — the persisted record (kills, deaths, wins, losses, KDR, ELO, streaks, games,
  damage). Mutated in memory during a match, flushed async.
- **`PlayerProfile`** — the in-memory session: current `PlayerState` (LOBBY / QUEUE / IN_MATCH /
  SPECTATING), current match id, selected kit, ranked preference, and a reference to its
  `PlayerStats`.

`PlayerProfileManager` loads a profile on join (async), caches it while online, and evicts + saves
on quit. `PlayerState` drives which scoreboard shows and what actions are allowed.

---

## 8. Presentation

- **ScoreboardManager** — a per-player Adventure sidebar. Lines are defined per state in
  `messages.yml` (`scoreboard.lobby/queue/match`) and refreshed each second. Flicker-free updates
  use one team per row with a unique invisible entry, rewriting a line only when its text changes.
- **MatchVisuals** — bundles titles, action bars, and sounds so match code stays readable. All text
  comes from `messages.yml`; all sounds from `config.yml`.
- **Results screen** — a multi-line panel plus a clickable line: `[⟳ REQUEUE]` runs
  `/duel <kit> <ranked|unranked>` (the same path the GUI uses), `[RETURN TO LOBBY]` runs `/leave`.
- **Text utility** — accepts **both** MiniMessage (`<gradient:#00e5ff:#0091ff>`) and legacy `&`
  codes, so config authors use whichever they like. The esports palette (black / dark-gray / white /
  blue / cyan) is applied through these strings.

---

## 9. Optional integrations

All are **soft-depends** — the plugin starts fine without them.

- **PlaceholderAPI** — `PvPGamesExpansion` registers the `pvpgames` identifier:
  `%pvpgames_elo%`, `%pvpgames_wins%`, `%pvpgames_kdr%`, `%pvpgames_streak%`, etc. TAB and
  DeluxeMenus can render these directly.
- **DecentHolograms** — `HologramManager` talks to it purely by reflection against its public
  `DHAPI`, so there's no compile dependency and it no-ops cleanly when absent. It refreshes the
  lines of holograms named `pvp_top_elo` / `pvp_top_wins` / `pvp_top_kills`.
- **DeluxeMenus** — `menus/duel_menu.yml` is a ready-made menu that drives the same `/duel`
  commands as the built-in GUI.
- **LuckPerms / TAB / Citizens / CombatLogX** — declared as soft-depends and supported via standard
  permissions / placeholders. (CombatLogX is unnecessary in-match because the match engine already
  controls combat fully; it's listed for hub/global use.)

---

## 10. Commands & permissions

| Command | Permission | Purpose |
|--------|-----------|---------|
| `/duel [kit] [ranked\|unranked]` | `pvpgames.play` | Open queue GUI, or queue directly. |
| `/stats [player]` | `pvpgames.play` | View stats (online or offline). |
| `/leaderboard [elo\|wins\|kills]` | `pvpgames.play` | View leaderboards. |
| `/leave` (`/hub`, `/lobby`, `/spawn`) | `pvpgames.play` | Context-aware exit (queue/spectate/forfeit/hub). |
| `/spectate <player>` | `pvpgames.play` | Watch a match. |
| `/arena …` | `pvpgames.admin` | create / setspawn1 / setspawn2 / enable / disable / list / delete. |
| `/pvp …` | `pvpgames.admin` | reload / forcestart / resetstats / debug / setlobby / info. |

`pvpgames.play` defaults to everyone; `pvpgames.admin` defaults to OP and implies `pvpgames.play`.

---

## 11. How to add a new game mode (e.g. Capture The Flag)

The framework is designed so this is additive. A concrete recipe:

1. **Add the mode** to the `GameMode` enum (already stubbed: `CTF`, `ROYALE`, `TOURNAMENT`), with
   its display name and team size.
2. **Create `match/CtfGame.java` implementing `Game`.** Reuse `GameStateEngine` exactly as
   `DuelGame` does. Implement the lifecycle:
   - `onCountdown()` — teleport teams to their flag spawns, give the CTF kit, freeze.
   - `onLive()` — enable capture logic; track flag carriers.
   - `onTick()` — check capture conditions / score limit / time limit.
   - `onEnded()` — award the winning team, update stats, show results.
   - `onCleanup()` — return everyone to the lobby, release the arena.
   - `onPlayerDeath(...)` / `onPlayerQuit(...)` — drop the flag / handle team forfeits.
3. **Extend arena data** if needed (CTF needs flag locations + team spawns). `Arena` already
   supports a kit whitelist; add fields the same way and persist them in `ArenaManager.save/load`.
4. **Add matchmaking** — either reuse `QueueManager` with team grouping, or add a `CtfQueue`. The
   `QueueEntry.sameBracket` concept generalizes to "same mode + map pool".
5. **Wire creation** — add `MatchController.startCtf(...)` mirroring `startDuel(...)`.
6. **Scoreboards** — add a `scoreboard.ctf` section to `messages.yml` and a branch in
   `ScoreboardManager.linesFor(...)`.

Crucially, **none of the existing engine, stats, storage, spectator, or leaderboard code changes** —
`CtfGame` plugs into the same `GameManager` tick loop and the same `DataStore`. That's the whole
point of the abstraction, and the same path leads to Royale, tournaments, ranked events, cosmetics
(driven off `PlayerProfile`), and a website API (a read endpoint over `DataStore`).

---

## 12. Threading & safety notes

- The global game tick (`GameManager`) iterates a concurrent snapshot, so a game can unregister
  itself mid-tick without a `ConcurrentModificationException`.
- All `DataStore` calls are async; callbacks re-enter the main thread before touching Bukkit.
- `ArenaManager.acquire/release` is `synchronized`, so two simultaneous match starts can't grab the
  same arena.
- On shutdown, tasks are cancelled first, then every online player's stats are flushed
  synchronously, then the pool is closed.

---

## 13. File-by-file index

| Package | Key classes | Responsibility |
|---------|-------------|----------------|
| `api` | `Game`, `GameMode` | The extension surface every mode implements. |
| `game`, `game.state` | `GameManager`, `GameStateEngine`, `GameState` | Registry, tick loop, state machine. |
| `match` | `DuelGame`, `MatchController`, `MatchVisuals` | The 1v1 mode, match construction, and feedback. |
| `queue` | `QueueManager`, `QueueEntry` | Matchmaking. |
| `arena` | `ArenaManager`, `Arena` | Arena data + allocation. |
| `kit` | `KitManager`, `Kit` | Kit definitions + application. |
| `profile` | `PlayerProfileManager`, `PlayerProfile`, `PlayerState` | Session state. |
| `stats` | `StatsManager`, `PlayerStats`, `Elo` | Stats mutation, ELO, async persistence. |
| `data` | `DataStore`, `SqlDataStore`, `MySqlDataStore`, `SqliteDataStore`, `StorageProvider` | Storage seam + backends. |
| `scoreboard`, `leaderboard`, `spectator`, `hologram` | respective managers | Presentation + boards + spectating. |
| `hook` | `PvPGamesExpansion` | PlaceholderAPI integration. |
| `core` | `LobbyManager` | Hub teleport, reset, lobby items. |
| `commands`, `commands.admin` | command + GUI classes | Player and admin commands. |
| `listeners` | `ConnectionListener`, `CombatListener`, `LobbyProtectionListener` | Lifecycle, combat rules, hub protection. |
| `util` | `Text`, `Items`, `Locations`, `Sounds`, `Placeholders` | Cross-cutting helpers. |
