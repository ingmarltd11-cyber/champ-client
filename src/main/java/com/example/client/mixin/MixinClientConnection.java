package com.example.client.mixin;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.function.BooleanSupplier;
import net.champ.client.event.impl.PacketReceiveEvent;
import net.champ.client.event.impl.PacketReceiveNettyEvent;
import net.champ.client.event.impl.PacketSendEvent;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Connection.class)
public abstract class MixinClientConnection {
   @Unique
   private final BooleanSupplier champ$connectionActive = this::method_10758;

   @Shadow
   public abstract boolean method_10758();

   @Shadow
   public abstract PacketFlow method_36121();

   @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
   private void champ$onPacketReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
      if (this.getReceiving() == PacketFlow.CLIENTBOUND) {
         if (PacketReceiveNettyEvent.dispatchSync(packet)) {
            ci.cancel();
         } else {
            PacketReceiveEvent.dispatchAsync(packet, this.champ$connectionActive);
         }
      }
   }

   @Inject(method = "send", at = @At("HEAD"), cancellable = true)
   private void champ$onPacketSend(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      if (this.getReceiving() == PacketFlow.CLIENTBOUND) {
         if (PacketSendEvent.dispatchSync(packet)) {
            ci.cancel();
         } else {
            ServerRotation.noteSent(packet);
         }
      }
   }
}
