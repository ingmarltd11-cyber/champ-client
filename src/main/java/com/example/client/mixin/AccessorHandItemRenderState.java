package com.example.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin(ItemStackRenderState.class)
public interface AccessorHandItemRenderState {
   @Accessor("layers")
   LayerRenderState[] champ$handLayers();

   @Accessor("activeLayerCount")
   int champ$handLayerCount();
}
