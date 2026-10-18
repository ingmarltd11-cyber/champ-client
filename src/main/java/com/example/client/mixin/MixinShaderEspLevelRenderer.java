package com.example.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.champ.client.module.impl.render.ShaderEspModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinShaderEspLevelRenderer {
   @Inject(method = "extractEntity", at = @At("RETURN"))
   private void champ$glowEspKeepState(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
      if (ShaderEspModule.active != null) {
         ShaderEspModule.extracted(entity, (EntityRenderState)cir.getReturnValue());
      }
   }

   @Inject(method = "renderLevel", at = @At("TAIL"))
   private void champ$glowEsp(
      CallbackInfo ci,
      @Local(argsOnly = true) Camera camera,
      @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
      @Local(argsOnly = true, ordinal = 1) Matrix4f projection
   ) {
      if (ShaderEspModule.active != null) {
         ShaderEspModule.afterLevel(camera, viewRotation, projection);
      }
   }
}
