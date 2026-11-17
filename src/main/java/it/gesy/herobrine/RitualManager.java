package it.gesy.herobrine;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;


/** Handles dropped ritual items and validates the complete 3x3 pattern. */
public final class RitualManager implements Listener {
    private final HerobrineLegendPlugin plugin;
    private final EncounterState state;
    private final HerobrineController controller;

    public RitualManager(HerobrineLegendPlugin plugin, EncounterState state, HerobrineController controller) {
        this.plugin = plugin;
        this.state = state;
        this.controller = controller;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getConfig().getBoolean("ritual.enabled", true)) return;
        if (plugin.getConfig().getBoolean("ritual.require-permission", false)
                && !event.getPlayer().hasPermission("herobrine.usealtar")) return;
        Material activation = material("ritual.activation-item", Material.CRYING_OBSIDIAN);
        Material deactivation = material("ritual.deactivation-item", Material.DIAMOND);
        Material type = event.getItemDrop().getItemStack().getType();
        if (type != activation && type != deactivation) return;
        watchUntilLanding(event.getPlayer(), event.getItemDrop(), type);
    }

    private void watchUntilLanding(Player player, Item item, Material type) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (!item.isValid() || item.isDead() || ticks++ > 100) { cancel(); return; }
                // A dropped item can hover inside fire rather than reaching isOnGround().
                // Check proximity to the netherrack throughout its descent.
                Block below = findNetherrackBelow(item.getLocation().getBlock());
                if (below == null) return;
                double horizontal = Math.hypot(item.getLocation().getX() - (below.getX() + 0.5),
                        item.getLocation().getZ() - (below.getZ() + 0.5));
                if (horizontal > 0.85 || item.getLocation().getY() > below.getY() + 2.1) return;
                cancel();
                // Delay the completed ritual by one second, without consuming the item yet.
                final Block ritualBlock = below;
                final Material ritualItem = type;
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!item.isValid() || item.isDead()) return;
                Block center = ritualBlock.getRelative(0, -1, 0);
                if (!isRitualComplete(center)) return;
                if (type == material("ritual.activation-item", Material.CRYING_OBSIDIAN)) {
                    if (state.isActive()) {
                        player.sendMessage(ChatColor.GRAY + "Il rituale è già attivo.");
                        return;
                    }
                    Block flame = below.getRelative(0, 1, 0);
                    if (flame.getType() != Material.AIR) return;
                    plugin.getHorrorManager().resetAllFear();
                    state.activate(center.getLocation());
                    // Visual lightning only: do not accidentally burn nearby builds.
                    below.getWorld().strikeLightningEffect(below.getLocation().add(0.5, 1, 0.5));
                    flame.setType(Material.FIRE);
                    playRitualEffects(below.getLocation().add(0.5, 1, 0.5), true);
                    consumeOne(item);
                    broadcastRitualTitle("§4§lLe§ka§r§4§lve Th§ki§r§4§ls Wo§kr§r§4§lld§r", "§4§lHerobrine is Here§r");
                    plugin.getLogger().info("Ritual activated at " + center.getWorld().getName() + " " + center.getX() + "," + center.getY() + "," + center.getZ());
                } else {
                    if (!state.isActive()) {
                        player.sendMessage(ChatColor.GRAY + "Il rituale non è attivo.");
                        return;
                    }
                    Location anchor = state.getAnchor();
                    if (anchor == null || !sameBlock(anchor, center.getLocation())) return;
                    // The player must extinguish the flame before banishing Herobrine.
                    if (below.getRelative(0, 1, 0).getType() == Material.FIRE) {
                        player.sendMessage(ChatColor.RED + "Spegni prima la fiamma sulla netherrack.");
                        return;
                    }
                    playRitualEffects(below.getLocation().add(0.5, 1, 0.5), false);
                    state.deactivate();
                    controller.deactivateImmediately();
                    plugin.getHorrorManager().resetAllFear();
                    consumeOne(item);
                    broadcastRitualTitle("§b§lPurification§r", "");
                    plugin.getLogger().info("Herobrine deactivated by " + player.getName());
                }
                }, Math.max(1L, plugin.getConfig().getLong("ritual.transition-delay-ticks", 20L)));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Global ritual titles: 10-tick fade in, 40-tick hold, 10-tick fade out. */
    private void broadcastRitualTitle(String title, String subtitle) {
        for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.sendTitle(title, subtitle, 10, 40, 10);
        }
    }

    /** Particle/sound choices reconstructed from the original 1.8 ritual bytecode. */
    private void playRitualEffects(Location at, boolean summon) {
        org.bukkit.World world = at.getWorld();
        if (world == null) return;
        if (summon) {
            world.playSound(at, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, .5f);
            world.playSound(at, Sound.ENTITY_PHANTOM_SWOOP, 1.5f, 0.85f);
            world.playSound(at, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 2f, .5f);
        } else {
            world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 2f, 1.5f);
            world.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.5f, .8f);
        }
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= (summon ? 60 : 8)) { cancel(); return; }
                if (summon) {
                    double angle = tick * .3, radius = 2.0 - tick / 30.0;
                    Location point = at.clone().add(Math.cos(angle) * radius, tick * .05, Math.sin(angle) * radius);
                    world.spawnParticle(Particle.SOUL, point, 3, .1, .1, .1, .01);
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, .1, .1, .1, .01);
                } else {
                    for (int i = 0; i < 8; i++) {
                        double angle = Math.PI / 4 * i;
                        Location point = at.clone().add(Math.cos(angle), 0, Math.sin(angle));
                        world.spawnParticle(Particle.END_ROD, point, 2, .1, .2, .1, .02);
                        world.spawnParticle(Particle.ENCHANT, point, 5, .2, .3, .2, .1);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean isRitualComplete(Block center) {
        if (center.getType() != material("ritual.center-block", Material.MOSSY_COBBLESTONE)) return false;
        Material ring = material("ritual.ring-block", Material.GOLD_BLOCK);
        Material redstone = material("ritual.corner-torch", Material.REDSTONE_TORCH);
        int[][] ringOffsets = {{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}};
        for (int[] offset : ringOffsets) {
            if (center.getRelative(offset[0], 0, offset[1]).getType() != ring) return false;
        }
        int[][] sideOffsets = {{-1,-1},{-1,1},{1,-1},{1,1}};
        for (int[] offset : sideOffsets) {
            if (center.getRelative(offset[0], 1, offset[1]).getType() != redstone) return false;
        }
        return center.getRelative(0, 1, 0).getType() == material("ritual.top-block", Material.NETHERRACK);
    }

    private Block findNetherrackBelow(Block itemBlock) {
        // Items rest slightly above the supporting block; fire may occupy the space above it.
        for (int depth = 0; depth <= 2; depth++) {
            Block candidate = itemBlock.getRelative(0, -depth, 0);
            if (candidate.getType() == material("ritual.top-block", Material.NETHERRACK)) return candidate;
        }
        return null;
    }

    private Material material(String path, Material fallback) {
        Material parsed = Material.matchMaterial(plugin.getConfig().getString(path, fallback.name()));
        return parsed == null ? fallback : parsed;
    }

    private void consumeOne(Item item) {
        ItemStack stack = item.getItemStack();
        if (stack.getAmount() <= 1) item.remove();
        else { stack.setAmount(stack.getAmount() - 1); item.setItemStack(stack); }
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null
                && a.getWorld().getUID().equals(b.getWorld().getUID())
                && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}
