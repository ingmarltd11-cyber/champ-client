package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.champ.client.event.impl.PlayerMoveEvent;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1313;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_746;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(class_746.class)
public abstract class MixinLocalPlayer {
   @WrapMethod(method = "method_5784")
   private void champ$onMove(class_1313 type, class_243 movement, Operation<Void> original) {
      if (!PlayerMoveEvent.TYPE.hasListeners()) {
         original.call(new Object[]{type, movement});
      } else {
         PlayerMoveEvent.move(movement, changed -> original.call(new Object[]{type, changed}));
      }
   }

   @Inject(method = "method_5773", at = @At("HEAD"))
   private void champ$walkServerRotation(CallbackInfo ci) {
      class_746 self = (class_746)this;
      if (class_310.method_1551().field_1724 == self) {
         ServerRotation.applyForMovement(self);
      }
   }

   @Inject(method = "method_5773", at = @At("RETURN"))
   private void champ$finishServerRotation(CallbackInfo ci) {
      ServerRotation.finishTick((class_746)this);
   }

   @Inject(method = "method_6007", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_744;method_3129()V", shift = Shift.AFTER))
   private void champ$freecamAfterInput(CallbackInfo ci) {
      class_746 self = (class_746)this;
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "method_3136", at = @At("HEAD"))
   private void champ$freecamBeforeSend(CallbackInfo ci) {
      class_746 self = (class_746)this;
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "method_3136", at = @At("RETURN"))
   private void champ$swapOutServerRotation(CallbackInfo ci) {
      class_746 self = (class_746)this;
      if (class_310.method_1551().field_1724 == self) {
         ServerRotation.afterSend(self);
      }
   }
}
