package com.superbot.plugin.fakeplayers;

import com.superbot.plugin.SuperBotPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class FakePlayerListener implements Listener {

    private final SuperBotPlugin plugin;

    public FakePlayerListener(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Send fake tab entries to the player when they join
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                plugin.getFakePlayerManager().sendFakeTabEntries(event.getPlayer()), 20L);
    }
}
