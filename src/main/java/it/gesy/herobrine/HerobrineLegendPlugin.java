package it.gesy.herobrine;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.plugin.java.JavaPlugin;

public final class HerobrineLegendPlugin extends JavaPlugin implements TabCompleter {
    // Keep command suggestions in one registry, reused by the completer.
    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(Arrays.asList(
            "status", "jumpscare", "attack", "fear", "setfear", "reload"));
    private static final List<String> PLAYER_ARGUMENT_COMMANDS = Collections.unmodifiableList(Arrays.asList(
            "jumpscare", "attack", "fear", "setfear"));
    private static final List<String> FEAR_VALUES = Collections.unmodifiableList(Arrays.asList(
            "0", "5", "25", "30", "50", "65", "75", "99", "100"));
    private RitualManager ritualManager;
    private HorrorManager horrorManager;
    private EncounterState encounterState;
    private HerobrineController herobrineController;

    public HorrorManager getHorrorManager() { return horrorManager; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("PacketEvents not found. HerobrineLegend requires PacketEvents 2.14.0 or later.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!PacketEvents.getAPI().isInitialized()) {
            getLogger().severe("PacketEvents is not initialized. Check your PacketEvents installation.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getCommand("herobrine").setTabCompleter(this);

        encounterState = new EncounterState(this);
        encounterState.load();
        herobrineController = new HerobrineController(this, encounterState);
        ritualManager = new RitualManager(this, encounterState, herobrineController);
        horrorManager = new HorrorManager(this, encounterState);
        herobrineController.setHorrorManager(horrorManager);
        getServer().getPluginManager().registerEvents(ritualManager, this);
        getServer().getPluginManager().registerEvents(herobrineController, this);
        herobrineController.start();
        horrorManager.start();

        getLogger().info("HerobrineLegend 0.4.4 enabled with PacketEvents.");
        if (encounterState.isActive()) {
            getLogger().info("Herobrine is already summoned: the encounter system has been restored.");
        }
    }

    @Override
    public void onDisable() {
        if (horrorManager != null) horrorManager.shutdown();
        if (herobrineController != null) herobrineController.shutdown();
        if (encounterState != null) encounterState.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("herobrine")) return false;
        if (!sender.hasPermission("herobrinelegend.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "HerobrineLegend: "
                    + (encounterState.isActive() ? ChatColor.GREEN + "SUMMONED" : ChatColor.GRAY + "INACTIVE"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            herobrineController.refreshConfiguration();
            horrorManager.refreshConfiguration();
            sender.sendMessage(ChatColor.GREEN + "Configuration reloaded.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("jumpscare")) {
            if (!encounterState.isActive()) {
                sender.sendMessage(ChatColor.RED + "Herobrine is not summoned. Activate the altar first.");
                return true;
            }
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(ChatColor.RED + "Player is not online."); return true; }
            sender.sendMessage(herobrineController.forceJumpscare(player)
                    ? ChatColor.GREEN + "Forced appearance near " + player.getName()
                    : ChatColor.RED + "Unable to spawn Herobrine near the player.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("attack")) {
            if (!encounterState.isActive()) {
                sender.sendMessage(ChatColor.RED + "Herobrine is not summoned. Activate the altar first.");
                return true;
            }
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) {
                sender.sendMessage(ChatColor.RED + "Player is not online.");
                return true;
            }
            if (herobrineController.forceAttack(player)) {
                sender.sendMessage(ChatColor.GREEN + "Forced Herobrine attack on " + player.getName() + " started (5 seconds).");
            } else {
                sender.sendMessage(ChatColor.RED + "Unable to spawn Herobrine near " + player.getName() + ". Check available space and the skin.");
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fear")) {
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(ChatColor.RED + "Player is not online."); return true; }
            sender.sendMessage(ChatColor.GOLD + player.getName() + ": Fear " + horrorManager.getFear(player) + "/100");
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setfear")) {
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(ChatColor.RED + "Player is not online."); return true; }
            try {
                int amount = Integer.parseInt(args[2]);
                horrorManager.setFear(player, amount);
                sender.sendMessage(ChatColor.GREEN + "Fear for " + player.getName() + " set to " + horrorManager.getFear(player));
            } catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Enter a whole number."); }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/herobrine status | jumpscare <player> | attack <player> | fear <player> | setfear <player> <amount> | reload");
        return true;
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("herobrine")
                || !sender.hasPermission("herobrinelegend.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) return matching(SUBCOMMANDS, args[0]);
        if (args.length == 2 && PLAYER_ARGUMENT_COMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = new ArrayList<>();
            for (Player player : getServer().getOnlinePlayers()) {
                if (sender instanceof Player && !((Player) sender).canSee(player)) continue;
                names.add(player.getName());
            }
            return matching(names, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setfear")) {
            return matching(FEAR_VALUES, args[2]);
        }
        return Collections.emptyList();
    }

    private static List<String> matching(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) matches.add(value);
        }
        return matches;
    }

}
