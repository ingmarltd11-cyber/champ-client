package net.champ.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.champ.client.chat.ModuleChat;
import net.champ.client.event.EventHandler;
import net.champ.client.event.Stage;
import net.champ.client.event.impl.Render3DEvent;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.gui.ChallengeToast;
import net.champ.client.gui.GlintHeadRenderer;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.FoglessLines;
import net.champ.client.util.playerbypass2.SimulationSignals;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PlayerBypass2Module extends ClientModule {
   private static final float LINE_WIDTH = 2.0F;
   private static final int MARK_COLOR = -11205;
   private static final int MARK_FILL_ALPHA = 34;
   private static final int MEMORY_SECONDS = 45;
   private static final float FALLOFF = 0.3F;
   private static final float MIN_SOURCE = 0.5F;
   private static final int MAX_FOUND = 256;
   private static final int ALERT_SPACING = 4;
   private static final int PIN_SPACING = 1;
   private static final float FROZEN_NEIGHBOUR = 0.5F;
   private static final int REPORT_SOURCES = 4;
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private final NumberSetting sensitivity = this.addSetting("sens", new NumberSetting("Sensitivity", 1.0F, 1.0F, 10.0F).step(1.0F));
   private final LongOpenHashSet peaks = new LongOpenHashSet();
   private final Map<ResourceKey<Level>, LongLinkedOpenHashSet> foundByDimension = new HashMap<>();
   private LongLinkedOpenHashSet found = new LongLinkedOpenHashSet();
   private int session = -1;
   private final LongOpenHashSet shown = new LongOpenHashSet();
   private final Long2FloatOpenHashMap field = new Long2FloatOpenHashMap();
   private final Long2FloatOpenHashMap raw = new Long2FloatOpenHashMap();
   private int ticks;

   public PlayerBypass2Module() {
      super(
         "player-bypass-2", "Player Bypass 2", "Finds hidden players from chunks that keep ticking outside your simulation distance.", ModuleCategory.BASE_RAID
      );
   }

   @Override
   protected void onEnable() {
      this.foundByDimension.clear();
      this.found = new LongLinkedOpenHashSet();
      this.session = SimulationSignals.session();
      SimulationSignals.clear();
      this.configure();
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      SimulationSignals.configure(false, 0, 0, true, 30);
      this.peaks.clear();
      this.foundByDimension.clear();
      this.found = new LongLinkedOpenHashSet();
      this.shown.clear();
      this.field.clear();
      this.raw.clear();
   }

   private void configure() {
      SimulationSignals.configure(true, 0, 0, true, 45);
   }

   private static boolean netherOrEnd(Level level) {
      ResourceKey<Level> dimension = level.dimension();
      return dimension.equals(Level.NETHER)
         || dimension.equals(Level.END)
         || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.NETHER)
         || level.dimensionTypeRegistration().is(BuiltinDimensionTypes.END)
         || !level.dimensionType().comp_642();
   }

   private float threshold() {
      return 8.0F * (float)Math.pow(2.0, (5.0 - this.sensitivity.get()) / 2.5);
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         if (this.session != SimulationSignals.session()) {
            this.session = SimulationSignals.session();
            this.foundByDimension.clear();
            this.peaks.clear();
            this.shown.clear();
         }

         this.found = this.foundByDimension.computeIfAbsent(mc.level.dimension(), key -> new LongLinkedOpenHashSet());
         this.configure();
         SimulationSignals.tick();
         if (netherOrEnd(mc.level)) {
            SimulationSignals.dropPins();
            this.shown.clear();
         } else {
            this.drainPins();
            if (++this.ticks % 5 == 0) {
               this.collect(mc.player);
               this.alert();
               this.refreshShown(mc.player);
            }
         }
      }
   }

   private void collect(LocalPlayer self) {
      this.peaks.clear();
      this.field.clear();
      this.raw.clear();
      long now = SimulationSignals.now();
      int sim = SimulationSignals.simulation();
      int pcx = self.blockPosition().getX() >> 4;
      int pcz = self.blockPosition().getZ() >> 4;
      int start = SimulationSignals.ringStart();
      int view = SimulationSignals.view();
      int side = 2 * sim + 1;
      float[] weights = new float[side * side];
      float span = sim * 1.4143F + 1.0F;

      for (int dx = -sim; dx <= sim; dx++) {
         for (int dz = -sim; dz <= sim; dz++) {
            weights[(dx + sim) * side + dz + sim] = 1.0F - 0.3F * (float)Math.sqrt(dx * dx + dz * dz) / span;
         }
      }

      ObjectIterator var24 = SimulationSignals.chunks().long2ObjectEntrySet().iterator();

      while (var24.hasNext()) {
         Entry<SimulationSignals.ChunkSignal> entry = (Entry<SimulationSignals.ChunkSignal>)var24.next();
         long key = entry.getLongKey();
         int cx = ChunkPos.getX(key);
         int cz = ChunkPos.getZ(key);
         int d = SimulationSignals.distance(cx, cz, pcx, pcz);
         if (d >= start && d <= view) {
            float score = ((SimulationSignals.ChunkSignal)entry.getValue()).score(now);
            if (!(score < 0.5F)) {
               for (int dx = -sim; dx <= sim; dx++) {
                  for (int dz = -sim; dz <= sim; dz++) {
                     long area = ChunkPos.asLong(cx + dx, cz + dz);
                     this.field.addTo(area, score * weights[(dx + sim) * side + dz + sim]);
                     this.raw.addTo(area, score);
                  }
               }
            }
         }
      }

      LongIterator var25 = SimulationSignals.frozen().iterator();

      while (var25.hasNext()) {
         long frozen = (Long)var25.next();
         this.field.remove(frozen);
         this.raw.remove(frozen);
         int fx = ChunkPos.getX(frozen);
         int fz = ChunkPos.getZ(frozen);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
               long near = ChunkPos.asLong(fx + dx, fz + dz);
               if (this.field.containsKey(near)) {
                  this.field.put(near, this.field.get(near) * 0.5F);
                  this.raw.put(near, this.raw.get(near) * 0.5F);
               }
            }
         }
      }

      float threshold = this.threshold();
      ObjectIterator var29 = this.field.long2FloatEntrySet().iterator();

      while (var29.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry)var29.next();
         long key = entry.getLongKey();
         if (!(this.raw.get(key) < threshold) && this.isPeak(key, entry.getFloatValue(), sim)) {
            this.peaks.add(key);
         }
      }
   }

   private boolean isPeak(long key, float value, int reach) {
      int cx = ChunkPos.getX(key);
      int cz = ChunkPos.getZ(key);

      for (int dx = -reach; dx <= reach; dx++) {
         for (int dz = -reach; dz <= reach; dz++) {
            if (dx != 0 || dz != 0) {
               long other = ChunkPos.asLong(cx + dx, cz + dz);
               float near = this.field.get(other);
               if (near > value || near == value && other < key) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private void drainPins() {
      SimulationSignals.Pin pin;
      while ((pin = SimulationSignals.pollPin()) != null) {
         BlockPos pos = pin.pos();
         long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
         LOGGER.info("[Player Bypass 2] {}", pin.detail());
         boolean known = false;
         LongListIterator var6 = this.found.iterator();

         while (var6.hasNext()) {
            long old = (Long)var6.next();
            if (SimulationSignals.distance(ChunkPos.getX(old), ChunkPos.getZ(old), ChunkPos.getX(key), ChunkPos.getZ(key))
               <= 1) {
               known = true;
               break;
            }
         }

         if (!known) {
            this.found.add(key);

            while (this.found.size() > 256) {
               this.found.removeFirstLong();
            }

            ModuleChat.send(this, pin.detail());
            ChallengeToast.addOrUpdate(
               GlintHeadRenderer.stack(),
               "Player Activity Detected",
               Component.literal("at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
               true
            );
         }
      }
   }

   private void alert() {
      long now = SimulationSignals.now();
      int spacing = Math.max(4, 2 * SimulationSignals.simulation());
      LongIterator var4 = this.peaks.iterator();

      while (var4.hasNext()) {
         long key = (Long)var4.next();
         boolean known = false;
         LongListIterator x = this.found.iterator();

         while (x.hasNext()) {
            long old = (Long)x.next();
            if (SimulationSignals.distance(ChunkPos.getX(old), ChunkPos.getZ(old), ChunkPos.getX(key), ChunkPos.getZ(key))
               <= spacing) {
               known = true;
               break;
            }
         }

         if (!known) {
            this.found.add(key);

            while (this.found.size() > 256) {
               this.found.removeFirstLong();
            }

            int xx = (ChunkPos.getX(key) << 4) + 8;
            int z = (ChunkPos.getZ(key) << 4) + 8;
            this.report(key, xx, z, now);
            ChallengeToast.addOrUpdate(GlintHeadRenderer.stack(), "Player Activity Detected", Component.literal("at " + xx + ", " + z), true);
         }
      }
   }

   private void refreshShown(LocalPlayer self) {
      this.shown.clear();
      int pcx = self.blockPosition().getX() >> 4;
      int pcz = self.blockPosition().getZ() >> 4;
      int reach = SimulationSignals.view() + SimulationSignals.simulation() + 2;
      LongListIterator var5 = this.found.iterator();

      while (var5.hasNext()) {
         long key = (Long)var5.next();
         if (SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), pcx, pcz) <= reach) {
            this.shown.add(key);
         }
      }
   }

   private void report(long found, int x, int z, long now) {
      int sim = SimulationSignals.simulation();
      int fx = ChunkPos.getX(found);
      int fz = ChunkPos.getZ(found);
      List<Entry<SimulationSignals.ChunkSignal>> sources = new ArrayList<>();
      ObjectIterator lines = SimulationSignals.chunks().long2ObjectEntrySet().iterator();

      while (lines.hasNext()) {
         Entry<SimulationSignals.ChunkSignal> entry = (Entry<SimulationSignals.ChunkSignal>)lines.next();
         long key = entry.getLongKey();
         if (SimulationSignals.distance(ChunkPos.getX(key), ChunkPos.getZ(key), fx, fz) <= sim
            && !(((SimulationSignals.ChunkSignal)entry.getValue()).score(now) < 0.5F)) {
            sources.add(entry);
         }
      }

      sources.sort((a, b) -> Float.compare(((SimulationSignals.ChunkSignal)b.getValue()).score(now), ((SimulationSignals.ChunkSignal)a.getValue()).score(now)));
      List<String> linesx = new ArrayList<>();
      linesx.add(
         "Found "
            + x
            + ", "
            + z
            + " from "
            + sources.size()
            + " chunks (sim "
            + sim
            + ", ring "
            + SimulationSignals.ringStart()
            + "-"
            + SimulationSignals.view()
            + ")"
      );

      for (int i = 0; i < Math.min(4, sources.size()); i++) {
         SimulationSignals.ChunkSignal signal = (SimulationSignals.ChunkSignal)sources.get(i).getValue();
         long key = sources.get(i).getLongKey();
         StringBuilder kinds = new StringBuilder();

         for (SimulationSignals.Kind kind : SimulationSignals.Kind.values()) {
            if (signal.count(kind) > 0) {
               kinds.append(kinds.isEmpty() ? "" : ", ").append(kind.title).append(" x").append(signal.count(kind));
            }
         }

         linesx.add(
            " chunk "
               + ((ChunkPos.getX(key) << 4) + 8)
               + ", "
               + ((ChunkPos.getZ(key) << 4) + 8)
               + String.format(Locale.ROOT, " score %.1f: ", signal.score(now))
               + kinds
         );

         for (String recent : signal.recent()) {
            linesx.add("   " + recent);
         }
      }

      for (String line : linesx) {
         LOGGER.info("[Player Bypass 2] {}", line);
         ModuleChat.send(this, line);
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null && !netherOrEnd(mc.level)) {
         if (!this.shown.isEmpty()) {
            FoglessLines lines = FoglessLines.forFrame(event.partialTick);
            double bottom = mc.level.getMinY();
            double top = mc.level.getMaxY() + 1;
            GizmoStyle fill = GizmoStyle.fill(ARGB.color(34, -11205));
            LongIterator var9 = this.shown.iterator();

            while (var9.hasNext()) {
               long key = (Long)var9.next();
               double x0 = ChunkPos.getX(key) << 4;
               double z0 = ChunkPos.getZ(key) << 4;
               double x1 = x0 + 16.0;
               double z1 = z0 + 16.0;
               Gizmos.cuboid(new AABB(x0, bottom, z0, x1, top, z1), fill).setAlwaysOnTop();
               column(lines, x0, z0, x1, z1, bottom, top);
            }
         }
      }
   }

   private static void column(FoglessLines lines, double x0, double z0, double x1, double z1, double bottom, double top) {
      double[][] corners = new double[][]{{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};

      for (int i = 0; i < corners.length; i++) {
         double[] a = corners[i];
         double[] b = corners[(i + 1) % corners.length];
         lines.line(new Vec3(a[0], bottom, a[1]), new Vec3(a[0], top, a[1]), -11205, 2.0F);
         lines.line(new Vec3(a[0], bottom, a[1]), new Vec3(b[0], bottom, b[1]), -11205, 2.0F);
         lines.line(new Vec3(a[0], top, a[1]), new Vec3(b[0], top, b[1]), -11205, 2.0F);
      }
   }
}
