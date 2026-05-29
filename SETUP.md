# Setup, Build & Testing Guide

This walks you from a fresh clone to a running, playable demo, then through a complete manual
test of every system.

---

## 1. Prerequisites

- **JDK 21** (Temurin/Adoptium recommended). Verify with `java -version`.
- **IntelliJ IDEA Community Edition** (or any IDE; IntelliJ is assumed below).
- A **Paper 1.21.x** server jar for testing — download from <https://papermc.io/downloads>.
- *(Optional)* a **MySQL 8** server if you want to test the MySQL backend. Not required —
  the plugin falls back to a local SQLite file automatically.

---

## 2. Generate the Gradle wrapper jar (one-time)

This repo includes the wrapper *scripts* and config, but Git ignores binary jars by policy, so you
generate the wrapper jar once. **IntelliJ does this automatically** when you open the project. If
you prefer the command line and already have any Gradle installed:

```bash
gradle wrapper --gradle-version 8.11.1
```

After this you'll have `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`, and you
never need a system Gradle again.

> If you don't have Gradle at all, opening the folder in IntelliJ IDEA and letting it import the
> Gradle project is the simplest path — it provisions everything.

---

## 3. Open in IntelliJ

1. **File → Open** and select the project folder (the one containing `build.gradle.kts`).
2. IntelliJ detects the Gradle project and imports it. Accept the prompt to use the Gradle
   wrapper.
3. **File → Project Structure → Project**: set the SDK to **JDK 21** and language level to 21.
4. Wait for Gradle to download Paper + dependencies (first import only).

---

## 4. Build

From IntelliJ's Gradle tool window run **Tasks → build → build**, or on the command line:

```bash
./gradlew build          # macOS/Linux
gradlew.bat build        # Windows
```

The shaded, ready-to-use plugin jar is produced at:

```
build/libs/PvPGamesDemoCore-1.0.0.jar
```

This jar already contains HikariCP and the JDBC drivers (relocated), so you don't install anything
extra on the server.

---

## 5. Run a test server

1. Create a server folder and drop a `paper-1.21.x.jar` in it.
2. Create `eula.txt` containing `eula=true` (or run once and accept).
3. Copy `build/libs/PvPGamesDemoCore-1.0.0.jar` into `<server>/plugins/`.
4. Start the server:
   ```bash
   java -Xms1G -Xmx2G -jar paper-1.21.x.jar nogui
   ```
5. On first start the plugin writes `plugins/PvPGamesDemoCore/` with `config.yml`, `messages.yml`,
   `kits.yml`, and `arenas.yml`. With no MySQL configured it logs
   `Storage backend: SQLite.` and is immediately usable.

### Speeding up local testing

