package net.champ.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;

@Environment(EnvType.CLIENT)
public final class ItemLook extends ItemStackRenderState {
   public final List<Object> identity = new ArrayList<>();
   private final List<LayerRenderState> layers = new ArrayList<>();

   public void method_65605() {
      super.clear();
      this.identity.clear();
      this.layers.clear();
   }

   public LayerRenderState method_65601() {
      LayerRenderState layer = super.newLayer();
      this.layers.add(layer);
      return layer;
   }

   public void method_70946(Object element) {
      if (!(element instanceof FoilType)) {
         this.identity.add(element);
      }
   }

   public boolean resolve(ItemStack stack) {
      Minecraft mc = Minecraft.getInstance();

      try {
         mc.getItemModelResolver().updateForTopItem(this, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
      } catch (RuntimeException badModel) {
         this.clear();
         return false;
      }

      for (LayerRenderState layer : this.layers) {
         layer.setFoilType(FoilType.NONE);
      }

      if (this.identity.isEmpty()) {
         this.identity.add(stack.getItem());
      }

      return true;
   }
}
