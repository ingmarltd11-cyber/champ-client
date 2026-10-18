package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.champ.client.event.impl.Render2DEvent;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.module.impl.render.NoRenderModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class MixinGui {
   @ModifyExpressionValue(method = "renderCrosshair", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
   private boolean champ$freecamCrosshair(boolean firstPerson) {
      FreecamModule freecam = FreecamModule.active;
      return freecam != null && freecam.showBody() && !firstPerson || firstPerson;
   }

   @Inject(method = "render", at = @At("TAIL"))
   private void champ$onRender2D(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      Render2DEvent.dispatch(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
   }

   @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
   private void champ$cancelVanillaEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      ci.cancel();
   }

   @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
   private void champ$cancelTextureOverlay(GuiGraphics graphics, Identifier sprite, float alpha, CallbackInfo ci) {
      if (NoRenderModule.hidePumpkinPowderSnow()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
   private void champ$cancelPortalOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
      if (NoRenderModule.hideNauseaPortal()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
   private void champ$cancelConfusionOverlay(GuiGraphics graphics, float alpha, CallbackInfo ci) {
      if (NoRenderModule.hideNauseaPortal()) {
         ci.cancel();
      }
   }
}
