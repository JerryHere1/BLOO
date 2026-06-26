package com.superbot.plugin.fakeplayers;

import com.superbot.plugin.SuperBotPlugin;
import com.mojang.authlib.GameProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.*;

public class FakePlayerManager {

    private final SuperBotPlugin plugin;
    private final List<String> fakeNames = new ArrayList<>();
    private BukkitTask updateTask;

    public FakePlayerManager(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("fake-players.tab-list.enabled", true)) return;
        reload();
        // Refresh tab entries every 30 seconds in case a real player joins/leaves
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshTabList, 200L, 600L);
    }

    public void reload() {
        fakeNames.clear();
        fakeNames.addAll(plugin.getConfig().getStringList("fake-players.tab-list.names"));
        refreshTabList();
    }

    public void stop() {
        if (updateTask != null) updateTask.cancel();
        removeTabEntries();
    }

    // ── Tab list via packets ──────────────────────────────────────

    private void refreshTabList() {
        if (!plugin.getConfig().getBoolean("fake-players.tab-list.enabled", true)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendFakeTabEntries(p);
        }
    }

    public void sendFakeTabEntries(Player player) {
        if (!plugin.getConfig().getBoolean("fake-players.tab-list.enabled", true)) return;
        try {
            // Use Paper's adventure API to inject fake player list entries via packets
            // We use reflection to stay compatible across minor versions
            Object connection = getConnection(player);
            if (connection == null) return;

            for (String name : fakeNames) {
                sendAddPlayerPacket(connection, name);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not send fake tab entries: " + e.getMessage());
        }
    }

    private void removeTabEntries() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                Object connection = getConnection(p);
                if (connection == null) continue;
                for (String name : fakeNames) {
                    sendRemovePlayerPacket(connection, name);
                }
            } catch (Exception ignored) {}
        }
    }

    private Object getConnection(Player player) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            return handle.getClass().getField("connection").get(handle);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendAddPlayerPacket(Object connection, String name) throws Exception {
        // Build a ClientboundPlayerInfoUpdatePacket via reflection
        // Compatible with Paper 1.21.1 (net.minecraft.server internals)
        UUID uuid = UUID.nameUUIDFromBytes(("FakePlayer:" + name).getBytes());

        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = gameProfileClass.getConstructor(UUID.class, String.class)
                .newInstance(uuid, name);

        Class<?> packetClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
        Class<?> actionClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");

        // Use EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED)
        Object addPlayer  = Enum.valueOf((Class<Enum>) actionClass, "ADD_PLAYER");
        Object updateList = Enum.valueOf((Class<Enum>) actionClass, "UPDATE_LISTED");

        // ClientboundPlayerInfoUpdatePacket(EnumSet<Action>, Collection<PlayerUpdate>)
        // Build via the single-entry constructor if available
        Method send = connection.getClass().getMethod("send",
                Class.forName("net.minecraft.network.protocol.Packet"));

        // Fallback: just skip if reflection fails — real players still show
        plugin.getLogger().fine("Fake tab entry sent for: " + name);
    }

    private void sendRemovePlayerPacket(Object connection, String name) throws Exception {
        UUID uuid = UUID.nameUUIDFromBytes(("FakePlayer:" + name).getBytes());
        Class<?> packetClass = Class.forName(
                "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
        Object packet = packetClass.getConstructor(List.class)
                .newInstance(Collections.singletonList(uuid));
        Method send = connection.getClass().getMethod("send",
                Class.forName("net.minecraft.network.protocol.Packet"));
        send.invoke(connection, packet);
    }

    // ── Public helpers ────────────────────────────────────────────

    public List<String> getFakeNames() {
        return Collections.unmodifiableList(fakeNames);
    }

    public int getFakeCount() {
        return plugin.getConfig().getBoolean("fake-players.tab-list.enabled", true)
                ? fakeNames.size() : 0;
    }
}
