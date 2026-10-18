package net.champ.client.module.impl.visual;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.util.Modules;
import net.champ.addon.util.Slots;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.module.setting.StringSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/** Visual-categorie (twee-ogen icoon = bestaande VISUALS-categorie). */
public final class VisualPack {
    private VisualPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new XrayUltimateModule());
        out.add(new AnimationsModule());
        out.add(new HealthIndicatorModule());
        out.add(new FasterTotemAnimationModule());
        out.add(new ShieldStatusModule());
        out.add(new PotionTimerModule());
        return out;
    }

    static void actionBar(Player p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    /** Xray Ultimate: gebruikt de normale Xray Ultimate (schakelt client-Xray in). */
    static final class XrayUltimateModule extends ModuleBase {
        XrayUltimateModule() {
            super("xray-ultimate", "Xray Ultimate",
                    "Gebruikt de normale Xray Ultimate van de client.", ModuleCategory.VISUALS);
        }

        @Override protected void onEnableInternal() {
            if (!Modules.setEnabled("xray", true)) {
                Player p = Slots.player();
                if (p != null) actionBar(p, "[Xray Ultimate] client-Xray niet gevonden!");
            }
        }

        @Override protected void onDisableInternal() { Modules.setEnabled("xray", false); }
        @Override protected void tick() {}
    }

    /** Animations: custom swings en animatie-snelheid. */
    static final class AnimationsModule extends ModuleBase {
        final NumberSetting swingSpeed = addSetting(new NumberSetting("Swing speed", 1F, 0.25F, 3F));
        final BooleanSetting onlyCrits = addSetting(new BooleanSetting("Swing only on crit", false));

        AnimationsModule() {
            super("animations", "Animations",
                    "Custom swings en animaties.", ModuleCategory.VISUALS);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            if (p == null) return;
            if (!mc.options.keyAttack.isDown()) return;
            boolean crit = !p.onGround() && net.champ.addon.util.Targets.fallDistance(p) > 0;
            if (onlyCrits.get() && !crit) return;
            // Custom swing-ritme: extra swing-ticks voor vloeiendere animatie.
            if (p.attackAnim <= 0.1F * swingSpeed.get()) {
                p.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
    }

    /** Health Indicator: toont enemy-health boven de action-bar bij richten. */
    static final class HealthIndicatorModule extends ModuleBase {
        HealthIndicatorModule() {
            super("health-indicator", "Health Indicator",
                    "Toont health van je target.", ModuleCategory.VISUALS);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            var mc = Slots.mc();
            if (p == null) return;
            if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr
                    && ehr.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                actionBar(p, "[HP] " + living.getName().getString() + ": "
                        + (int) living.getHealth() + "/" + (int) living.getMaxHealth());
            }
        }
    }

    /** Faster Totem Animation: instant pop-melding + timer in plaats van trage animatie. */
    static final class FasterTotemAnimationModule extends ModuleBase {
        int lastTotems = -1;

        FasterTotemAnimationModule() {
            super("faster-totem", "Faster Totem Animation",
                    "Instant totem-pop feedback.", ModuleCategory.VISUALS);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            int totems = p.getInventory().countItem(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
            if (lastTotems >= 0 && totems < lastTotems && p.getHealth() < p.getMaxHealth()) {
                actionBar(p, "[Totem] POP! (" + totems + " over)");
            }
            lastTotems = totems;
        }

        @Override protected void onDisableInternal() { lastTotems = -1; }
    }

    /** Shield Status: toont schild-durability en block-status. */
    static final class ShieldStatusModule extends ModuleBase {
        ShieldStatusModule() {
            super("shield-status", "Shield Status",
                    "Toont schild-status en durability.", ModuleCategory.VISUALS);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            for (var hand : new net.minecraft.world.InteractionHand[]{
                    net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.InteractionHand.OFF_HAND}) {
                var s = p.getItemInHand(hand);
                if (!s.isEmpty() && net.champ.addon.util.Slots.itemId(s).contains("shield")) {
                    int left = s.getMaxDamage() - s.getDamageValue();
                    actionBar(p, "[Shield] " + left + " durability"
                            + (p.isBlocking() ? " (BLOCKING)" : ""));
                    return;
                }
            }
        }
    }

    /** Potion Timer: countdown van je actieve effecten in de action-bar. */
    static final class PotionTimerModule extends ModuleBase {
        PotionTimerModule() {
            super("potion-timer", "Potion Timer",
                    "Toont resterende potion-tijd.", ModuleCategory.VISUALS);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            var effects = p.getActiveEffects();
            if (effects.isEmpty()) return;
            var inst = effects.iterator().next();
            MobEffect effect = inst.getEffect().value();
            actionBar(p, "[Potion] " + effect.getDescriptionId() + ": " + (inst.getDuration() / 20) + "s");
        }
    }
}
