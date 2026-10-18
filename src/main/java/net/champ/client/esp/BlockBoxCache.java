package net.champ.client.esp;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.function.LongFunction;
import net.champ.client.util.BlockBoxHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

@Environment(EnvType.CLIENT)
public final class BlockBoxCache {
   private final int capacity;
   private final LongFunction<AABB> factory;
   private final Long2ObjectLinkedOpenHashMap<BlockBoxCache.Entry> boxes = new Long2ObjectLinkedOpenHashMap();
   private float size = Float.NaN;
   private long frame;

   BlockBoxCache(int capacity) {
      this(capacity, null);
   }

   public BlockBoxCache(int capacity, LongFunction<AABB> factory) {
      this.capacity = Math.max(1, capacity);
      this.factory = factory;
   }

   public void beginFrame(float size) {
      if (Float.floatToIntBits(this.size) != Float.floatToIntBits(size)) {
         this.boxes.clear();
         this.size = size;
      }

      this.frame++;
   }

   public AABB box(long packed) {
      BlockBoxCache.Entry hit = (BlockBoxCache.Entry)this.boxes.getAndMoveToLast(packed);
      if (hit != null) {
         hit.frame = this.frame;
         return hit.box;
      }

      AABB made = this.factory == null ? BlockBoxHelper.scaledBlockBox(BlockPos.of(packed), this.size) : this.factory.apply(packed);
      if (this.boxes.size() == this.capacity) {
         if (((BlockBoxCache.Entry)this.boxes.get(this.boxes.firstLongKey())).frame == this.frame) {
            return made;
         }

         this.boxes.removeFirst();
      }

      this.boxes.put(packed, new BlockBoxCache.Entry(made, this.frame));
      return made;
   }

   int size() {
      return this.boxes.size();
   }

   @Environment(EnvType.CLIENT)
   private static final class Entry {
      final AABB box;
      long frame;

      Entry(AABB box, long frame) {
         this.box = box;
         this.frame = frame;
      }
   }
}
