package it.gesy.herobrine;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class HerobrineLegendPlugin extends JavaPlugin {
    private RitualManager ritualManager;
    private EncounterState encounterState;
    private HerobrineController herobrineController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("packetevents") == null) {
            getLogger().severe("PacketEvents non trovato. HerobrineLegend richiede PacketEvents 2.14.0 o superiore.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!PacketEvents.getAPI().isInitialized()) {
            getLogger().severe("PacketEvents non è inizializzato. Controlla l'installazione di PacketEvents.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        encounterState = new EncounterState(this);
        encounterState.load();
        herobrineController = new HerobrineController(this, encounterState);
        ritualManager = new RitualManager(this, encounterState, herobrineController);
        getServer().getPluginManager().registerEvents(ritualManager, this);
        getServer().getPluginManager().registerEvents(herobrineController, this);
        herobrineController.start();

        getLogger().info("HerobrineLegend 0.3.1 abilitato con PacketEvents.");
        if (encounterState.isActive()) {
            getLogger().info("Herobrine risulta già evocato: il sistema di incontri è stato ripristinato.");
        }
    }

    @Override
    public void onDisable() {
        if (herobrineController != null) herobrineController.shutdown();
        if (encounterState != null) encounterState.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("herobrinelegend")) return false;
        if (!sender.hasPermission("herobrinelegend.admin")) {
            sender.sendMessage(ChatColor.RED + "Non hai il permesso.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "HerobrineLegend: "
                    + (encounterState.isActive() ? ChatColor.GREEN + "EVOCATO" : ChatColor.GRAY + "DISATTIVATO"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            herobrineController.refreshConfiguration();
            sender.sendMessage(ChatColor.GREEN + "Configurazione ricaricata.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/herobrinelegend status|reload");
        return true;
    }
}
