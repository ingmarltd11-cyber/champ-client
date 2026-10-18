package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.champ.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class MixinNameProtectGui {
   @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true)
   private Component champ$nameProtectTitle(Component title) {
      return NameProtectModule.protect(title);
   }

   @ModifyVariable(method = "setSubtitle", at = @At("HEAD"), argsOnly = true)
   private Component champ$nameProtectSubtitle(Component subtitle) {
      return NameProtectModule.protect(subtitle);
   }

   @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
   private Component champ$nameProtectActionBar(Component message) {
      return NameProtectModule.protect(message);
   }

   @ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
   private Component champ$nameProtectSidebarTitle(Component original) {
      return NameProtectModule.protect(original);
   }

   @ModifyExpressionValue(
      method = "displayScoreboardSidebar",
      at = @At(value = "FIELD", opcode = 180, ordinal = 0, target = "Lnet/minecraft/client/gui/Gui$1DisplayEntry;comp_2131:Lnet/minecraft/network/chat/Component;")
   )
   private Component champ$nameProtectSidebarLineWidth(Component original) {
      return NameProtectModule.protect(original);
   }

   @ModifyExpressionValue(
      method = "displayScoreboardSidebar",
      at = @At(value = "FIELD", opcode = 180, ordinal = 1, target = "Lnet/minecraft/client/gui/Gui$1DisplayEntry;comp_2131:Lnet/minecraft/network/chat/Component;")
   )
   private Component champ$nameProtectSidebarLineDraw(Component original) {
      return NameProtectModule.protect(original);
   }
}
