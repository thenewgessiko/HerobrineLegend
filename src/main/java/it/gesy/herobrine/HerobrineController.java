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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Drives the client-side player NPC and the first stalking/attack behaviour. */
public final class HerobrineController implements Listener {
    private enum Mode { NONE, STALKING, ATTACKING }

    private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_500_000_000);
    private static final int ATTACK_DURATION_TICKS = 5 * 20;
    private final HerobrineLegendPlugin plugin;
    private final EncounterState state;
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

        if (mode == Mode.STALKING) {
            faceTarget();
            if (plugin.getConfig().getBoolean("herobrine.stalking.vanish-when-looked-at", true)
                    && isTargetLookingAtHerobrine()) {
                despawn();
                nextAttemptTicks = 40;
                return;
            }
            if (modeTicks >= modeDurationTicks) {
                despawn();
                nextAttemptTicks = randomBetween(40, 100);
            }
            return;
        }

        if (mode == Mode.ATTACKING) {
            chaseTarget();
            if (modeTicks >= ATTACK_DURATION_TICKS) {
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
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) return;

        target = candidates.get(random.nextInt(candidates.size()));
        double attackChance = plugin.getConfig().getDouble("herobrine.combat.attack-chance-percent", 25.0);
        boolean stalkingEnabled = plugin.getConfig().getBoolean("herobrine.stalking.enabled", true);
        mode = !stalkingEnabled || random.nextDouble() * 100.0 < attackChance
                ? Mode.ATTACKING : Mode.STALKING;
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
        UserProfile profile = new UserProfile(profileId, "Herobrine",
                List.of(new TextureProperty("textures", texture, signature == null || signature.isBlank() ? null : signature)));
        npc = new NPC(profile, ENTITY_IDS.getAndIncrement(), GameMode.SURVIVAL, Component.text("Herobrine"), null, null, null);
        npc.setLocation(toPacketLocation(location));
    }

    private void updateViewers() {
        if (npc == null || npcLocation == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(viewer);
            if (channel == null) continue;
            if (viewer.getWorld().equals(npcLocation.getWorld())) {
                if (!npc.hasSpawned(channel)) npc.spawn(channel);
            } else if (npc.hasSpawned(channel)) {
                npc.despawn(channel);
            }
        }
    }

    private void faceTarget() {
        if (npc == null || target == null) return;
        npcLocation.setYaw(yawToward(npcLocation, target.getLocation()));
        npc.updateRotation(npcLocation.getYaw(), 0.0f);
    }

    private boolean isTargetLookingAtHerobrine() {
        Vector toNpc = npcLocation.toVector().subtract(target.getEyeLocation().toVector()).normalize();
        Vector look = target.getEyeLocation().getDirection().normalize();
        return look.dot(toNpc) > 0.94 && target.hasLineOfSight(npcLocation.clone().add(0, 1.0, 0));
    }

    private void chaseTarget() {
        if (npc == null || target == null) return;
        Location targetLocation = target.getLocation();
        Vector delta = targetLocation.toVector().subtract(npcLocation.toVector());
        double distance = delta.length();
        if (distance > 0.001) {
            double speed = Math.max(0.05, plugin.getConfig().getDouble("herobrine.combat.chase-speed", 0.28));
            Vector step = delta.normalize().multiply(Math.min(speed, distance));
            Location next = npcLocation.clone().add(step);
            next.setYaw(yawToward(next, targetLocation));
            next.setPitch(0.0f);
            moveNpcSmoothly(npcLocation, next);
            npcLocation = next;
            npc.setLocation(toPacketLocation(npcLocation));
        }

        if (distance <= 2.0 && attackCooldownTicks-- <= 0) {
            attackCooldownTicks = Math.max(1, (int) Math.round(plugin.getConfig().getDouble("herobrine.combat.attack-cooldown-seconds", 1.0) * 20.0));
            swingMainArm();
            double damage = Math.max(0.0, plugin.getConfig().getDouble("herobrine.combat.damage", 4.0));
            target.damage(damage);
            Vector knockback = target.getLocation().toVector().subtract(npcLocation.toVector());
            if (knockback.lengthSquared() > 0.001) target.setVelocity(knockback.normalize().multiply(0.35).setY(0.18));
        }
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

    private void despawn() {
        if (npc != null) npc.despawnAll();
        npc = null;
        target = null;
        npcLocation = null;
        mode = Mode.NONE;
        modeTicks = 0;
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
