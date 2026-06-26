package com.superbot.plugin.data;

import com.superbot.plugin.SuperBotPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class BotDataManager {

    private final SuperBotPlugin plugin;
    private Connection connection;

    // In-memory registry: ownerUUID -> BotInstance
    private final Map<UUID, BotInstance> activeBots = new HashMap<>();

    public BotDataManager(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "bots.db");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
            plugin.getLogger().info("SQLite database connected.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to SQLite database", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bot_data (
                    owner_uuid TEXT PRIMARY KEY,
                    npc_id     INTEGER NOT NULL,
                    mode       TEXT    NOT NULL DEFAULT 'IDLE',
                    backpack   BLOB
                )
            """);
        }
    }

    public void shutdown() {
        // Persist all active bots
        for (BotInstance bot : activeBots.values()) {
            saveBotData(bot);
        }
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing database", e);
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────

    public void registerBot(BotInstance bot) {
        activeBots.put(bot.getOwnerUuid(), bot);
        saveBotData(bot);
    }

    public void removeBot(UUID ownerUuid) {
        activeBots.remove(ownerUuid);
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM bot_data WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to delete bot data", e);
        }
    }

    public BotInstance getBotByOwner(UUID ownerUuid) {
        return activeBots.get(ownerUuid);
    }

    public BotInstance getBotByNpcId(int npcId) {
        return activeBots.values().stream()
                .filter(b -> b.getNpcId() == npcId)
                .findFirst().orElse(null);
    }

    public Collection<BotInstance> getAllBots() {
        return Collections.unmodifiableCollection(activeBots.values());
    }

    public boolean hasBot(UUID ownerUuid) {
        return activeBots.containsKey(ownerUuid);
    }

    public int countBotsFor(UUID ownerUuid) {
        return (int) activeBots.values().stream()
                .filter(b -> b.getOwnerUuid().equals(ownerUuid))
                .count();
    }

    public void saveBotData(BotInstance bot) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO bot_data (owner_uuid, npc_id, mode, backpack)
                VALUES (?, ?, ?, ?)
            """)) {
            ps.setString(1, bot.getOwnerUuid().toString());
            ps.setInt(2, bot.getNpcId());
            ps.setString(3, bot.getMode().name());
            ps.setBytes(4, serializeBackpack(bot.getBackpack()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save bot data for " + bot.getOwnerUuid(), e);
        }
    }

    public Map<UUID, int[]> loadAllNpcIds() {
        Map<UUID, int[]> result = new HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT owner_uuid, npc_id, mode, backpack FROM bot_data")) {
            while (rs.next()) {
                UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
                int npcId = rs.getInt("npc_id");
                result.put(ownerUuid, new int[]{npcId});
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load bot NPC IDs", e);
        }
        return result;
    }

    public BotMode loadMode(UUID ownerUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT mode FROM bot_data WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return BotMode.valueOf(rs.getString("mode"));
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load mode for " + ownerUuid, e);
        }
        return BotMode.IDLE;
    }

    public ItemStack[] loadBackpack(UUID ownerUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT backpack FROM bot_data WHERE owner_uuid = ?")) {
            ps.setString(1, ownerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    byte[] blob = rs.getBytes("backpack");
                    if (blob != null) return deserializeBackpack(blob);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load backpack for " + ownerUuid, e);
        }
        return new ItemStack[27];
    }

    // ── Serialization ─────────────────────────────────────────────

    private byte[] serializeBackpack(ItemStack[] items) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeInt(items.length);
            for (ItemStack item : items) oos.writeObject(item);
            return bos.toByteArray();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to serialize backpack", e);
            return new byte[0];
        }
    }

    private ItemStack[] deserializeBackpack(byte[] data) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bis)) {
            int len = ois.readInt();
            ItemStack[] items = new ItemStack[len];
            for (int i = 0; i < len; i++) items[i] = (ItemStack) ois.readObject();
            return items;
        } catch (IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to deserialize backpack", e);
            return new ItemStack[27];
        }
    }
}
