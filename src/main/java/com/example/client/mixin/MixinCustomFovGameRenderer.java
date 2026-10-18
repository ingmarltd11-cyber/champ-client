package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.champ.client.module.impl.render.CustomFovModule;
import net.champ.client.module.impl.render.ZoomModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class MixinCustomFovGameRenderer {
   @Shadow
   public abstract boolean method_35765();

   @ModifyReturnValue(method = "getFov", at = @At("RETURN"))
   private float champ$customFov(float original, @Local(argsOnly = true) boolean useFovSetting) {
      CustomFovModule module = CustomFovModule.active;
      float fov = module != null && !this.isPanoramicMode() ? module.fov() : original;
      if (!useFovSetting) {
         CustomFovModule.hudFovApplied(original, fov);
      }

      return useFovSetting && !this.isPanoramicMode() ? ZoomModule.fov(fov) : fov;
   }
}
