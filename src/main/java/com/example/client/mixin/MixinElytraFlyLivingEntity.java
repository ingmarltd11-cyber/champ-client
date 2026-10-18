package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.module.impl.movement.ElytraFlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinElytraFlyLivingEntity {
   @ModifyReturnValue(method = "isFallFlying", at = @At("RETURN"))
   private boolean champ$elytraFlyIsFallFlying(boolean original) {
      ElytraFlyModule module = ElytraFlyModule.active;
      if (module == null) {
         return original;
      }

      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      return this == player && mc.isSameThread() ? module.modifyFallFlying(player, original) : original;
   }
}
