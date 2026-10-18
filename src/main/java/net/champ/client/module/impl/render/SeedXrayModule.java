package net.champ.client.module.impl.render;

import java.util.List;
import java.util.Set;
import java.util.function.LongPredicate;
import net.champ.client.esp.BlockLayer;
import net.champ.client.esp.ChunkScanner;
import net.champ.client.esp.EspStyle;
import net.champ.client.event.EventHandler;
import net.champ.client.event.impl.Render3DEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.BlockColorListSetting;
import net.champ.client.module.setting.Setting;
import net.champ.client.module.setting.BooleanSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;

/**
 * Seed Xray: ore-ESP met een eigen kleur per erts en optionele tracers.
 *
 * Let op: dit zoekt alleen in de chunks die de client al heeft. De seed wordt niet gebruikt om
 * ertsen te voorspellen (dat vraagt een nagebouwde wereldgeneratie), dus ertsen die de server
 * verbergt zie je hier ook niet.
 */
@Environment(EnvType.CLIENT)
public final class SeedXrayModule extends ClientModule implements ChunkScanner.Consumer {
   private static final List<String> DEFAULT_ORES = List.of(
      "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
      "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
      "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore",
      "minecraft:ancient_debris"
   );

   private final EspStyle style = new EspStyle(new EspStyle.Registrar() {
      @Override
      public <S extends Setting> S add(String id, S setting) {
         return SeedXrayModule.this.addSetting(id, setting);
      }
   }, "Outline", 0.2F, 2048.0F);
   private final BooleanSetting sTracers = this.addSetting("tracers", new BooleanSetting("Tracers", false));
   private final BlockColorListSetting sOres = this.addSetting(BlockColorListSetting.blockList("Ore Colors", DEFAULT_ORES));
   private final BlockLayer layer = new BlockLayer(new BlockLayer.Palette() {
      @Override
      public int color(Object kind) {
         return SeedXrayModule.this.sOres.colorArgb((Block)kind);
      }

      @Override
      public int tracerColor(Object kind) {
         return SeedXrayModule.this.sOres.tracerColorArgb((Block)kind);
      }
   }, () -> Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.dimension());
   private Set<String> scannedIds = Set.of();

   public SeedXrayModule() {
      super("seed-xray", "Seed Xray", "Ore-ESP met eigen kleur per erts en tracers (gebruikt de seed niet).", ModuleCategory.VISUALS);
      this.sOres.onChange(this::rescan);
   }

   private void rescan() {
      if (this.isEnabled()) {
         Set<String> ids = this.sOres.enabledIds();
         if (!ids.equals(this.scannedIds)) {
            boolean removed = !ids.containsAll(this.scannedIds);
            this.scannedIds = ids;
            if (removed) {
               this.layer.clear();
            }

            ChunkScanner.rescan();
         }
      }
   }

   @Override
   protected void onEnable() {
      this.scannedIds = this.sOres.enabledIds();
      ChunkScanner.register(this);
   }

   @Override
   protected void onDisable() {
      ChunkScanner.unregister(this);
      this.layer.clear();
   }

   @Override
   public boolean wants(BlockState state) {
      return this.sOres.isEnabled(state.getBlock());
   }

   @Override
   public void found(long pos, BlockState state) {
      this.layer.put(pos, state.getBlock());
   }

   @Override
   public void removed(long pos) {
      this.layer.remove(pos);
   }

   @Override
   public void reset() {
      this.layer.clear();
   }

   @Override
   public void chunkScanned(long chunkKey, LongPredicate stillThere) {
      this.layer.retainChunk(chunkKey, stillThere);
   }

   @EventHandler
   public void onRender3D(Render3DEvent event) {
      this.layer.render(event, this.style);
      if (this.sTracers.get() && this.sOres.hasAnyTracerEnabled()) {
         this.layer.renderTracers(this.style);
      }
   }
}
