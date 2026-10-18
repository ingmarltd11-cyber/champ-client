package net.champ.client.module.impl.combat;

import com.example.client.mixin.AccessorCombatMinecraft;
import net.champ.client.event.EventHandler;
import net.champ.client.event.EventPriority;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class FastXpModule extends ClientModule {
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 3.0F).step(1.0F));

   public FastXpModule() {
      super("fastxp", "FastXP", "Throws experience bottles faster while use is held.", ModuleCategory.MISC);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.screen == null && mc.options.keyUse.isDown()) {
         if (!player.isUsingItem() && holdsXp(player)) {
            AccessorCombatMinecraft access = (AccessorCombatMinecraft)mc;
            int wait = access.champ$rightClickDelay();
            if (!ServerRotation.pending() && !MeleeAttacks.actedThisTick(player)) {
               if (wait > this.delay.getInt() + 1) {
                  access.champ$setRightClickDelay(this.delay.getInt() + 1);
               }
            } else {
               access.champ$setRightClickDelay(Math.max(wait, 2));
               KeyMapping use = mc.options.keyUse;

               while (use.consumeClick()) {
               }
            }
         }
      }
   }

   private static boolean holdsXp(LocalPlayer player) {
      ItemStack main = player.getMainHandItem();
      return main.is(Items.EXPERIENCE_BOTTLE)
         || player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE) && !(main.getItem() instanceof BlockItem);
   }
}
