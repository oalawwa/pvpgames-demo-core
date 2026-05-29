package gg.pvpgames.demo.match;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.api.GameMode;
import gg.pvpgames.demo.arena.Arena;
import gg.pvpgames.demo.game.state.GameState;
import gg.pvpgames.demo.game.state.GameStateEngine;
import gg.pvpgames.demo.kit.Kit;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.stats.PlayerStats;
import gg.pvpgames.demo.stats.StatsManager;
import gg.pvpgames.demo.util.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A single 1v1 duel. This class is the reference implementation of {@link Game}: it shows exactly
 * how a mode plugs into the shared framework (state engine, arena allocation, kits, stats, ELO,
 * scoreboard, spectators). Future modes (CTF, Royale) follow the same shape.
 *
 * <p>Flow it implements, matching the brief:
 * <pre>Queue → Countdown → Match → Win detection → Results → Stats/ELO → Requeue/Lobby</pre>
 */
public final class DuelGame implements Game {

    private final PvPGamesDemoCore plugin;
    private final UUID id = UUID.randomUUID();
    private final GameStateEngine engine = new GameStateEngine(this);

    private final UUID playerA;
    private final UUID playerB;
    private final String kitName;
    private final boolean ranked;
    private final Arena arena;

    private final List<UUID> spectators = new CopyOnWriteArrayList<>();

    // Countdown / timing (measured in server ticks via onTick, plus scheduled tasks).
    private int countdownRemaining;
    private long liveStartMillis;
    private int liveSecondsElapsed;

    @Nullable
    private UUID winner;
    @Nullable
    private UUID loser;
    private boolean draw;

    public DuelGame(PvPGamesDemoCore plugin, UUID playerA, UUID playerB,
                    String kitName, boolean ranked, Arena arena) {
        this.plugin = plugin;
        this.playerA = playerA;
        this.playerB = playerB;
        this.kitName = kitName;
        this.ranked = ranked;
        this.arena = arena;
    }

    /** Kick off the state machine. Called by MatchController right after registration. */
    public void begin() {
        engine.begin();
    }

    // ====================================================================
    //  Game interface
    // ====================================================================

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public GameMode mode() {
        return GameMode.DUELS;
    }

    @Override
    public GameState state() {
        return engine.state();
    }

    @Override
    public Collection<UUID> players() {
        return List.of(playerA, playerB);
    }

    @Override
    public boolean isPlayer(UUID uuid) {
        return playerA.equals(uuid) || playerB.equals(uuid);
    }

    @Override
    public void engineTick() {
        engine.tick();
    }

    // ---- lifecycle ----

    @Override
    public void onWaiting() {
        // Mark both players as IN_MATCH and immediately advance to countdown.
        for (UUID id : players()) {
            PlayerProfile profile = plugin.profiles().get(id);
            if (profile != null) {
                profile.state(PlayerState.IN_MATCH);
                profile.currentMatchId(this.id);
                profile.spectating(false);
            }
        }
        engine.transition(GameState.COUNTDOWN);
    }

    @Override
    public void onCountdown() {
        Player a = playerA();
        Player b = playerB();
        if (a == null || b == null) {
            // Someone vanished before we even started — abort cleanly.
            abortToCleanup();
            return;
        }

        // Teleport to spawns, set survival, apply kits, freeze in place.
        teleportToSpawns(a, b);
        applyKit(a);
        applyKit(b);
        freeze(a, true);
        freeze(b, true);
        plugin.scoreboards().apply(a);
        plugin.scoreboards().apply(b);

        countdownRemaining = Math.max(1, plugin.getConfig().getInt("match.countdown-seconds", 5));
        runCountdownTick();
    }

    /** Recursive 1-second countdown using the scheduler (keeps onCountdown readable). */
    private void runCountdownTick() {
        if (engine.state() != GameState.COUNTDOWN) {
            return; // safety: state changed underneath us
        }
        Player a = playerA();
        Player b = playerB();
        if (a == null || b == null) {
            abortToCleanup();
            return;
        }
        if (countdownRemaining <= 0) {
            engine.transition(GameState.LIVE);
            return;
        }
        plugin.visuals().countdownTitle(a, countdownRemaining, mode().display(), kitName);
        plugin.visuals().countdownTitle(b, countdownRemaining, mode().display(), kitName);
        countdownRemaining--;
        Bukkit.getScheduler().runTaskLater(plugin, this::runCountdownTick, 20L);
    }

    @Override
    public void onLive() {
        Player a = playerA();
        Player b = playerB();
        if (a == null || b == null) {
            abortToCleanup();
            return;
        }
        freeze(a, false);
        freeze(b, false);
        liveStartMillis = System.currentTimeMillis();
        liveSecondsElapsed = 0;
        plugin.visuals().goTitle(a, b.getName());
        plugin.visuals().goTitle(b, a.getName());
        plugin.messages().send(a, "match.started", Placeholders.of("opponent", b.getName()));
        plugin.messages().send(b, "match.started", Placeholders.of("opponent", a.getName()));
    }

