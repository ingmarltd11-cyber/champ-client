package net.champ.addon.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Kleine util rond hotbar/inventory: zoeken van slots binnen één class zodat
 * elke module het op dezelfde uniforme manier kan gebruiken.
 *
 * Mapping-notitie: compiled tegen 1.21.11 yarn; PlayerInventory.pick (class_1723),
 * pregnant met dezelfde methodes in de productierelease van de meta client.
 */
public final class Slots {
    private Slots() {}

    public static Minecraft mc() { return Minecraft.getInstance(); }

    public static LocalPlayer player() { return mc().player; }

    /** Registry-pad van een stack (bv. "tnt_minecart"), nooit null. */
    private static final java.util.Map<net.minecraft.world.item.Item, String> ITEM_IDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static String itemId(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        // Registry-lookup + String alloc eenmalig per item-type, daarna een map-hit.
        return ITEM_IDS.computeIfAbsent(s.getItem(), i -> BuiltInRegistries.ITEM.getKey(i).getPath());
    }

    private static final java.util.Map<net.minecraft.world.level.block.Block, String> BLOCK_IDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Registry-pad van een blok (bv. "oak_planks"), gecachet. */
    public static String blockId(net.minecraft.world.level.block.Block b) {
        return BLOCK_IDS.computeIfAbsent(b, k -> BuiltInRegistries.BLOCK.getKey(k).getPath());
    }

    /** Hotbar-slot waarvan het item-id exact gelijk is (geen substring-match), of -1. */
    public static int hotbarSlotExact(String itemId) {
        Player p = player();
        if (p == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            var stack = p.getInventory().getItem(slot);
            if (stack != null && !stack.isEmpty() && itemId(stack).equals(itemId)) return slot;
        }
        return -1;
    }

    /** Hotbar-slot dat op de gegeven item-id begint, of -1. */
    public static int hotbarSlotContaining(String itemId) {
        Player p = player();
        if (p == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            var stack = p.getInventory().getItem(slot);
            if (stack != null && itemId(stack).contains(itemId)) return slot;
        }
        return -1;
    }

    /** Eerste hotbar-slot zonder item, of -1. */
    public static int firstEmptyHotbarSlot() {
        Player p = player();
        if (p == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            var stack = p.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) return slot;
        }
        return -1;
    }

    public static void select(int hotbarSlot) {
        Player p = player();
        if (p != null && hotbarSlot >= 0 && hotbarSlot < 9) p.getInventory().setSelectedSlot(hotbarSlot);
    }

    public static int selected() {
        Player p = player();
        return p == null ? -1 : p.getInventory().getSelectedSlot();
    }

    public static boolean isInHotbar(String itemId) { return hotbarSlotContaining(itemId) >= 0; }
}
