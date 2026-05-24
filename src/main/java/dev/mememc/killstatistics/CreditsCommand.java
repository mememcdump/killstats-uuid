package dev.mememc.killstatistics;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class CreditsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        sender.sendMessage(ChatColor.GRAY + "");
        sender.sendMessage(ChatColor.GOLD + "Silver SMP");
        sender.sendMessage(ChatColor.GRAY + "");
        sender.sendMessage(ChatColor.YELLOW + "Owner: Sammy & Silver");
        sender.sendMessage(ChatColor.GRAY + "Developer: " + ChatColor.AQUA + "MemeIsLIVE");
        sender.sendMessage(ChatColor.GRAY + "");
        sender.sendMessage(ChatColor.GOLD + "mememc.club");
        sender.sendMessage(ChatColor.GRAY + "");
        return true;
    }
}
