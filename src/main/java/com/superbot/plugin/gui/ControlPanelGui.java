package com.superbot.plugin.gui;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import com.superbot.plugin.data.BotMode;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

public class ControlPanelGui {

    public static final String CONTROL_TITLE = ChatColor.DARK_GRAY + "⚙ " + ChatColor.GOLD + "SuperBot Control Panel";
    public static final String BACKPACK_TITLE = ChatColor.DARK_GRAY + "🎒 " + ChatColor.GOLD + "SuperBot Backpack";

    public static void openControlPanel(Player player, BotInstance bot) {
        Inventory inv = Bukkit.createInventory(null, 27, CONTROL_TITLE);

        // Fill border with glass
        ItemStack glass = makeItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, glass);

        // Slot 10: Mining Mode
        inv.setItem(10, makeItem(Material.DIAMOND_PICKAXE,
                ChatColor.AQUA + "⛏ Mining Mode",
                ChatColor.GRAY + "Bot will scan for ores",
                ChatColor.GRAY + "and dig tunnels to find them.",
                "",
                bot.getMode() == BotMode.MINING
                        ? ChatColor.GREEN + "▶ ACTIVE"
                        : ChatColor.YELLOW + "Click to activate"));

        // Slot 11: Lumberjack Mode
        inv.setItem(11, makeItem(Material.DIAMOND_AXE,
                ChatColor.GREEN + "🪓 Lumberjack Mode",
                ChatColor.GRAY + "Bot will chop down logs",
                ChatColor.GRAY + "and roam for more trees.",
                "",
                bot.getMode() == BotMode.LUMBERJACK
                        ? ChatColor.GREEN + "▶ ACTIVE"
                        : ChatColor.YELLOW + "Click to activate"));

        // Slot 12: Farming Mode
        inv.setItem(12, makeItem(Material.DIAMOND_HOE,
                ChatColor.YELLOW + "🌾 Farming Mode",
                ChatColor.GRAY + "Bot will harvest mature crops",
                ChatColor.GRAY + "and instantly replant them.",
                "",
                bot.getMode() == BotMode.FARMING
                        ? ChatColor.GREEN + "▶ ACTIVE"
                        : ChatColor.YELLOW + "Click to activate"));

        // Slot 14: Combat Mode
        inv.setItem(14, makeItem(Material.DIAMOND_SWORD,
                ChatColor.RED + "⚔ Combat Mode",
                ChatColor.GRAY + "Bot will actively hunt",
                ChatColor.GRAY + "hostile mobs nearby.",
                "",
                bot.getMode() == BotMode.COMBAT
                        ? ChatColor.GREEN + "▶ ACTIVE"
                        : ChatColor.YELLOW + "Click to activate"));

        // Slot 13: Status display
        String statusColor = bot.isBackpackFull() ? ChatColor.RED.toString() : ChatColor.GREEN.toString();
        inv.setItem(13, makeItem(Material.NETHER_STAR,
                ChatColor.GOLD + "★ Bot Status",
                ChatColor.GRAY + "Mode: " + ChatColor.WHITE + bot.getMode().name(),
                ChatColor.GRAY + "Backpack: " + statusColor + (bot.isBackpackFull() ? "FULL" : "OK"),
                ChatColor.GRAY + "Status: " + ChatColor.WHITE + bot.getStatusLabel()));

        // Slot 16: Backpack
        inv.setItem(16, makeItem(Material.CHEST,
                ChatColor.GOLD + "🎒 View Backpack",
                ChatColor.GRAY + "Open the bot's virtual storage.",
                "",
                bot.isBackpackFull()
                        ? ChatColor.RED + "⚠ Backpack is FULL!"
                        : ChatColor.GREEN + "Click to open"));

        // Slot 22: Stop / Idle
        inv.setItem(22, makeItem(Material.BARRIER,
                ChatColor.RED + "⏹ Set Idle",
                ChatColor.GRAY + "Pause all bot activity."));

        player.openInventory(inv);
    }

    public static void openBackpack(Player player, BotInstance bot) {
        Inventory inv = Bukkit.createInventory(null, 27, BACKPACK_TITLE);
        ItemStack[] backpack = bot.getBackpack();
        for (int i = 0; i < Math.min(backpack.length, 27); i++) {
            inv.setItem(i, backpack[i]);
        }
        player.openInventory(inv);
    }

    // ── Item builder ──────────────────────────────────────────────

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }
}
