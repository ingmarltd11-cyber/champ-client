package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.champ.client.module.impl.misc.NoInvLeakModule;
import net.champ.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class MixinNoInvLeakContainerScreen {
   @Shadow
   protected Slot hoveredSlot;
   @Shadow
   protected int leftPos;
   @Shadow
   protected int topPos;

   @Shadow
   public abstract AbstractContainerMenu getMenu();

   @WrapOperation(method = "renderSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
   private ItemStack champ$noInvLeakSlot(Slot slot, Operation<ItemStack> original) {
      ItemStack stack = (ItemStack)original.call(new Object[]{slot});
      return NoInvLeakModule.ownSlot(slot)
         ? NoInvLeakModule.container(stack, slot, this.leftPos + slot.x, this.topPos + slot.y, slot == this.hoveredSlot)
         : stack;
   }

   @ModifyVariable(method = "renderFloatingItem", at = @At("HEAD"), argsOnly = true)
   private ItemStack champ$noInvLeakCarried(ItemStack stack, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
      return NoInvLeakModule.carried(stack, x, y);
   }

   @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
   private void champ$noInvLeakTooltip(GuiGraphics guiGraphics, int x, int y, CallbackInfo ci) {
      if (NoInvLeakModule.hidesTooltips() && NoInvLeakModule.ownSlot(this.hoveredSlot)) {
         if (NoInvLeakModule.overlaying() && this.hoveredSlot.hasItem() && this.getMenu().getCarried().isEmpty()) {
            StreamOverlay.tooltip(this.hoveredSlot.getItem(), x, y);
         }

         ci.cancel();
      }
   }
}
