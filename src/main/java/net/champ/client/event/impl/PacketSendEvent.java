package net.champ.client.event.impl;

import net.champ.client.event.CancellableEvent;
import net.champ.client.event.PooledCancellableEventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_2596;

@Environment(EnvType.CLIENT)
public final class PacketSendEvent extends CancellableEvent {
   public static final PooledCancellableEventType<PacketSendEvent> TYPE = new PooledCancellableEventType<>("PacketSendEvent", PacketSendEvent::new, 1024);
   public class_2596<?> packet;

   private PacketSendEvent() {
   }

   public static boolean dispatchSync(class_2596<?> packet) {
      if (!TYPE.hasPreListeners()) {
         return false;
      }

      PacketSendEvent event = TYPE.pool.acquire();
      event.packet = packet;
      event.resetCancellation();

      try {
         TYPE.dispatch(event);
         return event.isCancelled();
      } finally {
         event.packet = null;
         TYPE.pool.release(event);
      }
   }
}
