package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.champ.client.module.impl.misc.NoInvLeakModule;
import net.champ.client.module.impl.misc.noinvleak.StreamOverlay;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_332;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(class_465.class)
public abstract class MixinNoInvLeakContainerScreen {
   @Shadow
   protected class_1735 field_2787;
   @Shadow
   protected int field_2776;
   @Shadow
   protected int field_2800;

   @Shadow
   public abstract class_1703 method_17577();

   @WrapOperation(method = "method_2385", at = @At(value = "INVOKE", target = "Lnet/minecraft/class_1735;method_7677()Lnet/minecraft/class_1799;"))
   private class_1799 champ$noInvLeakSlot(class_1735 slot, Operation<class_1799> original) {
      class_1799 stack = (class_1799)original.call(new Object[]{slot});
      return NoInvLeakModule.ownSlot(slot)
         ? NoInvLeakModule.container(stack, slot, this.field_2776 + slot.field_7873, this.field_2800 + slot.field_7872, slot == this.field_2787)
         : stack;
   }

   @ModifyVariable(method = "method_2382", at = @At("HEAD"), argsOnly = true)
   private class_1799 champ$noInvLeakCarried(class_1799 stack, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y) {
      return NoInvLeakModule.carried(stack, x, y);
   }

   @Inject(method = "method_2380", at = @At("HEAD"), cancellable = true)
   private void champ$noInvLeakTooltip(class_332 guiGraphics, int x, int y, CallbackInfo ci) {
      if (NoInvLeakModule.hidesTooltips() && NoInvLeakModule.ownSlot(this.field_2787)) {
         if (NoInvLeakModule.overlaying() && this.field_2787.method_7681() && this.method_17577().method_34255().method_7960()) {
            StreamOverlay.tooltip(this.field_2787.method_7677(), x, y);
         }

         ci.cancel();
      }
   }
}
