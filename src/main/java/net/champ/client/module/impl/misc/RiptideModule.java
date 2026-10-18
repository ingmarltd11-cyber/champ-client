package net.champ.client.module.impl.misc;

import net.champ.client.event.EventHandler;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1268;
import net.minecraft.class_310;
import net.minecraft.class_746;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class RiptideModule extends ClientModule {
   private int releasedTick = -100;

   public RiptideModule() {
      super("riptide", "Riptide", "Uses a riptide trident while you hold right-click.", ModuleCategory.DONUT);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      class_310 mc = class_310.method_1551();
      class_746 player = mc.field_1724;
      if (player != null && mc.field_1761 != null && mc.field_1755 == null) {
         if (GLFW.glfwGetMouseButton(mc.method_22683().method_4490(), 1) == 1) {
            if (GrimRiptide.clientAccepts()) {
               class_1268 hand = hand(player);
               if (hand != null) {
                  if (!player.method_6115()) {
                     if (player.field_6012 - this.releasedTick >= 9) {
                        mc.field_1761.method_2919(player, hand);
                     }
                  } else if (GrimRiptide.isRiptide(player.method_6030())) {
                     if (player.method_6048() >= chargeTicks()) {
                        if (player.field_6012 - this.releasedTick >= 9) {
                           mc.field_1761.method_2897(player);
                           this.releasedTick = player.field_6012;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int chargeTicks() {
      return Math.max(9, TridentUtilModule.minChargeTicks(10));
   }

   private static class_1268 hand(class_746 player) {
      if (GrimRiptide.isRiptide(player.method_6047())) {
         return class_1268.field_5808;
      } else {
         return GrimRiptide.isRiptide(player.method_6079()) ? class_1268.field_5810 : null;
      }
   }
}
