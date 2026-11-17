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
    private boolean nextLowFearSoundIsEntity = false;
    public Soundscape(HerobrineLegendPlugin plugin) { this.plugin = plugin; }

    public boolean play(Player player, String key, Location location) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return false;
        ConfigurationSection entry = plugin.getConfig().getConfigurationSection("soundscape.events." + key);
        if (entry == null || !entry.getBoolean("enabled", true)) return false;
        String name = entry.getString("sound", "AMBIENT_CAVE");
        try {
            Sound sound = Sound.valueOf(name);
            // Vary related sounds from the same creature, preserving the
            // configured sound as the fallback.
            if (entry.getBoolean("randomize-entity-variants", true)) {
                String[] variants = switch (key) {
                    case "phantom" -> new String[]{"ENTITY_PHANTOM_AMBIENT", "ENTITY_PHANTOM_FLAP", "ENTITY_PHANTOM_SWOOP"};
                    case "ghast" -> new String[]{"ENTITY_GHAST_AMBIENT"};
                    // ENTITY_ENDERMAN_STARE is reserved for eye-triggered NPC vanish.
                    case "enderman-stare" -> new String[]{"ENTITY_ENDERMAN_AMBIENT", "ENTITY_ENDERMAN_SCREAM"};
                    case "heartbeat" -> new String[]{"ENTITY_WARDEN_HEARTBEAT", "ENTITY_WARDEN_AMBIENT", "ENTITY_WARDEN_NEARBY_CLOSE"};
                    case "warden" -> new String[]{"ENTITY_WARDEN_HEARTBEAT", "ENTITY_WARDEN_AMBIENT", "ENTITY_WARDEN_NEARBY_CLOSE"};
                    default -> new String[0];
                };
                if (variants.length > 0) {
                    try { sound = Sound.valueOf(variants[random.nextInt(variants.length)]); }
                    catch (IllegalArgumentException ignored) { /* use configured sound */ }
                }
            }
            float volume = (float) Math.max(0, Math.min(4, entry.getDouble("volume", 0.7)));
            // Low, subtly varied pitch to avoid identical repetitions.
            double basePitch = entry.getDouble("pitch", 0.65);
            float pitch = (float) Math.max(0.5, Math.min(0.90,
                    basePitch + (random.nextDouble() - 0.5) * 0.16));
            player.playSound(location, sound, volume, pitch);
            // A heartbeat has two closely spaced beats (lub-dub).
            // The second beat is purely acoustic: it must not count as
            // another event, increase Fear, or alter any encounter timer.
            if (sound == Sound.ENTITY_WARDEN_HEARTBEAT) {
                Location beatAt = location.clone();
                float secondVolume = volume * 0.85f;
                float secondPitch = Math.max(0.5f, pitch * 0.94f);
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.playSound(beatAt, Sound.ENTITY_WARDEN_HEARTBEAT,
                                secondVolume, secondPitch);
                    }
                }, 3L);
            }
            return true;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid soundscape.events." + key + ".sound: " + name);
            return false;
        }
    }

    /** Fear 0-29 only: alternate eerie and creature sounds on successful events. */
    public boolean playLowFearAmbient(Player player) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return false;
        boolean entityFirst = nextLowFearSoundIsEntity;
        if (playFromPool(player, entityFirst ? "soundscape.low-fear.entity-events" : "soundscape.appearance.eerie-events",
                entityFirst ? List.of("ghast", "phantom", "warden", "heartbeat") : List.of("cave", "spawn", "distant-breaking"))) {
            nextLowFearSoundIsEntity = !entityFirst;
            return true;
        }
        return false;
    }

    /** Fear 30-100: cave ambience only; ignore stale custom appearance pools. */
    public boolean playAppearanceEerie(Player player) {
        return play(player, "cave", player.getLocation());
    }

    private boolean playFromPool(Player player, String path, List<String> defaults) {
        if (!plugin.getConfig().getBoolean("soundscape.enabled", true)) return false;
        List<String> options = plugin.getConfig().getStringList(path);
        if (options.isEmpty()) options = defaults;
        if (options.isEmpty()) return false;
        String key = options.get(random.nextInt(options.size()));
        return play(player, key, player.getLocation());
    }

    /** Legacy entry point: only the pre-stalking Fear loop may use it. */
    public boolean playRandomAmbient(Player player) {
        return playLowFearAmbient(player);
    }
}
