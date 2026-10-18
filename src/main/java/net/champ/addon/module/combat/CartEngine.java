package net.champ.addon.module.combat;

import net.champ.addon.util.Slots;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Gemeenschappelijke "cart"-engine voor alle cart-modules: selecteert eerst de
 * rail en zet die, selecteert daarna de TNT-mijnkarretjes en spawn ze bovenop de
 * rail. Rotatie-spoofing gebeurt aan de kant van de Champ Client zelf
 * (PacketSpoofEngine) zodat de plaatsing menselijk blijft.
 */
public final class CartEngine {
    private CartEngine() {}

    public enum Phase { IDLE, PLACE_RAIL, PLACE_CART, DONE }

    /** Probeert één zet van de FSM; true als er een actie is uitgevoerd. */
    public static boolean step(Phase phase, int slotRail, int slotCart) {
        Minecraft mc = Slots.mc();
        var player = Slots.player();
        Level level = mc.level;
        if (mc == null || player == null || level == null) return false;

        BlockPos target = targetUnderAim(level, player);
        if (target == null) return false;

        switch (phase) {
            case PLACE_RAIL -> {
                if (slotRail < 0 || !hasItem(Items.RAIL)) return false;
                Slots.select(slotRail);
                place(mc, player, target);
                return true;
            }
            case PLACE_CART -> {
                if (!hasItem(Items.TNT_MINECART)) return false;
                Slots.select(slotCart);
                place(mc, player, target.above());
                return true;
            }
            default -> { return false; }
        }
    }

    private static void place(Minecraft mc, net.minecraft.world.entity.player.Player player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        // Zelfde plaatsing als normale rechtermuisknop: client-side oplossing via gebruik.
        mc.startUseItem();
    }

    public static boolean hasItem(net.minecraft.world.item.Item item) {
        var p = Slots.player();
        return p != null && p.getInventory().countItem(item) > 0;
    }

    /** Bepaalt het doel-positie: onder kijkrichting of onder de speler zelf. */
    static BlockPos targetUnderAim(Level level, net.minecraft.world.entity.player.Player player) {
        var hit = player.pick(4.5D, 0F, false);
        if (hit instanceof BlockHitResult bhr && level.getBlockState(bhr.getBlockPos()).isSolidRender(
                level, bhr.getBlockPos())) return bhr.getBlockPos();
        return null;
    }
}
