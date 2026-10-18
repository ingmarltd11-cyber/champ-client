package net.champ.client.module.impl.render;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import net.champ.client.esp.BlockBoxCache;
import net.champ.client.esp.BlockLayer;
import net.champ.client.esp.EspStyle;
import net.champ.client.esp.HorizontalCandidates;
import net.champ.client.event.EventHandler;
import net.champ.client.event.impl.PacketReceiveNettyEvent;
import net.champ.client.event.impl.Render3DEvent;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BlockColorListSetting;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ColorSetting;
import net.champ.client.module.setting.ModeSetting;
import net.champ.client.util.BlockEntityLeakTracker;
import net.champ.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2626;
import net.minecraft.class_2680;
import net.minecraft.class_310;

@Environment(EnvType.CLIENT)
public final class BlockEntityDebugModule extends ClientModule {
   private static final int SETTINGS_VERSION = 1;
   private static final float FILL_ALPHA = 0.8F;
   private static final Set<String> NEW_IN_TABLE = Set.of("minecraft:jukebox", "minecraft:piston", "minecraft:sticky_piston");
   private final ModeSetting sDetection = this.addSetting("detection", new ModeSetting("Detection", "All", "All", "Below Zero", "Below -20", "Below -30"));
   private final BooleanSetting sBlockFilter = this.addSetting("block-filter", new BooleanSetting("Block Filter", false));
   private final EspStyle style = new EspStyle((x$0, x$1) -> (S)this.addSetting(x$0, x$1), "Boxes", 0.8F, 256.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting sCustomTracerColor = this.addSetting("custom-tracer-color", new BooleanSetting("Custom Tracer Color", false));
   private final ColorSetting sTracerColor = this.addSetting("tracer-color", new ColorSetting("Tracer Color", -1));
   private final BlockColorListSetting sBlockColors = this.addSetting(BlockColorListSetting.createForBlockEntityDebug());
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return BlockEntityDebugModule.this.sBlockColors.colorArgb((class_2248)kind);
      }

