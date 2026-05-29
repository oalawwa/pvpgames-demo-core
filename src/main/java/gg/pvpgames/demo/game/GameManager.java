package gg.pvpgames.demo.game;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.api.GameMode;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of every live {@link Game} plus the single repeating task that ticks them all.
 *
 * <p>This is the framework's "engine room": one tick loop drives all matches, and a lookup map
 * lets listeners answer "is this player in a game, and which one?" in O(1). Because everything is
 * keyed by the {@link Game} interface, adding a new mode requires zero changes here.
 */
public final class GameManager {

    private final PvPGamesDemoCore plugin;

    /** matchId -> Game */
    private final ConcurrentHashMap<UUID, Game> games = new ConcurrentHashMap<>();
    /** playerId -> matchId, for both competitors and spectators (fast reverse lookup). */
    private final ConcurrentHashMap<UUID, UUID> playerIndex = new ConcurrentHashMap<>();

    @Nullable
    private BukkitTask tickTask;

    public GameManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** Start the global 1-tick game loop. */
    public void start() {
        if (tickTask != null) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 1L, 1L);
    }

    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tickAll() {
        // Iterate over a snapshot so games can remove themselves mid-tick safely.
        for (Game game : games.values()) {
            try {
                game.engineTick();
            } catch (Exception e) {
                plugin.getLogger().warning("Error ticking game " + game.id() + ": " + e.getMessage());
                if (plugin.configs().debug()) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Register a freshly created game and index its competitors. Spectators are indexed on add. */
    public void register(Game game) {
        games.put(game.id(), game);
        for (UUID p : game.players()) {
            playerIndex.put(p, game.id());
        }
    }

    /** Remove a finished game and clear its index entries. Called by the game during CLEANUP. */
    public void unregister(Game game) {
        games.remove(game.id());
        playerIndex.values().removeIf(id -> id.equals(game.id()));
    }

    /** Update the reverse index when a player joins/leaves a game as spectator. */
    public void indexPlayer(UUID player, UUID matchId) {
        playerIndex.put(player, matchId);
    }

    public void unindexPlayer(UUID player) {
        playerIndex.remove(player);
    }

    @Nullable
    public Game byId(UUID matchId) {
        return games.get(matchId);
    }

    /** The game a player is currently part of (competitor or spectator), or null. */
    @Nullable
    public Game byPlayer(UUID player) {
        UUID matchId = playerIndex.get(player);
        return matchId == null ? null : games.get(matchId);
    }

    public boolean isInGame(UUID player) {
        return playerIndex.containsKey(player);
    }

    public Collection<Game> activeGames() {
        return games.values();
    }

    public int activeCount() {
        return games.size();
    }

    /** Count active games of a particular mode (handy for stats / future tournament limits). */
    public long countByMode(GameMode mode) {
        return games.values().stream().filter(g -> g.mode() == mode).count();
    }
}
