package net.champ.client.module.impl.misc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.champ.client.chat.ModuleChat;
import net.champ.client.event.EventHandler;
import net.champ.client.event.Stage;
import net.champ.client.event.impl.BlockUpdateEvent;
import net.champ.client.event.impl.PacketAppliedEvent;
import net.champ.client.event.impl.Render3DEvent;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ColorSetting;
import net.champ.client.util.LevelUtil;
import net.champ.client.util.quantum.ChunkSnapshotStore;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientLevel;

@Environment(EnvType.CLIENT)
public final class QuantumModule extends ClientModule {
   private static final int SCAN_RADIUS = 8;
   private static final int FLAG_RADIUS = 9;
   private static final double MARKER_Y = 63.0;
   private static final float LINE_WIDTH = 1.5F;
   private static final int CYAN = 65535;
   private static final int SOURCE_FILL_ALPHA = 40;
   private static final Set<Block> IGNORED_NEW_BLOCKS = Set.of(
      Blocks.FIRE,
      Blocks.FROSTED_ICE,
      Blocks.COAL_ORE,
      Blocks.DEEPSLATE_COAL_ORE,
      Blocks.IRON_ORE,
      Blocks.DEEPSLATE_IRON_ORE,
      Blocks.COPPER_ORE,
      Blocks.DEEPSLATE_COPPER_ORE,
      Blocks.GOLD_ORE,
      Blocks.DEEPSLATE_GOLD_ORE,
      Blocks.REDSTONE_ORE,
      Blocks.DEEPSLATE_REDSTONE_ORE,
      Blocks.EMERALD_ORE,
      Blocks.DEEPSLATE_EMERALD_ORE,
      Blocks.LAPIS_ORE,
      Blocks.DEEPSLATE_LAPIS_ORE,
      Blocks.DIAMOND_ORE,
      Blocks.DEEPSLATE_DIAMOND_ORE,
      Blocks.OAK_LEAVES,
      Blocks.ACACIA_LEAVES,
      Blocks.BIRCH_LEAVES,
      Blocks.JUNGLE_LEAVES,
      Blocks.DARK_OAK_LEAVES,
      Blocks.PALE_OAK_LEAVES,
      Blocks.CHERRY_LEAVES,
      Blocks.AZALEA_LEAVES,
      Blocks.FLOWERING_AZALEA_LEAVES,
      Blocks.MOSSY_COBBLESTONE,
      Blocks.RAW_COPPER_BLOCK,
      Blocks.STONE,
      Blocks.OBSERVER,
      Blocks.REPEATER,
      Blocks.COMPARATOR,
      Blocks.REDSTONE_TORCH,
      Blocks.REDSTONE_LAMP,
      Blocks.REDSTONE_WIRE,
      Blocks.DROPPER
   );
   private static final Set<Block> IGNORED_OLD_BLOCKS = Set.of(Blocks.AMETHYST_BLOCK, Blocks.STONE, Blocks.FROSTED_ICE);
   private final BooleanSetting renderChunks = this.addSetting("render-chunks", new BooleanSetting("Render Chunks", true));
   private final BooleanSetting clearOnToggle = this.addSetting("clear-chunks-on-toggle", new BooleanSetting("Clear On Toggle", true));
   private final BooleanSetting highlightSources = this.addSetting("render-flagged-chunk", new BooleanSetting("Highlight Source Chunks", true));
   private final ColorSetting chunkFill = this.addSetting("chunk-side-color", new ColorSetting("Chunk Fill Color", 687810600));
   private final ColorSetting chunkLine = this.addSetting("chunk-line-color", new ColorSetting("Chunk Line Color", -55256));
   private final BooleanSetting advancedDebug = this.addSetting("advanced-debug", new BooleanSetting("Advanced Debug", false));
   private final BooleanSetting render = this.addSetting("render", new BooleanSetting("Debug Render", true));
   private final ColorSetting fillColor = this.addSetting("side-color", new ColorSetting("Debug Fill Color", 687810600));
   private final ColorSetting lineColor = this.addSetting("line-color", new ColorSetting("Debug Line Color", -55256));
   private final ChunkSnapshotStore snapshots = new ChunkSnapshotStore(Blocks.AIR.defaultBlockState());
   private final Set<ChunkPos> justSent = new HashSet<>();
   private final List<BlockPos> changedBlocks = new ArrayList<>();
   private final Set<ChunkPos> flaggedChunks = new HashSet<>();
   private final Set<ChunkPos> sourceChunks = new HashSet<>();
   private Set<ChunkPos> previousRange = Set.of();
   private ChunkPos lastPos;
   private ClientLevel sessionLevel;
   private ClientPacketListener connection;

