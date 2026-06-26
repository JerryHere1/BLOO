package com.superbot.plugin.commands;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import com.superbot.plugin.data.BotMode;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SpawnBotCommand implements CommandExecutor {

    private final SuperBotPlugin plugin;

    public SpawnBotCommand(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        String permission = plugin.getConfig().getString("spawn-permission", "superbot.spawn");
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            player.sendMessage(plugin.msg("no-permission"));
            return true;
        }

        int maxBots = plugin.getConfig().getInt("max-bots-per-player", 1);
        if (plugin.getDataManager().countBotsFor(player.getUniqueId()) >= maxBots) {
            player.sendMessage(plugin.msg("max-bots", "{max}", String.valueOf(maxBots)));
            return true;
        }

        // Create the Citizens NPC
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        String botName = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("bot-name", "&6SuperBot"));

        NPC npc = registry.createNPC(EntityType.PLAYER, botName);
        npc.spawn(player.getLocation());

        // Make it vulnerable to damage
        npc.setProtected(false);

        // Load saved backpack if any
        ItemStack[] savedBackpack = plugin.getDataManager().loadBackpack(player.getUniqueId());
        BotMode savedMode = plugin.getDataManager().loadMode(player.getUniqueId());

        BotInstance bot = new BotInstance(player.getUniqueId(), npc.getId(), npc);
        bot.setMode(savedMode);
        bot.setStatusLabel("Idle");

        // Restore backpack contents
        ItemStack[] bp = bot.getBackpack();
        for (int i = 0; i < savedBackpack.length; i++) {
            bp[i] = savedBackpack[i];
        }
        bot.setBackpackFull(bot.isActuallyFull());

        plugin.getDataManager().registerBot(bot);

        player.sendMessage(plugin.msg("spawned"));
        return true;
    }
}
