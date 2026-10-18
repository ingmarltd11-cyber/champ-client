package net.champ.client.module.impl.combat;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.spoof.SilentAim;
import net.champ.addon.util.Inv;
import net.champ.addon.util.LookAt;
import net.champ.addon.util.Slots;
import net.champ.addon.util.Targets;
import net.champ.client.event.impl.KeyEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.KeySetting;
import net.champ.client.module.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/** Instant-macro's, shield-breakers, liquid-drain en aim-assists (hitbox/reach). */
public final class InstantPack {
    private InstantPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new InstantShieldModule());
        out.add(new InstantCrossbowModule());
        out.add(new AutoShieldBreakerModule());
        out.add(new AutoDrainModule());
        out.add(new ExpandHitboxesModule());
        out.add(new ReachModule());
        return out;
    }

    /** Gedeelde runtime-state die de mixins lezen (hitbox/reach/spear). */
    public static final class AddonState {
        public static volatile float hitboxExpand = 0F;
        public static volatile float reachBonus = 0F;
        public static volatile float spearReachBonus = 0F;
    }

    /** Instant Shield: haalt schild uit inventory, gebruikt het, legt het terug. */
    static final class InstantShieldModule extends ModuleBase {
        final KeySetting trigger = addSetting(new KeySetting("Shield key", -1));
        final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap back", true));
        int prevSlot = -1;
        int holdTicks;

        InstantShieldModule() {
            super("instant-shield", "Instant Shield",
                    "Haalt schild uit inventory, blockt, legt het terug.", ModuleCategory.COMBAT);
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (CartPack.triggerPressed(this, trigger, ev)) armShield();
            });
        }

        private void armShield() {
            Player p = Slots.player();
            if (p == null) return;
            int hotbar = Slots.hotbarSlotContaining("shield");
            if (hotbar >= 0) {
                prevSlot = Slots.selected();
                Slots.select(hotbar);
            } else {
                int found = Inv.find("shield");
                if (found < 0) return;
                prevSlot = Slots.selected();
                int free = Slots.firstEmptyHotbarSlot();
                if (free < 0) return;
                Inv.moveToHotbar(found, free);
                Slots.select(free);
            }
            holdTicks = 12;
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            if (holdTicks > 0) {
                holdTicks--;
                if (!p.isUsingItem()) Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
                if (holdTicks == 0 && swapBack.get() && prevSlot >= 0) Slots.select(prevSlot);
            }
        }
    }

    /** Instant Crossbow: pakt kruisboog uit inventory, schiet, legt terug. */
    static final class InstantCrossbowModule extends ModuleBase {
        final KeySetting trigger = addSetting(new KeySetting("Crossbow key", -1));
        final BooleanSetting swapBack = addSetting(new BooleanSetting("Swap back", true));
        int prevSlot = -1;
        int stage;

        InstantCrossbowModule() {
            super("instant-crossbow", "Instant Crossbow",
                    "Kruisboog uit inventory pakken, schieten, terugleggen.", ModuleCategory.COMBAT);
        }

        @Override protected void onEnableInternal() {
            on(KeyEvent.TYPE, ev -> {
                if (CartPack.triggerPressed(this, trigger, ev)) stage = 1;
            });
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null || stage == 0) return;
            if (stage == 1) {
                int hotbar = Slots.hotbarSlotContaining("crossbow");
                if (hotbar < 0) {
                    int found = Inv.find("crossbow");
                    if (found < 0) { stage = 0; return; }
                    int free = Slots.firstEmptyHotbarSlot();
                    if (free < 0) { stage = 0; return; }
                    Inv.moveToHotbar(found, free);
                    hotbar = free;
                }
                prevSlot = Slots.selected();
                Slots.select(hotbar);
                stage = 2;
            } else {
                Slots.mc().gameMode.useItem(p, InteractionHand.MAIN_HAND);
                if (swapBack.get() && prevSlot >= 0) Slots.select(prevSlot);
                stage = 0;
            }
        }
    }

    /** Auto Shield Breaker: spamt clicks tot schild down is, bijl hoeft niet in hotbar. */
    static final class AutoShieldBreakerModule extends ModuleBase {
        final NumberSetting cps = addSetting(new NumberSetting("Clicks per tick", 2, 1, 10));
        final NumberSetting range = addSetting(new NumberSetting("Range", 4.5F, 1F, 6F));

        AutoShieldBreakerModule() {
            super("auto-shield-breaker", "Auto Shield Breaker",
                    "Spamt tot het schild van de enemy down is.", ModuleCategory.COMBAT);
        }

        @Override protected void tick() {
            Player p = Slots.player();
            if (p == null) return;
            Player enemy = Targets.nearestEnemy(range.get());
            if (enemy == null || !enemy.isBlocking()) return;
            // Bijl uit inventory/hotbar pakken (hoeft niet in hotbar te liggen).
            int axe = Slots.hotbarSlotContaining("_axe");
            if (axe < 0) {
                int found = Inv.find("_axe");
                if (found < 0) return;
                int free = Slots.firstEmptyHotbarSlot();
                if (free < 0) return;
                Inv.moveToHotbar(found, free);
                axe = free;
            }
            Slots.select(axe);
            for (int i = 0; i < (int) cps.get(); i++) Slots.mc().gameMode.attack(p, enemy);
        }
    }

    /**
     * Auto Drain: scant liquid-bronnen (water/lava) binnen bereik, richt erop (silent aim of gewone aim),
     * pakt ze op met een LEGE bucket, en plaatst anders een cobweb in het liquid.
     */
    static final class AutoDrainModule extends ModuleBase {
        final NumberSetting range = addSetting(new NumberSetting("Scan range", 6, 2, 12));
        final BooleanSetting useSilentAim = addSetting(new BooleanSetting("Silent aim (else normal aim)", true));
        final BooleanSetting useCobweb = addSetting(new BooleanSetting("Cobweb if no empty bucket", true));

        private static final int MAX_ATTEMPTS = 3;
        private int cooldown, scanTimer, attempts;
        private BlockPos target;
        private final Set<Long> skip = new HashSet<>();
        private long nextSkipClear;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        AutoDrainModule() {
            super("auto-drain", "Auto Drain",
                    "Pakt liquids op met een lege bucket, anders cobweb erin.", ModuleCategory.COMBAT);
        }

        @Override protected void onEnableInternal() {
            target = null; cooldown = scanTimer = attempts = 0; skip.clear();
        }

        @Override protected void tick() {
            LocalPlayer p = Slots.player();
            var mc = Slots.mc();
            var level = mc.level;
            if (p == null || level == null || mc.gameMode == null || mc.screen != null) return;
            if (cooldown > 0) { cooldown--; return; }
            long now = System.currentTimeMillis();
            if (now >= nextSkipClear) { skip.clear(); nextSkipClear = now + 10_000L; }

            int bucket = Slots.hotbarSlotExact("bucket");          // exact: water_bucket/lava_bucket tellen niet
            int web = useCobweb.get() ? Slots.hotbarSlotExact("cobweb") : -1;
            if (bucket < 0 && web < 0) { target = null; return; }

            if (target != null && !isSource(level.getBlockState(target))) { target = null; attempts = 0; }
            if (target == null) {
                if (++scanTimer < 4) return;                        // scannen elke 4 ticks is ruim genoeg
                scanTimer = 0;
                target = findLiquid(p, level);
                attempts = 0;
                if (target == null) return;
            }

            Vec3 center = Vec3.atCenterOf(target);
            if (!aimAt(p, center)) return;

            if (bucket >= 0) {
                Slots.select(bucket);
                // Een bucket pakt liquid op via een gewone rechtsklik in de kijkrichting, niet via useItemOn.
                var result = mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
                if (result.consumesAction()) p.swing(InteractionHand.MAIN_HAND);
            } else {
                Slots.select(web);
                // Liquid is vervangbaar: klikken op het liquid-blok zelf zet de cobweb ín het liquid.
                var result = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                        new BlockHitResult(center, Direction.UP, target, false));
                if (result.consumesAction()) p.swing(InteractionHand.MAIN_HAND);
            }
            cooldown = 4;
            if (++attempts >= MAX_ATTEMPTS) {                       // lukt niet (bv. niet raakbaar): even overslaan
                skip.add(target.asLong());
                target = null;
                attempts = 0;
            }
        }

        private static boolean isSource(BlockState state) {
            var fluid = state.getFluidState();
            return fluid.isSource() && (fluid.is(FluidTags.WATER) || fluid.is(FluidTags.LAVA));
        }

        /** Dichtstbijzijnde bereikbare bron met vrij zicht (geen blok ertussen). */
        private BlockPos findLiquid(LocalPlayer p, net.minecraft.world.level.Level level) {
            double reach = Math.min(range.get(), p.blockInteractionRange());
            int r = (int) Math.ceil(reach);
            BlockPos pp = p.blockPosition();
            Vec3 eye = p.getEyePosition();
            double best = reach * reach;
            BlockPos found = null;
            for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
                m.set(pp.getX() + dx, pp.getY() + dy, pp.getZ() + dz);
                if (!isSource(level.getBlockState(m))) continue;
                double d = eye.distanceToSqr(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5);
                if (d > best) continue;
                if (skip.contains(m.asLong())) continue;
                Vec3 c = Vec3.atCenterOf(m);
                var clip = level.clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (clip.getType() != HitResult.Type.MISS) continue;   // blok ertussen
                best = d;
                found = m.immutable();
            }
            return found;
        }

        /** Silent aim: server-rotatie via de bestaande SilentAim-state. Anders: vloeiend met de camera. */
        private boolean aimAt(LocalPlayer p, Vec3 target) {
            if (useSilentAim.get()) {
                float[] a = Targets.anglesTo(Targets.eye(p), target);
                SilentAim.request(a[0], a[1], 3);
                return true;
            }
            return LookAt.smooth(p, target, 30F);
        }
    }

    /** Expand Hitboxes: vergroot hitboxes met een slider (detecteerbaar!). */
    static final class ExpandHitboxesModule extends ModuleBase {
        final NumberSetting amount = addSetting(new NumberSetting("Expand amount", 0.3F, 0F, 1.5F));

        ExpandHitboxesModule() {
            super("expand-hitboxes", "Expand Hitboxes",
                    "Vergroot entity-hitboxes. Let op: goed detecteerbaar!", ModuleCategory.COMBAT);
        }

        @Override protected void onEnableInternal() { AddonState.hitboxExpand = amount.get(); }
        @Override protected void onDisableInternal() { AddonState.hitboxExpand = 0F; }
        @Override protected void tick() { AddonState.hitboxExpand = amount.get(); }
    }

    /**
     * Reach: extra reach met slider via vanilla-attributen
     * (entity + block interaction range). Waarschuwing: zeer detecteerbaar!
     */
    static final class ReachModule extends ModuleBase {
        final NumberSetting bonus = addSetting(new NumberSetting("Extra reach", 1.0F, 0F, 4F));
        static final net.minecraft.resources.Identifier REACH_ID =
                net.minecraft.resources.Identifier.fromNamespaceAndPath("champ-addons", "reach");

        ReachModule() {
            super("reach", "Reach",
                    "Extra reach. Waarschuwing: zeer detecteerbaar!", ModuleCategory.COMBAT);
        }

        @Override protected void onEnableInternal() { apply(); }
        @Override protected void onDisableInternal() { clear(); AddonState.reachBonus = 0F; }
        @Override protected void tick() { apply(); }

        // Attributen via registry-id's (mapping-veilig, geen harde veldnamen).
        private static net.minecraft.world.entity.ai.attributes.AttributeInstance attr(
                Player p, String id) {
            try {
                var level = Slots.mc().level;
                if (level == null) return null;
                var lookup = level.registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.ATTRIBUTE);
                var holder = lookup.get(net.minecraft.resources.Identifier.withDefaultNamespace(id));
                if (holder.isEmpty()) return null;
                return p.getAttribute(holder.get());
            } catch (Exception e) {
                return null;
            }
        }

        private void apply() {
            float extra = bonus.get();
            AddonState.reachBonus = extra;
            var p = Slots.player();
            if (p == null) return;
            for (String id : new String[]{"player.entity_interaction_range", "player.block_interaction_range"}) {
                var inst = attr(p, id);
                if (inst == null) continue;
                inst.removeModifier(REACH_ID);
                if (extra > 0.001F) inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        REACH_ID, extra,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }

        private void clear() {
            var p = Slots.player();
            if (p == null) return;
            for (String id : new String[]{"player.entity_interaction_range", "player.block_interaction_range"}) {
                var inst = attr(p, id);
                if (inst != null) inst.removeModifier(REACH_ID);
            }
        }
    }
}
