package it.gesy.herobrine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Persists the summoned state and the ritual anchor across server restarts. */
public final class EncounterState {
    private final HerobrineLegendPlugin plugin;
    private final File file;
    private boolean active;
    private UUID worldId;
    private int x, y, z;

    public EncounterState(HerobrineLegendPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "state.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        active = yaml.getBoolean("active", false);
        String world = yaml.getString("ritual.world");
        if (world != null) {
            try { worldId = UUID.fromString(world); } catch (IllegalArgumentException ignored) { worldId = null; }
        }
        x = yaml.getInt("ritual.x"); y = yaml.getInt("ritual.y"); z = yaml.getInt("ritual.z");
    }

    public void activate(Location anchor) {
        active = true;
        worldId = anchor.getWorld().getUID();
        x = anchor.getBlockX(); y = anchor.getBlockY(); z = anchor.getBlockZ();
        save();
    }

    public void deactivate() {
        active = false;
        save();
    }

    public boolean isActive() { return active; }

    public Location getAnchor() {
        if (worldId == null) return null;
        World world = Bukkit.getWorld(worldId);
        return world == null ? null : new Location(world, x, y, z);
    }

    public void save() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Impossibile creare la cartella dati del plugin.");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", active);
        if (worldId != null) yaml.set("ritual.world", worldId.toString());
        yaml.set("ritual.x", x); yaml.set("ritual.y", y); yaml.set("ritual.z", z);
        try { yaml.save(file); }
        catch (IOException exception) { plugin.getLogger().severe("Impossibile salvare state.yml: " + exception.getMessage()); }
    }
}
