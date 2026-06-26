package com.superbot.plugin.commands;

import com.superbot.plugin.SuperBotPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final SuperBotPlugin plugin;

    public ReloadCommand(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("superbot.admin")) {
            sender.sendMessage(plugin.msg("no-permission"));
            return true;
        }
        plugin.reloadConfig();
        sender.sendMessage(plugin.msg("reloaded"));
        return true;
    }
}
