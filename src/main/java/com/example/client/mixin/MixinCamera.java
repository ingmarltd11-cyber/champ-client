package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.module.impl.render.FreelookModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class MixinCamera {
   @Shadow
   protected abstract void setRotation(float var1, float var2);

   @Shadow
   protected abstract void setPosition(double var1, double var3, double var5);

   @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
   private void champ$freelookRotation(Camera camera, float yRot, float xRot, Operation<Void> original) {
      FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
      if (FreecamModule.active == null && freelook != null && freelook.isEnabled()) {
         original.call(new Object[]{camera, freelook.cameraYaw(), freelook.cameraPitch()});
      } else {
         original.call(new Object[]{camera, yRot, xRot});
      }
   }

   @WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
   private float champ$freelookThroughBlocks(Camera camera, float distance, Operation<Float> original) {
      return FreelookModule.throughBlocks() ? distance : (Float)original.call(new Object[]{camera, distance});
   }

   @Inject(method = "setup", at = @At("RETURN"))
   private void champ$freecamCamera(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null) {
         this.setPosition(freecam.getX(partialTick), freecam.getY(partialTick), freecam.getZ(partialTick));
         this.setRotation(freecam.getYaw(), freecam.getPitch());
      }
   }
}
