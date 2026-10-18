package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.champ.client.module.impl.misc.NoInvLeakModule;
import net.champ.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Gui.class)
public abstract class MixinNoInvLeakGui {
   @Unique
   private static final Identifier CHAMP$SELECTION = Identifier.withDefaultNamespace("hud/hotbar_selection");
   @Shadow
   private int field_2040;
   @Shadow
   private ItemStack field_2031;

   @ModifyVariable(method = "renderSlot", at = @At("HEAD"), argsOnly = true)
   private ItemStack champ$noInvLeakHotbar(
      ItemStack stack, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y, @Local(argsOnly = true, ordinal = 2) int seed
   ) {
      return NoInvLeakModule.hotbar(stack, x, y, seed);
   }

   @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
   private void champ$noInvLeakItemName(GuiGraphics guiGraphics, CallbackInfo ci) {
      if (NoInvLeakModule.hidesItemName()) {
         if (NoInvLeakModule.overlaying() && this.toolHighlightTimer > 0 && !this.lastToolHighlight.isEmpty()) {
            int alpha = Math.min(255, (int)(this.toolHighlightTimer * 256.0F / 10.0F));
            int y = guiGraphics.guiHeight() - 59;
            if (!Minecraft.getInstance().gameMode.canHurtPlayer()) {
               y += 14;
            }

            StreamOverlay.itemName(this.lastToolHighlight, alpha, y);
         }

         ci.cancel();
      }
   }

   @WrapWithCondition(
      method = "renderItemHotbar",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V")
   )
   private boolean champ$noInvLeakSelection(GuiGraphics guiGraphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height) {
      if (NoInvLeakModule.hidesSelectedSlot() && CHAMP$SELECTION.equals(sprite)) {
         if (NoInvLeakModule.overlaying()) {
            StreamOverlay.selection(x, y);
         }

         return false;
      } else {
         return true;
      }
   }
}
