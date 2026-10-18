package com.example.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.champ.client.module.impl.misc.NameProtectModule;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public abstract class MixinNameProtectClientPacketListener {
   @Inject(method = "handlePlayerInfoUpdate", at = @At("TAIL"))
   private void champ$nameProtectPlayerList(ClientboundPlayerInfoUpdatePacket packet, CallbackInfo ci) {
      NameProtectModule.onPlayerInfoApplied(packet);
   }

   @ModifyExpressionValue(method = "handlePlayerCombatKill", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;message()Lnet/minecraft/network/chat/Component;"))
   private Component champ$nameProtectDeathMessage(Component message) {
      return NameProtectModule.protect(message);
   }
}
