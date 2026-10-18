package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.KeybindContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(KeybindContents.class)
public abstract class MixinTranslationGuardKeybind {
   @Shadow
   @Final
   private String name;

   @ModifyReturnValue(method = "getNestedComponent", at = @At("RETURN"))
   private Component champ$guardKeybind(Component original) {
      return TranslationGuard.active() ? TranslationGuard.keybind(this.name) : original;
   }
}
