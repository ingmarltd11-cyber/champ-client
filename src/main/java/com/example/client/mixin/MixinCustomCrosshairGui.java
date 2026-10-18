package com.example.client.mixin;

import net.champ.client.module.impl.render.CustomCrosshairModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class MixinCustomCrosshairGui {
   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void champ$hideVanillaCrosshair(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      if (CustomCrosshairModule.active != null) {
         ci.cancel();
      }
   }
}
