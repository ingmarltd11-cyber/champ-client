package com.example.client.mixin;

import net.champ.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.FallingBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(FallingBlockRenderer.class)
public abstract class MixinFallingBlockRenderer {
   @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
   private void champ$cancelFallingBlock(FallingBlockEntity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
      if (NoRenderModule.hideFallingBlocks()) {
         cir.setReturnValue(false);
      }
   }
}
