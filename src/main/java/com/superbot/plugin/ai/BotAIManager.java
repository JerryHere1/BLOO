package com.superbot.plugin.ai;

import com.superbot.plugin.SuperBotPlugin;
import com.superbot.plugin.data.BotInstance;
import com.superbot.plugin.data.BotMode;
import com.superbot.plugin.holograms.HologramManager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class BotAIManager {

    private final SuperBotPlugin plugin;
    private HologramManager hologramManager;
    private BukkitTask aiTask;
    private BukkitTask combatTask;

    public BotAIManager(SuperBotPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        hologramManager = new HologramManager(plugin);

        int aiInterval     = plugin.getConfig().getInt("ai.tick-interval", 40);
        int combatInterval = plugin.getConfig().getInt("ai.combat-tick-interval", 10);

        aiTask = new BukkitRunnable() {
            @Override public void run() {
                for (BotInstance bot : plugin.getDataManager().getAllBots()) {
                    if (!isNpcSpawned(bot)) continue;
                    tickBot(bot);
                }
            }
        }.runTaskTimer(plugin, aiInterval, aiInterval);

        combatTask = new BukkitRunnable() {
            @Override public void run() {
                for (BotInstance bot : plugin.getDataManager().getAllBots()) {
                    if (!isNpcSpawned(bot)) continue;
                    tickCombat(bot);
                }
            }
        }.runTaskTimer(plugin, combatInterval, combatInterval);
    }

    public void stop() {
        if (aiTask != null)     aiTask.cancel();
        if (combatTask != null) combatTask.cancel();
        if (hologramManager != null) hologramManager.removeAll();
    }

    // ── Utility ──────────────────────────────────────────────────

    private boolean isNpcSpawned(BotInstance bot) {
        NPC npc = bot.getNpc();
        return npc != null && npc.isSpawned() && npc.getEntity() != null;
    }

    private Entity getBotEntity(BotInstance bot) {
        return bot.getNpc().getEntity();
    }

    private Location getBotLocation(BotInstance bot) {
        return getBotEntity(bot).getLocation();
    }

    // ── Priority 1: Combat ────────────────────────────────────────

    private void tickCombat(BotInstance bot) {
        if (bot.isBackpackFull() && bot.getMode() != BotMode.COMBAT) return;

        double radius = plugin.getConfig().getDouble("ai.combat-scan-radius", 6);
        Location loc  = getBotLocation(bot);

        List<Entity> nearby = getBotEntity(bot).getNearbyEntities(radius, radius, radius);
        LivingEntity target = null;
        for (Entity e : nearby) {
            if (e instanceof Monster mob) {
                target = mob;
                break;
            }
        }

        if (target == null) return;

        // Face the target
        faceEntity(bot, target);

        // Equip sword
        equipItem(bot, Material.DIAMOND_SWORD);

        // Swing arm animation
        swingArm(bot);

        // Deal damage
        double dmg = plugin.getConfig().getDouble("ai.combat-damage", 4.0);
        target.damage(dmg, getBotEntity(bot));

        bot.setStatusLabel("⚔ Combat!");
        updateHologram(bot);
    }

    // ── Priority 2: Gathering ─────────────────────────────────────

    private void tickBot(BotInstance bot) {
        if (bot.getMode() == BotMode.IDLE || bot.getMode() == BotMode.COMBAT) return;

        if (bot.isBackpackFull()) {
            bot.setStatusLabel("📦 Backpack Full!");
            updateHologram(bot);
            notifyOwner(bot, "backpack-full");
            return;
        }

        boolean worked = false;

        switch (bot.getMode()) {
            case MINING    -> worked = tickMining(bot);
            case LUMBERJACK -> worked = tickLumberjack(bot);
            case FARMING   -> worked = tickFarming(bot);
            default        -> {}
        }

        // Priority 3: roam if nothing to do
        if (!worked) tickRoam(bot);
    }

    // ── Mining ───────────────────────────────────────────────────

    private boolean tickMining(BotInstance bot) {
        int radius = plugin.getConfig().getInt("ai.mining-scan-radius", 5);
        Location loc = getBotLocation(bot);

        Block ore = scanForOre(loc, radius);
        if (ore == null) {
            // Tunneling: break block directly in front
            Block front = getFrontBlock(bot);
            if (front != null && front.getType().isSolid() && !front.getType().name().contains("ORE")) {
                mineBlock(bot, front, front.getType(), new ItemStack(Material.COBBLESTONE, 1));
                bot.setStatusLabel("⛏ Tunneling...");
                updateHologram(bot);
                return true;
            }
            return false;
        }

        faceBlock(bot, ore);
        equipItem(bot, Material.DIAMOND_PICKAXE);
        swingArm(bot);

        // Get configured drop
        String blockKey = ore.getType().name();
        String dropKey  = plugin.getConfig().getString("mining.ores." + blockKey + ".drop", "COBBLESTONE");
        int    dropAmt  = plugin.getConfig().getInt("mining.ores." + blockKey + ".amount", 1);
        Material dropMat = Material.matchMaterial(dropKey);
        if (dropMat == null) dropMat = Material.COBBLESTONE;

        mineBlock(bot, ore, Material.STONE, new ItemStack(dropMat, dropAmt));
        bot.setStatusLabel("⛏ Mining " + formatName(ore.getType()) + "...");
        updateHologram(bot);
        return true;
    }

    private Block scanForOre(Location center, int radius) {
        Set<String> oreKeys = plugin.getConfig().getConfigurationSection("mining.ores") != null
                ? plugin.getConfig().getConfigurationSection("mining.ores").getKeys(false)
                : new HashSet<>();

        World world = center.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = world.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (oreKeys.contains(b.getType().name())) return b;
                }
            }
        }
        return null;
    }

    // ── Lumberjack ───────────────────────────────────────────────

    private boolean tickLumberjack(BotInstance bot) {
        int radius = plugin.getConfig().getInt("ai.lumber-scan-radius", 5);
        Location loc = getBotLocation(bot);

        List<String> logTypes = plugin.getConfig().getStringList("lumber.logs");
        Block log = scanForBlock(loc, radius, logTypes);
        if (log == null) return false;

        faceBlock(bot, log);
        equipItem(bot, Material.DIAMOND_AXE);
        swingArm(bot);
        mineBlock(bot, log, Material.AIR, new ItemStack(log.getType(), 1));

        bot.setStatusLabel("🪓 Chopping " + formatName(log.getType()) + "...");
        updateHologram(bot);
        return true;
    }

    // ── Farming ──────────────────────────────────────────────────

    private boolean tickFarming(BotInstance bot) {
        int radius = plugin.getConfig().getInt("ai.farming-scan-radius", 4);
        Location loc = getBotLocation(bot);
        World world = loc.getWorld();

        Set<String> cropKeys = plugin.getConfig().getConfigurationSection("farming.crops") != null
                ? plugin.getConfig().getConfigurationSection("farming.crops").getKeys(false)
                : new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block b = world.getBlockAt(loc.getBlockX() + x, loc.getBlockY(), loc.getBlockZ() + z);
                String key = b.getType().name();
                if (!cropKeys.contains(key)) continue;

                if (!(b.getBlockData() instanceof Ageable ageable)) continue;
                int matureAge = plugin.getConfig().getInt("farming.crops." + key + ".mature-age", 7);
                if (ageable.getAge() < matureAge) continue;

                faceBlock(bot, b);
                equipItem(bot, Material.DIAMOND_HOE);
                swingArm(bot);

                // Harvest
                String dropKey  = plugin.getConfig().getString("farming.crops." + key + ".drop", key);
                int    dropAmt  = plugin.getConfig().getInt("farming.crops." + key + ".drop-amount", 1);
                String seedKey  = plugin.getConfig().getString("farming.crops." + key + ".seed", key);
                int    seedAmt  = plugin.getConfig().getInt("farming.crops." + key + ".seed-amount", 1);

                Material dropMat = Material.matchMaterial(dropKey);
                Material seedMat = Material.matchMaterial(seedKey);

                if (dropMat != null) addToBackpackSafe(bot, new ItemStack(dropMat, dropAmt));
                if (seedMat != null) addToBackpackSafe(bot, new ItemStack(seedMat, seedAmt));

                // Replant (set age back to 0)
                Ageable replant = (Ageable) b.getBlockData().clone();
                replant.setAge(0);
                b.setBlockData(replant);

                bot.setStatusLabel("🌾 Farming " + formatName(b.getType()) + "...");
                updateHologram(bot);
                return true;
            }
        }
        return false;
    }

    // ── Roaming & Hazard Detection ────────────────────────────────

    private void tickRoam(BotInstance bot) {
        bot.setStatusLabel(bot.getMode() == BotMode.MINING ? "⛏ Tunneling..." : "🚶 Roaming...");
        updateHologram(bot);

        Entity entity = getBotEntity(bot);
        Location loc  = entity.getLocation();
        World world   = loc.getWorld();

        // Hazard detection: check block in front and the block below it
        Vector dir    = loc.getDirection().setY(0).normalize();
        Location front = loc.clone().add(dir);
        Block frontBlock      = world.getBlockAt(front);
        Block belowFrontBlock = world.getBlockAt(front.clone().subtract(0, 1, 0));

        boolean hazard = false;
        if (frontBlock.getType() == Material.LAVA || frontBlock.getType() == Material.WATER) {
            hazard = true;
        }
        if (belowFrontBlock.getType() == Material.AIR
                || belowFrontBlock.getType() == Material.LAVA
                || belowFrontBlock.getType() == Material.WATER) {
            hazard = true;
        }

        if (hazard) {
            // Push backwards
            Vector back = dir.clone().multiply(-0.4).setY(0.1);
            entity.setVelocity(back);
            return;
        }

        // Safe to move forward
        double speed = plugin.getConfig().getDouble("ai.roam-speed", 0.25);
        Vector move  = dir.clone().multiply(speed);
        entity.setVelocity(move);
    }

    // ── Shared Helpers ────────────────────────────────────────────

    private Block scanForBlock(Location center, int radius, List<String> types) {
        World world = center.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = world.getBlockAt(
                            center.getBlockX() + x,
                            center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (types.contains(b.getType().name())) return b;
                }
            }
        }
        return null;
    }

    private void mineBlock(BotInstance bot, Block block, Material replace, ItemStack drop) {
        // Play break effect
        block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, block.getType());
        block.setType(replace);
        addToBackpackSafe(bot, drop);

        // Citizens navigator: move to where block was
        navigateTo(bot, block.getLocation());
    }

    private void addToBackpackSafe(BotInstance bot, ItemStack item) {
        boolean added = bot.addToBackpack(item);
        if (!added) {
            notifyOwner(bot, "backpack-full");
        }
        // Auto-save every time backpack changes
        plugin.getDataManager().saveBotData(bot);
    }

    private void faceBlock(BotInstance bot, Block block) {
        NPC npc = bot.getNpc();
        if (!npc.isSpawned()) return;
        Location botLoc    = getBotLocation(bot);
        Location blockLoc  = block.getLocation().add(0.5, 0.5, 0.5);
        Vector dir         = blockLoc.toVector().subtract(botLoc.toVector()).normalize();
        Location look      = botLoc.setDirection(dir);
        npc.getEntity().teleport(look);
    }

    private void faceEntity(BotInstance bot, Entity target) {
        NPC npc = bot.getNpc();
        if (!npc.isSpawned()) return;
        Location botLoc    = getBotLocation(bot);
        Location targetLoc = target.getLocation();
        Vector dir         = targetLoc.toVector().subtract(botLoc.toVector()).normalize();
        Location look      = botLoc.setDirection(dir);
        npc.getEntity().teleport(look);
    }

    private void equipItem(BotInstance bot, Material material) {
        Entity entity = getBotEntity(bot);
        if (entity instanceof LivingEntity le) {
            le.getEquipment().setItemInMainHand(new ItemStack(material));
        }
    }

    private void swingArm(BotInstance bot) {
        Entity entity = getBotEntity(bot);
        // Play a click sound at the entity's location to simulate arm swing
        entity.getWorld().playEffect(entity.getLocation(), Effect.CLICK1, 0);
        entity.getWorld().playEffect(entity.getLocation(), Effect.CLICK2, 0);
    }

    private Block getFrontBlock(BotInstance bot) {
        Entity entity = getBotEntity(bot);
        Location loc  = entity.getLocation();
        Vector dir    = loc.getDirection().setY(0).normalize();
        return loc.getWorld().getBlockAt(loc.clone().add(dir));
    }

    private void navigateTo(BotInstance bot, Location target) {
        NPC npc = bot.getNpc();
        if (!npc.isSpawned()) return;
        try {
            npc.getNavigator().setTarget(target);
        } catch (Exception ignored) {
            // Navigator may not be available; velocity roaming handles movement
        }
    }

    private void updateHologram(BotInstance bot) {
        if (hologramManager != null) {
            hologramManager.update(bot);
        }
    }

    private void notifyOwner(BotInstance bot, String msgKey) {
        Player owner = Bukkit.getPlayer(bot.getOwnerUuid());
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(plugin.msg(msgKey));
        }
    }

    private String formatName(Material material) {
        return material.name().replace("_", " ").toLowerCase();
    }

    // ── Public API ────────────────────────────────────────────────

    public HologramManager getHologramManager() {
        return hologramManager;
    }
}
