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
    private enum Mode { NONE, STALKING, STALKING_JUMPSCARE, ATTACK_WINDUP, ATTACKING }

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
    // Independent absolute stalking clock: never restart at NPC despawn.
    private long encounterClockTicks;
    private long nextStalkingAttemptAt;
    private boolean nextStalkingVanishLightning;
    private boolean eyeTriggeredVanish;
    private int attackCooldownTicks;
    private boolean forcedAttack;
    private int stalkingContactCooldown;
    private String npcProfileName;
    private String npcTeamName;
    private int jumpTicksRemaining;
    private double verticalVelocity;
    private UUID huntingTargetId;
    private int completedHuntingAppearances;
    private long nextHuntingAppearanceAt;
    private boolean huntingAppearanceInProgress;
    private int jumpscareRushTicks;

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
        huntingAppearanceInProgress = false;
        huntingTargetId = null;
        completedHuntingAppearances = 0;
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
        modeDurationTicks = plugin.getConfig().getInt("herobrine.stalking.duration-seconds", 15) * 20;
        createNpc(spawn);
        if (npc == null) { despawn(); return false; }
        updateViewers();
        plugin.getLogger().info("Forced Herobrine jumpscare on " + player.getName());
        return true;
    }

    private void tick() {
        encounterClockTicks++;
        long stalkingInterval = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 300));
        boolean stalkingSlot = encounterClockTicks >= nextStalkingAttemptAt;
        if (!state.isActive()) {
            huntingAppearanceInProgress = false;
            huntingTargetId = null;
            completedHuntingAppearances = 0;
            if (npc != null) despawn();
            nextStalkingAttemptAt = encounterClockTicks + 300;
            return;
        }

        if (npc == null) {
            // Six actual appearances, separated by fifteen seconds AFTER
            // the previous disappearance. No fixed 90-second timeout.
            if (horrorManager != null && plugin.getConfig().getBoolean("fear.cycling.enabled", true)) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!horrorManager.isHunting(player) || player.isDead()) continue;
                    if (huntingTargetId == null || !huntingTargetId.equals(player.getUniqueId())) {
                        huntingTargetId = player.getUniqueId();
                        completedHuntingAppearances = 0;
                        huntingAppearanceInProgress = false;
                        nextHuntingAppearanceAt = encounterClockTicks;
                    }
                    if (completedHuntingAppearances < 6 && encounterClockTicks >= nextHuntingAppearanceAt) {
                        Location spawn = findSpawnLocation(player);
                        if (spawn != null) {
                            startHuntingEncounter(player, spawn);
                            if (npc != null) {
                                huntingAppearanceInProgress = true;
                                return;
                            }
                        }
                    }
                    // One NPC at a time: never start stalking during hunting.
                    return;
                }
            }
            if (stalkingSlot && (horrorManager == null || Bukkit.getOnlinePlayers().stream().noneMatch(horrorManager::isHunting))) {
                nextStalkingAttemptAt = encounterClockTicks + stalkingInterval;
                tryStartEncounter();
            }
            return;
        }

        if (target == null || !target.isOnline() || target.isDead()
                || !target.getWorld().equals(npcLocation.getWorld())) {
            huntingAppearanceInProgress = false;
            despawn();
            nextAttemptTicks = 20;
            return;
        }

        if (horrorManager != null && (mode == Mode.ATTACK_WINDUP || mode == Mode.ATTACKING)
                && !forcedAttack && !horrorManager.isHunting(target)) {
            despawn();
            return;
        }
        if (horrorManager != null && horrorManager.isHunting(target) && mode == Mode.STALKING) {
            despawn();
            return;
        }
        updateViewers();
        modeTicks++;
        // Exactly one additional dark entity voice, 2 seconds after a stalking
        // appearance. This is audio only: it must not raise Fear or reset timers.
        // It never plays during hunting, nor if Herobrine has already vanished.
        if (mode == Mode.STALKING && modeTicks == 40 && target != null) {
            Sound[] voices = {
                Sound.ENTITY_ENDERMAN_SCREAM,
                Sound.ENTITY_WARDEN_AMBIENT,
                Sound.ENTITY_WARDEN_NEARBY_CLOSE
            };
            Sound selected = voices[random.nextInt(voices.length)];
            float pitch = 0.50f + random.nextFloat() * 0.22f;
            target.playSound(npcLocation, selected, 0.9f, pitch);
        }
        emitManifestationSmoke();
        if (stalkingContactCooldown > 0) stalkingContactCooldown--;

        if (mode == Mode.STALKING_JUMPSCARE) {
            faceTarget();
            emitPresenceParticles();
            // Instant face-to-face jumpscare, not a walking chase.
            if (jumpscareRushTicks > 0) {
                jumpscareRushTicks = 0;
                Location playerFeet = target.getLocation();
                Vector facing = playerFeet.getDirection().setY(0);
                if (facing.lengthSquared() < 0.001) facing = new Vector(0, 0, 1);
                Location close = playerFeet.clone().add(facing.normalize().multiply(1.0));
                if (!hasRoom(close)) close = playerFeet.clone();
                close.setYaw(yawToward(close, target.getEyeLocation()));
                close.setPitch(0);
                npcLocation = close;
                npc.setLocation(toPacketLocation(close));
                npc.despawnAll();
                updateViewers();
                faceTarget();
                target.damage(1.0);
                applyStalkingContactEffects(target, false);
                modeTicks = 0;
                return;
            }
            if (modeTicks >= 20) {
                despawn();
                nextAttemptTicks = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 300));
            }
            return;
        }

        if (mode == Mode.STALKING) {
            if (modeTicks >= Math.max(1, plugin.getConfig().getInt("herobrine.stalking.duration-seconds", 15)) * 20) {
                despawn();
                nextAttemptTicks = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 300));
                return;
            }
            faceTarget();
            emitPresenceParticles();
            if (npcLocation.distanceSquared(target.getLocation()) <= 2.25
                    && stalkingContactCooldown == 0 && !isTargetLookingAtHerobrine()) {
                stalkingContactCooldown = 60;
                int fear = horrorManager == null ? 0 : horrorManager.getFear(target);
                applyStalkingContactEffects(target, fear < 65);
                Location impact = target.getLocation();
                spawnOriginalVanishParticles(target.getWorld(), impact);
                playStalkingVanishLightning(target.getWorld(), impact);
                target.damage(1.0);
                despawn(false);
                nextAttemptTicks = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 300));
                return;
            }
            if (plugin.getConfig().getBoolean("herobrine.stalking.vanish-when-looked-at", true)
                    && npcLocation.distanceSquared(target.getLocation()) > 4.0
                    && isTargetLookingAtHerobrine()) {
                int fear = horrorManager == null ? 0 : horrorManager.getFear(target);
                eyeTriggeredVanish = true;
                if (fear >= plugin.getConfig().getInt("herobrine.stalking.eye-jumpscare.min-fear", 65)
                        && fear <= plugin.getConfig().getInt("herobrine.stalking.eye-jumpscare.max-fear", 99)
                        && plugin.getConfig().getBoolean("herobrine.stalking.eye-jumpscare.enabled", true)) {
                    mode = Mode.STALKING_JUMPSCARE;
                    modeTicks = 0;
                    jumpscareRushTicks = 1; // instant face-to-face teleport
                    target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.0f, 0.65f);
                } else {
                    despawn();
                    nextAttemptTicks = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 300));
                }
                return;
            }
            // Stalking ends after 15 seconds or when the player looks directly into its eyes.
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
            if (!forcedAttack && (horrorManager == null || !horrorManager.isHunting(target))) {
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

    private void startHuntingEncounter(Player player, Location spawn) {
        target = player;
        mode = Mode.ATTACK_WINDUP;
        modeTicks = 0;
        modeDurationTicks = ATTACK_DURATION_TICKS;
        attackCooldownTicks = 0;
        forcedAttack = false;
        npcLocation = spawn;
        createNpc(spawn);
        if (npc == null) { despawn(); return; }
        updateViewers();
        if (horrorManager != null) horrorManager.onEncounter(player, true);
    }

    private void tryStartEncounter() {
        Location anchor = state.getAnchor();
        if (anchor == null || anchor.getWorld() == null) return;

        List<Player> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(anchor.getWorld()) && !player.isDead()
                    && player.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && horrorManager != null
                    && !horrorManager.isHunting(player)
                    && horrorManager.getFear(player) >= plugin.getConfig().getInt("herobrine.stalking.min-fear-to-appear", 30)) {
                candidates.add(player);
            }
        }
        if (candidates.isEmpty()) return;

        target = candidates.get(random.nextInt(candidates.size()));
        double attackChance = plugin.getConfig().getDouble("combat.chase.start-chance-percent", 100.0);
        boolean stalkingEnabled = plugin.getConfig().getBoolean("herobrine.stalking.enabled", true);
        boolean mayAttack = plugin.getConfig().getBoolean("combat.enabled", true) && horrorManager != null && horrorManager.isHunting(target);
        mode = mayAttack && random.nextDouble() * 100.0 < attackChance
                ? Mode.ATTACK_WINDUP : Mode.STALKING;
        modeTicks = 0;
        modeDurationTicks = mode == Mode.STALKING ? plugin.getConfig().getInt("herobrine.stalking.duration-seconds", 15) * 20
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
        Location eyes = npcLocation.clone().add(0, 1.62, 0);
        Location targetEyes = target.getEyeLocation();
        npcLocation.setYaw(yawToward(eyes, targetEyes));
        double horizontal = Math.hypot(targetEyes.getX() - eyes.getX(), targetEyes.getZ() - eyes.getZ());
        float pitch = (float) Math.toDegrees(Math.atan2(eyes.getY() - targetEyes.getY(), horizontal));
        npcLocation.setPitch(Math.max(-85.0f, Math.min(85.0f, pitch)));
        npc.updateRotation(npcLocation.getYaw(), npcLocation.getPitch());
    }

    private boolean isTargetLookingAtHerobrine() {
        Location eyes = npcLocation.clone().add(0, 1.62, 0);
        Location playerEyes = target.getEyeLocation();
        Vector toEyes = eyes.toVector().subtract(playerEyes.toVector());
        double distance = toEyes.length();
        if (distance < 0.001) return true;
        Vector ray = toEyes.normalize();
        // Narrow eye contact, not just looking toward the NPC's body.
        double dot = playerEyes.getDirection().normalize().dot(ray);
        double tolerance = Math.atan2(0.19, distance);
        if (dot < Math.cos(Math.max(Math.toRadians(2.5), tolerance))) return false;
        return target.getWorld().rayTraceBlocks(playerEyes, ray, distance) == null;
    }

    private boolean teleportNearTarget() {
        Location player = target.getLocation();
        Vector forward = player.getDirection().setY(0);
        if (forward.lengthSquared() < 0.001) forward = new Vector(1, 0, 0);
        forward.normalize();
        // Appear within view, but not inside the player's collision box.
        for (double angle : new double[]{0, 45, -45, 90, -90, 180}) {
            Vector offset = forward.clone().rotateAroundY(Math.toRadians(angle)).multiply(1.7);
            Location candidate = player.clone().add(offset);
            candidate.setY(player.getY());
            if (!hasRoom(candidate) || !hasSupport(candidate)) continue;
            Location previous = npcLocation.clone();
            candidate.setYaw(yawToward(candidate, target.getEyeLocation()));
            candidate.setPitch(0);
            // NPC.setLocation updates server-side position; packet teleport must reach viewers too.
            npcLocation = candidate;
            npc.setLocation(toPacketLocation(candidate));
            npc.despawnAll();
            updateViewers();
            faceTarget();
            return true;
        }
        return false;
    }

    private void chaseTargetWithoutDamage() {
        if (npc == null || target == null) return;
        Vector delta = target.getLocation().toVector().subtract(npcLocation.toVector());
        Vector horizontal = new Vector(delta.getX(), 0, delta.getZ());
        if (horizontal.lengthSquared() < 0.0001) return;
        Vector motion = horizontal.normalize().multiply(Math.min(0.65, horizontal.length()));
        Location next = resolveCollision(npcLocation, motion);
        next.setYaw(npcLocation.getYaw());
        next.setPitch(npcLocation.getPitch());
        moveNpcSmoothly(npcLocation, next);
        npcLocation = next;
        npc.setLocation(toPacketLocation(npcLocation));
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
            // Do not abort the entire chase tick when a wall blocks horizontal movement.
            // Vertical jump/gravity must still advance, and melee logic must still run.
            next.setYaw(npcLocation.getYaw());
            next.setPitch(npcLocation.getPitch());
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

    /** Packet NPC physics: jump ahead of a one-block ledge, integrate vertical
     * velocity every tick and never pass through solid blocks. */
    private Location resolveCollision(Location origin, Vector motion) {
        Location feet = origin.clone();
        Vector horizontal = motion.clone().setY(0);
        boolean grounded = hasSupport(feet);

        if (grounded && verticalVelocity <= 0 && jumpTicksRemaining == 0
                && plugin.getConfig().getBoolean("combat.chase.auto-jump", true)
                && horizontal.lengthSquared() > 0.00001) {
            Vector direction = horizontal.clone().normalize();
            // Anticipate a raised floor by at least one block before contact.
            // Detect the actual solid foot-level block, not only a full-body
            // collision (which may miss the ledge at fractional positions).
            boolean oneBlockLedge = false;
            for (double distance = 1.0; distance <= 2.0; distance += 0.20) {
                Location probe = feet.clone().add(direction.clone().multiply(distance));
                Location footBlock = probe.clone().add(0, 0.10, 0);
                Location landing = probe.clone().add(0, 1.01, 0);
                if (!footBlock.getBlock().isPassable()
                        && hasRoom(landing)
                        && hasRoom(feet.clone().add(0, 1.01, 0))) {
                    oneBlockLedge = true;
                    break;
                }
            }
            if (oneBlockLedge) {
                verticalVelocity = 0.52;
                jumpTicksRemaining = 1;
            }
        }

        // Vertical physics must run even while horizontal movement is blocked.
        if (jumpTicksRemaining > 0 || !grounded) {
            double dy = verticalVelocity;
            if (dy > 0) {
                // Move upward in small steps to prevent ceiling clipping.
                double remaining = dy;
                while (remaining > 0.0001) {
                    double step = Math.min(0.10, remaining);
                    Location up = feet.clone().add(0, step, 0);
                    if (!hasRoom(up)) {
                        verticalVelocity = 0;
                        break;
                    }
                    feet = up;
                    remaining -= step;
                }
            } else if (dy < 0) {
                double remaining = Math.min(0.65, -dy);
                while (remaining > 0.0001) {
                    double step = Math.min(0.10, remaining);
                    Location down = feet.clone().subtract(0, step, 0);
                    if (!hasRoom(down) || hasSupport(down)) {
                        // Stop at the current floor; never snap upwards into
                        // an unrelated block or fall through the landing ledge.
                        verticalVelocity = 0;
                        jumpTicksRemaining = 0;
                        break;
                    }
                    feet = down;
                    remaining -= step;
                }
            }
            // Do not cancel the upward half of a jump merely because a floor
            // is still close below the NPC.
            if (verticalVelocity > 0 || !hasSupport(feet)) {
                verticalVelocity = Math.max(-0.65, verticalVelocity - 0.08);
            } else {
                verticalVelocity = 0;
                jumpTicksRemaining = 0;
            }
        } else {
            verticalVelocity = 0;
        }

        Location candidate = feet.clone().add(horizontal);
        if (hasRoom(candidate)) return candidate;
        Location alongX = feet.clone().add(horizontal.getX(), 0, 0);
        Location alongZ = feet.clone().add(0, 0, horizontal.getZ());
        if (hasRoom(alongX)) return alongX;
        if (hasRoom(alongZ)) return alongZ;
        return feet;
    }

    private boolean hasRoom(Location feet) {
        double[] offsets = {-0.29, 0.29};
        for (double dx : offsets) for (double dz : offsets) {
            for (double y : new double[]{0.05, 0.65, 1.25, 1.75}) {
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

    private void applyStalkingContactEffects(Player player, boolean knockbackEnabled) {
        int ticks = Math.max(1, plugin.getConfig().getInt("herobrine.stalking.contact.effect-duration-ticks", 60));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, ticks, 0, false, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GHAST_SCREAM, 1.1f, 0.55f);
        if (knockbackEnabled && npcLocation != null) {
            Vector push = player.getLocation().toVector().subtract(npcLocation.toVector()).setY(0);
            if (push.lengthSquared() < 0.001) push = player.getLocation().getDirection().multiply(-1).setY(0);
            if (push.lengthSquared() < 0.001) push = new Vector(1, 0, 0);
            double strength = Math.max(0.0, plugin.getConfig().getDouble("herobrine.stalking.contact.knockback-strength", 2.0));
            player.setVelocity(push.normalize().multiply(strength * 0.5).setY(0.45));
        }
    }

    private void emitManifestationSmoke() {
        if (npcLocation == null || !plugin.getConfig().getBoolean("herobrine.appearance.manifestation-smoke.enabled", true)) return;
        int duration = Math.max(0, plugin.getConfig().getInt("herobrine.appearance.manifestation-smoke.duration-seconds", 3)) * 20;
        // Hunting smoke follows the moving NPC for the whole pursuit; stalking
        // smoke lasts only the configured manifestation period.
        boolean hunting = mode == Mode.ATTACK_WINDUP || mode == Mode.ATTACKING;
        if (hunting) duration = Math.max(0, plugin.getConfig().getInt(
                "herobrine.appearance.manifestation-smoke.hunting-duration-seconds", 1)) * 20;
        if (modeTicks > duration) return;
        if (modeTicks % 2 != 0) return;
        World world = npcLocation.getWorld();
        int around = Math.max(0, plugin.getConfig().getInt("herobrine.appearance.manifestation-smoke.around-herobrine-count", 2));
        if (around > 0) world.spawnParticle(Particle.SMOKE, npcLocation.clone().add(0, 0.9, 0), around, 0.28, 0.65, 0.28, 0.003);
        int count = Math.max(0, plugin.getConfig().getInt("herobrine.appearance.manifestation-smoke.ground-particle-count", 65));
        double radius = Math.max(0, plugin.getConfig().getDouble("herobrine.appearance.manifestation-smoke.ground-radius", 5.0));
        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double x = npcLocation.getX() + rng.nextDouble(-radius, radius);
            double z = npcLocation.getZ() + rng.nextDouble(-radius, radius);
            Location point = new Location(world, x, npcLocation.getY() + 1.0, z);
            for (int depth = 0; depth < 5; depth++) {
                Location floor = point.clone().subtract(0, depth, 0);
                if (floor.clone().subtract(0, 0.25, 0).getBlock().getType().isSolid()
                        && floor.getBlock().isPassable()) {
                    world.spawnParticle(Particle.SMOKE, floor.add(0, 0.12, 0), 1, 0.12, 0.06, 0.12, 0.002);
                    break;
                }
            }
        }
    }

    private void emitPresenceParticles() {
        if (npcLocation == null || modeTicks % 5 != 0) return;
        npcLocation.getWorld().spawnParticle(Particle.SMOKE, npcLocation.clone().add(0, 1, 0), 3, .25, .65, .25, .01);
    }

    private void playStalkingVanishLightning(World world, Location at) {
        if (mode != Mode.STALKING && mode != Mode.STALKING_JUMPSCARE) return;
        boolean lightning = nextStalkingVanishLightning;
        nextStalkingVanishLightning = !nextStalkingVanishLightning;
        if (lightning) world.strikeLightningEffect(at);
    }

    private void playDespawnEffects() {
        if (npcLocation == null || mode == Mode.NONE) return;
        World world = npcLocation.getWorld();
        Location at = npcLocation.clone();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.getWorld().equals(world) || viewer.getLocation().distanceSquared(at) > 48 * 48) continue;
            if (eyeTriggeredVanish) {
                viewer.playSound(at, Sound.ENTITY_ENDERMAN_STARE, 1.0f, 0.60f);
            }
            viewer.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.55f);
        }
        spawnOriginalVanishParticles(world, at);
        playStalkingVanishLightning(world, at);
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
        if (npc != null && huntingAppearanceInProgress
                && (mode == Mode.ATTACK_WINDUP || mode == Mode.ATTACKING)) {
            huntingAppearanceInProgress = false;
            completedHuntingAppearances++;
            nextHuntingAppearanceAt = encounterClockTicks
                    + Math.max(1, plugin.getConfig().getInt("herobrine.hunting.appearance-interval-seconds", 15)) * 20L;
            if (completedHuntingAppearances >= 6 && horrorManager != null && target != null) {
                horrorManager.finishHunting(target);
                huntingTargetId = null;
                completedHuntingAppearances = 0;
            }
        }
        // Fifteen seconds counted from the stalking disappearance, just like hunting.
        if (npc != null && (mode == Mode.STALKING || mode == Mode.STALKING_JUMPSCARE)) {
            nextStalkingAttemptAt = encounterClockTicks
                    + Math.max(1, plugin.getConfig().getInt("herobrine.stalking.check-interval-ticks", 300));
        }
        if (npc != null && effects) playDespawnEffects();
        if (npc != null) npc.despawnAll();
        removeNameTeams();
        jumpTicksRemaining = 0;
        verticalVelocity = 0;
        npc = null;
        target = null;
        npcLocation = null;
        mode = Mode.NONE;
        modeTicks = 0;
        forcedAttack = false;
        jumpscareRushTicks = 0;
        eyeTriggeredVanish = false;
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
