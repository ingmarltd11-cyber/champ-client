package com.example.client.mixin;

import net.champ.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardInput.class)
public abstract class MixinSneakKeyboardInput {
   @Inject(method = "tick", at = @At("TAIL"))
   private void champ$forceSneak(CallbackInfo ci) {
      SneakModule sneak = SneakModule.active;
      if (sneak != null && sneak.doVanilla()) {
         ClientInput input = (ClientInput)this;
         Input keys = input.keyPresses;
         if (!keys.comp_3164()) {
            input.keyPresses = new Input(
               keys.comp_3159(), keys.comp_3160(), keys.comp_3161(), keys.comp_3162(), keys.comp_3163(), true, keys.comp_3165()
            );
         }
      }
   }
}
