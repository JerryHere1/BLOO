package com.superbot.plugin.data;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class BotInstance {

    private final UUID ownerUuid;
    private final int npcId;
    private NPC npc;
    private BotMode mode;
    private final ItemStack[] backpack; // 27 slots (single chest)
    private boolean backpackFull;
    private String statusLabel;

    public BotInstance(UUID ownerUuid, int npcId, NPC npc) {
        this.ownerUuid = ownerUuid;
        this.npcId = npcId;
        this.npc = npc;
        this.mode = BotMode.IDLE;
        this.backpack = new ItemStack[27];
        this.backpackFull = false;
        this.statusLabel = "Idle";
    }

    // ── Getters & setters ────────────────────────────────────────

    public UUID getOwnerUuid() { return ownerUuid; }
    public int getNpcId()       { return npcId; }
    public NPC getNpc()         { return npc; }
    public void setNpc(NPC npc) { this.npc = npc; }

    public BotMode getMode()           { return mode; }
    public void setMode(BotMode mode)  { this.mode = mode; }

    public ItemStack[] getBackpack()   { return backpack; }
    public boolean isBackpackFull()    { return backpackFull; }
    public void setBackpackFull(boolean full) { this.backpackFull = full; }

    public String getStatusLabel()                    { return statusLabel; }
    public void setStatusLabel(String statusLabel)    { this.statusLabel = statusLabel; }

    // ── Backpack helpers ──────────────────────────────────────────

    /**
     * Add an item to the backpack, stacking properly.
     * @return true if added successfully, false if no space.
     */
    public boolean addToBackpack(ItemStack item) {
        if (item == null) return true;
        int remaining = item.getAmount();

        // First pass: stack onto existing matching items
        for (int i = 0; i < backpack.length; i++) {
            if (remaining <= 0) break;
            ItemStack slot = backpack[i];
            if (slot != null && slot.isSimilar(item)) {
                int canFit = slot.getMaxStackSize() - slot.getAmount();
                int take = Math.min(canFit, remaining);
                slot.setAmount(slot.getAmount() + take);
                remaining -= take;
            }
        }

        // Second pass: fill empty slots
        for (int i = 0; i < backpack.length; i++) {
            if (remaining <= 0) break;
            if (backpack[i] == null) {
                ItemStack copy = item.clone();
                int take = Math.min(item.getMaxStackSize(), remaining);
                copy.setAmount(take);
                backpack[i] = copy;
                remaining -= take;
            }
        }

        if (remaining > 0) {
            backpackFull = true;
            return false;
        }

        // Re-evaluate full status
        backpackFull = isActuallyFull();
        return true;
    }

    public boolean isActuallyFull() {
        for (ItemStack slot : backpack) {
            if (slot == null) return false;
            if (slot.getAmount() < slot.getMaxStackSize()) return false;
        }
        return true;
    }
}
