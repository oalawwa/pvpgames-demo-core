package gg.pvpgames.demo.profile;

import gg.pvpgames.demo.stats.PlayerStats;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-player runtime container. There is exactly one of these per online player, created on join
 * and removed on quit. It bundles:
 * <ul>
 *   <li>persistent {@link PlayerStats} (loaded from the {@code DataStore})</li>
 *   <li>transient session state — which {@link PlayerState} they're in, and the id of the match
 *       or queue they belong to</li>
 * </ul>
 *
 * <p>Keeping this separate from {@link PlayerStats} cleanly divides "what we save to the database"
 * from "what only matters while the player is online".
 */
public final class PlayerProfile {

    private final UUID uuid;
    private PlayerStats stats;

    private PlayerState state = PlayerState.LOBBY;

    /** Id of the match this player is currently in (playing or spectating), or null. */
    @Nullable
    private UUID currentMatchId;

    /** Whether the player is a spectator (vs a competitor) in {@link #currentMatchId}. */
    private boolean spectating;

    /** The kit the player last selected in the menu; used to pre-fill requeue. */
    private String selectedKit = "NoDebuff";

    /** Whether the player's last/selected queue was ranked. */
    private boolean rankedSelected = true;

    public PlayerProfile(UUID uuid, PlayerStats stats) {
        this.uuid = uuid;
        this.stats = stats;
    }

    public UUID uuid() {
        return uuid;
    }

    @Nullable
    public Player player() {
        return org.bukkit.Bukkit.getPlayer(uuid);
    }

    public PlayerStats stats() {
        return stats;
    }

    public void stats(PlayerStats stats) {
        this.stats = stats;
    }

    public PlayerState state() {
        return state;
    }

    public void state(PlayerState state) {
        this.state = state;
    }

    @Nullable
    public UUID currentMatchId() {
        return currentMatchId;
    }

    public void currentMatchId(@Nullable UUID id) {
        this.currentMatchId = id;
    }

    public boolean spectating() {
        return spectating;
    }

    public void spectating(boolean spectating) {
        this.spectating = spectating;
    }

    public String selectedKit() {
        return selectedKit;
    }

    public void selectedKit(String kit) {
        this.selectedKit = kit;
    }

    public boolean rankedSelected() {
        return rankedSelected;
    }

    public void rankedSelected(boolean ranked) {
        this.rankedSelected = ranked;
    }

    public boolean inMatch() {
        return state == PlayerState.IN_MATCH;
    }

    public boolean inQueue() {
        return state == PlayerState.QUEUE;
    }
}
