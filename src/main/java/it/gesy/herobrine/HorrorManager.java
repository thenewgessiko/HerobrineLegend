package it.gesy.herobrine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Original atmospheric effects implementation, inspired by configurable gameplay concepts. */
public final class HorrorManager {
    private final HerobrineLegendPlugin plugin;
    private final EncounterState state;
    private final Random random = new Random();
    private final Map<UUID, Integer> fear = new HashMap<>();
    private final Set<UUID> hostile = new HashSet<>();
    private BukkitTask task;
    private int ticks;
    private final Soundscape soundscape;

    public HorrorManager(HerobrineLegendPlugin plugin, EncounterState state) {
        this.plugin = plugin;
        this.state = state;
        this.soundscape = new Soundscape(plugin);
    }

    public void start() {
        shutdown();
        ticks = 0;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void refreshConfiguration() { ticks = 0; }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        fear.clear();
        hostile.clear();
    }

    public void onEncounter(Player player, boolean attacking) {
        if (!state.isActive() || !plugin.getConfig().getBoolean("horror.enabled", true)) return;
        addFear(player, plugin.getConfig().getInt("horror.fear.triggers.see-herobrine", 15)
                + (attacking ? plugin.getConfig().getInt("horror.fear.triggers.attack", 20) : 0));
        if (plugin.getConfig().getBoolean("horror.effects.spawn-sound", true))
            soundscape.play(player, "spawn", player.getLocation());
        if (plugin.getConfig().getBoolean("horror.effects.spawn-particles", true))
            player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 12, .6, .7, .6, .01);
    }

    private void tick() {
        ticks++;
        if (ticks % 60 == 0) {
            int decay = Math.max(0, plugin.getConfig().getInt("horror.fear.decay-per-minute", 1));
            fear.replaceAll((uuid, amount) -> Math.max(0, amount - decay));
            for (Map.Entry<UUID, Integer> entry : fear.entrySet()) updateHostility(entry.getKey(), entry.getValue());
        }
        if (!state.isActive() || !plugin.getConfig().getBoolean("horror.enabled", true)) return;
        int interval = Math.max(1, plugin.getConfig().getInt("horror.hallucinations.interval-seconds", 10));
        if (ticks % interval != 0) return;
        Location anchor = state.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(anchor.getWorld()) || player.isDead()) continue;
            double chance = plugin.getConfig().getDouble("horror.hallucinations.chance-percent", 20.0);
            int level = fear.getOrDefault(player.getUniqueId(), 0);
            if (random.nextDouble() * 100 >= Math.min(100, chance + level * .25)) continue;
            playHallucination(player);
            // Atmospheric audio can raise fear before the first NPC appearance.
            if (random.nextDouble() * 100 < plugin.getConfig().getDouble("soundscape.ambient.chance-percent", 20.0)) {
                soundscape.playRandomAmbient(player);
                addFear(player, plugin.getConfig().getInt("fear.triggers.hear-sound", 5));
            }
        }
    }

    private void playHallucination(Player player) {
        Location at = player.getLocation();
        int effect = random.nextInt(4);
        if (effect == 0 && plugin.getConfig().getBoolean("horror.hallucinations.phantom-footsteps", true)) {
            Location nearby = at.clone().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            soundscape.play(player, "footsteps", nearby);
        } else if (effect == 1 && plugin.getConfig().getBoolean("horror.hallucinations.heartbeat", true)) {
            soundscape.play(player, "heartbeat", at);
        } else if (effect == 2 && plugin.getConfig().getBoolean("horror.hallucinations.breath-effect", true)) {
            player.spawnParticle(Particle.CLOUD, at.clone().add(0, 1.5, 0), 8, .25, .15, .25, .01);
        } else if (effect == 3 && plugin.getConfig().getBoolean("horror.hallucinations.distant-sounds", true)) {
            soundscape.play(player, "distant-breaking", at.clone().add(6, 0, 6));
        } else return;
        if (effect != 2) addFear(player, plugin.getConfig().getInt("fear.triggers.hear-sound", 5));
        int level = fear.getOrDefault(player.getUniqueId(), 0);
        if (level >= plugin.getConfig().getInt("horror.fear.blindness-threshold", 70)
                && plugin.getConfig().getBoolean("horror.fear.brief-blindness", true)
                && random.nextDouble() < .15) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, true, false, false));
        }
    }

    private void addFear(Player player, int amount) {
        if (!plugin.getConfig().getBoolean("horror.fear.enabled", true)) return;
        int max = Math.max(1, plugin.getConfig().getInt("horror.fear.max-level", 100));
        int current = fear.merge(player.getUniqueId(), Math.max(0, amount), (a,b) -> Math.min(max, a+b));
        updateHostility(player.getUniqueId(), current);
    }
    public int getFear(Player player) { return fear.getOrDefault(player.getUniqueId(), 0); }

    public void setFear(Player player, int level) {
        int max = Math.max(1, plugin.getConfig().getInt("fear.max-level", 100));
        int clamped = Math.max(0, Math.min(max, level));
        fear.put(player.getUniqueId(), clamped);
        updateHostility(player.getUniqueId(), clamped);
    }

    public boolean isHostile(Player player) {
        return player != null && hostile.contains(player.getUniqueId());
    }

    private void updateHostility(UUID id, int level) {
        int start = plugin.getConfig().getInt("combat.fear.activate-at", 100);
        int stop = plugin.getConfig().getInt("combat.fear.deactivate-below", 70);
        if (level >= start) hostile.add(id);
        else if (level < stop) hostile.remove(id);
    }

}
