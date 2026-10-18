package net.champ.client.module.impl.mace;

import net.champ.addon.Addons;
import net.champ.addon.module.ModuleBase;
import net.champ.addon.spoof.SilentAim;
import net.champ.addon.util.Inv;
import net.champ.addon.util.Slots;
import net.champ.addon.util.Targets;
import net.champ.client.event.impl.KeyEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.KeySetting;
import net.champ.client.module.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Mace-categorie: pearl-catch macro's, windcharge-tech, shield-drain, elytra, totem. */
public final class MacePack {
    private MacePack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new AutoPearlCatchModule());
        out.add(new AutoWindchargePearlModule());
        out.add(new WindchargeClutchModule());
        out.add(new ShieldDrainModule());
        out.add(new AutoElytraModule());
        out.add(new MaceTotemModule());
        return out;
    }

    static void throwUp(Player p, String itemId) {
        int slot = Slots.hotbarSlotContaining(itemId);
        if (slot < 0) return;
        Slots.select(slot);
        SilentAim.request(p.getYRot(), -85F, 3);
        Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
    }

    static void warn(Player p, String text) {
        p.displayClientMessage(net.minecraft.network.chat.Component.literal(text), false);
    }

    /** Auto Pearl Catch: pearl + windcharge omhoog (silent), delays instelbaar. */
    static final class AutoPearlCatchModule extends ModuleBase {
        final NumberSetting pearlDelay = addSetting(new NumberSetting("Pearl delay (ticks)", 4, 0, 40));
        final NumberSetting chargeDelay = addSetting(new NumberSetting("Windcharge delay (ticks)", 6, 0, 40));
        final BooleanSetting elytraTakeoff = addSetting(new BooleanSetting("Elytra takeoff", true));
        final BooleanSetting breachCheck = addSetting(new BooleanSetting("Breach check (<10.1)", true));
        final KeySetting trigger = addSetting(new KeySetting("Catch key", -1));
        int stage, wait;

        AutoPearlCatchModule() {
            super("auto-pearl-catch", "Auto Pearl Catch",
                    "Pearl + windcharge omhoog, altijd silent aim.", Addons.mace());
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (isEnabled() && trigger.value() >= 0 && ev.key == trigger.value() && ev.action == 1) {
                    stage = 1; wait = 0;
                }
            });
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null || stage == 0) return;
            if (wait-- > 0) return;
            if (stage == 1) {
                throwUp(p, "ender_pearl");
                stage = 2; wait = (int) pearlDelay.get();
            } else if (stage == 2) {
                throwUp(p, "wind_charge");
                stage = 3; wait = (int) chargeDelay.get();
            } else {
                if (elytraTakeoff.get()) putOnElytra();
                if (breachCheck.get()) selectMace(p);
                stage = 0;
            }
        }

        private void putOnElytra() {
            Player p = Slots.player();
            if (p == null) return;
            var chest = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            if (!chest.isEmpty() && net.champ.addon.util.Slots.itemId(chest).contains("elytra")) return;
            int found = Inv.find("elytra");
            if (found >= 0) Inv.swapHotbarWith(Slots.selected(), 7); // chest-slot 7
        }

        private void selectMace(Player p) {
            // Breach-check: val je < 10.1 blocks, kies Breach-mace, anders Density.
            boolean low = net.champ.addon.util.Targets.fallDistance(p) < 10.1F;
            int slot = findNamedMace(low ? "breach" : "density");
            if (slot >= 0) Slots.select(slot);
        }

        private int findNamedMace(String kind) {
            Player p = Slots.player();
            if (p == null) return -1;
            for (int i = 0; i < 9; i++) {
                var s = p.getInventory().getItem(i);
                if (!s.isEmpty() && net.champ.addon.util.Slots.itemId(s).contains("mace")
                        && s.getHoverName().getString().toLowerCase().contains(kind)) return i;
            }
            return Slots.hotbarSlotContaining("mace");
        }
    }

    /** Auto Windcharge Pearl Catch: windcharge mikt op je eigen vliegende pearl. */
    static final class AutoWindchargePearlModule extends ModuleBase {
        final BooleanSetting automatic = addSetting(new BooleanSetting("Automatic", true));
        final NumberSetting delay = addSetting(new NumberSetting("Throw delay (ticks)", 3, 0, 20));
        int wait;

        AutoWindchargePearlModule() {
            super("auto-windcharge-pearl", "Auto Windcharge Pearl",
                    "Windcharge raakt je eigen pearl via silent aim.", Addons.mace());
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var level = Slots.mc().level;
            if (p == null || level == null || !automatic.get()) return;
            if (wait-- > 0) return;
            var pearls = level.getEntitiesOfClass(ThrownEnderpearl.class, p.getBoundingBox().inflate(24),
                    e -> e.getOwner() == p);
            if (pearls.isEmpty()) return;
            var pearl = pearls.get(0);
            // Iets onder de pearl mikken zodat de charge hem echt raakt.
            Vec3 aim = pearl.position().add(0, -0.35, 0);
            float[] a = Targets.anglesTo(Targets.eye(p), aim);
            SilentAim.request(a[0], a[1], 2);
            int slot = Slots.hotbarSlotContaining("wind_charge");
            if (slot < 0) return;
            Slots.select(slot);
            Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
            wait = (int) delay.get();
        }
    }

    /** Windcharge Clutch: breekt je val automatisch met windcharge (silent). */
    static final class WindchargeClutchModule extends ModuleBase {
        final NumberSetting minFall = addSetting(new NumberSetting("Min fall distance", 4F, 1F, 20F));
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 2, 0, 20));
        final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap back", true));
        int wait, prevSlot = -1;

        WindchargeClutchModule() {
            super("windcharge-clutch", "Windcharge Clutch",
                    "Goait automatisch windcharge om je val te breken.", Addons.mace());
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            if (wait-- > 0) {
                if (wait == 0 && swapBack.get() && prevSlot >= 0) Slots.select(prevSlot);
                return;
            }
            if (net.champ.addon.util.Targets.fallDistance(p) < minFall.get() || p.onGround()) return;
            int slot = Slots.hotbarSlotContaining("wind_charge");
            if (slot < 0) return;
            prevSlot = Slots.selected();
            Slots.select(slot);
            SilentAim.request(p.getYRot(), 85F, 2);
            Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
            wait = (int) delay.get();
        }
    }

    /** Shield Drain (mace): spamt mace tot schild breekt, waarschuwt bij stun slam. */
    static final class ShieldDrainModule extends ModuleBase {
        final NumberSetting clicksPerTick = addSetting(new NumberSetting("Clicks per tick", 3, 1, 10));
        final NumberSetting range = addSetting(new NumberSetting("Range", 4.5F, 1F, 6F));
        final BooleanSetting stunSlamOn = addSetting(new BooleanSetting("Stun slam enabled", false));
        boolean warned;

        ShieldDrainModule() {
            super("shield-drain", "Shield Drain",
                    "Mace-spam tegen schild. Stun slam kan dit breken!", Addons.mace());
        }

        @Override protected void onEnableInternal() { warned = false; }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            Player enemy = Targets.nearestEnemy(range.get());
            if (enemy == null || !enemy.isBlocking()) return;
            if (stunSlamOn.get() && !warned) {
                warned = true;
                warn(p, "[Champ Client] Shield Drain: stun slam staat aan, dit werkt mogelijk niet goed!");
            }
            int mace = Slots.hotbarSlotContaining("mace");
            if (mace < 0) return;
            Slots.select(mace);
            for (int i = 0; i < (int) clicksPerTick.get(); i++) Slots.mc().gameMode.attack(p, enemy);
        }
    }

    /** Auto Elytra: keybind trekt elytra aan + pakt vuurwerk in de hand. */
    static final class AutoElytraModule extends ModuleBase {
        final KeySetting trigger = addSetting(new KeySetting("Elytra key", -1));

        AutoElytraModule() {
            super("auto-elytra", "Auto Elytra",
                    "Trek elytra aan + vuurwerk in hand met één key.", Addons.mace());
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (isEnabled() && trigger.value() >= 0 && ev.key == trigger.value() && ev.action == 1) {
                    dressElytra();
                }
            });
        }

        @Override protected void tick() {}

        private void dressElytra() {
            Player p = Slots.player();
            if (p == null) return;
            int found = Inv.find("elytra");
            if (found >= 0) {
                // armor-slots: 5 helm, 6 chest, 7 legs, 8 boots. Swap met chest.
                Inv.click(found, 0, net.minecraft.world.inventory.ClickType.PICKUP);
                Inv.click(6, 0, net.minecraft.world.inventory.ClickType.PICKUP);
                Inv.click(found, 0, net.minecraft.world.inventory.ClickType.PICKUP);
            }
            int rocket = Slots.hotbarSlotContaining("firework_rocket");
            if (rocket >= 0) Slots.select(rocket);
        }
    }

    /**
     * Auto Totem voor Mace (eigen ontwerp: "Mace Guard"): houdt totem in offhand
     * tijdens mace-gevechten, eet automatisch een golden apple bij laag leven
     * terwijl je een mace vasthoudt, en legt daarna de totem terug.
     */
    static final class MaceTotemModule extends ModuleBase {
        final NumberSetting minHealth = addSetting(new NumberSetting("Apple below hearts", 6F, 1F, 20F));

        MaceTotemModule() {
            super("mace-totem", "Mace Guard",
                    "Totem-bewaking + auto-apple tijdens mace-gevechten.", Addons.mace());
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            boolean holdingMace = net.champ.addon.util.Slots.itemId(p.getMainHandItem()).contains("mace");
            // Offhand altijd totem als we vechten met een mace.
            var off = p.getOffhandItem();
            if (holdingMace && (off.isEmpty() || !net.champ.addon.util.Slots.itemId(off).contains("totem"))) {
                int found = Inv.find("totem_of_undying");
                if (found >= 0) {
                    Inv.click(found, 0, net.minecraft.world.inventory.ClickType.PICKUP);
                    Inv.click(45, 0, net.minecraft.world.inventory.ClickType.PICKUP);
                }
            }
            // Laag leven + mace vast = gapple eten.
            if (holdingMace && p.getHealth() < minHealth.get() * 2F) {
                int apple = Slots.hotbarSlotContaining("golden_apple");
                if (apple >= 0) {
                    Slots.select(apple);
                    Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
                }
            }
        }
    }
}