      @Override
      public int tracerColor(Object kind) {
         return BlockEntityDebugModule.this.tracerColorOf((class_2248)kind);
      }
   }, () -> class_310.method_1551().field_1687 == null ? null : class_310.method_1551().field_1687.method_27983());
   private final LongOpenHashSet spawners = new LongOpenHashSet();
   private List<BlockEntityDebugModule.SpawnerBeam> mobileBeams;
   private HorizontalCandidates<BlockEntityDebugModule.SpawnerBeam> beamCandidates;
   private BlockBoxCache beamBoxes;
   private int seenGeneration = -1;
   private Set<String> scannedIds = Set.of();

   public BlockEntityDebugModule() {
      super("block-entity-debug", "Block Entity Debug", "Renders every leaked block entity as ESP (always-on Netty capture).", ModuleCategory.BASE_RAID);
      this.sCustomTracerColor.visibleWhen(this.sTracers::get);
      this.sTracerColor.visibleWhen(() -> this.sTracers.get() && this.sCustomTracerColor.get());
      this.sDetection.onChange(this::resync);
      this.sBlockFilter.onChange(this::resync);
      this.sBlockColors.onChange(this::onBlockColorsChanged);
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      this.style.shape.set("Boxes");
      this.style.boxSize.set(1.0F);
      this.style.fillAlpha.set(0.8F);
      this.sBlockColors.resetColors();
      int plain = BlockColorListSetting.rgbaToArgb(this.sBlockColors.defaultColor());
      boolean filtered = this.sBlockFilter.get();

      for (BlockColorListSetting.BlockEntry entry : this.sBlockColors.entries()) {
         if (BlockColorListSetting.rgbaToArgb(entry.color()) != plain && (!filtered || NEW_IN_TABLE.contains(entry.id()))) {
            this.sBlockColors.select(entry);
         }
      }
   }

   private int tracerColorOf(class_2248 block) {
      int own = this.sBlockColors.isEnabled(block) ? this.sBlockColors.tracerColorArgb(block) : this.sBlockColors.colorArgb(block);
      if (own == 0) {
         return 0;
      } else {
         return this.sCustomTracerColor.get() ? this.sTracerColor.argb() : own;
      }
   }

   private void onBlockColorsChanged() {
      if (this.isEnabled() && this.sBlockFilter.get() && !this.sBlockColors.enabledIds().equals(this.scannedIds)) {
         this.resync();
      }
   }

   @Override
   protected void onEnable() {
      BlockEntityLeakTracker.recordAdditions(true);
      this.resync();
   }

   @Override
   protected void onDisable() {
      BlockEntityLeakTracker.recordAdditions(false);
      this.layer.clear();
      this.spawners.clear();
      this.mobileBeams = null;
      this.beamCandidates = null;
      this.beamBoxes = null;
   }

   private void resync() {
      if (this.isEnabled()) {
         this.layer.clear();
         this.spawners.clear();
         this.mobileBeams = null;
         this.beamCandidates = null;
         this.beamBoxes = null;
         this.scannedIds = this.sBlockColors.enabledIds();
         this.seenGeneration = BlockEntityLeakTracker.generation();

         while (BlockEntityLeakTracker.pollAdded() != null) {
         }

         for (Entry<class_2338, BlockEntityLeakTracker.Entry> entry : BlockEntityLeakTracker.view().entrySet()) {
            this.accept(entry.getKey(), entry.getValue().block());
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (BlockEntityLeakTracker.generation() != this.seenGeneration) {
         this.resync();
      } else {
         class_2338 pos;
         while ((pos = BlockEntityLeakTracker.pollAdded()) != null) {
            BlockEntityLeakTracker.Entry entry = BlockEntityLeakTracker.view().get(pos);
            if (entry != null) {
               this.accept(pos, entry.block());
            }
         }
      }
   }

   private void accept(class_2338 pos, class_2248 block) {
      if (this.passesDetection(pos)) {
         if (!this.sBlockFilter.get() || block == class_2246.field_10124 || this.sBlockColors.isEnabled(block)) {
            this.layer.put(pos.method_10063(), block);
            if (block == class_2246.field_10260 && this.spawners.add(pos.method_10063())) {
               this.mobileBeams = null;
            }
         }
      }
   }

   private boolean passesDetection(class_2338 pos) {
      return switch (this.sDetection.get()) {
         case "Below Zero" -> pos.method_10264() < 0;
         case "Below -20" -> pos.method_10264() < -20;
         case "Below -30" -> pos.method_10264() < -30;
         default -> true;
      };
   }

   @EventHandler
   public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (event.packet instanceof class_2626 packet) {
         if (BlockEntityLeakTracker.contains(packet.method_11309())) {
            class_2680 state = packet.method_11308();
            if (state.method_26215() || state.method_27852(class_2246.field_10340) || state.method_27852(class_2246.field_28888)) {
               event.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.layer.render(event, this.style);
      if (this.sTracers.get()) {
         this.layer.renderTracers(this.style);
      }

      if (!this.spawners.isEmpty()) {
         class_310 mc = class_310.method_1551();
         if (mc.field_1724 != null) {
            class_243 eye = mc.field_1724.method_5836(event.partialTick);
            double rangeSq = (double)this.style.range.get() * this.style.range.get();
            int color = this.sBlockColors.colorArgb(class_2246.field_10260);
            class_12179 beam = class_12179.method_75536(color, this.style.lineWidth.get());
            if (MobilePerformance.active()) {
               this.renderMobileBeams(eye, rangeSq, beam);
            } else {
               this.mobileBeams = null;
               this.beamCandidates = null;
               this.beamBoxes = null;
               LongIterator var8 = this.spawners.iterator();

               while (var8.hasNext()) {
                  long packed = (Long)var8.next();
                  class_2338 pos = class_2338.method_10092(packed);
                  if (!(eye.method_1028(pos.method_10263() + 0.5, pos.method_10264() + 1.0, pos.method_10260() + 0.5) > rangeSq)) {
                     class_12180.method_75541(
                           new class_238(
                              pos.method_10263() + 0.3,
                              pos.method_10264() + 1.0,
                              pos.method_10260() + 0.3,
                              pos.method_10263() + 0.7,
                              pos.method_10264() + 257.0,
                              pos.method_10260() + 0.7
                           ),
                           beam
                        )
                        .method_75533();
                  }
               }
            }
         }
      }
   }

   private void renderMobileBeams(class_243 eye, double rangeSq, class_12179 style) {
      if (this.mobileBeams == null) {
         this.mobileBeams = new ArrayList<>(this.spawners.size());
         LongIterator var5 = this.spawners.iterator();

         while (var5.hasNext()) {
            long packed = (Long)var5.next();
            this.mobileBeams.add(new BlockEntityDebugModule.SpawnerBeam(packed));
         }
      }

      if (this.beamCandidates == null) {
         this.beamCandidates = new HorizontalCandidates<>(
            beamx -> class_2338.method_10061(beamx.position()) + 0.5, beamx -> class_2338.method_10083(beamx.position()) + 0.5
         );
      }

      if (this.beamBoxes == null) {
         this.beamBoxes = new BlockBoxCache(4096, BlockEntityDebugModule::beamBounds);
      }

      this.beamBoxes.beginFrame(1.0F);

      for (BlockEntityDebugModule.SpawnerBeam beam : this.beamCandidates.select(this.mobileBeams, eye.field_1352, eye.field_1350, Math.sqrt(rangeSq))) {
         long packed = beam.position();
         if (!(eye.method_1028(class_2338.method_10061(packed) + 0.5, class_2338.method_10071(packed) + 1.0, class_2338.method_10083(packed) + 0.5) > rangeSq)) {
            class_12180.method_75541(this.beamBoxes.box(packed), style).method_75533();
         }
      }
   }

   private static class_238 beamBounds(long packed) {
      int x = class_2338.method_10061(packed);
      int y = class_2338.method_10071(packed);
      int z = class_2338.method_10083(packed);
      return new class_238(x + 0.3, y + 1.0, z + 0.3, x + 0.7, y + 257.0, z + 0.7);
   }

   @Environment(EnvType.CLIENT)
   private record SpawnerBeam(long position) {
   }
}
