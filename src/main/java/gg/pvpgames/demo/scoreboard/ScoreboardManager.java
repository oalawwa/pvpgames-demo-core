package gg.pvpgames.demo.scoreboard;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.stats.PlayerStats;
import gg.pvpgames.demo.util.Placeholders;
import gg.pvpgames.demo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Per-player sidebar scoreboard with an esports look. Lines are defined per game-state in
 * messages.yml ({@code scoreboard.lobby/queue/match}) and refreshed every second by a repeating
 * task so timers and health stay live.
 *
 * <p>Implementation detail: to update lines without the classic flicker, each line is rendered via
 * a team prefix keyed to a unique invisible entry. We only rewrite a line when its text actually
 * changes.
 */
public final class ScoreboardManager {

    private final PvPGamesDemoCore plugin;
    @Nullable
    private BukkitTask task;

    public ScoreboardManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null || !plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerProfile profile = plugin.profiles().get(player);
            if (profile == null || profile.state() == PlayerState.SPECTATING) {
                continue;
            }
            apply(player);
        }
    }

    /** Build/refresh the sidebar for a single player based on their current state. */
    public void apply(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }

        Scoreboard board = player.getScoreboard();
        // Give the player their own fresh scoreboard if they're still on the (shared) main one.
        if (board == plugin.getServer().getScoreboardManager().getMainScoreboard()) {
            board = plugin.getServer().getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Objective obj = board.getObjective("pvpsb");
        if (obj == null) {
            obj = board.registerNewObjective("pvpsb", Criteria.DUMMY,
                    Text.parse(plugin.getConfig().getString("scoreboard.title", "PvPGames")));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(Text.parse(plugin.getConfig().getString("scoreboard.title", "PvPGames")));
        }

        List<String> lines = linesFor(player, profile);
        renderLines(board, obj, lines);
    }

    /** Pick and resolve the right line set for the player's state. */
    private List<String> linesFor(Player player, PlayerProfile profile) {
        PlayerStats s = profile.stats();
        switch (profile.state()) {
            case QUEUE -> {
                var entry = plugin.queues().entry(player.getUniqueId());
                Placeholders ph = Placeholders.create()
                        .set("mode", entry != null ? entry.mode().display() : "-")
                        .set("kit", entry != null ? entry.kit() : "-")
                        .set("wait", entry != null ? entry.waitedSeconds() : 0);
                return resolve("scoreboard.queue", ph);
            }
            case IN_MATCH -> {
                Game game = plugin.gameManager().byPlayer(player.getUniqueId());
                if (game instanceof gg.pvpgames.demo.match.DuelGame duel) {
                    return resolve("scoreboard.match", duel.scoreboardPlaceholders(player));
                }
                // Fallback to lobby layout if the match type isn't recognised.
            }
            default -> {
                // LOBBY (and any fallthrough)
            }
        }
        Placeholders ph = Placeholders.create()
                .set("player", player.getName())
                .set("elo", s.elo())
                .set("wins", s.wins())
                .set("streak", s.currentStreak())
                .set("online", plugin.getServer().getOnlinePlayers().size());
        return resolve("scoreboard.lobby", ph);
    }

    private List<String> resolve(String key, Placeholders ph) {
        List<String> raw = plugin.messages().rawList(key);
        return raw.stream().map(ph::apply).toList();
    }

    /**
     * Render up to 15 lines top-to-bottom. Each line uses a dedicated team whose prefix carries the
     * text, with a unique hidden entry per row so duplicate text lines don't collapse.
     */
    private void renderLines(Scoreboard board, Objective obj, List<String> lines) {
        int max = Math.min(lines.size(), 15);
        // Scores count down so the first line shows on top.
        for (int i = 0; i < max; i++) {
            int score = max - i;
            String teamName = "sb_" + score;
            String entry = uniqueEntry(score); // invisible color-code string, stable per row

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
            Component prefix = Text.parse(lines.get(i));
            // Only update if changed (cheap equality on the serialized component).
            if (!prefix.equals(team.prefix())) {
                team.prefix(prefix);
            }
            obj.getScore(entry).setScore(score);
        }
        // Remove stale rows if the new layout is shorter than before.
        for (int score = max + 1; score <= 15; score++) {
            String entry = uniqueEntry(score);
            board.resetScores(entry);
            Team team = board.getTeam("sb_" + score);
            if (team != null) {
                team.unregister();
            }
        }
    }

    /**
     * A per-row entry made only of legacy color codes (invisible, never displayed) so each row is
     * a distinct scoreboard entry even when two rows have identical visible text.
     */
    private String uniqueEntry(int score) {
        // Map 1..15 to color codes §1..§f.
        char c = Integer.toHexString(score & 0xF).charAt(0);
        return "§" + c + "§r";
    }

    /** Remove the sidebar entirely (used for spectators). */
    public void clear(Player player) {
        Scoreboard board = player.getScoreboard();
        Objective obj = board.getObjective("pvpsb");
        if (obj != null) {
            obj.unregister();
        }
    }
}
