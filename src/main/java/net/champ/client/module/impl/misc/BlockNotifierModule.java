package net.champ.client.module.impl.misc;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.champ.client.esp.ChunkScanner;
import net.champ.client.event.Stage;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.gui.ChallengeToast;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BlockColorListSetting;
import net.champ.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;

@Environment(EnvType.CLIENT)
public final class BlockNotifierModule extends ClientModule implements ChunkScanner.Consumer {
   private static final int MAX_KNOWN = 50000;
   private final BlockColorListSetting blocks = this.addSetting("blocks", BlockColorListSetting.blockList("Blocks", List.of("minecraft:spawner")));
   private final BooleanSetting sound = this.addSetting("sound", new BooleanSetting("Sound", true));
   private final LongLinkedOpenHashSet known = new LongLinkedOpenHashSet();
   private final Map<Block, BlockNotifierModule.Finds> pending = new LinkedHashMap<>();
   private Set<String> scannedIds = Set.of();
   private ResourceKey<Level> dimension;

   public BlockNotifierModule() {
      super(
         "block-notifier", "Block Notifier", "Shows a toast with the block and its coordinates when one of your chosen blocks shows up.", ModuleCategory.MISC
      );
      this.blocks.onChange(this::rescan);
   }

   @Override
   protected void onEnable() {
      this.known.clear();
      this.pending.clear();
      this.dimension = null;
      this.scannedIds = this.blocks.enabledIds();
      ChunkScanner.register(this);
      this.on(TickEvent.TYPE, Stage.POST, this::onTick);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.known.clear();
      this.pending.clear();
   }

   private void rescan() {
      if (this.isEnabled()) {
         Set<String> ids = this.blocks.enabledIds();
         if (!ids.equals(this.scannedIds)) {
            this.scannedIds = ids;
            ChunkScanner.rescan();
         }
      }
   }

   @Override
   public boolean wants(BlockState state) {
      return this.blocks.isEnabled(state.getBlock());
   }

   @Override
   public void found(long pos, BlockState state) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         this.syncDimension(mc);
         if (this.known.add(pos)) {
            if (this.known.size() > 50000) {
               this.known.removeFirstLong();
            }

            BlockPos at = BlockPos.of(pos);
            BlockNotifierModule.Finds finds = this.pending.computeIfAbsent(state.getBlock(), block -> new BlockNotifierModule.Finds());
            finds.count++;
            double distanceSq = mc.player == null ? 0.0 : mc.player.distanceToSqr(Vec3.atCenterOf(at));
            if (distanceSq < finds.nearestSq) {
               finds.nearestSq = distanceSq;
               finds.nearest = at;
            }
         }
      }
   }

   @Override
   public void removed(long pos) {
      this.known.remove(pos);
   }

   @Override
   public void reset() {
      this.known.clear();
      this.pending.clear();
   }

   private void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null) {
         this.syncDimension(mc);
         if (!this.pending.isEmpty()) {
            for (Entry<Block, BlockNotifierModule.Finds> entry : this.pending.entrySet()) {
               this.notify(entry.getKey(), entry.getValue());
            }

            this.pending.clear();
         }
      }
   }

   private void syncDimension(Minecraft mc) {
      ResourceKey<Level> current = mc.level.dimension();
      if (!current.equals(this.dimension)) {
         this.dimension = current;
         this.known.clear();
         this.pending.clear();
      }
   }

   private void notify(Block block, BlockNotifierModule.Finds finds) {
      Item item = block.asItem();
      ItemStack icon = new ItemStack(item == Items.AIR ? Items.COMPASS : item);
      String title = block.getName().getString();
      BlockPos at = finds.nearest;
      Component message = Component.literal("at " + at.getX() + ", " + at.getY() + ", " + at.getZ());

      for (int i = 0; i < finds.count; i++) {
         ChallengeToast.addOrUpdate(icon, title, message, this.sound.get());
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Finds {
      int count;
      BlockPos nearest;
      double nearestSq = Double.MAX_VALUE;
   }
}
