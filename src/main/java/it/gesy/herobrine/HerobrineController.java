package it.gesy.herobrine;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.npc.NPC;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemSwingAnimation;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.LinearVecDelta;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSwingAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Scoreboard;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Drives the client-side player NPC and the first stalking/attack behaviour. */
public final class HerobrineController implements Listener {
    private enum Mode { NONE, STALKING, ATTACK_WINDUP, ATTACKING }

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_500_000_000);
    private static final int ATTACK_DURATION_TICKS = 5 * 20;
    private final HerobrineLegendPlugin plugin;
    private final EncounterState state;
    private HorrorManager horrorManager;

    public void setHorrorManager(HorrorManager manager) { this.horrorManager = manager; }
    private final Random random = new Random();
    private BukkitTask task;
    private NPC npc;
    private Player target;
    private Location npcLocation;
    private Mode mode = Mode.NONE;
    private int modeTicks;
    private int modeDurationTicks;
    private int nextAttemptTicks;
    private int attackCooldownTicks;
    private boolean forcedAttack;
    private int stalkingContactCooldown;
    private String npcProfileName;
    private String npcTeamName;
    private int jumpTicksRemaining;
    private double jumpStartY;
    private double jumpEndY;

    public HerobrineController(HerobrineLegendPlugin plugin, EncounterState state) {
        this.plugin = plugin;
        this.state = state;
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        despawn();
    }

    /** Called by the ritual manager so deactivation is visible immediately. */
    public void deactivateImmediately() {
        despawn();
        nextAttemptTicks = 0;
    }

    /** Re-applies settings after /herobrinelegend reload. */
    public void refreshConfiguration() {
        despawn();
        nextAttemptTicks = 0;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (npc == null) return;
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(event.getPlayer());
        if (channel != null) npc.despawn(channel);
        if (target != null && target.getUniqueId().equals(event.getPlayer().getUniqueId())) {
            despawn();
            nextAttemptTicks = 20;
        }
    }

    /** Forces a five-second attack regardless of fear, but only while the altar is active. */
    public boolean forceAttack(Player player) {
        if (!state.isActive() || player == null || !player.isOnline() || player.isDead()
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        Location spawn = findSpawnLocation(player);
        if (spawn == null) return false;
        despawn();
        target = player;
        npcLocation = spawn;
        mode = Mode.ATTACK_WINDUP;
        forcedAttack = true;
        modeTicks = 0;
        modeDurationTicks = ATTACK_DURATION_TICKS;
        attackCooldownTicks = 0;
        createNpc(spawn);
        if (npc == null) {
            despawn();
            return false;
        }
        updateViewers();
        plugin.getLogger().info("Forced Herobrine attack on " + player.getName());
        return true;
    }

    /** Forced visual encounter; requires an active altar and does not modify fear. */
    public boolean forceJumpscare(Player player) {
        if (!state.isActive() || player == null || !player.isOnline() || player.isDead()
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        Location spawn = findSpawnLocation(player);
        if (spawn == null) return false;
        despawn();
        target = player;
        npcLocation = spawn;
        mode = Mode.STALKING;
        forcedAttack = false;
        modeTicks = 0;
        modeDurationTicks = 100;
        createNpc(spawn);
        if (npc == null) { despawn(); return false; }
        updateViewers();
        plugin.getLogger().info("Forced Herobrine jumpscare on " + player.getName());
        return true;
    }

    private void tick() {
        if (!state.isActive()) {
            if (npc != null) despawn();
            nextAttemptTicks = 0;
            return;
        }

        if (npc == null) {
            if (nextAttemptTicks-- <= 0) {
                nextAttemptTicks = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 40));
                tryStartEncounter();
            }
            return;
        }

        if (target == null || !target.isOnline() || target.isDead()
                || !target.getWorld().equals(npcLocation.getWorld())) {
            despawn();
            nextAttemptTicks = 20;
            return;
        }

        updateViewers();
        modeTicks++;
        if (stalkingContactCooldown > 0) stalkingContactCooldown--;

        if (mode == Mode.STALKING) {
            faceTarget();
            emitPresenceParticles();
            if (npcLocation.distanceSquared(target.getLocation()) <= 2.25 && stalkingContactCooldown == 0) {
                stalkingContactCooldown = 60;
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, true));
                target.getWorld().strikeLightningEffect(target.getLocation());
                target.setHealth(Math.max(0.0, target.getHealth() - 1.0)); // exactly half a heart, regardless of armor
                despawn(false);
                nextAttemptTicks = 40;
                return;
            }
            if (plugin.getConfig().getBoolean("herobrine.stalking.vanish-when-looked-at", true)
                    && npcLocation.distanceSquared(target.getLocation()) > 4.0
                    && isTargetLookingAtHerobrine()) {
                despawn();
                nextAttemptTicks = 40;
                return;
            }
            // Stalking ends only on player gaze, altar deactivation, or invalid target.
            return;
        }

        if (mode == Mode.ATTACK_WINDUP) {
            faceTarget();
            emitPresenceParticles();
            if (modeTicks >= 20) {
                mode = Mode.ATTACKING;
                modeTicks = 0;
            }
            return;
        }

        if (mode == Mode.ATTACKING) {
            if (!forcedAttack && (horrorManager == null || !horrorManager.isHostile(target))) {
                mode = Mode.STALKING;
                modeTicks = 0;
                modeDurationTicks = randomBetween(100, 160);
                return;
            }
            faceTarget();
            chaseTarget();
            if (modeTicks >= Math.max(1, plugin.getConfig().getInt("combat.chase.duration-seconds", 5) * 20)) {
                despawn();
                nextAttemptTicks = randomBetween(40, 100);
            }
        }
    }

    private void tryStartEncounter() {
        if (random.nextDouble() * 100.0 > plugin.getConfig().getDouble("herobrine.stalking.appearance-chance-percent", 25.0)) return;
        Location anchor = state.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return;

        List<Player> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(anchor.getWorld()) && !player.isDead()
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && horrorManager != null
                    && horrorManager.getFear(player) >= plugin.getConfig().getInt("herobrine.stalking.min-fear-to-appear", 30)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) return;

        target = candidates.get(random.nextInt(candidates.size()));
        double attackChance = plugin.getConfig().getDouble("combat.chase.start-chance-percent", 100.0);
        boolean stalkingEnabled = plugin.getConfig().getBoolean("herobrine.stalking.enabled", true);
        boolean mayAttack = plugin.getConfig().getBoolean("combat.enabled", true) && horrorManager != null && horrorManager.isHostile(target);
        mode = mayAttack && random.nextDouble() * 100.0 < attackChance
                ? Mode.ATTACK_WINDUP : Mode.STALKING;
        modeTicks = 0;
        modeDurationTicks = mode == Mode.STALKING ? randomBetween(100, 160)
                : ATTACK_DURATION_TICKS;
        attackCooldownTicks = 0;

        Location spawn = findSpawnLocation(target);
        if (spawn == null) {
            target = null;
            mode = Mode.NONE;
            return;
        }
        npcLocation = spawn;
        createNpc(spawn);
        if (npc == null) return;
        updateViewers();
        if (horrorManager != null) horrorManager.onEncounter(target, mode == Mode.ATTACK_WINDUP);
        plugin.getLogger().info("Herobrine encounter started: " + mode + " near " + target.getName());
    }

    private Location findSpawnLocation(Player player) {
        World world = player.getWorld();
        double min = Math.max(4.0, plugin.getConfig().getDouble("herobrine.stalking.min-distance", 10.0));
        double max = Math.max(min + 1.0, plugin.getConfig().getDouble("herobrine.stalking.max-distance", 30.0));
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = min + random.nextDouble() * (max - min);
            int x = (int) Math.floor(player.getLocation().getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getLocation().getZ() + Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafe(candidate)) {
                candidate.setYaw(yawToward(candidate, player.getLocation()));
                return candidate;
            }
        }
        return null;
    }

    private boolean isSafe(Location location) {
        return location.getBlock().isPassable()
                && location.clone().add(0, 1, 0).getBlock().isPassable()
                && location.clone().subtract(0, 1, 0).getBlock().getType().isSolid();
    }

    private void createNpc(Location location) {
        String texture = plugin.getConfig().getString("herobrine.appearance.skin.texture-value", "");
        String signature = plugin.getConfig().getString("herobrine.appearance.skin.signature", "");
        if (texture == null || texture.isBlank()) {
            plugin.getLogger().severe("Skin texture-value mancante in config.yml; NPC non creato.");
            mode = Mode.NONE;
            target = null;
            return;
        }
        UUID profileId = UUID.randomUUID();
        npcProfileName = "HB" + profileId.toString().replace("-", "").substring(0, 12);
        npcTeamName = "hb" + profileId.toString().replace("-", "").substring(0, 12);
        UserProfile profile = new UserProfile(profileId, npcProfileName,
                List.of(new TextureProperty("textures", texture, signature == null || signature.isBlank() ? null : signature)));
        npc = new NPC(profile, ENTITY_IDS.getAndIncrement(), GameMode.SURVIVAL, Component.empty(), null, null, null);
        npc.setLocation(toPacketLocation(location));
    }

    private void updateViewers() {
        if (npc == null || npcLocation == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
            if (channel == null) continue;
            if (viewer.getWorld().equals(npcLocation.getWorld())) {
                if (!npc.hasSpawned(channel)) {
                    hideNameFor(viewer);
                    npc.spawn(channel);
                }
            } else if (npc.hasSpawned(channel)) {
                npc.despawn(channel);
            }
        }
    }

    private void hideNameFor(Player viewer) {
        if (npcProfileName == null || npcTeamName == null) return;
        Scoreboard board = viewer.getScoreboard();
        Team team = board.getTeam(npcTeamName);
        if (team == null) team = board.registerNewTeam(npcTeamName);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(npcProfileName);
    }

    private void removeNameTeams() {
        if (npcTeamName == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Team team = viewer.getScoreboard().getTeam(npcTeamName);
            if (team != null) team.unregister();
        }
        npcTeamName = null;
        npcProfileName = null;
    }

    private void faceTarget() {
        if (npc == null || target == null) return;
        npcLocation.setYaw(yawToward(npcLocation, target.getLocation()));
        npc.updateRotation(npcLocation.getYaw(), 0.0f);
    }

    private boolean isTargetLookingAtHerobrine() {
        Vector toNpc = npcLocation.toVector().subtract(target.getEyeLocation().toVector()).normalize();
        Vector look = target.getEyeLocation().getDirection().normalize();
        return look.dot(toNpc) > 0.94 && target.getWorld().rayTraceBlocks(target.getEyeLocation(), toNpc, target.getEyeLocation().distance(npcLocation.clone().add(0, 1.0, 0))) == null;
    }

    private void chaseTarget() {
        if (npc == null || target == null) return;
        Location targetLocation = target.getLocation();
        Vector delta = targetLocation.toVector().subtract(npcLocation.toVector());
        double distance = delta.length();
        if (distance > 0.001) {
            // Client relative-move packets have limited delta range; keep each step small.
            double speed = Math.min(0.9, Math.max(0.05, plugin.getConfig().getDouble("combat.chase.speed-blocks-per-tick", 0.28)));
            // Horizontal steering: the target's vertical position must not steal jump speed.
            Vector horizontal = new Vector(delta.getX(), 0, delta.getZ());
            Vector step = horizontal.lengthSquared() < 0.0001 ? new Vector(0, 0, 0)
                    : horizontal.normalize().multiply(Math.min(speed, Math.sqrt(delta.getX() * delta.getX() + delta.getZ() * delta.getZ())));
            Location next = resolveCollision(npcLocation, step);
            if (next.distanceSquared(npcLocation) < 0.000001) return;
            next.setYaw(yawToward(next, targetLocation));
            next.setPitch(0.0f);
            moveNpcSmoothly(npcLocation, next);
            npcLocation = next;
            npc.setLocation(toPacketLocation(npcLocation));
        }

        if (distance <= 2.0 && hasClearAttackPath() && attackCooldownTicks-- <= 0) {
            attackCooldownTicks = Math.max(1, (int) Math.round(plugin.getConfig().getInt("combat.attack.cooldown-ticks", 20)));
            swingMainArm();
            double damage = Math.max(0.0, plugin.getConfig().getDouble("combat.attack.damage", 4.0));
            target.damage(damage);
            Vector knockback = target.getLocation().toVector().subtract(npcLocation.toVector());
            if (knockback.lengthSquared() > 0.001) target.setVelocity(knockback.normalize().multiply(0.35).setY(0.18));
        }
    }

    /** Client-side NPCs have no Bukkit physics. Walk the jump arc in small packet steps. */
    private Location resolveCollision(Location origin, Vector motion) {
        Location forward = origin.clone().add(motion.getX(), 0, motion.getZ());
        if (jumpTicksRemaining > 0) {
            int elapsed = 6 - jumpTicksRemaining + 1;
            double progress = elapsed / 6.0;
            double desiredY = jumpStartY + (jumpEndY - jumpStartY) * progress
                    + Math.sin(Math.PI * progress) * 0.42;
            Location candidate = forward.clone();
            candidate.setY(desiredY);
            if (!hasRoom(candidate)) {
                candidate = origin.clone();
                candidate.setY(desiredY);
            }
            if (!hasRoom(candidate)) return origin.clone();
            jumpTicksRemaining--;
            if (jumpTicksRemaining == 0) {
                candidate.setY(jumpEndY);
                if (!hasRoom(candidate) || !hasSupport(candidate)) return origin.clone();
            }
            return candidate;
        }
        if (hasRoom(forward) && hasSupport(forward)) return forward;

        if (plugin.getConfig().getBoolean("combat.chase.auto-jump", true)) {
            // Detect the obstructing block at the NPC's feet; the landing area
            // must have a solid floor exactly one block above the old floor.
            Location landing = forward.clone().add(0, 1, 0);
            Location launch = origin.clone().add(0, 0.55, 0);
            Location overhead = origin.clone().add(0, 1, 0);
            if (hasRoom(launch) && hasRoom(overhead) && hasRoom(landing)
                    && hasSupport(landing) && !hasRoom(forward)) {
                jumpStartY = origin.getY();
                jumpEndY = landing.getY();
                jumpTicksRemaining = 6;
                return resolveCollision(origin, motion);
            }
        }

        Location alongX = origin.clone().add(motion.getX(), 0, 0);
        Location alongZ = origin.clone().add(0, 0, motion.getZ());
        if (hasRoom(alongX) && hasSupport(alongX)) return alongX;
        if (hasRoom(alongZ) && hasSupport(alongZ)) return alongZ;
        if (hasRoom(forward)) {
            Location down = forward.clone().subtract(0, 1, 0);
            if (hasRoom(down) && hasSupport(down)) return down;
        }
        return origin.clone();
    }

    private boolean hasRoom(Location feet) {
        double[] offsets = {-0.29, 0.29};
        for (double dx : offsets) for (double dz : offsets) {
            for (double y : new double[]{0.05, 1.65}) {
                if (!feet.clone().add(dx, y, dz).getBlock().isPassable()) return false;
            }
        }
        return true;
    }

    private boolean hasSupport(Location feet) {
        return !feet.clone().subtract(0, 0.1, 0).getBlock().isPassable();
    }

    private boolean hasClearAttackPath() {
        Location eye = npcLocation.clone().add(0, 1.6, 0);
        Location other = target.getEyeLocation();
        Vector ray = other.toVector().subtract(eye.toVector());
        double length = ray.length();
        return length < 0.01 || npcLocation.getWorld().rayTraceBlocks(eye, ray.normalize(), length) == null;
    }

    private void emitPresenceParticles() {
        if (npcLocation == null || modeTicks % 5 != 0) return;
        npcLocation.getWorld().spawnParticle(Particle.SMOKE, npcLocation.clone().add(0, 1, 0), 3, .25, .65, .25, .01);
    }

    private void playDespawnEffects() {
        if (npcLocation == null || mode == Mode.NONE) return;
        World world = npcLocation.getWorld();
        Location at = npcLocation.clone();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(world) || viewer.getLocation().distanceSquared(at) > 48 * 48) continue;
            viewer.playSound(at, Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.55f);
            viewer.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.55f);
        }
        boolean lightning = mode == Mode.STALKING && random.nextDouble() * 100 <
                plugin.getConfig().getDouble("herobrine.stalking.despawn.lightning-chance-percent", 25);
        if (lightning) world.strikeLightningEffect(at); // visual only: no fire
        else spawnOriginalVanishParticles(world, at);
    }

    private void spawnOriginalVanishParticles(World world, Location at) {
        // Soul/enchanted spectral burst, matching the original 1.8 particle family.
        Location center = at.clone().add(0, 1, 0);
        world.spawnParticle(Particle.SOUL, center, 42, .42, .75, .42, .045);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 28, .35, .7, .35, .025);
        world.spawnParticle(Particle.END_ROD, center, 16, .3, .6, .3, .035);
        world.spawnParticle(Particle.ENCHANT, center, 35, .5, .8, .5, .12);
    }

    /** Sends native relative movement packets so the player model animates while running. */
    private void moveNpcSmoothly(Location from, Location to) {
        if (npc == null) return;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        WrapperPlayServerEntityRelativeMoveAndRotation packet =
                new WrapperPlayServerEntityRelativeMoveAndRotation(
                        npc.getId(), new LinearVecDelta(dx, dy, dz),
                        to.getYaw(), to.getPitch(), true);
        for (Object channel : npc.getChannels()) {
            PacketEvents.getAPI().getProtocolManager().sendPacket(channel, packet);
        }
    }

    private void swingMainArm() {
        if (npc == null) return;
        WrapperPlayServerSwingAnimation packet = new WrapperPlayServerSwingAnimation(
                npc.getId(), InteractionHand.MAIN_HAND,
                new ItemSwingAnimation(ItemSwingAnimation.Type.WHACK, 6));
        for (Object channel : npc.getChannels()) {
            PacketEvents.getAPI().getProtocolManager().sendPacket(channel, packet);
        }
    }

    private void despawn() { despawn(true); }

    private void despawn(boolean effects) {
        if (npc != null && effects) playDespawnEffects();
        if (npc != null) npc.despawnAll();
        removeNameTeams();
        jumpTicksRemaining = 0;
        npc = null;
        target = null;
        npcLocation = null;
        mode = Mode.NONE;
        modeTicks = 0;
        forcedAttack = false;
    }

    private int randomBetween(int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
    }

    private float yawToward(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private com.github.retrooper.packetevents.protocol.world.Location toPacketLocation(Location location) {
        return new com.github.retrooper.packetevents.protocol.world.Location(
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }
}
