package gg.pvpgames.demo.spectator;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import gg.pvpgames.demo.util.Placeholders;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Handles putting players into and out of spectator mode for a match. Spectators are set to
 * vanilla SPECTATOR gamemode (they can fly through the arena and can't be hit), teleported to the
 * action, and tracked on the {@link Game} so cleanup returns them to the lobby.
 *
 * <p>Eliminated duel players become spectators automatically so they can watch the rest of a
 * round; players can also {@code /spectate <player>} an ongoing match.
 */
public final class SpectatorManager {

    private final PvPGamesDemoCore plugin;

    public SpectatorManager(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Make {@code spectator} watch {@code game}. Records state, switches gamemode, teleports them
     * near a competitor, and notifies the match.
     */
    public boolean spectate(Player spectator, Game game) {
        PlayerProfile profile = plugin.profiles().get(spectator);
        if (profile == null) {
            return false;
        }
        // Leave any queue first.
        if (profile.state() == PlayerState.QUEUE) {
            plugin.queues().leave(spectator.getUniqueId());
        }

        profile.state(PlayerState.SPECTATING);
        profile.currentMatchId(game.id());
        profile.spectating(true);

        spectator.setGameMode(GameMode.SPECTATOR);
        // Teleport to the first competitor we can find.
        game.players().stream()
                .map(plugin.getServer()::getPlayer)
                .filter(p -> p != null)
                .findFirst()
                .ifPresent(target -> spectator.teleport(target.getLocation()));

        game.addSpectator(spectator);
        plugin.gameManager().indexPlayer(spectator.getUniqueId(), game.id());

        plugin.messages().send(spectator, "spectator.now-spectating",
                Placeholders.of("player", firstPlayerName(game)));
        plugin.scoreboards().clear(spectator);
        return true;
    }

    /** Stop spectating and return to the lobby. */
    public void stopSpectating(Player spectator) {
        PlayerProfile profile = plugin.profiles().get(spectator);
        if (profile != null && profile.spectating()) {
            Game game = plugin.gameManager().byPlayer(spectator.getUniqueId());
            if (game != null) {
                game.removeSpectator(spectator.getUniqueId());
            }
            plugin.gameManager().unindexPlayer(spectator.getUniqueId());
            profile.spectating(false);
        }
        plugin.lobby().sendToLobby(spectator);
    }

    private String firstPlayerName(Game game) {
        return game.players().stream()
                .map(plugin.getServer()::getPlayer)
                .filter(p -> p != null)
                .map(Player::getName)
                .findFirst()
                .orElse("?");
    }
}
