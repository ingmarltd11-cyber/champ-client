package com.example.client.mixin.indigo;

import net.champ.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(value = AbstractTerrainRenderContext.class, remap = false)
public abstract class MixinAbstractTerrainRenderContext {
   @Inject(method = "bufferQuad", at = @At("HEAD"))
   private void champ$xrayBufferQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
      if (XrayModule.active != null) {
         float alpha = XrayModule.CURRENT_ALPHA.get()[0];
         if (!(alpha <= 0.0F) && !(alpha >= 1.0F)) {
            quad.renderLayer(ChunkSectionLayer.TRANSLUCENT);

            for (int vertex = 0; vertex < 4; vertex++) {
               quad.color(vertex, ARGB.multiplyAlpha(quad.color(vertex), alpha));
            }
         }
      }
   }
}
