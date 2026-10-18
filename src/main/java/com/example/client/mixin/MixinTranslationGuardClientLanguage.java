package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import net.champ.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ClientLanguage.class)
public abstract class MixinTranslationGuardClientLanguage {
   @Inject(method = "loadFrom", at = @At("HEAD"), require = 0)
   private static void champ$beginLoad(ResourceManager resourceManager, List<String> languages, boolean rightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
      TranslationGuard.beginLoad();
   }

   @WrapOperation(
      method = "appendFrom",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/locale/Language;loadFromJson(Ljava/io/InputStream;Ljava/util/function/BiConsumer;)V"),
      require = 0
   )
   private static void champ$tapLoad(InputStream stream, BiConsumer<String, String> output, Operation<Void> original, @Local Resource resource) {
      original.call(new Object[]{stream, TranslationGuard.tap(resource, output)});
   }

   @Inject(method = "loadFrom", at = @At("RETURN"), require = 0)
   private static void champ$endLoad(ResourceManager resourceManager, List<String> languages, boolean rightToLeft, CallbackInfoReturnable<ClientLanguage> cir) {
      TranslationGuard.endLoad();
   }
}
