package me.miyeoukman.lschat.command;

import me.miyeoukman.lschat.Main;
import me.miyeoukman.lschat.api.event.AsyncLiveChatEvent;
import me.miyeoukman.lschat.api.event.AsyncLiveDonationEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class LSChatCommand implements CommandExecutor {

    private final Main plugin;

    public LSChatCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        try {
            if (args.length == 0) {
                sender.sendMessage(ChatColor.AQUA + "LSChat v" + plugin.getDescription().getVersion());
                sender.sendMessage(ChatColor.GRAY + "/lschat reload - Config reload");
                sender.sendMessage(ChatColor.GRAY + "/lschat test <msg> - Test chat event");
                sender.sendMessage(ChatColor.GRAY + "/lschat testdonate <user> <amount> <msg> - Test donation event");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("lschat.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }
                plugin.reload();
                sender.sendMessage(ChatColor.GREEN + "LSChat configuration reloaded!");
                return true;
            }

            if (args[0].equalsIgnoreCase("test")) {
                if (!sender.hasPermission("lschat.admin")) return true;
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /lschat test <message>");
                    return true;
                }
                
                // Safer message reconstruction
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                String msg = sb.toString().trim();
                
                // Fire event asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> 
                    Bukkit.getPluginManager().callEvent(new AsyncLiveChatEvent("Test", "Tester", msg))
                );
                
                sender.sendMessage(ChatColor.GREEN + "Test chat event fired.");
                return true;
            }
            
            if (args[0].equalsIgnoreCase("testdonate")) {
                if (!sender.hasPermission("lschat.admin")) return true;
                // /lschat testdonate <user> <amount> <msg>
                if (args.length < 4) {
                     sender.sendMessage(ChatColor.RED + "Usage: /lschat testdonate <user> <amount> <msg>");
                     return true;
                }
                String user = args[1];
                double amount;
                try {
                    amount = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount.");
                    return true;
                }
                
                // Reconstruct message from remaining args
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    sb.append(args[i]).append(" ");
                }
                String msg = sb.toString().trim();
                
                // Fire event asynchronously
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> 
                    Bukkit.getPluginManager().callEvent(new AsyncLiveDonationEvent("Test", user, msg, amount))
                );
                
                sender.sendMessage(ChatColor.GREEN + "Test donation event fired.");
                return true;
            }
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
}
