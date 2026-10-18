package net.champ.addon.module.spear;

import net.champ.addon.Addons;
import net.champ.addon.module.ModuleBase;
import net.champ.addon.module.combat.InstantPack;
import net.champ.addon.util.Modules;
import net.champ.addon.util.Slots;
import net.champ.client.event.impl.KeyEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.KeySetting;
import net.champ.client.module.setting.NumberSetting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** Spear-categorie: lunge-macro, double-tap, reach en een eigen riposte-module. */
public final class SpearPack {
    private SpearPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new InstantLungeModule());
        out.add(new SpearDoubleTapModule());
        out.add(new ExtendedSpearReachModule());
        out.add(new SpearRiposteModule());
        return out;
    }

    /**
     * Beste spear in hotbar: hoogste "lunge"-enchant level (Donut-custom),
     * met fallback naar de eerste spear als het enchant niet gevonden wordt.
     */
    static int bestSpear() {
        Player p = Slots.player();
        if (p == null) return -1;
        int best = -1, bestLvl = -1, first = -1;
        for (int i = 0; i < 9; i++) {
            var s = p.getInventory().getItem(i);
            if (s.isEmpty() || !net.champ.addon.util.Slots.itemId(s).contains("spear")) continue;
            if (first < 0) first = i;
            int lvl = lungeLevel(s);
            if (lvl > bestLvl) { bestLvl = lvl; best = i; }
        }
        return best >= 0 ? best : first;
    }

    private static int lungeLevel(net.minecraft.world.item.ItemStack stack) {
        try {
            var level = Slots.mc().level;
            if (level == null) return 0;
            var lookup = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var key = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("lunge"));
            var holder = lookup.getOrThrow(key);
            return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Instant Lunge: zoekt beste spear, swapt erheen, lungt, swapt terug, gaat uit. */
    static final class InstantLungeModule extends ModuleBase {
        final KeySetting trigger = addSetting(new KeySetting("Lunge key", -1));
        final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap back", true));
        final NumberSetting swapBackDelay = addSetting(new NumberSetting("Swap back delay (ticks)", 4, 0, 40));
        int prevSlot = -1, wait;
        boolean fired;

        InstantLungeModule() {
            super("instant-lunge", "Instant Lunge",
                    "Beste spear pakken, lungeren, terugswappen, uit.", Addons.spear());
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (isEnabled() && trigger.value() >= 0 && ev.key == trigger.value() && ev.action == 1) {
                    int spear = bestSpear();
                    if (spear < 0) return;
                    prevSlot = Slots.selected();
                    Slots.select(spear);
                    fired = false;
                    wait = 0;
                }
            });
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null || prevSlot < 0) return;
            if (!fired) {
                Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
                fired = true;
                wait = (int) swapBackDelay.get();
                return;
            }
            if (wait-- > 0) return;
            if (swapBack.get()) Slots.select(prevSlot);
            prevSlot = -1;
            setEnabled(false); // schakelt zichzelf uit na de actie
        }
    }

    /**
     * Spear Mace Double Tap: auto-mace gaat uit (met waarschuwing); houd je spear
     * vast en charge (rechtermuis) — zodra de spear iets raakt volgt direct een
     * mace-hit: double tap door totems heen.
     */
    static final class SpearDoubleTapModule extends ModuleBase {
        boolean warned;

        SpearDoubleTapModule() {
            super("spear-double-tap", "Spear Mace Double Tap",
                    "Spear-hit gevolgd door instant mace-hit. Zet Auto Mace uit!", Addons.spear());
        }

        @Override protected void onEnableInternal() {
            warned = false;
            if (Modules.isEnabled("auto-mace") || Modules.isEnabled("mace")) {
                Modules.setEnabled("auto-mace", false);
                Modules.setEnabled("mace", false);
                warned = true;
            }
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            if (warned) {
                // Eenmalige waarschuwing dat auto-mace uit is gezet.
                p.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "[Champ Client] Double Tap: Auto Mace uitgeschakeld."), false);
                warned = false;
                // Hierna: spear vasthouden + chargen; de mace-hit volgt in tick 2.
            }
            boolean holdingSpear = net.champ.addon.util.Slots.itemId(p.getMainHandItem()).contains("spear");
            if (!holdingSpear || !p.isUsingItem()) return;
            // Spear raakt iets (crosshair entity) -> direct mace-hit erachteraan.
            var mc = Slots.mc();
            if (mc.hitResult != null
                    && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                int mace = Slots.hotbarSlotContaining("mace");
                if (mace < 0) return;
                int back = Slots.selected();
                Slots.select(mace);
                var target = ((net.minecraft.world.phys.EntityHitResult) mc.hitResult).getEntity();
                mc.gameMode.attack(p, target);
                Slots.select(back);
            }
        }
    }

    /**
     * Extended Spear Reach: extra reach voor de spear + auto-mace aan, want bij
     * vasthouden van de spear swapt hij attribuut-gewijs naar de mace.
     */
    static final class ExtendedSpearReachModule extends ModuleBase {
        final NumberSetting bonus = addSetting(new NumberSetting("Spear reach bonus", 1.5F, 0F, 4F));

        ExtendedSpearReachModule() {
            super("extended-spear-reach", "Extended Spear Reach",
                    "Extra spear-reach + auto-mace aan bij spear vast.", Addons.spear());
        }

        @Override protected void onEnableInternal() {
            Modules.setEnabled("auto-mace", true);
            Modules.setEnabled("mace", true);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) { InstantPack.AddonState.spearReachBonus = 0F; return; }
            boolean holdingSpear = net.champ.addon.util.Slots.itemId(p.getMainHandItem()).contains("spear");
            InstantPack.AddonState.spearReachBonus = holdingSpear ? bonus.get() : 0F;
        }

        @Override protected void onDisableInternal() { InstantPack.AddonState.spearReachBonus = 0F; }
    }

    /**
     * Spear Riposte (eigen ontwerp): blokkeer kort met schild zodra een enemy
     * uithaalt, riposteer daarna instant met een spear-thrust + mace-tap.
     */
    static final class SpearRiposteModule extends ModuleBase {
        final NumberSetting blockTicks = addSetting(new NumberSetting("Block time (ticks)", 6, 1, 20));
        final NumberSetting range = addSetting(new NumberSetting("Range", 4.5F, 1F, 6F));
        int stage, wait, back;

        SpearRiposteModule() {
            super("spear-riposte", "Spear Riposte",
                    "Kort blokken, dan instant spear-thrust + mace-tap.", Addons.spear());
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            if (p == null) return;
            if (stage == 0) {
                var enemy = net.champ.addon.util.Targets.nearestEnemy(range.get());
                if (enemy == null || enemy.getAttackAnim(0F) < 0.3F) return;
                int shield = Slots.hotbarSlotContaining("shield");
                if (shield < 0) return;
                back = Slots.selected();
                Slots.select(shield);
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                stage = 1; wait = (int) blockTicks.get();
                return;
            }
            if (wait-- > 0) return;
            p.stopUsingItem();
            int spear = bestSpear();
            if (spear >= 0) {
                Slots.select(spear);
                if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr) {
                    mc.gameMode.attack(p, ehr.getEntity());
                }
            }
            Slots.select(back);
            stage = 0;
        }
    }
}
