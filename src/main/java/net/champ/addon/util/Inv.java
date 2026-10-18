package net.champ.addon.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory window-clicks (container-id 0 = speler). Slotlayout speler-container:
 * 5-8 armor, 9-35 main, 36-44 hotbar, 45 offhand.
 */
public final class Inv {
    private Inv() {}

    public static int containerId() {
        Player p = Slots.player();
        return p == null ? 0 : p.containerMenu.containerId;
    }

    /** Slotnummers hieronder gelden alleen voor het speler-inventory, niet voor een kist/trade/enz. */
    public static boolean menuReady() {
        Player p = Slots.player();
        return p != null && p.containerMenu == p.inventoryMenu;
    }

    public static void click(int slot, int button, ClickType type) {
        var mc = Slots.mc();
        Player p = Slots.player();
        if (mc == null || mc.gameMode == null || p == null || !menuReady()) return;
        mc.gameMode.handleInventoryMouseClick(containerId(), slot, button, type, p); // ClickType (MojMap-stijl)
    }

    /** Container-slot (9-44) met item-id erin, of -1. */
    public static int find(String itemId) {
        Player p = Slots.player();
        if (p == null || !menuReady()) return -1;
        for (int slot = 9; slot <= 44; slot++) {
            ItemStack s = p.containerMenu.getSlot(slot).getItem();
            if (!s.isEmpty() && net.champ.addon.util.Slots.itemId(s).contains(itemId)) return slot;
        }
        return -1;
    }

    /** Container-slot (9-35, hoofdinventory) met exact dit item-id, of -1. */
    public static int findMainExact(String itemId) {
        Player p = Slots.player();
        if (p == null || !menuReady()) return -1;
        for (int slot = 9; slot <= 35; slot++) {
            ItemStack s = p.containerMenu.getSlot(slot).getItem();
            if (!s.isEmpty() && Slots.itemId(s).equals(itemId)) return slot;
        }
        return -1;
    }

    /** Wisselt een container-slot met een hotbar-slot in één klik (SWAP). */
    public static void swapToHotbar(int containerSlot, int hotbar) {
        click(containerSlot, hotbar, ClickType.SWAP);
    }

    /** Verplaatst stack van container-slot naar hotbar-slot (0-8) via pickup/place. */
    public static void moveToHotbar(int fromSlot, int hotbar) {
        int toSlot = 36 + hotbar;
        click(fromSlot, 0, ClickType.PICKUP);
        click(toSlot, 0, ClickType.PICKUP);
    }

    /** Wisselt hotbar-slot met armor/offhand-container-slot. */
    public static void swapHotbarWith(int hotbar, int containerSlot) {
        click(36 + hotbar, 0, ClickType.PICKUP);
        click(containerSlot, 0, ClickType.PICKUP);
        click(36 + hotbar, 0, ClickType.PICKUP);
    }
}
