package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.event.impl.PlayerAttackEntityEvent;
import net.champ.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Player.class)
public abstract class MixinPlayer {
   @Inject(method = "attack", at = @At("HEAD"))
   private void champ$onAttack(Entity target, CallbackInfo ci) {
      if ((Object) this == Minecraft.getInstance().player) {
         PlayerAttackEntityEvent.dispatch(target);
      }
   }

   @ModifyReturnValue(method = "getFlyingSpeed", at = @At("RETURN"))
   private float champ$flySpeed(float original) {
      FlyModule fly = FlyModule.active;
      if (fly == null) {
         return original;
      } else {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && (Object) this == player) {
            float override = fly.getOffGroundSpeed(player);
            return override != -1.0F ? override : original;
         } else {
            return original;
         }
      }
   }
}
