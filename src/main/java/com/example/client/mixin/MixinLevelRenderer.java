package com.example.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.champ.client.event.impl.Render3DEvent;
import net.champ.client.module.impl.render.NoRenderModule;
import net.champ.client.util.ShaderOverlay;
import net.champ.client.util.TracerEngine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.material.FogType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.Gizmos.TemporaryCollection;
import net.minecraft.client.renderer.fog.FogRenderer.FogMode;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer {
   @ModifyArg(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V"),
      index = 0
   )
   private Frustum champ$onRenderLevel(
      Frustum frustum, @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation, @Local(argsOnly = true, ordinal = 1) Matrix4f projection
   ) {
      float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
      TracerEngine.beginFrame(viewRotation, projection);
      TemporaryCollection capture = ShaderOverlay.capture();

      try {
         Render3DEvent.dispatch(partialTick, frustum);
      } catch (Throwable var9) {
         if (capture != null) {
            try {
               capture.close();
            } catch (Throwable var8) {
               var9.addSuppressed(var8);
            }
         }

         throw var9;
      }

      if (capture != null) {
         capture.close();
      }

      return frustum;
   }

   @ModifyArg(
      method = "renderLevel",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;addLateDebugPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/CameraRenderState;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Matrix4f;)V"
      ),
      index = 2
   )
   private GpuBufferSlice champ$noFluidFogOnTop(GpuBufferSlice fog, @Local(argsOnly = true) Camera camera) {
      return champ$onTopFog(fog, camera);
   }

   @Unique
   private static GpuBufferSlice champ$onTopFog(GpuBufferSlice fog, Camera camera) {
      return camera.getFluidInCamera() == FogType.NONE
         ? fog
         : ((AccessorGameRenderer)Minecraft.getInstance().gameRenderer).champ$getFogRenderer().getBuffer(FogMode.NONE);
   }

   @Inject(method = "renderLevel", at = @At("TAIL"))
   private void champ$overlayAfterShaderPack(
      CallbackInfo ci,
      @Local(argsOnly = true) GpuBufferSlice fog,
      @Local(argsOnly = true) Camera camera,
      @Local(argsOnly = true, ordinal = 0) Matrix4f viewRotation,
      @Local(argsOnly = true, ordinal = 1) Matrix4f projection
   ) {
      if (ShaderOverlay.pending()) {
         ShaderOverlay.render(camera, viewRotation, projection, fog, champ$onTopFog(fog, camera));
      }
   }

   @Inject(method = "renderLevel", at = @At("TAIL"))
   private void champ$worldFogForHand(CallbackInfo ci, @Local(argsOnly = true) GpuBufferSlice fog, @Local(argsOnly = true) Camera camera) {
      if (camera.getFluidInCamera() != FogType.NONE) {
         RenderSystem.setShaderFog(fog);
      }
   }

   @Inject(method = "doesMobEffectBlockSky", at = @At("HEAD"), cancellable = true)
   private void champ$keepSkyDuringBlindnessDarkness(Camera camera, CallbackInfoReturnable<Boolean> cir) {
      if (NoRenderModule.hideBlindnessDarkness()) {
         cir.setReturnValue(false);
      }
   }
}