Optionally add a `build.gradle.kts` run task or use the
[`run-paper`](https://github.com/jpenilla/run-task) plugin. For the demo, manual copy is fine.

---

## 6. Configure MySQL (optional)

Edit `plugins/PvPGamesDemoCore/config.yml`:

```yaml
storage:
  type: MYSQL
  fallback-to-sqlite: true
  mysql:
    host: 127.0.0.1
    port: 3306
    database: pvpgames_demo
    username: pvp
    password: "your-password"
```

Create the database once:

```sql
CREATE DATABASE pvpgames_demo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pvp'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON pvpgames_demo.* TO 'pvp'@'%';
FLUSH PRIVILEGES;
```

The plugin creates its table automatically on startup:

```sql
-- pvp_players
uuid VARCHAR(36) PK, name VARCHAR(16),
kills INT, deaths INT, wins INT, losses INT, games_played INT,
elo INT, current_streak INT, best_streak INT, damage_dealt DOUBLE
```

Run `/pvp reload` or restart. The console will log `Storage backend: MySQL (connected).`
If MySQL is unreachable and `fallback-to-sqlite: true`, it logs a warning and uses SQLite instead —
so a bad password during a demo never takes the server down.

---

## 7. First-run server setup (in-game, as OP)

```text
/pvp setlobby                  # sets the hub to where you stand
/arena create classic1
/arena setspawn1 classic1      # stand where player 1 should spawn
/arena setspawn2 classic1      # stand where player 2 should spawn
/arena enable classic1
/arena list                    # confirm it shows "enabled (spawns: 2/2)"
```

You need at least **one enabled arena** for matches to start. Create several so multiple duels can
run at once.

---

## 8. Full testing checklist

Use two accounts (or a second client / a friend). Tick each item:

**Lobby & profile**
- [ ] Joining puts you in the hub in adventure mode with the lobby hotbar.
- [ ] The sidebar scoreboard shows your name, ELO, wins, streak, and online count.
- [ ] You cannot break/place blocks or lose hunger in the lobby.

**Queue**
- [ ] `/duel` opens the kit GUI; left-click = ranked, right-click = unranked.
- [ ] `/duel NoDebuff ranked` queues directly; scoreboard switches to the "Searching…" layout.
- [ ] `/leave` removes you from the queue.
- [ ] Two players queuing the same kit + ranked flag get matched within ~1–2 seconds.

**Match flow**
- [ ] Both players teleport to the arena spawns and are frozen during a 5-second countdown.
- [ ] Countdown shows as a big title with a tick sound; "FIGHT!" appears at zero.
- [ ] You receive the correct kit (armor + items) and full health/hunger.
- [ ] You can damage your opponent but not before "FIGHT!" and not in the lobby.
- [ ] The in-match scoreboard shows both players' hearts and the match timer.

**Results & stats**
- [ ] Killing your opponent ends the match without a death screen.
- [ ] Both players see the results panel (VICTORY/DEFEAT) with mode, opponent, duration, ELO, record.
- [ ] In a **ranked** match, ELO goes up for the winner and down for the loser; in **unranked** it
      doesn't change but win/loss/KDR still update.
- [ ] The clickable `[⟳ REQUEUE]` prompt rejoins the same queue; clicking returns/`/leave` goes to hub.
- [ ] After the results delay both players return to the lobby automatically.
- [ ] `/stats` reflects the updated kills/deaths/wins/losses/streak/damage.

**Leaderboards**
- [ ] `/leaderboard elo`, `/leaderboard wins`, `/leaderboard kills` list top players.
- [ ] *(If DecentHolograms installed)* holograms update within the configured refresh interval.

**Spectator**
- [ ] While a match runs, a third player `/spectate <fighter>` is put in spectator mode at the arena.
- [ ] Spectators can't be hit and can't deal damage.
- [ ] `/leave` returns a spectator to the lobby.

**Disconnect / forfeit**
- [ ] If a fighter logs out or `/leave`s mid-match, the opponent wins and stats update correctly.

**Admin**
- [ ] `/pvp forcestart <p1> <p2> NoDebuff` starts a duel immediately.
- [ ] `/pvp resetstats <player>` and `/pvp resetstats all` zero stats.
- [ ] `/pvp debug` toggles verbose logging; `/pvp info` prints a runtime summary.
- [ ] `/pvp reload` re-reads configs, kits, and arenas without a restart.

**Persistence**
- [ ] Relog and confirm your stats persisted.
- [ ] *(MySQL)* `SELECT * FROM pvp_players;` shows your row.

---

## 9. Common issues

| Symptom | Cause / Fix |
|--------|-------------|
| `No enabled arenas are available` | Create + enable an arena with both spawns set. |
| Players don't get matched | They must pick the **same kit and ranked/unranked** option. |
| MySQL won't connect | Check host/port/credentials; the console logs the exact error, then falls back to SQLite if enabled. |
| Holograms don't appear | DecentHolograms not installed, or `holograms.enabled: false`. Create them via DecentHolograms; the plugin refreshes lines by name. |
| Placeholders show as literal `%pvpgames_elo%` | PlaceholderAPI not installed, or the player's profile hasn't loaded yet. |
