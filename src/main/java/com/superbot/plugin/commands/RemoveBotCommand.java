package com.superbot.plugin.commands;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import com.superbot.plugin.holograms.HologramManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RemoveBotCommand implements CommandExecutor {

    private final SuperBotPlugin plugin;

    public RemoveBotCommand(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        BotInstance bot = plugin.getDataManager().getBotByOwner(player.getUniqueId());
        if (bot == null) {
            player.sendMessage(plugin.msg("bot-not-found"));
            return true;
        }

        // Save backpack before removing
        plugin.getDataManager().saveBotData(bot);

        // Remove hologram
        HologramManager holo = plugin.getAiManager().getHologramManager();
        if (holo != null) holo.remove(bot);

        // Despawn and destroy NPC
        NPC npc = bot.getNpc();
        if (npc != null) {
            if (npc.isSpawned()) npc.despawn();
            CitizensAPI.getNPCRegistry().deregister(npc);
        }

        plugin.getDataManager().removeBot(player.getUniqueId());
        player.sendMessage(plugin.msg("removed"));
        return true;
    }
}
