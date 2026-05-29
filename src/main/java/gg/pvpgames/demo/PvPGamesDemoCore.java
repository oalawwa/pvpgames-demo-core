package gg.pvpgames.demo;

import gg.pvpgames.demo.arena.ArenaManager;
import gg.pvpgames.demo.commands.DuelCommand;
import gg.pvpgames.demo.commands.LeaderboardCommand;
import gg.pvpgames.demo.commands.LeaveCommand;
import gg.pvpgames.demo.commands.QueueMenu;
import gg.pvpgames.demo.commands.SpectateCommand;
import gg.pvpgames.demo.commands.StatsCommand;
import gg.pvpgames.demo.commands.admin.ArenaCommand;
import gg.pvpgames.demo.commands.admin.PvPCommand;
import gg.pvpgames.demo.config.ConfigManager;
import gg.pvpgames.demo.config.Messages;
import gg.pvpgames.demo.data.DataStore;
import gg.pvpgames.demo.data.StorageProvider;
import gg.pvpgames.demo.game.GameManager;
import gg.pvpgames.demo.hologram.HologramManager;
import gg.pvpgames.demo.hook.PvPGamesExpansion;
import gg.pvpgames.demo.kit.KitManager;
import gg.pvpgames.demo.leaderboard.LeaderboardManager;
import gg.pvpgames.demo.listeners.CombatListener;
import gg.pvpgames.demo.listeners.ConnectionListener;
import gg.pvpgames.demo.listeners.LobbyProtectionListener;
import gg.pvpgames.demo.match.MatchController;
import gg.pvpgames.demo.match.MatchVisuals;
import gg.pvpgames.demo.core.LobbyManager;
import gg.pvpgames.demo.profile.PlayerProfileManager;
import gg.pvpgames.demo.queue.QueueManager;
import gg.pvpgames.demo.scoreboard.ScoreboardManager;
import gg.pvpgames.demo.spectator.SpectatorManager;
import gg.pvpgames.demo.stats.StatsManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entry point and service locator. It builds every manager in the correct order on enable,
 * wires commands and listeners, and tears everything down cleanly on disable.
 *
 * <p>The managers are exposed via short accessor methods (e.g. {@link #queues()},
 * {@link #stats()}) so the rest of the codebase reads naturally and there's a single, obvious
 * place to find any subsystem. This is the classic "core plugin holds the managers" pattern used
 * across production Minecraft networks.
 */
public final class PvPGamesDemoCore extends JavaPlugin {

    // ---- core services ----
    private ConfigManager configManager;
    private Messages messages;
    private DataStore dataStore;

    // ---- player + stats ----
    private PlayerProfileManager profileManager;
    private StatsManager statsManager;

    // ---- gameplay framework ----
    private GameManager gameManager;
    private QueueManager queueManager;
    private ArenaManager arenaManager;
    private KitManager kitManager;
    private MatchController matchController;
    private MatchVisuals matchVisuals;

    // ---- presentation + world ----
    private LobbyManager lobbyManager;
    private ScoreboardManager scoreboardManager;
    private LeaderboardManager leaderboardManager;
    private SpectatorManager spectatorManager;
    private HologramManager hologramManager;
    private QueueMenu queueMenu;

    private boolean placeholderApiHooked;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();

        // 1) Config first — everything else reads from it.
        this.configManager = new ConfigManager(this);
        configManager.loadAll();
        this.messages = new Messages(configManager);

        // 2) Storage (MySQL with SQLite fallback). Fatal if it can't initialize.
        try {
            this.dataStore = StorageProvider.create(this);
        } catch (Exception e) {
            getLogger().severe("Could not initialize storage: " + e.getMessage());
            getLogger().severe("Disabling plugin to avoid data loss.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 3) Player + stats services.
        this.profileManager = new PlayerProfileManager(this);
        this.statsManager = new StatsManager(this, dataStore);

        // 4) Gameplay framework managers.
        this.kitManager = new KitManager(this);
        kitManager.load();
        this.arenaManager = new ArenaManager(this);
        arenaManager.load();
        this.gameManager = new GameManager(this);
        this.matchVisuals = new MatchVisuals(this);
        this.matchController = new MatchController(this);
        this.queueManager = new QueueManager(this);

        // 5) Presentation + world.
        this.lobbyManager = new LobbyManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.spectatorManager = new SpectatorManager(this);
        this.hologramManager = new HologramManager(this);
        hologramManager.init();
        this.leaderboardManager = new LeaderboardManager(this);

        // 6) Start the repeating tasks.
        gameManager.start();
        queueManager.start();
        scoreboardManager.start();
        leaderboardManager.start();

        // 7) Register listeners + commands.
        this.queueMenu = new QueueMenu(this);
        registerListeners(
                new ConnectionListener(this),
                new CombatListener(this),
                new LobbyProtectionListener(this),
                queueMenu);
        registerCommands();

        // 8) Optional integrations.
        hookPlaceholderApi();

        // 9) Load any players already online (e.g. on /reload).
        getServer().getOnlinePlayers().forEach(p ->
                statsManager.loadAsync(p.getUniqueId(), p.getName(), stats -> {
                    profileManager.cache(p.getUniqueId(), stats);
                    lobbyManager.sendToLobby(p);
                }));

        getLogger().info("PvPGames Demo Core enabled in " + (System.currentTimeMillis() - start) + "ms.");
        getLogger().info("Storage: " + dataStore.name() + " | Kits: " + kitManager.all().size()
                + " | Arenas: " + arenaManager.count());
    }

    @Override
    public void onDisable() {
        // Stop tasks first so nothing mutates state during shutdown.
        if (queueManager != null) queueManager.stop();
        if (gameManager != null) gameManager.stop();
        if (scoreboardManager != null) scoreboardManager.stop();
        if (leaderboardManager != null) leaderboardManager.stop();

        // Persist all online players' stats synchronously (we're shutting down).
        if (statsManager != null && profileManager != null) {
            statsManager.saveAllProfiles(profileManager.all());
        }
        if (dataStore != null) {
            dataStore.close();
        }
        getLogger().info("PvPGames Demo Core disabled.");
    }

    /** Full reload triggered by /pvp reload: re-read configs and reload kits/arenas. */
    public void reloadEverything() {
        configManager.loadAll();
        kitManager.load();
        arenaManager.load();
        leaderboardManager.refreshNow();
    }

    private void registerListeners(Listener... listeners) {
        for (Listener l : listeners) {
            getServer().getPluginManager().registerEvents(l, this);
        }
    }

    private void registerCommands() {
        bind("pvp", new PvPCommand(this));
        bind("arena", new ArenaCommand(this));
        bind("duel", new DuelCommand(this));
        bind("stats", new StatsCommand(this));
        bind("leaderboard", new LeaderboardCommand(this));
        bind("leave", new LeaveCommand(this));
        bind("spectate", new SpectateCommand(this));
    }

    private void bind(String name, Object executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' missing from plugin.yml — skipping.");
            return;
        }
        command.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            command.setTabCompleter(tc);
        }
    }

    private void hookPlaceholderApi() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new PvPGamesExpansion(this).register();
                placeholderApiHooked = true;
                getLogger().info("Registered PlaceholderAPI expansion 'pvpgames'.");
            } catch (Throwable t) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
            }
        }
    }

    // ====================================================================
    //  Accessors (service locator)
    // ====================================================================

    public ConfigManager configs() {
        return configManager;
    }

    public Messages messages() {
        return messages;
    }

    public DataStore dataStore() {
        return dataStore;
    }

    public PlayerProfileManager profiles() {
        return profileManager;
    }

    public StatsManager stats() {
        return statsManager;
    }

    public GameManager gameManager() {
        return gameManager;
    }

    public QueueManager queues() {
        return queueManager;
    }

    public ArenaManager arenas() {
        return arenaManager;
    }

    public KitManager kits() {
        return kitManager;
    }

    public MatchController matchController() {
        return matchController;
    }

    public MatchVisuals visuals() {
        return matchVisuals;
    }

    public LobbyManager lobby() {
        return lobbyManager;
    }

    public ScoreboardManager scoreboards() {
        return scoreboardManager;
    }

    public LeaderboardManager leaderboards() {
        return leaderboardManager;
    }

    public SpectatorManager spectators() {
        return spectatorManager;
    }

    public HologramManager holograms() {
        return hologramManager;
    }

    public QueueMenu queueMenu() {
        return queueMenu;
    }

    public boolean placeholderApiHooked() {
        return placeholderApiHooked;
    }
}
