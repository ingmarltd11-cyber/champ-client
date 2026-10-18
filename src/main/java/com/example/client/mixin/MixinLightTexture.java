package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.render.FullbrightModule;
import net.champ.client.module.impl.render.NoRenderModule;
import net.champ.client.module.impl.render.XrayModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LightTexture.class)
public abstract class MixinLightTexture {
   @Shadow
   @Final
   private GpuTexture texture;

   @Inject(
      method = "updateLightTexture",
      at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LightTexture;updateLightTexture:Z", opcode = 181, shift = Shift.AFTER),
      cancellable = true
   )
   private void champ$xrayFullbright(float partialTick, CallbackInfo ci) {
      if (XrayModule.active != null) {
         RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, -1);
         ci.cancel();
      }
   }

   @ModifyExpressionValue(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
   private float champ$hideDarknessPulse(float blendFactor) {
      return NoRenderModule.hideBlindnessDarkness() ? 0.0F : blendFactor;
   }

   @ModifyArg(
      method = "updateLightTexture",
      at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 6),
      index = 0
   )
   private float champ$fullbrightGamma(float gamma) {
      FullbrightModule fullbright = ModuleRegistry.get(FullbrightModule.class);
      return fullbright != null && fullbright.isEnabled() ? fullbright.gamma() : gamma;
   }
}
