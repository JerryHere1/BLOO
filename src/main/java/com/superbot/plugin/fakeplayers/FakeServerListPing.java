package com.superbot.plugin.fakeplayers;

import com.superbot.plugin.SuperBotPlugin;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class FakeServerListPing implements Listener {

    private final SuperBotPlugin plugin;

    public FakeServerListPing(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerListPing(PaperServerListPingEvent event) {
        // ── Fake player COUNT (shown before joining) ──────────────
        if (plugin.getConfig().getBoolean("fake-players.server-list.enabled", true)) {
            int realPlayers  = event.getNumPlayers();
            int fakeExtra    = plugin.getConfig().getInt("fake-players.server-list.extra-count", 20);
            int maxPlayers   = plugin.getConfig().getInt("fake-players.server-list.max-players", 100);

            event.setNumPlayers(realPlayers + fakeExtra);
            event.setMaxPlayers(maxPlayers);
        }

        // ── Fake player NAMES in the hover tooltip ────────────────
        if (plugin.getConfig().getBoolean("fake-players.server-list.show-names-in-hover", true)) {
            // Paper lets us add fake entries to the sample shown on hover
            for (String name : plugin.getFakePlayerManager().getFakeNames()) {
                // Add up to 5 names to the hover sample (more looks spammy)
                if (event.getPlayerSample().size() >= 5) break;
                com.destroystokyo.paper.profile.PlayerProfile profile =
                        plugin.getServer().createProfile(
                                java.util.UUID.nameUUIDFromBytes(("FakePlayer:" + name).getBytes()),
                                name);
                event.getPlayerSample().add(profile);
            }
        }
    }
}
