package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.champ.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.Display.TextDisplay.CachedInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(TextDisplay.class)
public abstract class MixinNameProtectTextDisplay {
   @Unique
   private int champ$nameProtectGeneration;

   @ModifyExpressionValue(
      method = "cacheDisplay",
      at = @At(
         value = "FIELD",
         opcode = 180,
         ordinal = 0,
         target = "Lnet/minecraft/world/entity/Display$TextDisplay;clientDisplayCache:Lnet/minecraft/world/entity/Display$TextDisplay$CachedInfo;"
      )
   )
   private CachedInfo champ$nameProtectRefresh(CachedInfo cached) {
      int generation = NameProtectModule.generation();
      if (generation == this.champ$nameProtectGeneration) {
         return cached;
      }

      this.champ$nameProtectGeneration = generation;
      return null;
   }

   @ModifyExpressionValue(
      method = "cacheDisplay",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Display$TextDisplay$TextRenderState;text()Lnet/minecraft/network/chat/Component;")
   )
   private Component champ$nameProtectText(Component text) {
      return NameProtectModule.protect(text);
   }
}
