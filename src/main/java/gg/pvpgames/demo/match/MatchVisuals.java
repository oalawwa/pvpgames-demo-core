package gg.pvpgames.demo.match;

import gg.pvpgames.demo.PvPGamesDemoCore;
import gg.pvpgames.demo.util.Placeholders;
import gg.pvpgames.demo.util.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Small helper that bundles the polished PvP feedback — titles, action bars, and sounds — so the
 * match code reads cleanly. Everything pulls text from messages.yml and sound names from config,
 * keeping the esports presentation fully configurable.
 */
public final class MatchVisuals {

    private final PvPGamesDemoCore plugin;

    public MatchVisuals(PvPGamesDemoCore plugin) {
        this.plugin = plugin;
    }

    /** Big title used for the countdown number, plus a click/tick sound. */
    public void countdownTitle(Player player, int seconds, String mode, String kit) {
        Placeholders ph = Placeholders.of("seconds", seconds).set("mode", mode).set("kit", kit);
        Component title = plugin.messages().component("match.countdown-title", ph);
        Component sub = plugin.messages().component("match.countdown-subtitle", ph);
        player.showTitle(Title.title(title, sub, fast()));
        Sounds.play(player, plugin.getConfig().getString("sounds.countdown-tick"), 1f,
                1.0f + (0.1f * (3 - Math.min(seconds, 3)))); // pitch rises near zero
    }

    /** "FIGHT!" title at go-time, plus the match-start sound. */
    public void goTitle(Player player, String opponent) {
        Placeholders ph = Placeholders.of("opponent", opponent);
        player.showTitle(Title.title(
                plugin.messages().component("match.go-title", ph),
                plugin.messages().component("match.go-subtitle", ph),
                fast()));
        Sounds.play(player, plugin.getConfig().getString("sounds.match-start"));
    }

    public void actionBar(Player player, String messageKey, Placeholders ph) {
        player.sendActionBar(plugin.messages().component(messageKey, ph));
    }

    public void winSound(Player player) {
        Sounds.play(player, plugin.getConfig().getString("sounds.win"));
    }

    public void loseSound(Player player) {
        Sounds.play(player, plugin.getConfig().getString("sounds.lose"));
    }

    private Title.Times fast() {
        return Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(800), Duration.ofMillis(200));
    }
}
