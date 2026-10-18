package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LevelRenderer.class)
public abstract class MixinFreecamLevelRenderer {
   @Inject(method = "isSectionCompiledAndVisible", at = @At("HEAD"), cancellable = true)
   private void champ$freecamEntitiesReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (FreecamModule.active != null) {
         cir.setReturnValue(true);
      }
   }

   @WrapOperation(
      method = "renderLevel",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V")
   )
   private void champ$freelookCullInsideBlocks(LevelRenderer renderer, Camera camera, Frustum frustum, boolean spectator, Operation<Void> original) {
      original.call(new Object[]{renderer, camera, frustum, spectator || camera.isDetached() && FreelookModule.throughBlocks()});
   }
}
