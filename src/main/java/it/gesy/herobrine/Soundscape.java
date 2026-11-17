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

    public void play(Player player, String key, Location location) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return;
        ConfigurationSection entry = plugin.getConfig().getConfigurationSection("soundscape.events." + key);
        if (entry == null || !entry.getBoolean("enabled", true)) return;
        String name = entry.getString("sound", "AMBIENT_CAVE");
        try {
            Sound sound = Sound.valueOf(name);
            float volume = (float) Math.max(0, Math.min(4, entry.getDouble("volume", 0.7)));
            float pitch = (float) Math.max(0.5, Math.min(2, entry.getDouble("pitch", 0.75)));
            player.playSound(location, sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid soundscape.events." + key + ".sound: " + name);
        }
    }

    public void playRandomAmbient(Player player) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return;
        List<String> options = plugin.getConfig().getStringList("soundscape.ambient.events");
        if (options.isEmpty()) return;
        String key = options.get(random.nextInt(options.size()));
        Location at = player.getLocation().clone().add(random.nextInt(17)-8, 0, random.nextInt(17)-8);
        play(player, key, at);
    }
}
