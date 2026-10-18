package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.champ.client.event.EventBus;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.impl.misc.AutoEatModule;
import net.champ.client.util.PerformanceBudget;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_310;
import net.minecraft.class_239.class_240;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(class_310.class)
public abstract class MixinMinecraft {
   @Inject(method = "method_1574", at = @At("HEAD"))
   private void champ$onTickStart(CallbackInfo ci) {
      PerformanceBudget.beginTick();
      EventBus.INSTANCE.drainAsync();
      ServerRotation.startTick();
      TickEvent.dispatch();
   }

   @Inject(method = "method_1574", at = @At("RETURN"))
   private void champ$onTickEnd(CallbackInfo ci) {
      TickEvent.dispatchPost();
   }

   @ModifyExpressionValue(
      method = "method_1583",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/class_239;method_17783()Lnet/minecraft/class_239$class_240;")
   )
   private class_240 champ$autoEatIgnoreCrosshair(class_240 type) {
      return AutoEatModule.isEating() ? class_240.field_1333 : type;
   }
}
