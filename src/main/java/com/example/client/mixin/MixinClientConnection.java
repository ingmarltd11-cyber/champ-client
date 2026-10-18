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
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2598;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(class_2535.class)
public abstract class MixinClientConnection {
   @Unique
   private final BooleanSupplier champ$connectionActive = this::method_10758;

   @Shadow
   public abstract boolean method_10758();

   @Shadow
   public abstract class_2598 method_36121();

   @Inject(method = "method_10770", at = @At("HEAD"), cancellable = true)
   private void champ$onPacketReceive(ChannelHandlerContext ctx, class_2596<?> packet, CallbackInfo ci) {
      if (this.method_36121() == class_2598.field_11942) {
         if (PacketReceiveNettyEvent.dispatchSync(packet)) {
            ci.cancel();
         } else {
            PacketReceiveEvent.dispatchAsync(packet, this.champ$connectionActive);
         }
      }
   }

   @Inject(method = "method_52906", at = @At("HEAD"), cancellable = true)
   private void champ$onPacketSend(class_2596<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
      if (this.method_36121() == class_2598.field_11942) {
         if (PacketSendEvent.dispatchSync(packet)) {
            ci.cancel();
         } else {
            ServerRotation.noteSent(packet);
         }
      }
   }
}
