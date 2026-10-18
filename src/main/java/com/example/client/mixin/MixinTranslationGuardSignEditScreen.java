package com.example.client.mixin;

import net.champ.client.util.TranslationGuard;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractSignEditScreen.class)
public abstract class MixinTranslationGuardSignEditScreen {
   @Shadow
   private SignText field_43362;
   @Shadow
   @Final
   private String[] field_40425;

   @Inject(method = "<init>(Lnet/minecraft/world/level/block/entity/SignBlockEntity;ZZLnet/minecraft/network/chat/Component;)V", at = @At("TAIL"))
   private void champ$guardMessages(SignBlockEntity sign, boolean front, boolean filtered, Component title, CallbackInfo ci) {
      for (int line = 0; line < this.messages.length; line++) {
         this.messages[line] = TranslationGuard.plain(this.text.getMessage(line, filtered));
      }
   }
}
