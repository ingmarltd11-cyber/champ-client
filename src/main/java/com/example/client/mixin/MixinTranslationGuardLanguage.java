package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.champ.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.locale.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(Language.class)
public abstract class MixinTranslationGuardLanguage {
   @ModifyReturnValue(method = "getInstance", at = @At("RETURN"))
   private static Language champ$guardLanguage(Language original) {
      return TranslationGuard.active() ? TranslationGuard.language(original) : original;
   }
}