   public QuantumModule() {
      super("quantum", "Quantum & Render", "Finds player activity in resent chunks and marks the area around it.", ModuleCategory.BASE_RAID);
      this.usesNotifications().set(false);
      this.highlightSources.visibleWhen(this.renderChunks::get);
      this.chunkFill.visibleWhen(this.renderChunks::get);
      this.chunkLine.visibleWhen(this.renderChunks::get);
      this.render.visibleWhen(this.advancedDebug::get);
      this.fillColor.visibleWhen(() -> this.render.get() && this.advancedDebug.get());
      this.lineColor.visibleWhen(() -> this.render.get() && this.advancedDebug.get());
   }

   @Override
   protected void onEnable() {
      this.sessionLevel = null;
      this.resetSession();
      if (this.clearOnToggle.get()) {
         this.flaggedChunks.clear();
         this.sourceChunks.clear();
      }
   }

   @Override
   protected void onDisable() {
      this.snapshots.clear();
   }

   private void resetSession() {
      this.snapshots.clear();
      this.justSent.clear();
      this.changedBlocks.clear();
      this.previousRange = Set.of();
      this.lastPos = null;
   }

   private void ensureSession(ClientLevel level) {
      this.ensureConnection();
      if (level != this.sessionLevel) {
         this.sessionLevel = level;
         this.resetSession();
      }
   }

   private void ensureConnection() {
      ClientPacketListener current = Minecraft.getInstance().getConnection();
      if (current != this.connection) {
         this.connection = current;
         this.flaggedChunks.clear();
         this.sourceChunks.clear();
      }
   }

