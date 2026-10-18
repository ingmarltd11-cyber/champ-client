package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.champ.client.event.impl.MouseInputEvent;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.combat.AimAssistModule;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.module.impl.render.FreelookModule;
import net.champ.client.module.impl.render.ZoomModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
   @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
   private void champ$onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
      if (MouseInputEvent.dispatch(window, buttonInfo.button(), action, buttonInfo.modifiers())) {
         ci.cancel();
      }
   }

   @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
   private void champ$wheel(long window, double horizontal, double vertical, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (vertical != 0.0 && window == mc.getWindow().handle() && mc.screen == null && mc.getOverlay() == null) {
         if (ZoomModule.scroll(vertical) || FreecamModule.scroll(vertical)) {
            ci.cancel();
         }
      }
   }

   @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
   private void champ$cameraTurn(LocalPlayer player, double yRot, double xRot, Operation<Void> original, @Local(argsOnly = true) double movementTime) {
      double scale = ZoomModule.turnScale();
      yRot *= scale;
      xRot *= scale;
      FreecamModule freecam = FreecamModule.active;
      if (freecam != null) {
         freecam.turn(yRot, xRot);
      } else {
         FreelookModule freelook = ModuleRegistry.get(FreelookModule.class);
         if (freelook != null && freelook.cameraMode()) {
            freelook.turnCamera(yRot, xRot);
         } else {
            AimAssistModule assist = ModuleRegistry.get(AimAssistModule.class);
            if (assist != null && assist.isEnabled()) {
               double[] turn = assist.nudge(player, yRot, xRot, movementTime);
               yRot += turn[0];
               xRot += turn[1];
            }

            original.call(new Object[]{player, yRot, xRot});
         }
      }
   }
}
