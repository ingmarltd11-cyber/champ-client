package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.module.impl.movement.BoatFlyModule;
import net.champ.client.module.impl.movement.EntityControlModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class MixinEntityControlLocalPlayer {
   @ModifyReturnValue(method = "getJumpRidingScale", at = @At("RETURN"))
   private float champ$entityControlMaxJump(float original) {
      return EntityControlModule.maxJump() ? 1.0F : original;
   }

   @ModifyReturnValue(method = "jumpableVehicle", at = @At("RETURN"))
   private PlayerRideableJumping champ$boatFlyCancelJump(PlayerRideableJumping original) {
      return original != null && BoatFlyModule.cancelsMountJump((LocalPlayer)(Object)(Object)this) ? null : original;
   }
}
