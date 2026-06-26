package com.superbot.plugin.listeners;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import com.superbot.plugin.data.BotMode;
import com.superbot.plugin.gui.ControlPanelGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class GuiListener implements Listener {

    private final SuperBotPlugin plugin;

    public GuiListener(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // ── Control Panel ─────────────────────────────────────────
        if (title.equals(ControlPanelGui.CONTROL_TITLE)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;

            BotInstance bot = plugin.getDataManager().getBotByOwner(player.getUniqueId());
            if (bot == null) return;

            int slot = event.getRawSlot();
            switch (slot) {
                case 10 -> setMode(player, bot, BotMode.MINING,     "⛏ Mining");
                case 11 -> setMode(player, bot, BotMode.LUMBERJACK, "🪓 Lumberjack");
                case 12 -> setMode(player, bot, BotMode.FARMING,    "🌾 Farming");
                case 14 -> setMode(player, bot, BotMode.COMBAT,     "⚔ Combat");
                case 16 -> {
                    player.closeInventory();
                    ControlPanelGui.openBackpack(player, bot);
                }
                case 22 -> setMode(player, bot, BotMode.IDLE, "Idle");
            }
            return;
        }

        // ── Backpack ──────────────────────────────────────────────
        if (title.equals(ControlPanelGui.BACKPACK_TITLE)) {
            // Allow free interaction — items can be dragged out
            // We'll sync on close
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals(ControlPanelGui.BACKPACK_TITLE)) {
            BotInstance bot = plugin.getDataManager().getBotByOwner(player.getUniqueId());
            if (bot == null) return;

            // Sync inventory contents back into the bot's backpack array
            ItemStack[] contents = event.getInventory().getContents();
            ItemStack[] backpack = bot.getBackpack();
            for (int i = 0; i < 27; i++) {
                backpack[i] = (i < contents.length) ? contents[i] : null;
            }

            // Re-evaluate full status
            bot.setBackpackFull(bot.isActuallyFull());

            // Persist
            plugin.getDataManager().saveBotData(bot);
        }
    }

    private void setMode(Player player, BotInstance bot, BotMode mode, String label) {
        bot.setMode(mode);
        bot.setStatusLabel(label);
        plugin.getDataManager().saveBotData(bot);
        player.sendMessage(plugin.msg("prefix") + "§eBot mode set to §a" + mode.name());
        // Refresh the GUI
        ControlPanelGui.openControlPanel(player, bot);
    }
}
