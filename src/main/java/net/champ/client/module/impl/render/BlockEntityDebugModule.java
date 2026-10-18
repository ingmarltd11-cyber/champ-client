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
import net.champ.client.module.setting.Setting;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ColorSetting;
import net.champ.client.module.setting.ModeSetting;
import net.champ.client.util.BlockEntityLeakTracker;
import net.champ.client.util.MobilePerformance;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class BlockEntityDebugModule extends ClientModule {
   private static final int SETTINGS_VERSION = 1;
   private static final float FILL_ALPHA = 0.8F;
   private static final Set<String> NEW_IN_TABLE = Set.of("minecraft:jukebox", "minecraft:piston", "minecraft:sticky_piston");
   private final ModeSetting sDetection = this.addSetting("detection", new ModeSetting("Detection", "All", "All", "Below Zero", "Below -20", "Below -30"));
   private final BooleanSetting sBlockFilter = this.addSetting("block-filter", new BooleanSetting("Block Filter", false));
   private final EspStyle style = new EspStyle(new EspStyle.Registrar() {
      @Override
      public <S extends Setting> S add(String id, S setting) {
         return BlockEntityDebugModule.this.addSetting(id, setting);
      }
   }, "Boxes", 0.8F, 256.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", true));
   private final BooleanSetting sCustomTracerColor = this.addSetting("custom-tracer-color", new BooleanSetting("Custom Tracer Color", false));
   private final ColorSetting sTracerColor = this.addSetting("tracer-color", new ColorSetting("Tracer Color", -1));
   private final BlockColorListSetting sBlockColors = this.addSetting(BlockColorListSetting.createForBlockEntityDebug());
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return BlockEntityDebugModule.this.sBlockColors.colorArgb((Block)kind);
      }

      @Override
      public int tracerColor(Object kind) {
         return BlockEntityDebugModule.this.tracerColorOf((Block)kind);
      }
   }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
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

   private int tracerColorOf(Block block) {
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

         for (Entry<BlockPos, BlockEntityLeakTracker.Entry> entry : BlockEntityLeakTracker.view().entrySet()) {
            this.accept(entry.getKey(), entry.getValue().block());
         }
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      if (BlockEntityLeakTracker.generation() != this.seenGeneration) {
         this.resync();
      } else {
         BlockPos pos;
         while ((pos = BlockEntityLeakTracker.pollAdded()) != null) {
            BlockEntityLeakTracker.Entry entry = BlockEntityLeakTracker.view().get(pos);
            if (entry != null) {
               this.accept(pos, entry.block());
            }
         }
      }
   }

   private void accept(BlockPos pos, Block block) {
      if (this.passesDetection(pos)) {
         if (!this.sBlockFilter.get() || block == Blocks.AIR || this.sBlockColors.isEnabled(block)) {
            this.layer.put(pos.asLong(), block);
            if (block == Blocks.SPAWNER && this.spawners.add(pos.asLong())) {
               this.mobileBeams = null;
            }
         }
      }
   }

   private boolean passesDetection(BlockPos pos) {
      return switch (this.sDetection.get()) {
         case "Below Zero" -> pos.getY() < 0;
         case "Below -20" -> pos.getY() < -20;
         case "Below -30" -> pos.getY() < -30;
         default -> true;
      };
   }

   @EventHandler
   public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
      if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
         if (BlockEntityLeakTracker.contains(packet.getPos())) {
            BlockState state = packet.getBlockState();
            if (state.isAir() || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
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
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null) {
            Vec3 eye = mc.player.getEyePosition(event.partialTick);
            double rangeSq = (double)this.style.range.get() * this.style.range.get();
            int color = this.sBlockColors.colorArgb(Blocks.SPAWNER);
            GizmoStyle beam = GizmoStyle.stroke(color, this.style.lineWidth.get());
            if (MobilePerformance.active()) {
               this.renderMobileBeams(eye, rangeSq, beam);
            } else {
               this.mobileBeams = null;
               this.beamCandidates = null;
               this.beamBoxes = null;
               LongIterator var8 = this.spawners.iterator();

               while (var8.hasNext()) {
                  long packed = (Long)var8.next();
                  BlockPos pos = BlockPos.of(packed);
                  if (!(eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5) > rangeSq)) {
                     Gizmos.cuboid(
                           new AABB(
                              pos.getX() + 0.3,
                              pos.getY() + 1.0,
                              pos.getZ() + 0.3,
                              pos.getX() + 0.7,
                              pos.getY() + 257.0,
                              pos.getZ() + 0.7
                           ),
                           beam
                        )
                        .setAlwaysOnTop();
                  }
               }
            }
         }
      }
   }

   private void renderMobileBeams(Vec3 eye, double rangeSq, GizmoStyle style) {
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
            beamx -> BlockPos.getX(beamx.position()) + 0.5, beamx -> BlockPos.getZ(beamx.position()) + 0.5
         );
      }

      if (this.beamBoxes == null) {
         this.beamBoxes = new BlockBoxCache(4096, BlockEntityDebugModule::beamBounds);
      }

      this.beamBoxes.beginFrame(1.0F);

      for (BlockEntityDebugModule.SpawnerBeam beam : this.beamCandidates.select(this.mobileBeams, eye.x, eye.z, Math.sqrt(rangeSq))) {
         long packed = beam.position();
         if (!(eye.distanceToSqr(BlockPos.getX(packed) + 0.5, BlockPos.getY(packed) + 1.0, BlockPos.getZ(packed) + 0.5) > rangeSq)) {
            Gizmos.cuboid(this.beamBoxes.box(packed), style).setAlwaysOnTop();
         }
      }
   }

   private static AABB beamBounds(long packed) {
      int x = BlockPos.getX(packed);
      int y = BlockPos.getY(packed);
      int z = BlockPos.getZ(packed);
      return new AABB(x + 0.3, y + 1.0, z + 0.3, x + 0.7, y + 257.0, z + 0.7);
   }

   @Environment(EnvType.CLIENT)
   private record SpawnerBeam(long position) {
   }
}
