package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.champ.client.module.impl.render.TimeChangerModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientLevel.ClientLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Environment(EnvType.CLIENT)
@Mixin(ClientLevel.class)
public abstract class MixinTimeChangerClientLevel {
   @WrapOperation(method = "setTimeFromServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel$ClientLevelData;setDayTime(J)V"))
   private void champ$timeChangerServerDayTime(ClientLevelData levelData, long dayTime, Operation<Void> original) {
      TimeChangerModule timeChanger = TimeChangerModule.active;
      if (timeChanger == null) {
         original.call(new Object[]{levelData, dayTime});
      } else {
         ClientLevel level = (ClientLevel)this;
         long shown = levelData.getDayTime();
         long forced = timeChanger.interceptServerDayTime(level, dayTime);
         original.call(new Object[]{levelData, forced});
         if (forced != shown) {
            level.environmentAttributes().invalidateTickCache();
         }
      }
   }
}
