package com.example.client.mixin;

import net.champ.client.module.impl.movement.SneakModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(ServerboundInteractPacket.class)
public abstract class MixinSneakServerboundInteractPacket {
   @ModifyVariable(method = "<init>(IZLnet/minecraft/network/protocol/game/ServerboundInteractPacket$Action;)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
   private static boolean champ$packetSneak(boolean usingSecondaryAction) {
      if (usingSecondaryAction) {
         return true;
      }

      SneakModule sneak = SneakModule.active;
      return sneak != null && sneak.doPacket();
   }
}
