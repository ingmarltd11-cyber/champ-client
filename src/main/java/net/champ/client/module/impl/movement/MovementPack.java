package net.champ.client.module.impl.movement;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.util.Slots;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Movement-aanvulling: Fast Bridge (sprint/freecam/jump-delay bestaan al). */
public final class MovementPack {
    private MovementPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new FastBridgeModule());
        return out;
    }

    /** Fast Bridge: plaatst automatisch blokken onder je terwijl je achteruit loopt. */
    static final class FastBridgeModule extends ModuleBase {
        final NumberSetting delay = addSetting(new NumberSetting("Place delay (ticks)", 1, 0, 10));
        final BooleanSetting autoSneak = addSetting(new BooleanSetting("Auto sneak at edge", true));
        int wait;

        FastBridgeModule() {
            super("fast-bridge", "Fast Bridge",
                    "Brugt automatisch terwijl je achteruit loopt.", ModuleCategory.MOVEMENT);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            var level = mc.level;
            if (p == null || level == null) return;
            if (wait-- > 0) return;
            BlockPos below = p.blockPosition().below();
            if (!level.getBlockState(below).isAir()) return;
            // Rand: sneak zodat je niet valt (alleen als er niks onder je is).
            if (autoSneak.get()) p.setSneaking(true);
            // Blok onder je voeten plaatsen vanaf het laatste solide blok.
            BlockPos anchor = below;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos side = below.relative(d);
                if (!level.getBlockState(side).isAir()) { anchor = side; break; }
            }
            int blocks = Slots.hotbarSlotContaining("cobblestone");
            if (blocks < 0) blocks = Slots.firstEmptyHotbarSlot() == -1 ? -1
                    : findAnyBlock();
            if (blocks < 0) return;
            Slots.select(blocks);
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(anchor), Direction.UP, anchor, false));
            wait = (int) delay.get();
            p.setSneaking(false);
        }

        private int findAnyBlock() {
            Player p = Slots.player();
            if (p == null) return -1;
            for (int i = 0; i < 9; i++) {
                var s = p.getInventory().getItem(i);
                if (!s.isEmpty() && net.champ.addon.util.Slots.itemId(s).contains("cobblestone")) return i;
            }
            for (int i = 0; i < 9; i++) {
                var s = p.getInventory().getItem(i);
                if (!s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
            }
            return -1;
        }
    }
}
