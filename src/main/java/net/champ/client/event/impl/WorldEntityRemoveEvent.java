package net.champ.client.event.impl;

import net.champ.client.event.Event;
import net.champ.client.event.EventType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1297;

@Environment(EnvType.CLIENT)
public final class WorldEntityRemoveEvent extends Event {
   public static final EventType<WorldEntityRemoveEvent> TYPE = new EventType<>("WorldEntityRemoveEvent");
   public class_1297 entity;
   private static final WorldEntityRemoveEvent INSTANCE = new WorldEntityRemoveEvent();

   private WorldEntityRemoveEvent() {
   }

   public static void dispatch(class_1297 entity) {
      if (TYPE.hasPreListeners()) {
         INSTANCE.entity = entity;
         TYPE.dispatch(INSTANCE);
         INSTANCE.entity = null;
      }
   }
}
