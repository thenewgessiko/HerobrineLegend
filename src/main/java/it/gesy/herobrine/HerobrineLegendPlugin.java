package it.gesy.herobrine;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class HerobrineLegendPlugin extends JavaPlugin {
    private RitualManager ritualManager;
    private HorrorManager horrorManager;
    private EncounterState encounterState;
    private HerobrineController herobrineController;

    public HorrorManager getHorrorManager() { return horrorManager; }

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
        horrorManager = new HorrorManager(this, encounterState);
        herobrineController.setHorrorManager(horrorManager);
        getServer().getPluginManager().registerEvents(ritualManager, this);
        getServer().getPluginManager().registerEvents(herobrineController, this);
        herobrineController.start();
        horrorManager.start();

        getLogger().info("HerobrineLegend 0.4.4 FINAL abilitato con PacketEvents.");
        if (encounterState.isActive()) {
            getLogger().info("Herobrine risulta già evocato: il sistema di incontri è stato ripristinato.");
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
            horrorManager.refreshConfiguration();
            sender.sendMessage(ChatColor.GREEN + "Configurazione ricaricata.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("jumpscare")) {
            if (!encounterState.isActive()) {
                sender.sendMessage(ChatColor.RED + "Herobrine non è evocato: attiva prima l'altare.");
                return true;
            }
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(ChatColor.RED + "Giocatore non online."); return true; }
            sender.sendMessage(herobrineController.forceJumpscare(player)
                    ? ChatColor.GREEN + "Apparizione forzata vicino a " + player.getName()
                    : ChatColor.RED + "Impossibile creare l'apparizione vicino al giocatore.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("attack")) {
            if (!encounterState.isActive()) {
                sender.sendMessage(ChatColor.RED + "Herobrine non è evocato: attiva prima l'altare.");
                return true;
            }
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) {
                sender.sendMessage(ChatColor.RED + "Giocatore non online.");
                return true;
            }
            if (herobrineController.forceAttack(player)) {
                sender.sendMessage(ChatColor.GREEN + "Attacco forzato di Herobrine contro " + player.getName() + " avviato (5 secondi).");
            } else {
                sender.sendMessage(ChatColor.RED + "Impossibile generare Herobrine vicino a " + player.getName() + ". Controlla lo spazio disponibile e la skin.");
            }
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("fear")) {
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(ChatColor.RED + "Giocatore non online."); return true; }
            sender.sendMessage(ChatColor.GOLD + player.getName() + ": paura " + horrorManager.getFear(player) + "/100");
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setfear")) {
            org.bukkit.entity.Player player = getServer().getPlayerExact(args[1]);
            if (player == null) { sender.sendMessage(ChatColor.RED + "Giocatore non online."); return true; }
            try {
                int amount = Integer.parseInt(args[2]);
                horrorManager.setFear(player, amount);
                sender.sendMessage(ChatColor.GREEN + "Paura di " + player.getName() + " impostata a " + horrorManager.getFear(player));
            } catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Inserisci un numero intero."); }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "/herobrine status | jumpscare <player> | attack <player> | fear <player> | setfear <player> <amount> | reload");
        return true;
    }
}
