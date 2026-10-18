package net.champ.client.module.impl.render;

import net.champ.client.event.Stage;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

@Environment(EnvType.CLIENT)
public final class TimeChangerModule extends ClientModule {
   public static volatile TimeChangerModule active;
   private final NumberSetting time = this.addSetting("time", new NumberSetting("Time", 0.0F, -20000.0F, 20000.0F).step(1.0F));
   private ClientLevel trackedLevel;
   private long serverDayTime;
   private long appliedDayTime;

   public TimeChangerModule() {
      super("time-changer", "Time Changer", "Makes you able to set a custom time.", ModuleCategory.RENDER);
   }

   @Override
   protected void onEnable() {
      active = this;
      this.on(TickEvent.TYPE, Stage.POST, this::onPostTick);
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         this.applyForcedTime(level);
      }
   }

   @Override
   protected void onDisable() {
      active = null;
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && level == this.trackedLevel) {
         writeDayTime(level, this.currentServerDayTime(level));
      }

      this.trackedLevel = null;
   }

   private void onPostTick(TickEvent event) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null) {
         this.trackedLevel = null;
      } else {
         this.applyForcedTime(level);
      }
   }

   private void applyForcedTime(ClientLevel level) {
      this.serverDayTime = level == this.trackedLevel ? this.currentServerDayTime(level) : level.getDayTime();
      this.trackedLevel = level;
      this.appliedDayTime = this.forcedDayTime();
      writeDayTime(level, this.appliedDayTime);
   }

   private static void writeDayTime(ClientLevel level, long dayTime) {
      if (level.getDayTime() != dayTime) {
         level.getLevelData().setDayTime(dayTime);
         level.environmentAttributes().invalidateTickCache();
      }
   }

   private long currentServerDayTime(ClientLevel level) {
      return this.serverDayTime + (level.getDayTime() - this.appliedDayTime);
   }

   public long interceptServerDayTime(ClientLevel level, long dayTime) {
      this.trackedLevel = level;
      this.serverDayTime = dayTime;
      this.appliedDayTime = this.forcedDayTime();
      return this.appliedDayTime;
   }

   private long forcedDayTime() {
      return (long)this.time.get();
   }
}
