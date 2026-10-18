package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinElytraFlyEntity {
   @ModifyReturnValue(method = "getPose", at = @At("RETURN"))
   private Pose champ$elytraFlyGetPose(Pose original) {
      ElytraFlyModule module = ElytraFlyModule.active;
      if (module == null) {
         return original;
      } else {
         Minecraft mc = Minecraft.getInstance();
         LocalPlayer player = mc.player;
         if (this == player && mc.isSameThread()) {
            return module.canPacketEfly(player) ? Pose.FALL_FLYING : original;
         } else {
            return original;
         }
      }
   }
}
