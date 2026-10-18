package com.example.client.mixin;

import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.misc.SlowSwingModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1309;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(class_1309.class)
public abstract class MixinLivingEntity {
   @Inject(method = "method_6028", at = @At("RETURN"), cancellable = true)
   private void champ$modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
      SlowSwingModule module = ModuleRegistry.get(SlowSwingModule.class);
      if (module != null && module.isEnabled()) {
         if (this == class_310.method_1551().field_1724) {
            cir.setReturnValue(module.getModifiedSwingDuration((Integer)cir.getReturnValue()));
         }
      }
   }
}
