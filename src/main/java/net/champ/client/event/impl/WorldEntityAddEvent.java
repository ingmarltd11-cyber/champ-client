package net.champ.client.event.impl;

import net.champ.client.event.Event;
import net.champ.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;

@Environment(EnvType.CLIENT)
public final class WorldEntityAddEvent extends Event {
   public static final EventType<WorldEntityAddEvent> TYPE = new EventType<>("WorldEntityAddEvent");
   public Entity entity;
   private static final WorldEntityAddEvent INSTANCE = new WorldEntityAddEvent();

   private WorldEntityAddEvent() {
   }

   public static void dispatch(Entity entity) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.entity = entity;
         TYPE.dispatch(INSTANCE);
         INSTANCE.entity = null;
      }
   }
}