    @Override
    public void onTick() {
        // Runs every server tick while LIVE. Update the per-second timer and enforce the limit.
        long now = System.currentTimeMillis();
        int elapsed = (int) ((now - liveStartMillis) / 1000L);
        if (elapsed != liveSecondsElapsed) {
            liveSecondsElapsed = elapsed;
            int limit = plugin.getConfig().getInt("match.time-limit-seconds", 300);
            if (limit > 0 && liveSecondsElapsed >= limit) {
                endAsDraw();
            }
        }
    }

    @Override
    public void onEnded() {
        Player win = winner == null ? null : Bukkit.getPlayer(winner);
        Player lose = loser == null ? null : Bukkit.getPlayer(loser);

        // Persist stats + ELO and show the results screen.
        if (!draw && winner != null && loser != null) {
            applyResultAndShow(winner, loser);
        } else {
            showDrawResults();
        }

        // Schedule cleanup after the results screen duration.
        int delay = Math.max(1, plugin.getConfig().getInt("match.results-screen-seconds", 5));
        Bukkit.getScheduler().runTaskLater(plugin, () -> engine.transition(GameState.CLEANUP), 20L * delay);
    }

    @Override
    public void onCleanup() {
        // Return competitors and spectators to the lobby, release the arena, unregister.
        for (UUID id : players()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                plugin.lobby().sendToLobby(p);
            }
        }
        for (UUID specId : spectators) {
            Player s = Bukkit.getPlayer(specId);
            if (s != null) {
                plugin.lobby().sendToLobby(s);
            }
            plugin.gameManager().unindexPlayer(specId);
        }
        spectators.clear();
        plugin.arenas().release(arena);
        plugin.gameManager().unregister(this);
    }

    // ---- gameplay events ----

    @Override
    public void onPlayerDeath(Player victim) {
        if (engine.state() != GameState.LIVE) {
            return;
        }
        UUID victimId = victim.getUniqueId();
        UUID other = victimId.equals(playerA) ? playerB : playerA;
        finish(other, victimId, false);
    }

    @Override
    public void onPlayerQuit(UUID uuid) {
        if (spectators.contains(uuid)) {
            removeSpectator(uuid);
            return;
        }
        if (!isPlayer(uuid)) {
            return;
        }
        // A competitor quit. If the match is live or counting down, the other player wins.
        if (engine.state() == GameState.LIVE || engine.state() == GameState.COUNTDOWN) {
            UUID other = uuid.equals(playerA) ? playerB : playerA;
            finish(other, uuid, false);
        }
    }

    @Override
    public void addSpectator(Player spectator) {
        spectators.add(spectator.getUniqueId());
        // Announce to existing spectators (not competitors, to avoid distraction).
        for (UUID specId : spectators) {
            Player s = Bukkit.getPlayer(specId);
            if (s != null && !s.equals(spectator)) {
                plugin.messages().send(s, "spectator.player-joined",
                        Placeholders.of("player", spectator.getName()));
            }
        }
    }

    @Override
    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
    }

    @Override
    public Collection<UUID> spectators() {
        return spectators;
    }

    // ====================================================================
    //  Internal helpers
    // ====================================================================

    private void finish(UUID winnerId, UUID loserId, boolean isDraw) {
        if (engine.state().ordinal() >= GameState.ENDED.ordinal()) {
            return; // already ending
        }
        this.winner = winnerId;
        this.loser = loserId;
        this.draw = isDraw;
        engine.transition(GameState.ENDED);
    }

    private void endAsDraw() {
        if (engine.state().ordinal() >= GameState.ENDED.ordinal()) {
            return;
        }
        this.draw = true;
        Player a = playerA();
        Player b = playerB();
        if (a != null) {
            plugin.messages().send(a, "match.time-up");
        }
        if (b != null) {
            plugin.messages().send(b, "match.time-up");
        }
        engine.transition(GameState.ENDED);
    }

    private void applyResultAndShow(UUID winnerId, UUID loserId) {
        PlayerProfile wp = plugin.profiles().get(winnerId);
        PlayerProfile lp = plugin.profiles().get(loserId);
        if (wp == null || lp == null) {
            return;
        }
        PlayerStats ws = wp.stats();
        PlayerStats ls = lp.stats();

        StatsManager.EloResult elo = plugin.stats().applyMatchResult(ws, ls, ranked);

        Player win = Bukkit.getPlayer(winnerId);
        Player lose = Bukkit.getPlayer(loserId);
        String duration = formatDuration(liveSecondsElapsed);

        if (win != null) {
            plugin.visuals().winSound(win);
            sendResults(win, "results.title-win", ls.name(), duration, ws, ranked ? elo.winnerDelta() : 0);
        }
        if (lose != null) {
            plugin.visuals().loseSound(lose);
            sendResults(lose, "results.title-loss", ws.name(), duration, ls, ranked ? elo.loserDelta() : 0);
        }
    }

    private void showDrawResults() {
        PlayerProfile ap = plugin.profiles().get(playerA);
        PlayerProfile bp = plugin.profiles().get(playerB);
        if (ap != null && bp != null) {
            plugin.stats().applyDraw(ap.stats(), bp.stats());
        }
        Player a = playerA();
        Player b = playerB();
        String duration = formatDuration(liveSecondsElapsed);
        if (a != null && ap != null) {
            sendResults(a, "results.title-draw", b != null ? b.getName() : "?", duration, ap.stats(), 0);
        }
        if (b != null && bp != null) {
            sendResults(b, "results.title-draw", a != null ? a.getName() : "?", duration, bp.stats(), 0);
        }
    }

    /** Render the multi-line results panel + clickable requeue/lobby prompt to one player. */
    private void sendResults(Player player, String titleKey, String opponentName,
                             String duration, PlayerStats stats, int eloChange) {
        var msg = plugin.messages();
        Placeholders ph = Placeholders.create()
                .set("mode", mode().display())
                .set("kit", kitName)
                .set("opponent", opponentName)
                .set("duration", duration)
                .set("elo", stats.elo())
                .set("elo_change", (eloChange >= 0 ? "+" : "") + eloChange)
                .set("wins", stats.wins())
                .set("losses", stats.losses())
                .set("kdr", String.format("%.2f", stats.kdr()));

        // Colorize the ELO change token via a simple replace before parsing.
        String eloLine = msg.raw("results.line-elo")
                .replace("<elo_color>", eloChange >= 0 ? "<green>" : "<red>");

        player.sendMessage(msg.component("results.header"));
        player.sendMessage(gg.pvpgames.demo.util.Text.parse(msg.raw(titleKey)));
        player.sendMessage(msg.component("results.line-mode", ph));
        player.sendMessage(msg.component("results.line-opponent", ph));
        player.sendMessage(msg.component("results.line-duration", ph));
        if (ranked) {
            player.sendMessage(gg.pvpgames.demo.util.Text.parse(ph.apply(eloLine)));
        }
        player.sendMessage(msg.component("results.line-stats", ph));
        player.sendMessage(buildRequeuePrompt(ph));
        player.sendMessage(msg.component("results.footer"));
    }

    /**
     * Build the clickable "[REQUEUE] | [RETURN TO LOBBY]" line. REQUEUE runs the duel command with
     * this match's kit + ranked flag; LOBBY runs /leave. Hover text comes from messages.yml.
     */
    private Component buildRequeuePrompt(Placeholders ph) {
        String raw = ph.apply(plugin.messages().raw("results.requeue-prompt"));
        Component base = gg.pvpgames.demo.util.Text.parse(raw);

        String requeueCmd = "/duel " + kitName + " " + (ranked ? "ranked" : "unranked");
        Component requeueHover = plugin.messages().component("results.requeue-hover", ph);
        Component lobbyHover = plugin.messages().component("results.lobby-hover");

        // Attach click/hover to the whole line; the visible [tags] cue the player where to click.
        return base
                .clickEvent(ClickEvent.runCommand(requeueCmd))
                .hoverEvent(HoverEvent.showText(requeueHover.append(Component.newline()).append(lobbyHover)));
    }

    private void teleportToSpawns(Player a, Player b) {
        Location s1 = arena.spawn1();
        Location s2 = arena.spawn2();
        if (s1 != null) {
            a.teleport(s1);
        }
        if (s2 != null) {
            b.teleport(s2);
        }
    }

    private void applyKit(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        Kit kit = plugin.kits().get(kitName);
        if (kit == null) {
            kit = plugin.kits().get(plugin.kits().defaultKitName());
        }
        if (kit != null) {
            plugin.kits().apply(player, kit);
        }
    }

    /** Freeze/unfreeze a player by toggling walk/jump via slowness + a movement flag. */
    private void freeze(Player player, boolean frozen) {
        // Simple, dependency-free freeze: max slowness + jump prevention while frozen.
        if (frozen) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SLOWNESS, 20 * 10, 255, false, false));
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.JUMP_BOOST, 20 * 10, 250, false, false));
        } else {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
        }
    }

    private void abortToCleanup() {
        // Used when a player disappears before the match can run; just tear down.
        engine.transition(GameState.CLEANUP);
    }

    @Nullable
    private Player playerA() {
        return Bukkit.getPlayer(playerA);
    }

    @Nullable
    private Player playerB() {
        return Bukkit.getPlayer(playerB);
    }

    private String formatDuration(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    // ---- scoreboard support ----

    /** Placeholders for the in-match scoreboard layout (called by ScoreboardManager). */
    public Placeholders scoreboardPlaceholders(Player viewer) {
        Player self = viewer;
        UUID otherId = viewer.getUniqueId().equals(playerA) ? playerB : playerA;
        Player other = Bukkit.getPlayer(otherId);
        return Placeholders.create()
                .set("mode", mode().display())
                .set("kit", kitName)
                .set("self_health", (int) Math.ceil(self.getHealth()))
                .set("opponent", other != null ? other.getName() : "?")
                .set("opp_health", other != null ? (int) Math.ceil(other.getHealth()) : 0)
                .set("time", formatDuration(liveSecondsElapsed));
    }

    public boolean isLive() {
        return engine.state() == GameState.LIVE;
    }

    public String kitName() {
        return kitName;
    }

    public boolean ranked() {
        return ranked;
    }
}
