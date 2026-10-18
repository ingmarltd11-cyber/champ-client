package net.champ.client.module.impl.crystal;

import net.champ.addon.Addons;
import net.champ.addon.module.ModuleBase;
import net.champ.addon.spoof.SilentAim;
import net.champ.addon.util.Slots;
import net.champ.addon.util.Targets;
import net.champ.client.event.impl.KeyEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.KeySetting;
import net.champ.client.module.setting.NumberSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Crystal-categorie: anchor-automatisering en obsidian-plaatsing. */
public final class CrystalPack {
    private CrystalPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new AutoAnchorModule());
        out.add(new AnchorMacroModule());
        out.add(new AutoObsidianModule());
        return out;
    }

    /** Gedeelde anchor-FSM: plaatsen -> vullen met 1 glowstone -> exploderen. */
    static final class AnchorFlow {
        int stage; // 0 idle, 1 place, 2 charge, 3 explode
        int wait;

        boolean step(int explodeSlot, int delayTicks) {
            Player p = Slots.player();
            if (p == null) return false;
            int anchor = Slots.hotbarSlotContaining("respawn_anchor");
            int glow = Slots.hotbarSlotContaining("glowstone");
            if (anchor < 0 || glow < 0) { stage = 0; return false; }
            if (wait-- > 0) return true;
            BlockPos base = p.blockPosition().below();
            if (stage == 0) { stage = 1; wait = delayTicks; return true; }
            if (stage == 1) {
                Slots.select(anchor);
                useAt(p, base.above());
                stage = 2; wait = delayTicks;
                return true;
            }
            if (stage == 2) {
                Slots.select(glow);
                useAt(p, base.above());
                stage = 3; wait = delayTicks;
                return true;
            }
            Slots.select(explodeSlot);
            useAt(p, base.above());
            stage = 0;
            return true;
        }

        static void useAt(Player p, BlockPos pos) {
            Vec3 target = Vec3.atCenterOf(pos);
            float[] a = Targets.anglesTo(Targets.eye(p), target);
            SilentAim.request(a[0], a[1], 3);
            Slots.mc().gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(target, Direction.UP, pos, false));
        }
    }

    /** Auto Respawn Anchor: plaatsen, vullen met 1 glowstone, exploderen met gekozen slot. */
    static final class AutoAnchorModule extends ModuleBase {
        final NumberSetting explodeSlot = addSetting(new NumberSetting("Explode hotbar slot", 1, 0, 8));
        final NumberSetting delay = addSetting(new NumberSetting("Delay (ticks)", 3, 0, 20));
        final AnchorFlow flow = new AnchorFlow();

        AutoAnchorModule() {
            super("auto-anchor", "Auto Anchor",
                    "Plaatst anchor, vult met 1 glowstone, explodeert.", Addons.crystal());
        }

        @Override protected void onEnableInternal() { flow.stage = 0; }
        @Override protected void tick() {
            if (!inGame()) return;
            flow.step((int) explodeSlot.get(), (int) delay.get());
        }
    }

    /** Anchor Macro: aanzetten, binden, bij klik plaatsen + vullen + exploderen. */
    static final class AnchorMacroModule extends ModuleBase {
        final NumberSetting explodeSlot = addSetting(new NumberSetting("Explode hotbar slot", 1, 0, 8));
        final KeySetting trigger = addSetting(new KeySetting("Macro key", -1));
        final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap back", true));
        final AnchorFlow flow = new AnchorFlow();
        int prevSlot = -1;
        boolean running;

        AnchorMacroModule() {
            super("anchor-macro", "Anchor Macro",
                    "Eén-klik anchor: plaatsen, vullen, exploderen.", Addons.crystal());
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (isEnabled() && trigger.value() >= 0 && ev.key == trigger.value() && ev.action == 1) {
                    prevSlot = Slots.selected();
                    flow.stage = 0;
                    flow.wait = 0;
                    running = true;
                }
            });
        }

        @Override protected void tick() {
            if (!running || !inGame()) return;
            boolean wasBusy = flow.stage != 0 || flow.wait > 0;
            flow.step((int) explodeSlot.get(), 2);
            if (wasBusy && flow.stage == 0 && flow.wait <= 0) {
                running = false;
                if (swapBack.get() && prevSlot >= 0) Slots.select(prevSlot);
                prevSlot = -1;
            }
        }
    }

    /** Auto Obsidian: bij klik op gekozen bind automatisch obsidian plaatsen. */
    static final class AutoObsidianModule extends ModuleBase {
        final KeySetting trigger = addSetting(new KeySetting("Place key", -1));

        AutoObsidianModule() {
            super("auto-obsidian", "Auto Obsidian",
                    "Plaatst automatisch obsidian bij klik op bind.", Addons.crystal());
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (isEnabled() && trigger.value() >= 0 && ev.key == trigger.value() && ev.action == 1) {
                    placeObsidian();
                }
            });
        }

        @Override protected void tick() {}

        private void placeObsidian() {
            Player p = Slots.player();
            if (p == null) return;
            int obby = Slots.hotbarSlotContaining("obsidian");
            if (obby < 0) return;
            BlockPos pos = p.blockPosition().below();
            Slots.select(obby);
            AnchorFlow.useAt(p, pos.above());
        }
    }
}
