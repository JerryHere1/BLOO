package com.superbot.plugin.holograms;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HologramManager {

    private final SuperBotPlugin plugin;
    // npcId -> ArmorStand UUID
    private final Map<Integer, UUID> holograms = new HashMap<>();

    public HologramManager(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void update(BotInstance bot) {
        if (!plugin.getConfig().getBoolean("holograms.enabled", true)) return;
        if (bot.getNpc() == null || !bot.getNpc().isSpawned()) return;

        double offsetY = plugin.getConfig().getDouble("holograms.height-offset", 2.3);
        Location loc = bot.getNpc().getEntity().getLocation().add(0, offsetY, 0);

        String text = SuperBotPlugin.colorize("&7[&e" + bot.getStatusLabel() + "&7]");

        UUID armorStandUUID = holograms.get(bot.getNpcId());

        // Try to find existing hologram
        if (armorStandUUID != null) {
            Entity existing = loc.getWorld().getEntity(armorStandUUID);
            if (existing instanceof ArmorStand as) {
                as.teleport(loc);
                as.setCustomName(text);
                return;
            }
        }

        // Spawn new hologram armor stand
        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setGravity(false);
        as.setVisible(false);
        as.setCustomNameVisible(true);
        as.setCustomName(text);
        as.setSmall(true);
        as.setInvulnerable(true);
        as.setCollidable(false);

        holograms.put(bot.getNpcId(), as.getUniqueId());
    }

    public void remove(BotInstance bot) {
        UUID uuid = holograms.remove(bot.getNpcId());
        if (uuid == null) return;
        if (bot.getNpc() != null && bot.getNpc().isSpawned()) {
            Entity e = bot.getNpc().getEntity().getWorld().getEntity(uuid);
            if (e != null) e.remove();
        }
    }

    public void removeAll() {
        // Hologram armor stands are world entities; they'll be cleaned on restart.
        // For a clean shutdown, despawn all.
        holograms.forEach((npcId, uuid) -> {
            plugin.getDataManager().getAllBots().stream()
                .filter(b -> b.getNpcId() == npcId)
                .findFirst()
                .ifPresent(this::remove);
        });
        holograms.clear();
    }
}
