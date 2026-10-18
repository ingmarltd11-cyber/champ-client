package net.champ.addon.module.combat;

import net.champ.addon.module.ModuleBase;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;

/**
 * Auto Cart: wanneer je een boog schiet plaatst de client 1 rail + 1 TNT-cart,
 * zodat de kartelling (detonatie van TNT) je enemy volgt op een aparte map.
 *
 * Verwacht: resourcepack?/server-item-compatibele RAIL/TNT_MINECART-items in hotbar.
 * Deze engine is een FSM: elke tick één actie, volledig gespoofd door EpoSpoofEngine.
 */
public final class AutoCartModule extends ModuleBase {
    private CartEngine.Phase phase = CartEngine.Phase.IDLE;
    private int tickDelay;

    public final NumberSetting delay = new NumberSetting("Place delay (ticks)", 2, 0, 10);
    public final BooleanSetting onlyWhenAimingEnemy = new BooleanSetting("Only when aiming enemy", true);
    public final BooleanSetting safeCart = new BooleanSetting("Safe cart (place blocks first)", false);

    public AutoCartModule() {
        super("auto-cart", "Auto Cart", "Plaats rail + TNT-cart zodra je een boog schiet.", ModuleCategory.COMBAT);
    }

    @Override protected void onEnableInternal() { phase = CartEngine.Phase.IDLE; tickDelay = 0; }

    @Override protected void tick() {
        if (!inGame()) { phase = CartEngine.Phase.IDLE; return; }
        if (!isShootingBow() || (onlyWhenAimingEnemy.get() && !aimingEntity())) return;

        int rail = net.champ.addon.util.Slots.hotbarSlotContaining("rail");
        int cart = net.champ.addon.util.Slots.hotbarSlotContaining("tnt_minecart");

        switch (phase) {
            case IDLE -> {
                if (safeCart.get()) { /* block-safe-fill komt in batch 2 */ }
                phase = CartEngine.Phase.PLACE_RAIL;
                tickDelay = (int) delay.get();
            }
            case PLACE_RAIL, PLACE_CART -> {
                if (tickDelay-- > 0) return;
                boolean did = CartEngine.step(phase, rail, cart);
                phase = did ? (phase == CartEngine.Phase.PLACE_RAIL
                        ? CartEngine.Phase.PLACE_CART : CartEngine.Phase.DONE) : CartEngine.Phase.IDLE;
            }
            case DONE -> { if (tickDelay-- <= 0) phase = CartEngine.Phase.IDLE; }
        }
    }

    private boolean isShootingBow() {
        var p = net.champ.addon.util.Slots.player();
        return p != null && p.isUsingItem()
                && (p.getUseItem().is(net.minecraft.world.item.Items.BOW));
    }

    private boolean aimingEntity() {
        var mc = net.champ.addon.util.Slots.mc();
        return mc != null && mc.hitResult != null && mc.hitResult.getType()
                == net.minecraft.world.phys.HitResult.Type.ENTITY;
    }
}
