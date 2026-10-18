package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.champ.client.event.impl.PlayerMoveEvent;
import net.champ.client.module.impl.misc.FreecamModule;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {
   @WrapMethod(method = "move")
   private void champ$onMove(MoverType type, Vec3 movement, Operation<Void> original) {
      if (!PlayerMoveEvent.TYPE.hasListeners()) {
         original.call(new Object[]{type, movement});
      } else {
         PlayerMoveEvent.move(movement, changed -> original.call(new Object[]{type, changed}));
      }
   }

   @Inject(method = "tick", at = @At("HEAD"))
   private void champ$walkServerRotation(CallbackInfo ci) {
      LocalPlayer self = (LocalPlayer)this;
      if (Minecraft.getInstance().player == self) {
         ServerRotation.applyForMovement(self);
      }
   }

   @Inject(method = "tick", at = @At("RETURN"))
   private void champ$finishServerRotation(CallbackInfo ci) {
      ServerRotation.finishTick((LocalPlayer)this);
   }

   @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = Shift.AFTER))
   private void champ$freecamAfterInput(CallbackInfo ci) {
      LocalPlayer self = (LocalPlayer)this;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "sendPosition", at = @At("HEAD"))
   private void champ$freecamBeforeSend(CallbackInfo ci) {
      LocalPlayer self = (LocalPlayer)this;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == self) {
         FreecamModule.drive(mc);
      }
   }

   @Inject(method = "sendPosition", at = @At("RETURN"))
   private void champ$swapOutServerRotation(CallbackInfo ci) {
      LocalPlayer self = (LocalPlayer)this;
      if (Minecraft.getInstance().player == self) {
         ServerRotation.afterSend(self);
      }
   }
}