   @EventHandler(stage = Stage.POST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null) {
         this.ensureSession(mc.level);
         if (LevelUtil.isOverworld(mc.level)) {
            ChunkPos current = mc.player.chunkPosition();
            if (this.lastPos != null && !this.lastPos.equals(current)) {
               Set<ChunkPos> range = chunksInRange(current);

               for (ChunkPos pos : range) {
                  if (!this.previousRange.contains(pos)) {
                     this.snapshots.store(pos.x, pos.z, extractSections(mc.level.getChunk(pos.x, pos.z)));
                  }
               }

               this.justSent.removeIf(posx -> !range.contains(posx));
               Set<Long> keys = new HashSet<>();

               for (ChunkPos pos : range) {
                  keys.add(ChunkSnapshotStore.chunkKey(pos.x, pos.z));
               }

               this.snapshots.retain(keys::contains);
               this.previousRange = range;
            }

            this.lastPos = current;
         }
      }
   }

   @EventHandler
   public void onPacketApplied(PacketAppliedEvent event) {
      if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {
         Minecraft mc = Minecraft.getInstance();
         ClientLevel level = mc.level;
         if (mc.player != null && level != null) {
            this.ensureSession(level);
            if (LevelUtil.isOverworld(level)) {
               ChunkPos pos = new ChunkPos(packet.getX(), packet.getZ());
               if (chunksInRange(mc.player.chunkPosition()).contains(pos)) {
                  if (LevelUtil.isLoaded(level, pos.x, pos.z)) {
                     PalettedContainer<BlockState>[] current = extractSections(level.getChunk(pos.x, pos.z));

                     for (ChunkSnapshotStore.BlockChange change : this.snapshots.compare(pos.x, pos.z, current, level.getMinSectionY())) {
                        Block newBlock = change.newState().getBlock();
                        Block oldBlock = change.oldState().getBlock();
                        if (!IGNORED_NEW_BLOCKS.contains(newBlock) && !IGNORED_OLD_BLOCKS.contains(oldBlock)) {
                           this.onActivity(pos, change, newBlock, oldBlock);
                           break;
                        }
                     }

                     this.justSent.add(pos);
                     this.snapshots.store(pos.x, pos.z, current);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onBlockUpdate(BlockUpdateEvent event) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && LevelUtil.isOverworld(level)) {
         BlockPos pos = event.pos;
         int sectionIndex = (pos.getY() >> 4) - level.getMinSectionY();
         this.snapshots
            .update(
               pos.getX() >> 4,
               pos.getZ() >> 4,
               sectionIndex,
               pos.getX() & 15,
               pos.getY() & 15,
               pos.getZ() & 15,
               event.newState
            );
      }
   }

   private void onActivity(ChunkPos pos, ChunkSnapshotStore.BlockChange change, Block newBlock, Block oldBlock) {
      ModuleChat.Body notice = ModuleChat.body().text("Activity ").good("detected").text(" in chunk ").value(pos.x + ", " + pos.z);
      if (this.advancedDebug.get()) {
         notice.text(": ").name(oldBlock.getName().getString()).text(" changed to ").name(newBlock.getName().getString());
      }

      ModuleChat.send(this, notice);
      this.changedBlocks.add(new BlockPos(change.worldX(), change.worldY(), change.worldZ()));
      this.flagArea(pos);
   }

   private void flagArea(ChunkPos source) {
      this.ensureConnection();
      this.sourceChunks.add(source);
      List<ChunkPos> area = new ArrayList<>();

      for (int dx = -9; dx <= 9; dx++) {
         for (int dz = -9; dz <= 9; dz++) {
            area.add(new ChunkPos(source.x + dx, source.z + dz));
         }
      }

      if (this.flaggedChunks.isEmpty()) {
         this.flaggedChunks.addAll(area);
      } else {
         List<ChunkPos> overlap = new ArrayList<>();

         for (ChunkPos candidate : area) {
            if (this.flaggedChunks.contains(candidate)) {
               overlap.add(candidate);
            }
         }

         this.flaggedChunks.clear();
         this.flaggedChunks.addAll(overlap.isEmpty() ? area : overlap);
      }
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && LevelUtil.isOverworld(mc.level)) {
         this.ensureConnection();
         if (this.renderChunks.get()) {
            this.renderFlaggedChunks();
         }

         if (this.render.get() && this.advancedDebug.get()) {
            GizmoStyle blockStyle = GizmoStyle.strokeAndFill(this.lineColor.argb(), 1.5F, this.fillColor.argb());

            for (BlockPos pos : this.changedBlocks) {
               Gizmos.cuboid(new AABB(pos), blockStyle).setAlwaysOnTop();
            }

            GizmoStyle sentStyle = GizmoStyle.strokeAndFill(-16711936, 1.5F, 671153920);

            for (ChunkPos pos : chunksInRange(mc.player.chunkPosition())) {
               if (this.justSent.contains(pos)) {
                  Gizmos.cuboid(marker(pos), sentStyle).setAlwaysOnTop();
               }
            }
         }
      }
   }

   private void renderFlaggedChunks() {
      GizmoStyle flagStyle = GizmoStyle.strokeAndFill(this.chunkLine.argb(), 1.5F, this.chunkFill.argb());
      boolean sources = this.highlightSources.get();

      for (ChunkPos pos : this.flaggedChunks) {
         if (!sources || !this.sourceChunks.contains(pos)) {
            Gizmos.cuboid(marker(pos), flagStyle).setAlwaysOnTop();
         }
      }

      if (sources) {
         GizmoStyle sourceStyle = GizmoStyle.strokeAndFill(-16711681, 1.5F, 671154175);

         for (ChunkPos pos : this.sourceChunks) {
            Gizmos.cuboid(marker(pos), sourceStyle).setAlwaysOnTop();
         }
      }
   }

   private static AABB marker(ChunkPos pos) {
      double x = pos.x * 16.0;
      double z = pos.z * 16.0;
      return new AABB(x, 63.0, z, x + 16.0, 63.0, z + 16.0);
   }

   static Set<ChunkPos> chunksInRange(ChunkPos center) {
      Set<ChunkPos> range = new LinkedHashSet<>();

      for (int dz = -8; dz <= 8; dz++) {
         int cut = Math.max(0, 3 - (8 - Math.abs(dz)));
         int halfWidth = 8 - cut;

         for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            range.add(new ChunkPos(center.x + dx, center.z + dz));
         }
      }

      return range;
   }

   private static PalettedContainer<BlockState>[] extractSections(LevelChunk chunk) {
      LevelChunkSection[] sections = chunk.getSections();
      PalettedContainer<BlockState>[] out = new PalettedContainer[sections.length];

      for (int index = 6; index <= 23 && index < sections.length; index++) {
         LevelChunkSection section = sections[index];
         if (section != null && !section.hasOnlyAir()) {
            out[index] = section.getStates().copy();
         }
      }

      return out;
   }
}
