package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.module.impl.movement.FlyModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class MixinFlyEntity {
   @ModifyReturnValue(method = "isInWater", at = @At("RETURN"))
   private boolean champ$flyIsInWater(boolean original) {
      return original && !this.champ$flyAffects();
   }

   @ModifyReturnValue(method = "isInLava", at = @At("RETURN"))
   private boolean champ$flyIsInLava(boolean original) {
      return original && !this.champ$flyAffects();
   }

   @ModifyExpressionValue(method = "updateSwimming", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;isUnderWater()Z"))
   private boolean champ$flyIsUnderWater(boolean original) {
      return original && !this.champ$flyAffects();
   }

   @Unique
   private boolean champ$flyAffects() {
      return FlyModule.active != null && (Object) this == Minecraft.getInstance().player;
   }
}
