package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.module.impl.movement.JesusModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntity.class)
public abstract class MixinJesusLivingEntity {
   @ModifyReturnValue(method = "canStandOnFluid", at = @At("RETURN"))
   private boolean champ$jesusCanStandOnFluid(boolean original, FluidState fluidState) {
      if (original) {
         return true;
      }

      JesusModule jesus = JesusModule.active;
      return jesus != null && this == Minecraft.getInstance().player ? jesus.canWalkOnFluid(fluidState) : false;
   }
}
