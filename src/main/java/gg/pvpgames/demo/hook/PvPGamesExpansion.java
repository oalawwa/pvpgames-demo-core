package gg.pvpgames.demo.hook;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.stats.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes player stats to PlaceholderAPI so TAB, DeluxeMenus, holograms, and any other PAPI-aware
 * plugin can render them. Identifier: {@code pvpgames}. Examples:
 *
 * <pre>
 *   %pvpgames_elo%      %pvpgames_wins%     %pvpgames_losses%
 *   %pvpgames_kills%    %pvpgames_deaths%   %pvpgames_kdr%
 *   %pvpgames_streak%   %pvpgames_best_streak%   %pvpgames_games%
 *   %pvpgames_winrate%  %pvpgames_damage%
 * </pre>
 *
 * <p>This class is only loaded/registered when PlaceholderAPI is installed (see PvPGamesDemoCore),
 * so referencing PAPI types here is safe.
 */
public final class PvPGamesExpansion extends PlaceholderExpansion {

    private final PvPGamesDemoCore plugin;

    public PvPGamesExpansion(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pvpgames";
    }

    @Override
    public @NotNull String getAuthor() {
        return "omar";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    /** Keep the expansion registered across reloads. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        PlayerProfile profile = plugin.profiles().get(player.getUniqueId());
        if (profile == null) {
            return ""; // player not online / not loaded
        }
        PlayerStats s = profile.stats();
        return switch (params.toLowerCase()) {
            case "elo" -> String.valueOf(s.elo());
            case "wins" -> String.valueOf(s.wins());
            case "losses" -> String.valueOf(s.losses());
            case "kills" -> String.valueOf(s.kills());
            case "deaths" -> String.valueOf(s.deaths());
            case "kdr" -> String.format("%.2f", s.kdr());
            case "streak" -> String.valueOf(s.currentStreak());
            case "best_streak" -> String.valueOf(s.bestStreak());
            case "games" -> String.valueOf(s.gamesPlayed());
            case "winrate" -> String.format("%.1f", s.winRate());
            case "damage" -> String.format("%.1f", s.damageDealt());
            case "state" -> profile.state().name();
            default -> null; // unknown placeholder
        };
    }
}
