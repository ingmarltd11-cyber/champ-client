package net.champ.client.module.impl.misc;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.util.Slots;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.module.setting.StringSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Misc deel 1: combat-utility (crits, pot, text, tool, armor, clicker). */
public final class MiscPack {
    private MiscPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new CriticalsModule());
        out.add(new AutoPotModule());
        out.add(new AutoTextModule());
        out.add(new AutoToolModule());
        out.add(new AutoArmorModule());
        out.add(new RightClickerModule());
        return out;
    }

    /** Criticals: maakt van normale hits crits (springt kort voor de hit). */
    static final class CriticalsModule extends ModuleBase {
        final BooleanSetting onlyFalling = addSetting(new BooleanSetting("Only while falling", false));

        CriticalsModule() {
            super("criticals", "Criticals",
                    "Maakt normale hits tot crits.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            if (p == null) return;
            if (mc.options.keyAttack.isDown() && mc.hitResult instanceof
                    net.minecraft.world.phys.EntityHitResult ehr) {
                if (p.onGround() && !onlyFalling.get()) p.jumpFromGround();
                if (!p.onGround() || net.champ.addon.util.Targets.fallDistance(p) > 0) {
                    mc.gameMode.attack(p, ehr.getEntity());
                }
            }
        }
    }

    /** Auto Pot: drinkt/gooit splash pots automatisch bij laag leven. */
    static final class AutoPotModule extends ModuleBase {
        final NumberSetting minHealth = addSetting(new NumberSetting("Drink below hearts", 7F, 1F, 20F));
        final BooleanSetting splashFirst = addSetting(new BooleanSetting("Splash first", true));

        AutoPotModule() {
            super("auto-pot", "Auto Pot",
                    "Gebruikt automatisch (splash) potions.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null || p.getHealth() >= minHealth.get() * 2F) return;
            int splash = Slots.hotbarSlotContaining("splash_potion");
            int drink = Slots.hotbarSlotContaining("potion");
            int slot = (splashFirst.get() && splash >= 0) ? splash : drink;
            if (slot < 0) slot = splash >= 0 ? splash : drink;
            if (slot < 0) return;
            Slots.select(slot);
            Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
        }
    }

    /** Auto Text: stuurt automatisch een chatbericht op interval. */
    static final class AutoTextModule extends ModuleBase {
        final StringSetting message = addSetting(new StringSetting("Message", "gg"));
        final NumberSetting interval = addSetting(new NumberSetting("Interval (seconds)", 30, 5, 600));
        long nextSend;

        AutoTextModule() {
            super("auto-text", "Auto Text",
                    "Stuurt automatisch een chatbericht.", ModuleCategory.MISC);
        }

        @Override protected void onEnableInternal() { nextSend = now() + seconds(); }
        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            if (now() < nextSend) return;
            nextSend = now() + seconds();
            try {
                p.connection.sendChat(message.get());
            } catch (Exception ignored) {}
        }

        private long seconds() { return (long) (interval.get() * 1000L); }
        private long now() { return System.currentTimeMillis(); }
    }

    /** Auto Tool: swapt naar de beste tool voor het blok waar je naar kijkt. */
    static final class AutoToolModule extends ModuleBase {
        AutoToolModule() {
            super("auto-tool", "Auto Tool",
                    "Swapt naar de beste tool voor het blok.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            var level = mc.level;
            if (p == null || level == null) return;
            if (!mc.options.keyAttack.isDown()) return;
            if (!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr)) return;
            var state = level.getBlockState(bhr.getBlockPos());
            int best = -1;
            float bestSpeed = 1F;
            for (int i = 0; i < 9; i++) {
                var s = p.getInventory().getItem(i);
                if (s.isEmpty()) continue;
                float speed = s.getDestroySpeed(state);
                if (speed > bestSpeed) { bestSpeed = speed; best = i; }
            }
            if (best >= 0) Slots.select(best);
        }
    }

    /** Auto Armour: trekt automatisch de beste armour aan uit inventory. */
    static final class AutoArmorModule extends ModuleBase {
        final NumberSetting checkInterval = addSetting(new NumberSetting("Check every (ticks)", 20, 5, 200));
        int wait;

        AutoArmorModule() {
            super("auto-armor", "Auto Armour",
                    "Trek automatisch de beste armour aan.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            if (wait-- > 0) return;
            wait = (int) checkInterval.get();
            Player p = Slots.player();
            if (p == null) return;
            // armor-slots 0=boots..3=helmet in inventory; container-slots 5-8.
            for (int armor = 0; armor < 4; armor++) {
                int best = -1, bestScore = armorScore(p.getItemBySlot(slotFor(armor)));
                for (int slot = 9; slot <= 35; slot++) {
                    var s = p.containerMenu.getSlot(slot).getItem();
                    if (s.isEmpty() || !(s.getItem() instanceof net.minecraft.world.item.ArmorItem ai)) continue;
                    if (!fitsSlot(ai, armor)) continue;
                    int score = armorScore(s);
                    if (score > bestScore) { bestScore = score; best = slot; }
                }
                if (best >= 0) {
                    net.champ.addon.util.Inv.click(best, 0, net.minecraft.world.inventory.ClickType.PICKUP);
                    net.champ.addon.util.Inv.click(8 - armor, 0, net.minecraft.world.inventory.ClickType.PICKUP);
                }
            }
        }

        // armor: 0 boots, 1 legs, 2 chest, 3 helmet -> container-slots 8,7,6,5.
        private static net.minecraft.world.entity.EquipmentSlot slotFor(int armor) {
            return switch (armor) {
                case 0 -> net.minecraft.world.entity.EquipmentSlot.FEET;
                case 1 -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                case 2 -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                default -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            };
        }

        private boolean fitsSlot(net.minecraft.world.item.ArmorItem ai, int armor) {
            return ai.getType().getSlot() == slotFor(armor);
        }

        private int armorScore(net.minecraft.world.item.ItemStack s) {
            if (s.isEmpty() || !(s.getItem() instanceof net.minecraft.world.item.ArmorItem ai)) return -1;
            return ai.getDefense() * 10 + (int) (ai.getToughness() * 5);
        }
    }

    /** Right Clicker: automatische rechtermuisklikken met instelbare CPS. */
    static final class RightClickerModule extends ModuleBase {
        final NumberSetting cps = addSetting(new NumberSetting("CPS", 8, 1, 20));
        long nextClick;

        RightClickerModule() {
            super("right-clicker", "Right Clicker",
                    "Automatische rechtermuisklikken.", ModuleCategory.MISC);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            if (p == null) return;
            if (!mc.options.keyUse.isDown()) return;
            long now = System.currentTimeMillis();
            if (now < nextClick) return;
            nextClick = now + (long) (1000 / Math.max(1, cps.get()));
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        }
    }
}
