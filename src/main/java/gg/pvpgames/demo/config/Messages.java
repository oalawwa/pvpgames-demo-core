package gg.pvpgames.demo.config;

import gg.pvpgames.demo.util.Placeholders;
import gg.pvpgames.demo.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Thin facade over messages.yml. Resolves a key, applies placeholders, prepends the prefix
 * (optional), and renders to an Adventure {@link Component}. Keeps message lookups one-liners
 * everywhere else in the codebase.
 */
public final class Messages {

    private final ConfigManager configs;

    public Messages(ConfigManager configs) {
        this.configs = configs;
    }

    private FileConfiguration src() {
        return configs.messages();
    }

    public String prefixRaw() {
        return src().getString("prefix", "");
    }

    /** Raw string for a key (no parsing), falling back to the key itself if missing. */
    public String raw(String key) {
        return src().getString(key, key);
    }

    public List<String> rawList(String key) {
        return src().getStringList(key);
    }

    /** Component for a key with placeholders, WITHOUT the prefix. */
    public Component component(String key, Placeholders ph) {
        return Text.parse(raw(key), ph);
    }

    public Component component(String key) {
        return Text.parse(raw(key));
    }

    /** Send a prefixed message to a sender. */
    public void send(CommandSender to, String key, Placeholders ph) {
        String body = ph == null ? raw(key) : ph.apply(raw(key));
        to.sendMessage(Text.parse(prefixRaw() + body));
    }

    public void send(CommandSender to, String key) {
        send(to, key, null);
    }

    /** Send WITHOUT the prefix (for multi-line panels like results / stats). */
    public void sendRaw(CommandSender to, String key, Placeholders ph) {
        to.sendMessage(component(key, ph));
    }
}
