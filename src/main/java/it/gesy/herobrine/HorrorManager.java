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
    private final Map<UUID, Integer> huntingStarted = new HashMap<>();
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
        huntingStarted.clear();
    }

    public void onEncounter(Player player, boolean attacking) {
        if (!state.isActive() || !plugin.getConfig().getBoolean("horror.enabled", true)) return;
        // One appearance = one Fear event, even if an ambient sound is played.
        // In stalking and hunting ambient audio only accompanies an appearance.
        addFear(player, 5);
        if (plugin.getConfig().getBoolean("horror.effects.spawn-sound", true)) {
            soundscape.playRandomAmbient(player);
        }

        if (plugin.getConfig().getBoolean("horror.effects.spawn-particles", true))
            player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 12, .6, .7, .6, .01);
    }

    private void tick() {
        ticks++;
        // Hunting ends only after the controller confirms six complete
        // disappearances. Do not time out at 90 seconds.
        if (ticks % 60 == 0) {
            int decay = Math.max(0, plugin.getConfig().getInt("horror.fear.decay-per-minute", 1));
            fear.replaceAll((uuid, amount) -> huntingStarted.containsKey(uuid) ? amount : Math.max(0, amount - decay));
            for (Map.Entry<UUID, Integer> entry : fear.entrySet()) updateHostility(entry.getKey(), entry.getValue());
        }
        if (!state.isActive() || !plugin.getConfig().getBoolean("horror.enabled", true)) return;
        Location anchor = state.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return;
        // Independent ambient loop: never gate sounds behind the hallucination roll.
        int audioInterval = Math.max(1, plugin.getConfig().getInt("soundscape.ambient.interval-seconds", 15));
        int hallucinationInterval = Math.max(1, plugin.getConfig().getInt("horror.hallucinations.interval-seconds", 15));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(anchor.getWorld()) || player.isDead()
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            // Independent ambience/hallucinations only in the pre-stalking phase.
            // No autonomous sounds or Fear increments during stalking/hunting.
            if (getFear(player) >= 30) continue;
            if (ticks % audioInterval == 0
                    && random.nextDouble() * 100 < plugin.getConfig().getDouble("soundscape.ambient.chance-percent", 75.0)
                    && soundscape.playRandomAmbient(player)) {
                addFear(player, plugin.getConfig().getInt("fear.triggers.hear-sound", 5));
            }
            if (ticks % hallucinationInterval == 0) {
                double chance = plugin.getConfig().getDouble("horror.hallucinations.chance-percent", 20.0);
                int level = fear.getOrDefault(player.getUniqueId(), 0);
                if (random.nextDouble() * 100 < Math.min(100, chance + level * .25)) {
                    playHallucination(player);
                }
            }
        }
    }

    private void playHallucination(Player player) {
        Location at = player.getLocation();
        int effect = random.nextInt(4);
        if (effect == 0 && plugin.getConfig().getBoolean("horror.hallucinations.phantom-footsteps", true)) {
            Location nearby = at.clone().add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            if (!soundscape.play(player, "footsteps", nearby)) return;
        } else if (effect == 1 && plugin.getConfig().getBoolean("horror.hallucinations.heartbeat", true)) {
            if (!soundscape.play(player, "heartbeat", at)) return;
        } else if (effect == 2 && plugin.getConfig().getBoolean("horror.hallucinations.breath-effect", true)) {
            player.spawnParticle(Particle.CLOUD, at.clone().add(0, 1.5, 0), 8, .25, .15, .25, .01);
        } else if (effect == 3 && plugin.getConfig().getBoolean("horror.hallucinations.distant-sounds", true)) {
            if (!soundscape.play(player, "distant-breaking", at)) return;
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
        UUID id = player.getUniqueId();
        if (huntingStarted.containsKey(id)) return;
        int before = fear.getOrDefault(id, 0);
        int increment = before < 30 ? 5 : before < 65 ? 2 : 1;
        // Preserve event counts: one event grants one scaled increment,
        // regardless of its legacy +5 configuration.
        int current = Math.min(max, before + (amount <= 0 ? 0 : increment));
        fear.put(id, current);
        if (current >= max && plugin.getConfig().getBoolean("fear.cycling.enabled", true)) huntingStarted.put(id, ticks);
        updateHostility(id, current);
    }
    public int getFear(Player player) { return fear.getOrDefault(player.getUniqueId(), 0); }

    public void setFear(Player player, int level) {
        int max = Math.max(1, plugin.getConfig().getInt("fear.max-level", 100));
        int clamped = Math.max(0, Math.min(max, level));
        UUID id = player.getUniqueId();
        fear.put(id, clamped);
        huntingStarted.remove(id);
        if (clamped >= max && plugin.getConfig().getBoolean("fear.cycling.enabled", true)) huntingStarted.put(id, ticks);
        updateHostility(id, clamped);
    }

    public void finishHunting(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        if (huntingStarted.remove(id) != null) {
            int reset = Math.max(0, plugin.getConfig().getInt("fear.cycling.reset-to", 0));
            fear.put(id, reset);
            updateHostility(id, reset);
        }
    }

    public boolean isHunting(Player player) {
        return player != null && huntingStarted.containsKey(player.getUniqueId())
                && plugin.getConfig().getBoolean("fear.cycling.enabled", true);
    }

    public int huntingElapsedSeconds(Player player) {
        Integer started = player == null ? null : huntingStarted.get(player.getUniqueId());
        return started == null ? -1 : Math.max(0, ticks - started);
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
