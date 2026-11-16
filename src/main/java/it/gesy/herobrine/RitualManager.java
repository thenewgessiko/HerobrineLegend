package it.gesy.herobrine;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
                if (!item.isOnGround()) return;
                cancel();
                Block below = item.getLocation().getBlock().getRelative(0, -1, 0);
                if (below.getType() != material("ritual.top-block", Material.NETHERRACK)) return;
                Block center = below.getRelative(0, -1, 0);
                if (!isRitualComplete(center)) return;
                if (type == material("ritual.activation-item", Material.CRYING_OBSIDIAN)) {
                    if (state.isActive()) {
                        player.sendMessage(ChatColor.GRAY + "Il rituale è già attivo.");
                        return;
                    }
                    state.activate(center.getLocation());
                    consumeOne(item);
                    player.sendMessage(ChatColor.DARK_PURPLE + "Qualcosa ha risposto al richiamo... Herobrine è stato evocato.");
                    plugin.getLogger().info("Ritual activated at " + center.getWorld().getName() + " " + center.getX() + "," + center.getY() + "," + center.getZ());
                } else {
                    if (!state.isActive()) {
                        player.sendMessage(ChatColor.GRAY + "Il rituale non è attivo.");
                        return;
                    }
                    Location anchor = state.getAnchor();
                    if (anchor == null || !sameBlock(anchor, center.getLocation())) return;
                    state.deactivate();
                    controller.deactivateImmediately();
                    consumeOne(item);
                    player.sendMessage(ChatColor.YELLOW + "La presenza è svanita.");
                    plugin.getLogger().info("Herobrine deactivated by " + player.getName());
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private boolean isRitualComplete(Block center) {
        if (center.getType() != material("ritual.center-block", Material.MOSSY_COBBLESTONE)) return false;
        Material ring = material("ritual.ring-block", Material.GOLD_BLOCK);
        Material redstone = material("ritual.side-redstone", Material.REDSTONE_WIRE);
        int[][] ringOffsets = {{-1,-1},{0,-1},{1,-1},{-1,0},{1,0},{-1,1},{0,1},{1,1}};
        for (int[] offset : ringOffsets) {
            if (center.getRelative(offset[0], 0, offset[1]).getType() != ring) return false;
        }
        int[][] sideOffsets = {{0,-1},{0,1},{-1,0},{1,0}};
        for (int[] offset : sideOffsets) {
            if (center.getRelative(offset[0], 1, offset[1]).getType() != redstone) return false;
        }
        return center.getRelative(0, 1, 0).getType() == material("ritual.top-block", Material.NETHERRACK);
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
