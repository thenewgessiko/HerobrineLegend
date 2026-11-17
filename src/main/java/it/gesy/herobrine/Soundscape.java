package it.gesy.herobrine;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Random;

/** Configurable, player-local sound effects. Never changes weather or time. */
public final class Soundscape {
    private final HerobrineLegendPlugin plugin;
    private final Random random = new Random();
    public Soundscape(HerobrineLegendPlugin plugin) { this.plugin = plugin; }

    public boolean play(Player player, String key, Location location) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return false;
        ConfigurationSection entry = plugin.getConfig().getConfigurationSection("soundscape.events." + key);
        if (entry == null || !entry.getBoolean("enabled", true)) return false;
        String name = entry.getString("sound", "AMBIENT_CAVE");
        try {
            Sound sound = Sound.valueOf(name);
            float volume = (float) Math.max(0, Math.min(4, entry.getDouble("volume", 0.7)));
            float pitch = (float) Math.max(0.5, Math.min(2, entry.getDouble("pitch", 0.75)));
            player.playSound(location, sound, volume, pitch);
            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid soundscape.events." + key + ".sound: " + name);
            return false;
        }
    }

    public boolean playRandomAmbient(Player player) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return false;
        List<String> options = plugin.getConfig().getStringList("soundscape.ambient.events");
        if (options.isEmpty()) return false;
        String key = options.get(random.nextInt(options.size()));
        // Play at the listener position so distant audio cannot become inaudible.
        Location at = player.getLocation();
        return play(player, key, at);
    }
}
