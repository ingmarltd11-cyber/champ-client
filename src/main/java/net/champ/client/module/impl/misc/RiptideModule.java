package net.champ.client.module.impl.misc;

import net.champ.client.event.EventHandler;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public final class RiptideModule extends ClientModule {
   private int releasedTick = -100;

   public RiptideModule() {
      super("riptide", "Riptide", "Uses a riptide trident while you hold right-click.", ModuleCategory.DONUT);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.gameMode != null && mc.screen == null) {
         if (GLFW.glfwGetMouseButton(mc.getWindow().handle(), 1) == 1) {
            if (GrimRiptide.clientAccepts()) {
               InteractionHand hand = hand(player);
               if (hand != null) {
                  if (!player.isUsingItem()) {
                     if (player.tickCount - this.releasedTick >= 9) {
                        mc.gameMode.useItem(player, hand);
                     }
                  } else if (GrimRiptide.isRiptide(player.getUseItem())) {
                     if (player.getTicksUsingItem() >= chargeTicks()) {
                        if (player.tickCount - this.releasedTick >= 9) {
                           mc.gameMode.releaseUsingItem(player);
                           this.releasedTick = player.tickCount;
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

   private static InteractionHand hand(LocalPlayer player) {
      if (GrimRiptide.isRiptide(player.getMainHandItem())) {
         return InteractionHand.MAIN_HAND;
      } else {
         return GrimRiptide.isRiptide(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
      }
   }
}
