package gg.pvpgames.demo.listeners;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.api.Game;
import gg.pvpgames.demo.match.DuelGame;
import gg.pvpgames.demo.profile.PlayerProfile;
import gg.pvpgames.demo.profile.PlayerState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.Listener;

/**
 * All combat-related rules:
 * <ul>
 *   <li>Track damage dealt (for stats) during a live match.</li>
 *   <li>Convert a "death" into our match win/lose flow without an actual death screen.</li>
 *   <li>Block damage in the lobby, during countdown, and to/for spectators.</li>
 *   <li>Prevent dropping items mid-match and respawn players straight into the lobby.</li>
 * </ul>
 */
public final class CombatListener implements Listener {

    private final PvPGamesDemoCore plugin;

    public CombatListener(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Gate all damage. We cancel it unless both parties are in the SAME live duel. We also record
     * damage dealt for stats here (after the event resolves the final amount).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        PlayerProfile vp = plugin.profiles().get(victim.getUniqueId());
        if (vp == null) {
            return;
        }
        // Spectators never take damage.
        if (vp.state() == PlayerState.SPECTATING) {
            event.setCancelled(true);
            return;
        }
        // In lobby or queue: no damage at all.
        if (vp.state() == PlayerState.LOBBY || vp.state() == PlayerState.QUEUE) {
            event.setCancelled(true);
            return;
        }
        // In a match: only allow damage while LIVE.
        Game game = plugin.gameManager().byPlayer(victim.getUniqueId());
        if (game instanceof DuelGame duel && !duel.isLive()) {
            event.setCancelled(true);
        }
    }

    /** Specifically handle PvP hits to validate same-match and record damage dealt. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        Game vGame = plugin.gameManager().byPlayer(victim.getUniqueId());
        Game aGame = plugin.gameManager().byPlayer(attacker.getUniqueId());

        // Both must be in the same live duel, and neither a spectator.
        if (vGame == null || vGame != aGame || !(vGame instanceof DuelGame duel) || !duel.isLive()
                || duel.spectators().contains(attacker.getUniqueId())
                || duel.spectators().contains(victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Record damage dealt for the attacker's stats (use final damage after armor/effects).
        PlayerProfile ap = plugin.profiles().get(attacker.getUniqueId());
        if (ap != null) {
            ap.stats().addDamageDealt(event.getFinalDamage());
        }
    }

    /**
     * Intercept lethal damage so we never show the vanilla death screen. We treat the victim as
     * "defeated", restore them, and let the duel decide the winner.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Game game = plugin.gameManager().byPlayer(victim.getUniqueId());
        if (!(game instanceof DuelGame duel) || !duel.isLive() || !duel.isPlayer(victim.getUniqueId())) {
            return;
        }
        double remaining = victim.getHealth() - event.getFinalDamage();
        if (remaining > 0.0) {
            return; // not lethal, let it through
        }
        // Lethal: cancel the death, record a kill/death, and end the round.
        event.setCancelled(true);
        victim.setHealth(getMaxHealth(victim));

        // Record kill on the opponent and death on the victim.
        java.util.UUID otherId = duel.players().stream()
                .filter(id -> !id.equals(victim.getUniqueId()))
                .findFirst().orElse(null);
        if (otherId != null) {
            PlayerProfile op = plugin.profiles().get(otherId);
            if (op != null) {
                op.stats().addKill();
            }
        }
        PlayerProfile vp = plugin.profiles().get(victim.getUniqueId());
        if (vp != null) {
            vp.stats().addDeath();
        }
        duel.onPlayerDeath(victim);
    }

    /** Safety net: if a real PlayerDeathEvent slips through, route it into the duel and clear drops. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Game game = plugin.gameManager().byPlayer(victim.getUniqueId());
        if (game instanceof DuelGame duel && duel.isPlayer(victim.getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            duel.onPlayerDeath(victim);
        }
    }

    /** Respawn anyone who somehow died straight back to the lobby. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var spawn = plugin.lobby().spawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    /** Block item drops unless the player is in a live match (no littering the hub). */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        PlayerProfile profile = plugin.profiles().get(event.getPlayer().getUniqueId());
        if (profile == null || profile.state() != PlayerState.IN_MATCH) {
            event.setCancelled(true);
        }
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            return p;
        }
        // Arrows etc.: trace back to the shooter if it's a player.
        if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private double getMaxHealth(Player player) {
        var attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getValue() : 20.0;
    }
}
