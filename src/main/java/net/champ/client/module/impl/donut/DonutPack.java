package net.champ.client.module.impl.donut;

import net.champ.addon.module.ModuleBase;
import net.champ.addon.util.Inv;
import net.champ.client.module.impl.donutmacros.MarketScan;
import net.champ.addon.util.LookAt;
import net.champ.addon.util.Slots;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.module.setting.StringSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Donut SMP-categorie. Alleen waarschuwen op niet-Donut servers (geen auto-off),
 * precies zoals gevraagd: bij join met een module aan volgt een chat-warning.
 */
public final class DonutPack {
    private DonutPack() {}

    public static List<ClientModule> list() {
        List<ClientModule> out = new ArrayList<>();
        out.add(new AutoSchematicBuilderModule());
        out.add(new AutoMineOutModule());
        out.add(new BaseFinderModule());
        return out;
    }

    /** True als we op Donut SMP zitten (ip-check), anders warn-only. */
    static boolean isDonut() {
        var mc = Slots.mc();
        try {
            var server = mc.getCurrentServer();
            if (server == null || server.ip == null) return false;
            return server.ip.toLowerCase(Locale.ROOT).contains("donut");
        } catch (Exception e) {
            return false;
        }
    }

    /** Bereik-check (vervangt het verwijderde Player.canInteractWithBlock uit oudere mappings). */
    static boolean canReach(LocalPlayer p, BlockPos pos) {
        double range = p.blockInteractionRange();
        return p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) < range * range;
    }

    static void warnNotDonut(Player p, String module) {
        p.displayClientMessage(Component.literal(
                "[Champ Client] " + module + " is voor Donut SMP — je zit ergens anders!"), false);
    }

    /** Laadt een .litematic (Litematica) en bouwt hem laag voor laag na. */
    static final class AutoSchematicBuilderModule extends ModuleBase {
        final StringSetting file = addSetting(new StringSetting("Schematic file", "build.litematic"));
        final NumberSetting delay = addSetting(new NumberSetting("Place delay (ticks)", 3, 0, 20));
        final BooleanSetting leaveOnPlayer = addSetting(new BooleanSetting("Leave when player nearby", true));
        final NumberSetting playerRange = addSetting(new NumberSetting("Player range", 24, 4, 64));
        final BooleanSetting autoBuy = addSetting(new BooleanSetting("Auto-buy materials", false));
        final BooleanSetting lookAtBlocks = addSetting(new BooleanSetting("Look at blocks", true));
        final BooleanSetting scanMarket = addSetting(new BooleanSetting("Scan market (/ah)", true));
        final net.champ.client.module.setting.KeySetting acceptKey =
                addSetting(new net.champ.client.module.setting.KeySetting("Accept purchase key", -1));

        private final MarketScan market = new MarketScan();
        private List<MarketScan.Quote> quotes = List.of();
        private long acceptUntil;

        /** Hoeveel nog-niet-geplaatste blokken per tick maximaal bekeken worden (houdt de tick-kosten constant). */
        private static final int WINDOW = 96;

        private record Parsed(BlockPlacement[] plan, int kinds, String error) {}

        private volatile Parsed parsed;
        private boolean loading;
        private BlockPlacement[] plan = new BlockPlacement[0];
        private boolean[] done = new boolean[0];
        private int head, remaining, wait, restockWait;
        private long nextIdleMsg;
        private BlockPos origin;
        private boolean warned;
        private final java.util.Set<String> warnedMissing = new java.util.HashSet<>();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        AutoSchematicBuilderModule() {
            super("auto-schematic", "Auto Schematic Builder",
                    "Laadt .litematic, telt kosten, bouwt automatisch.", ModuleCategory.DONUT);
        }

        @Override protected void onEnableInternal() {
            warned = false;
            plan = new BlockPlacement[0];
            done = new boolean[0];
            head = remaining = wait = restockWait = 0;
            nextIdleMsg = 0;
            warnedMissing.clear();
            market.stop();
            quotes = List.of();
            acceptUntil = 0;
            on(net.champ.client.event.impl.KeyEvent.TYPE, ev -> {
                if (isEnabled() && acceptKey.value() >= 0 && ev.key == acceptKey.value() && ev.action == 1
                        && System.currentTimeMillis() < acceptUntil) {
                    startBuy();
                }
            });
            LocalPlayer p = Slots.player();
            // Het bouwpunt ligt vast vanaf nu: eerst verschoof het mee met de speler en liep de bouw uit elkaar.
            origin = p != null ? p.blockPosition() : null;
            loading = true;
            File f = new File(Slots.mc().gameDirectory, "schematics/" + file.get());
            if (!f.getName().endsWith(".litematic")) {
                loading = false;
                if (p != null) warn(p, "Alleen .litematic wordt ondersteund!");
                return;
            }
            // Inlezen op een achtergrond-thread, anders hapert het spel bij grote schematics.
            java.util.concurrent.CompletableFuture.supplyAsync(() -> parse(f)).thenAccept(r -> parsed = r);
        }

        @Override protected void onDisableInternal() {
            market.stop();
            acceptUntil = 0;
        }

        @Override protected void tick() {
            LocalPlayer p = Slots.player();
            var mc = Slots.mc();
            var level = mc.level;
            if (p == null || level == null || mc.gameMode == null || mc.screen != null) return;
            if (!isDonut()) {
                if (!warned) { warned = true; warnNotDonut(p, "Auto Schematic Builder"); }
            } else warned = false;
            if (origin == null) origin = p.blockPosition();
            if (market.running()) return;   // tijdens scannen/kopen is de AH open en wordt er niet gebouwd

            Parsed ready = parsed;
            if (ready != null) { parsed = null; loading = false; apply(ready, p); }
            if (loading || remaining <= 0) return;
            if (leaveOnPlayer.get() && net.champ.addon.util.Targets.nearestEnemy(playerRange.get()) != null) return;
            if (restockWait > 0) restockWait--;
            if (wait > 0) { wait--; return; }

            while (head < plan.length && done[head]) head++;
            int end = Math.min(plan.length, head + WINDOW);
            for (int i = head; i < end; i++) {
                if (done[i]) continue;
                BlockPlacement b = plan[i];
                cursor.set(origin.getX() + b.dx(), origin.getY() + b.dy(), origin.getZ() + b.dz());
                BlockState st = level.getBlockState(cursor);
                if (Slots.blockId(st.getBlock()).equals(b.blockId())) { done[i] = true; remaining--; continue; }
                if (!(st.isAir() || st.canBeReplaced())) continue;       // er staat al iets anders
                if (!canReach(p, cursor)) continue;      // buiten bereik
                Direction support = supportFace(level, cursor);
                if (support == null) continue;                           // niets om tegenaan te plaatsen
                BlockPos pos = cursor.immutable();
                if (p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(pos))) continue;
                int slot = Slots.hotbarSlotExact(b.blockId());
                if (slot < 0) { restock(b.blockId(), p); continue; }

                BlockPos sup = pos.relative(support);
                Direction face = support.getOpposite();
                Vec3 hit = Vec3.atCenterOf(sup).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
                if (lookAtBlocks.get() && !LookAt.smooth(p, hit, 30F)) return;   // eerst netjes kijken
                Slots.select(slot);
                var result = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, sup, false));
                if (result.consumesAction()) {
                    p.swing(InteractionHand.MAIN_HAND);
                    done[i] = true;
                    remaining--;
                    if (remaining == 0) {
                        warn(p, "[Schematic] klaar!");
                        setEnabled(false);
                    }
                }
                wait = (int) delay.get();
                return;
            }
            long now = System.currentTimeMillis();
            if (now >= nextIdleMsg) {
                nextIdleMsg = now + 5000;
                p.displayClientMessage(Component.literal("[Schematic] " + remaining
                        + " blokken te gaan — niets plaatsbaar binnen bereik (loop dichterbij / mis steun of materiaal)."), true);
            }
        }

        /** Richting van een naastliggend vast blok waar tegenaan geplaatst kan worden. */
        private static Direction supportFace(net.minecraft.world.level.Level level, BlockPos pos) {
            BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
            for (Direction d : SUPPORT_ORDER) {
                n.setWithOffset(pos, d);
                BlockState ns = level.getBlockState(n);
                if (!ns.isAir() && !ns.canBeReplaced() && ns.getFluidState().isEmpty()) return d;
            }
            return null;
        }

        private static final Direction[] SUPPORT_ORDER = {
                Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP };

        /** Haalt materiaal uit het hoofdinventory naar de hotbar (max. één swap per paar ticks). */
        private void restock(String blockId, LocalPlayer p) {
            if (restockWait > 0) return;
            int from = Inv.findMainExact(blockId);
            if (from < 0) {
                if (warnedMissing.add(blockId)) {
                    warn(p, "[Schematic] mis materiaal: " + blockId
                            + (autoBuy.get() ? " (auto-buy is nog niet beschikbaar)" : ""));
                }
                return;
            }
            int target = Slots.firstEmptyHotbarSlot();
            if (target < 0) target = Math.max(0, Slots.selected());
            Inv.swapToHotbar(from, target);
            restockWait = 4;
        }

        private void apply(Parsed r, LocalPlayer p) {
            if (r.error() != null) { warn(p, "[Schematic] laden mislukt: " + r.error()); return; }
            plan = r.plan();
            done = new boolean[plan.length];
            head = 0;
            remaining = plan.length;
            warn(p, "[Schematic] " + r.kinds() + " soorten, " + plan.length
                    + " blokken (alleen bloktype, geen draai-richting). Bouwpunt = waar je stond.");
            startMarketScan(p);
        }

        /** Telt wat er nog nodig is (min wat je al hebt) en laat de /ah-scan de kosten bepalen. */
        private void startMarketScan(LocalPlayer p) {
            if (!scanMarket.get()) return;
            if (!isDonut()) { warn(p, "[Schematic] markt-scan overgeslagen: je zit niet op Donut SMP."); return; }
            Map<String, Integer> counts = new HashMap<>();
            for (BlockPlacement b : plan) counts.merge(b.blockId(), 1, Integer::sum);
            List<MarketScan.Need> needs = new ArrayList<>();
            for (var e : counts.entrySet()) {
                int missing = e.getValue() - owned(p, e.getKey());
                if (missing > 0) needs.add(new MarketScan.Need(e.getKey(), missing));
            }
            if (needs.isEmpty()) { warn(p, "[Schematic] je hebt al alle materialen."); return; }
            needs.sort((a, b) -> Integer.compare(b.amount(), a.amount()));
            if (needs.size() > 40) {
                warn(p, "[Schematic] " + needs.size() + " soorten nodig, alleen de 40 grootste worden geprijsd.");
                needs = new ArrayList<>(needs.subList(0, 40));
            }
            warn(p, "[Schematic] markt scannen via /ah voor " + needs.size() + " soorten... (blijf staan, de AH gaat open)");
            market.scan(needs, this::say, this::onScanDone);
        }

        private static int owned(LocalPlayer p, String itemId) {
            int n = 0;
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                var stack = p.getInventory().getItem(i);
                if (!stack.isEmpty() && Slots.itemId(stack).equals(itemId)) n += stack.getCount();
            }
            return n;
        }

        private void onScanDone(MarketScan.Result r) {
            quotes = r.quotes();
            quotedTotal = 0;
            int sure = 0;
            StringBuilder problems = new StringBuilder();
            for (MarketScan.Quote q : r.quotes()) {
                if (q.complete()) { sure++; quotedTotal += q.total(); }
                else problems.append("\n  - ").append(q.itemId()).append(": ").append(q.note());
            }
            say("[Schematic] Marktprijs (goedkoopste listings): $" + money(r.total()) + " voor "
                    + r.quotes().size() + " soorten" + (r.complete() ? "." : ", sommige onzeker:" + problems));
            if (sure == 0) return;
            if (!autoBuy.get()) { say("[Schematic] Auto-buy staat uit: koop zelf, of zet 'Auto-buy materials' aan."); return; }
            if (acceptKey.value() < 0) { say("[Schematic] Stel eerst een 'Accept purchase key' in om te kunnen kopen."); return; }
            acceptUntil = System.currentTimeMillis() + 60_000L;
            say("[Schematic] Kopen? Druk binnen 60 s op je 'Accept purchase key' ($" + money(quotedTotal)
                    + " voor " + sure + " zekere soorten). Doe je niets, dan wordt er niets gekocht.");
        }

        private double quotedTotal;

        private void startBuy() {
            if (market.running() || quotes.isEmpty()) return;
            acceptUntil = 0;
            say("[Schematic] Kopen gestart (max $" + money(quotedTotal * 1.05) + ")...");
            market.buy(quotes, quotedTotal * 1.05, this::say,
                    ok -> say(ok ? "[Schematic] materiaal gekocht, het bouwen gaat verder."
                            : "[Schematic] kopen gestopt of onvolledig, controleer je inventory."));
        }

        private void say(String text) {
            LocalPlayer p = Slots.player();
            if (p != null) warn(p, text);
        }

        private static String money(double v) {
            if (v >= 1e9) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
            if (v >= 1e6) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
            if (v >= 1e3) return String.format(Locale.ROOT, "%.1fK", v / 1e3);
            return String.format(Locale.ROOT, "%.0f", v);
        }

        /** Leest regio 0 van een .litematic. Draait op een achtergrond-thread, raakt de wereld niet aan. */
        private static Parsed parse(File f) {
            try {
                if (!f.isFile()) return new Parsed(null, 0, "bestand niet gevonden: " + f.getName());
                CompoundTag root = NbtIo.readCompressed(f.toPath(), net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                CompoundTag regions = root.getCompoundOrEmpty("Regions");
                if (regions.isEmpty()) return new Parsed(null, 0, "geen regio's in het bestand");
                CompoundTag region = regions.getCompoundOrEmpty(regions.keySet().iterator().next());
                CompoundTag size = region.getCompoundOrEmpty("Size");
                int sx = Math.abs(size.getIntOr("x", 0)), sy = Math.abs(size.getIntOr("y", 0)), sz = Math.abs(size.getIntOr("z", 0));
                long volume = (long) sx * sy * sz;
                if (volume <= 0) return new Parsed(null, 0, "lege regio");
                if (volume > 8_000_000L) return new Parsed(null, 0, "schematic te groot (" + volume + " blokken)");
                ListTag palette = region.getListOrEmpty("BlockStatePalette");
                long[] states = region.getLongArray("BlockStates").orElse(new long[0]);
                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
                String[] names = new String[palette.size()];
                boolean[] air = new boolean[palette.size()];
                for (int i = 0; i < names.length; i++) {
                    String n = palette.getCompoundOrEmpty(i).getStringOr("Name", "minecraft:air");
                    names[i] = n.startsWith("minecraft:") ? n.substring(10) : n;
                    air[i] = n.equals("minecraft:air") || n.equals("minecraft:cave_air") || n.equals("minecraft:void_air");
                }
                // Litematic-volgorde is x, dan z, dan y: dus al laag voor laag van onder naar boven.
                List<BlockPlacement> out = new ArrayList<>();
                boolean[] used = new boolean[names.length];
                for (int i = 0; i < volume; i++) {
                    int idx = bitAt(states, (long) i * bits, bits);
                    if (idx < 0 || idx >= names.length || air[idx]) continue;
                    used[idx] = true;
                    out.add(new BlockPlacement(i % sx, (int) (i / ((long) sx * sz)), (i / sx) % sz, names[idx]));
                }
                int kinds = 0;
                for (boolean u : used) if (u) kinds++;
                return new Parsed(out.toArray(new BlockPlacement[0]), kinds, null);
            } catch (Exception e) {
                return new Parsed(null, 0, String.valueOf(e.getMessage()));
            }
        }

        private static int bitAt(long[] arr, long bitIndex, int bits) {
            int start = (int) (bitIndex / 64), end = (int) ((bitIndex + bits - 1) / 64);
            if (end >= arr.length) return -1;
            int shift = (int) (bitIndex % 64);
            long mask = (1L << bits) - 1;
            if (start == end) return (int) ((arr[start] >>> shift) & mask);
            long v = (arr[start] >>> shift) | (arr[end] << (64 - shift));
            return (int) (v & mask);
        }

        private void warn(Player p, String text) {
            p.displayClientMessage(Component.literal(text), false);
        }

        record BlockPlacement(int dx, int dy, int dz, String blockId) {}
    }

    /** Auto Mine Out Area: hoeken of radius opgeven, mijnt blok voor blok uit met het gekozen houweel. */
    static final class AutoMineOutModule extends ModuleBase {
        final net.champ.client.module.setting.ModeSetting mode =
                addSetting(new net.champ.client.module.setting.ModeSetting("Mode", "Radius", "Radius", "Corners"));
        final NumberSetting rx = addSetting(new NumberSetting("X size", 8, 1, 64));
        final NumberSetting ry = addSetting(new NumberSetting("Y size", 4, 1, 64));
        final NumberSetting rz = addSetting(new NumberSetting("Z size", 8, 1, 64));
        final StringSetting pickaxe = addSetting(new StringSetting("Pickaxe", "netherite_pickaxe"));
        final net.champ.client.module.setting.KeySetting setCorner =
                addSetting(new net.champ.client.module.setting.KeySetting("Set corner", -1));
        final BooleanSetting lookAtBlocks = addSetting(new BooleanSetting("Look at blocks", true));
        BlockPos cornerA, cornerB, anchor;
        BlockPos target;
        Direction targetFace = Direction.UP;
        boolean warned;
        int mined, ticksSinceReport, idleTicks;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        private final BlockPos.MutableBlockPos nb = new BlockPos.MutableBlockPos();
        // regio-grenzen (inclusief), elke tick bijgewerkt
        private int minX, minY, minZ, maxX, maxY, maxZ;

        AutoMineOutModule() {
            super("auto-mine-out", "Auto Mine Out",
                    "Mijnt een gebied uit met gekozen houweel.", ModuleCategory.DONUT);
        }

        @Override protected void onEnableInternal() {
            warned = false;
            target = null;
            mined = ticksSinceReport = idleTicks = 0;
            LocalPlayer p = Slots.player();
            anchor = p != null ? p.blockPosition() : null;   // radius-modus blijft rond dit punt, loopt niet mee
            on(net.champ.client.event.impl.KeyEvent.TYPE, ev -> {
                if (isEnabled() && setCorner.value() >= 0 && ev.key == setCorner.value() && ev.action == 1) {
                    Player pl = Slots.player();
                    if (pl == null) return;
                    if (cornerA == null) cornerA = pl.blockPosition();
                    else if (cornerB == null) cornerB = pl.blockPosition();
                    else { cornerA = pl.blockPosition(); cornerB = null; }
                }
            });
        }

        @Override protected void tick() {
            LocalPlayer p = Slots.player();
            var mc = Slots.mc();
            var level = mc.level;
            if (p == null || level == null || mc.gameMode == null || mc.screen != null) return;
            if (!isDonut()) {
                if (!warned) { warned = true; warnNotDonut(p, "Auto Mine Out"); }
            } else warned = false;
            if (!updateBounds(p)) return;

            int pick = Slots.hotbarSlotContaining(pickaxe.get().toLowerCase(Locale.ROOT));
            if (pick >= 0 && pick != Slots.selected()) Slots.select(pick);

            if (target != null) {
                BlockState st = level.getBlockState(target);
                if (st.isAir()) { mined++; target = null; }
                else if (!canReach(p, target) || !inRegion(target)) target = null;
            }
            if (target == null && !pickTarget(p, level)) {
                if (++idleTicks % 100 == 1) reportIdle(p, level);
                return;
            }
            idleTicks = 0;

            Vec3 center = Vec3.atCenterOf(target);
            if (lookAtBlocks.get() && !LookAt.smooth(p, center, 35F)) return;
            // Net als linkermuisknop ingedrukt houden: continueDestroyBlock start en vervolgt het breken zelf.
            if (mc.gameMode.continueDestroyBlock(target, targetFace)) p.swing(InteractionHand.MAIN_HAND);

            if (++ticksSinceReport >= 40) {
                ticksSinceReport = 0;
                p.displayClientMessage(Component.literal("[Mine Out] " + mined + " blokken gemijnd"), true);
            }
        }

        private boolean updateBounds(Player p) {
            BlockPos a, b;
            if (mode.is("Corners")) {
                if (cornerA == null || cornerB == null) return false;
                a = cornerA; b = cornerB;
            } else {
                if (anchor == null) anchor = p.blockPosition();
                int x = (int) rx.get(), y = (int) ry.get(), z = (int) rz.get();
                a = anchor.offset(-x, -y, -z); b = anchor.offset(x, y, z);
            }
            minX = Math.min(a.getX(), b.getX()); maxX = Math.max(a.getX(), b.getX());
            minY = Math.min(a.getY(), b.getY()); maxY = Math.max(a.getY(), b.getY());
            minZ = Math.min(a.getZ(), b.getZ()); maxZ = Math.max(a.getZ(), b.getZ());
            return true;
        }

        private boolean inRegion(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        /**
         * Kiest het volgende blok: alleen binnen bereik, alleen blokken met een open zijde (dus niet dwars door
         * muren), bovenste lagen eerst en dichtbij eerst. Kijkt alleen in de bereik-kubus rond de speler
         * (hooguit ±5 blokken), dus de kosten zijn klein ongeacht de grootte van het gebied.
         */
        private boolean pickTarget(LocalPlayer p, net.minecraft.world.level.Level level) {
            int r = (int) Math.ceil(p.blockInteractionRange());
            BlockPos pp = p.blockPosition();
            int x0 = Math.max(minX, pp.getX() - r), x1 = Math.min(maxX, pp.getX() + r);
            int y0 = Math.max(minY, pp.getY() - r), y1 = Math.min(maxY, pp.getY() + r + 1);
            int z0 = Math.max(minZ, pp.getZ() - r), z1 = Math.min(maxZ, pp.getZ() + r);
            Vec3 eye = p.getEyePosition();
            double best = Double.MAX_VALUE;
            BlockPos bestPos = null;
            Direction bestFace = Direction.UP;
            for (int y = y1; y >= y0; y--) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
                m.set(x, y, z);
                BlockState st = level.getBlockState(m);
                if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                if (st.getDestroySpeed(level, m) < 0) continue;            // bedrock e.d.
                double dx = x + 0.5 - eye.x, dy = y + 0.5 - eye.y, dz = z + 0.5 - eye.z;
                double score = dx * dx + dy * dy + dz * dz + (maxY - y) * 3.0;   // bovenste lagen eerst
                if (score >= best) continue;
                if (!canReach(p, m)) continue;
                Direction face = exposedFace(level, m, dx, dy, dz);
                if (face == null) continue;
                best = score;
                bestPos = m.immutable();
                bestFace = face;
            }
            if (bestPos == null) return false;
            target = bestPos;
            targetFace = bestFace;
            return true;
        }

        /** Open zijde die het meest naar de speler wijst, of null als het blok helemaal ingesloten is. */
        private Direction exposedFace(net.minecraft.world.level.Level level, BlockPos pos, double dx, double dy, double dz) {
            Direction bestDir = null;
            double bestDot = -Double.MAX_VALUE;
            for (Direction d : Direction.values()) {
                nb.setWithOffset(pos, d);
                if (!level.getBlockState(nb).getCollisionShape(level, nb).isEmpty()) continue;
                double dot = -(d.getStepX() * dx + d.getStepY() * dy + d.getStepZ() * dz);
                if (dot > bestDot) { bestDot = dot; bestDir = d; }
            }
            return bestDir;
        }

        /** Niets binnen bereik: is het gebied klaar, of moet je verder lopen? (vroege stop, max. elke 5 s) */
        private void reportIdle(LocalPlayer p, net.minecraft.world.level.Level level) {
            for (int y = maxY; y >= minY; y--) for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                m.set(x, y, z);
                BlockState st = level.getBlockState(m);
                if (st.isAir() || !st.getFluidState().isEmpty() || st.getDestroySpeed(level, m) < 0) continue;
                p.displayClientMessage(Component.literal(
                        "[Mine Out] niets binnen bereik — loop dichterbij (" + mined + " gemijnd)"), true);
                return;
            }
            p.displayClientMessage(Component.literal("[Mine Out] klaar — " + mined + " blokken gemijnd"), false);
            setEnabled(false);
        }
    }

    /**
     * Base Finder: vliegt met elytra tot een base gevonden is, waarschuwt,
     * maakt een home aan als er plek is en vertrekt weer.
     */
    static final class BaseFinderModule extends ModuleBase {
        final BooleanSetting autoFly = addSetting(new BooleanSetting("Auto fly (elytra)", true));
        final BooleanSetting autoHome = addSetting(new BooleanSetting("Auto home", true));
        final StringSetting homeName = addSetting(new StringSetting("Home name", "champfind"));
        final NumberSetting scanRadius = addSetting(new NumberSetting("Scan radius (chunks)", 4, 1, 8));
        boolean warned, found;
        int artificial;
        final net.champ.addon.util.ChunkSweep sweep = new net.champ.addon.util.ChunkSweep(
                state -> isBaseBlock(state.getBlock()), 3, 32);

        private static boolean isBaseBlock(net.minecraft.world.level.block.Block b) {
            return b == Blocks.CHEST || b == Blocks.FURNACE || b == Blocks.BEACON
                    || b == Blocks.CRAFTING_TABLE || b == Blocks.ENDER_CHEST;
        }

        BaseFinderModule() {
            super("base-finder", "Base Finder",
                    "Vliegt tot een base, waarschuwt, home't, vertrekt.", ModuleCategory.DONUT);
        }

        @Override protected void onEnableInternal() { warned = false; found = false; artificial = 0; sweep.reset(); }

        @Override protected void tick() {
            LocalPlayer p = Slots.player();
            var mc = Slots.mc();
            var level = mc.level;
            if (p == null || level == null || found) return;
            if (!isDonut()) {
                if (!warned) { warned = true; warnNotDonut(p, "Base Finder"); }
            } else warned = false;
            if (autoFly.get() && p.isFallFlying()) {
                // Rechtuit vliegen: pitch licht omlaag voor snelheid.
                p.setYRot(p.getYRot());
            }
            BlockPos self = p.blockPosition();
            boolean done = sweep.step(level, self, (int) scanRadius.get(), (pos, state) -> artificial++);
            if (!(artificial > 12) && done) artificial = 0;
            if (artificial > 12) {
                found = true;
                p.displayClientMessage(Component.literal(
                        "[Champ Client] BASE GEVONDEN op " + self.toShortString() + "!"), false);
                if (autoHome.get()) {
                    try { p.connection.sendChat("/sethome " + homeName.get()); } catch (Exception ignored) {}
                }
                setEnabled(false); // vertrekken = module uit
            }
        }
    }
}
