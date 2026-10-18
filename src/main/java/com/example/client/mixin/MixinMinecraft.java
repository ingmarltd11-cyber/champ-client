package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.champ.client.event.EventBus;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.impl.misc.AutoEatModule;
import net.champ.client.util.PerformanceBudget;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {
   @Inject(method = "tick", at = @At("HEAD"))
   private void champ$onTickStart(CallbackInfo ci) {
      PerformanceBudget.beginTick();
      EventBus.INSTANCE.drainAsync();
      ServerRotation.startTick();
      TickEvent.dispatch();
   }

   @Inject(method = "tick", at = @At("RETURN"))
   private void champ$onTickEnd(CallbackInfo ci) {
      TickEvent.dispatchPost();
   }

   @ModifyExpressionValue(
      method = "startUseItem",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;")
   )
   private Type champ$autoEatIgnoreCrosshair(Type type) {
      return AutoEatModule.isEating() ? Type.MISS : type;
   }
}
