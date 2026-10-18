package net.champ.client.esp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;
import net.champ.client.event.impl.BlockUpdateEvent;
import net.champ.client.event.impl.PacketReceiveNettyEvent;
import net.champ.client.module.impl.client.PerformanceModule;
import net.champ.client.util.PerformanceBudget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Load;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ChunkScanner {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private static final Object OWNER = new Object();
   private static final ChunkScanner.Consumer[] NONE = new ChunkScanner.Consumer[0];
   private static final List<ChunkScanner.Consumer> consumers = new ArrayList<>();
   private static ChunkScanner.Consumer[] snapshot = NONE;
   private static final ArrayDeque<Long> queue = new ArrayDeque<>();
   private static final Set<Long> queued = new HashSet<>();
   private static volatile boolean resetPending;
   private static ChunkScanner.ScanCursor scanning;
   private static ClientLevel scanLevel;
   private static ChunkScanner.ScanBatch deliveryBatch;

   static ChunkScanner.ScanBatch deliveryBatch() {
      return deliveryBatch;
   }

   private ChunkScanner() {
   }

   public static void init() {
      ClientChunkEvents.CHUNK_LOAD.register((Load)(level, chunk) -> enqueue(chunk.getPos().toLong()));
      ClientTickEvents.END_CLIENT_TICK.register(ChunkScanner::tick);
      BlockUpdateEvent.TYPE.listen(OWNER, ChunkScanner::onBlockUpdate);
      PacketReceiveNettyEvent.TYPE.listen(OWNER, event -> {
         if (event.packet instanceof ClientboundLoginPacket) {
            resetPending = true;
         }
      });
   }

   public static void register(ChunkScanner.Consumer consumer) {
      if (!consumers.contains(consumer)) {
         consumers.add(consumer);
         snapshot = consumers.toArray(NONE);
         restartScan();
         queueLoadedChunks();
      }
   }

   public static void unregister(ChunkScanner.Consumer consumer) {
      consumers.remove(consumer);
      snapshot = consumers.toArray(NONE);
      restartScan();
      if (consumers.isEmpty()) {
         queue.clear();
         queued.clear();
      }
   }

   public static int pending() {
      return queue.size() + (scanning == null ? 0 : 1);
   }

   public static void rescan() {
      restartScan();
      queueLoadedChunks();
   }

   private static void restartScan() {
      if (scanning != null) {
         long key = scanning.chunk.getPos().toLong();
         scanning.batch.finished = true;
         scanning = null;
         enqueue(key);
      }
   }

   private static void queueLoadedChunks() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && mc.player != null) {
         int cx = mc.player.getBlockX() >> 4;
         int cz = mc.player.getBlockZ() >> 4;
         int radius = (Integer)mc.options.renderDistance().get() + 1;
         List<long[]> loaded = new ArrayList<>();

         for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
               if (mc.level.getChunkSource().hasChunk(x, z)) {
                  int dx = x - cx;
                  int dz = z - cz;
                  loaded.add(new long[]{ChunkPos.asLong(x, z), dx * dx + dz * dz});
               }
            }
         }

         loaded.sort(Comparator.comparingLong(a -> a[1]));

         for (long[] entry : loaded) {
            enqueue(entry[0]);
         }
      }
   }

   private static void enqueue(long chunkKey) {
      if (!consumers.isEmpty() && queued.add(chunkKey)) {
         queue.add(chunkKey);
      }
   }

   private static void tick(Minecraft mc) {
      if (scanLevel != mc.level) {
         if (scanning != null) {
            scanning.batch.finished = true;
         }

         scanning = null;
         scanLevel = mc.level;
      }

      if (resetPending) {
         resetPending = false;
         queue.clear();
         queued.clear();
         if (scanning != null) {
            scanning.batch.finished = true;
         }

         scanning = null;

         for (ChunkScanner.Consumer consumer : snapshot) {
            call(consumer, ChunkScanner.Consumer::reset);
         }

         queueLoadedChunks();
      }

      if (mc.level != null && !consumers.isEmpty()) {
         if (PerformanceModule.optimizationsEnabled()) {
            tickIncremental(mc);
         } else {
            restartScan();
            long deadline = System.nanoTime() + PerformanceModule.scanBudgetNanos();

            Long key;
            while ((key = queue.poll()) != null) {
               queued.remove(key);
               LevelChunk chunk = mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
               if (chunk != null) {
                  scan(mc, chunk);
               }

               if (System.nanoTime() >= deadline) {
                  break;
               }
            }
         }
      }
   }

   private static void tickIncremental(Minecraft mc) {
      long deadline = PerformanceBudget.acquire(PerformanceModule.scanBudgetNanos());
      long started = System.nanoTime();
      if (started < deadline) {
         try {
            while (System.nanoTime() < deadline) {
               if (scanning == null) {
                  Long key = queue.poll();
                  if (key == null) {
                     break;
                  }

                  queued.remove(key);
                  LevelChunk chunk = mc.level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                  if (chunk == null) {
                     continue;
                  }

                  scanning = new ChunkScanner.ScanCursor(chunk, mc.level.dimensionType().minY() >> 4, snapshot);
               }

               ChunkScanner.ScanCursor cursor = scanning;
               if (mc.level.getChunkSource().getChunkNow(cursor.chunk.getPos().x, cursor.chunk.getPos().z) != cursor.chunk) {
                  cursor.batch.finished = true;
                  scanning = null;
               } else {
                  boolean complete = cursor.step();
                  if (scanning == cursor && complete) {
                     scanning = null;
                     finishScan(cursor.chunk);
                  }
               }
            }
         } finally {
            PerformanceBudget.record(started);
         }
      }
   }

   private static void scan(Minecraft mc, LevelChunk chunk) {
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      int minSection = mc.level.dimensionType().minY() >> 4;
      LevelChunkSection[] sections = chunk.getSections();
      List<ChunkScanner.Consumer> interested = new ArrayList<>(consumers.size());

      for (int si = 0; si < sections.length; si++) {
         LevelChunkSection section = sections[si];
         if (section != null && !section.hasOnlyAir()) {
            interested.clear();

            for (ChunkScanner.Consumer consumer : consumers) {
               if (section.maybeHas(consumer::wants)) {
                  interested.add(consumer);
               }
            }

            if (!interested.isEmpty()) {
               int baseY = minSection + si << 4;

               for (int x = 0; x < 16; x++) {
                  for (int z = 0; z < 16; z++) {
                     for (int y = 0; y < 16; y++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!state.isAir()) {
                           long pos = BlockPos.asLong(minX + x, baseY + y, minZ + z);

                           for (ChunkScanner.Consumer consumer : interested) {
                              if (consumer.wants(state)) {
                                 found(consumer, pos, state);
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      finishScan(chunk);
   }

   private static void finishScan(LevelChunk chunk) {
      long chunkKey = chunk.getPos().toLong();
      MutableBlockPos probe = new MutableBlockPos();

      for (ChunkScanner.Consumer consumer : snapshot) {
         call(consumer, c -> c.chunkScanned(chunkKey, pos -> {
            BlockState state = chunk.getBlockState(probe.set(pos));
            return !state.isAir() && c.wants(state);
         }));
      }
   }

   private static void onBlockUpdate(BlockUpdateEvent event) {
      ChunkScanner.Consumer[] current = snapshot;
      if (current.length != 0) {
         long pos = event.pos.asLong();
         BlockState state = event.newState;
         ChunkScanner.ScanBatch previous = deliveryBatch;
         deliveryBatch = null;

         try {
            for (ChunkScanner.Consumer consumer : current) {
               if (!state.isAir() && consumer.wants(state)) {
                  found(consumer, pos, state);
               } else {
                  removed(consumer, pos);
               }
            }
         } finally {
            deliveryBatch = previous;
         }
      }
   }

   private static void found(ChunkScanner.Consumer consumer, long pos, BlockState state) {
      try {
         consumer.found(pos, state);
      } catch (RuntimeException e) {
         drop(consumer, e);
      }
   }

   private static void removed(ChunkScanner.Consumer consumer, long pos) {
      try {
         consumer.removed(pos);
      } catch (RuntimeException e) {
         drop(consumer, e);
      }
   }

   private static void call(ChunkScanner.Consumer consumer, java.util.function.Consumer<ChunkScanner.Consumer> action) {
      try {
         action.accept(consumer);
      } catch (RuntimeException e) {
         drop(consumer, e);
      }
   }

   private static void drop(ChunkScanner.Consumer consumer, RuntimeException e) {
      LOGGER.error("[Champ Client] Chunk scan consumer {} failed; disabling it", consumer.getClass().getSimpleName(), e);
      consumers.remove(consumer);
      snapshot = consumers.toArray(NONE);
      restartScan();
   }

   @Environment(EnvType.CLIENT)
   public interface Consumer {
      boolean wants(BlockState var1);

      void found(long var1, BlockState var3);

      void removed(long var1);

      void reset();

      default void chunkScanned(long chunkKey, LongPredicate stillThere) {
      }
   }

   @Environment(EnvType.CLIENT)
   static final class ScanBatch {
      boolean finished;
   }

   @Environment(EnvType.CLIENT)
   private static final class ScanCursor {
      final LevelChunk chunk;
      final int minSection;
      final ChunkScanner.Consumer[] listeners;
      final ChunkScanner.ScanBatch batch = new ChunkScanner.ScanBatch();
      final List<ChunkScanner.Consumer> interested = new ArrayList<>();
      int sectionIndex;
      int blockIndex;

      ScanCursor(LevelChunk chunk, int minSection, ChunkScanner.Consumer[] listeners) {
         this.chunk = chunk;
         this.minSection = minSection;
         this.listeners = listeners;
      }

      boolean step() {
         if (this.batch.finished) {
            return true;
         }

         ChunkScanner.ScanBatch previous = ChunkScanner.deliveryBatch;
         ChunkScanner.deliveryBatch = this.batch;

         try {
            boolean complete = this.stepBlocks();
            if (complete) {
               this.batch.finished = true;
            }

            return complete;
         } finally {
            ChunkScanner.deliveryBatch = previous;
         }
      }

      private boolean stepBlocks() {
         LevelChunkSection[] sections = this.chunk.getSections();
         if (this.sectionIndex >= sections.length) {
            return true;
         }

         LevelChunkSection section = sections[this.sectionIndex];
         if (this.blockIndex == 0) {
            this.interested.clear();
            if (section != null && !section.hasOnlyAir()) {
               for (ChunkScanner.Consumer consumer : this.listeners) {
                  if (ChunkScanner.consumers.contains(consumer) && section.maybeHas(consumer::wants)) {
                     this.interested.add(consumer);
                  }
               }
            }

            if (this.interested.isEmpty()) {
               this.sectionIndex++;
               return this.sectionIndex >= sections.length;
            }
         }

         int end = Math.min(4096, this.blockIndex + 256);
         int minX = this.chunk.getPos().getMinBlockX();
         int minZ = this.chunk.getPos().getMinBlockZ();
         int baseY = this.minSection + this.sectionIndex << 4;

         while (this.blockIndex < end && !this.batch.finished) {
            int index = this.blockIndex++;
            int x = index >> 8;
            int z = index >> 4 & 15;
            int y = index & 15;
            BlockState state = section.getBlockState(x, y, z);
            if (!state.isAir()) {
               long pos = BlockPos.asLong(minX + x, baseY + y, minZ + z);

               for (ChunkScanner.Consumer consumer : this.interested) {
                  if (consumer.wants(state)) {
                     ChunkScanner.found(consumer, pos, state);
                  }
               }
            }
         }

         if (this.blockIndex == 4096) {
            this.blockIndex = 0;
            this.sectionIndex++;
         }

         return this.sectionIndex >= sections.length;
      }
   }
}
