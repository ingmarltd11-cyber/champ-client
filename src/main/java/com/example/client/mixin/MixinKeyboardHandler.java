package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.champ.client.ChampClientMod;
import net.champ.client.event.impl.KeyInputEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(KeyboardHandler.class)
public abstract class MixinKeyboardHandler {
   @WrapMethod(method = "keyPress")
   private void champ$openGuiFirst(long window, int action, KeyEvent event, Operation<Void> original) {
      boolean guiKey = action != 0 && ChampClientMod.isGuiKey(window, event);
      if (!guiKey || action != 1 || !ChampClientMod.toggleGuiFromKey()) {
         original.call(new Object[]{window, action, event});
         if (guiKey) {
            ChampClientMod.dropGuiClicks();
         }
      }
   }

   @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
   private void champ$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
      if (KeyInputEvent.dispatch(event.comp_4795(), event.comp_4796(), action, event.comp_4797())) {
         ci.cancel();
      }
   }
}
