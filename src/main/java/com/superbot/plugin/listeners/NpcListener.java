package com.superbot.plugin.listeners;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import com.superbot.plugin.gui.ControlPanelGui;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NpcListener implements Listener {

    private final SuperBotPlugin plugin;

    public NpcListener(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC npc       = event.getNPC();

        BotInstance bot = plugin.getDataManager().getBotByNpcId(npc.getId());
        if (bot == null) return;

        // Only the owner can open the panel
        if (!bot.getOwnerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        ControlPanelGui.openControlPanel(player, bot);
    }
}
