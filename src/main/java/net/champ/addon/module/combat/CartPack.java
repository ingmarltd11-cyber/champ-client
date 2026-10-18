package net.champ.addon.module.combat;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.util.Slots;
import net.champ.addon.util.Targets;
import net.champ.client.event.impl.KeyEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.KeySetting;
import net.champ.client.module.setting.NumberSetting;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Cart-familie: xbow-cart macro's, safe-varianten, aura's en refill. */
public final class CartPack {
    private CartPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new AutoXbowCartModule());
        out.add(new AutoSafeCartModule());
        out.add(new CartAuraModule());
        out.add(new SafeXbowCartModule());
        out.add(new XbowCartAuraModule());
        out.add(new AutoCartRefillModule());
        return out;
    }

    /** Gedeelde FSM voor "plaats rail + cart onder mikpunt", één actie per tick. */
    static final class CartPlacer {
        int stage; // 0 idle, 1 rail, 2 cart
        int wait;

        boolean step(boolean useXbow, boolean safe, int delayTicks) {
            Player p = Slots.player();
            if (p == null) return false;
            int rail = Slots.hotbarSlotContaining("rail");
            int cart = Slots.hotbarSlotContaining(useXbow ? "tnt_minecart" : "minecart");
            if (rail < 0 || cart < 0) { stage = 0; return false; }
            if (wait-- > 0) return true;
            if (stage == 0) { stage = 1; wait = delayTicks; return true; }
            if (stage == 1) {
                if (safe) CartEngine.step(CartEngine.Phase.PLACE_RAIL, rail, cart);
                Slots.select(rail);
                Slots.mc().gameMode.useItem(p, p.getUsedItemHand());
                stage = 2; wait = delayTicks;
                return true;
            }
            Slots.select(cart);
            Slots.mc().gameMode.useItem(p, p.getUsedItemHand());
            stage = 0;
            return true;
        }
    }

    /** Auto Xbow Cart: automatische xbow-cart macro zodra gevecht start. */
    static final class AutoXbowCartModule extends ModuleBase {
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 3, 0, 20));
        final BooleanSetting onlyEnemy = addSetting(new BooleanSetting("Only near enemy", true));
        final CartPlacer placer = new CartPlacer();

        AutoXbowCartModule() {
            super("auto-xbow-cart", "Auto Xbow Cart",
                    "Automatische crossbow-cart macro zodra je vecht.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            if (onlyEnemy.get() && Targets.nearestEnemy(12) == null) { placer.stage = 0; return; }
            placer.step(true, false, (int) delay.get());
        }
    }

    /** Auto Safe Cart: plaatst eerst blokken zodat je minder schade krijgt. */
    static final class AutoSafeCartModule extends ModuleBase {
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 4, 0, 20));
        final CartPlacer placer = new CartPlacer();

        AutoSafeCartModule() {
            super("auto-safe-cart", "Auto Safe Cart",
                    "Cart met beschermende blokken tegen eigen schade.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            placer.step(false, true, (int) delay.get());
        }
    }

    /** Cart Aura: automatisch insta-carten bij enemies dichtbij, met safe-optie. */
    static final class CartAuraModule extends ModuleBase {
        final NumberSetting range = addSetting(new NumberSetting("Range", 4.5F, 1F, 8F));
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 5, 0, 20));
        final BooleanSetting safe = addSetting(new BooleanSetting("Safe cart", true));
        final CartPlacer placer = new CartPlacer();

        CartAuraModule() {
            super("cart-aura", "Cart Aura",
                    "Insta-cart automatisch bij enemies in de buurt.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            if (Targets.nearestEnemy(range.get()) == null) { placer.stage = 0; return; }
            placer.step(false, safe.get(), (int) delay.get());
        }
    }

    /** Safe Xbow Cart: slab/blok-bescherming + xbow-cart in één macro. */
    static final class SafeXbowCartModule extends ModuleBase {
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 4, 0, 20));
        final CartPlacer placer = new CartPlacer();

        SafeXbowCartModule() {
            super("safe-xbow-cart", "Safe Xbow Cart",
                    "Xbow-cart met slab-bescherming tegen eigen schade.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            placer.step(true, true, (int) delay.get());
        }
    }

    /** Xbow Cart Aura: zelfde als Cart Aura maar met crossbow-cart. */
    static final class XbowCartAuraModule extends ModuleBase {
        final NumberSetting range = addSetting(new NumberSetting("Range", 4.5F, 1F, 8F));
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 5, 0, 20));
        final CartPlacer placer = new CartPlacer();

        XbowCartAuraModule() {
            super("xbow-cart-aura", "Xbow Cart Aura",
                    "Automatische xbow-cart bij enemies in de buurt.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            if (Targets.nearestEnemy(range.get()) == null) { placer.stage = 0; return; }
            placer.step(true, false, (int) delay.get());
        }
    }

    /** Auto Cart Refill: vult gekozen hotbar-slots aan met rails/carts uit inventory. */
    static final class AutoCartRefillModule extends ModuleBase {
        final NumberSetting railSlot = addSetting(new NumberSetting("Rail slot", 7, 0, 8));
        final NumberSetting cartSlot = addSetting(new NumberSetting("Cart slot", 8, 0, 8));

        AutoCartRefillModule() {
            super("auto-cart-refill", "Auto Cart Refill",
                    "Vult rail- en cart-slots automatisch aan.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            if (!inGame()) return;
            refill((int) railSlot.get(), "rail");
            refill((int) cartSlot.get(), "tnt_minecart");
        }

        private void refill(int hotbar, String itemId) {
            Player p = Slots.player();
            if (p == null) return;
            var stack = p.getInventory().getItem(hotbar);
            if (!stack.isEmpty() && stack.getCount() > 8) return;
            int found = net.champ.addon.util.Inv.find(itemId);
            if (found >= 0) net.champ.addon.util.Inv.moveToHotbar(found, hotbar);
        }
    }

    /** Trigger-key helper voor macro-modules: vuurt op key-press. */
    static boolean triggerPressed(ModuleBase mod, KeySetting key, KeyEvent ev) {
        return mod.isEnabled() && key.value() >= 0 && ev.key == key.value() && ev.action == 1;
    }
}
