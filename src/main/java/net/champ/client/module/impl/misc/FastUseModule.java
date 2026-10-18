package net.champ.client.module.impl.misc;

import com.example.client.mixin.AccessorCombatMinecraft;
import net.champ.client.event.EventHandler;
import net.champ.client.event.EventPriority;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class FastUseModule extends ClientModule {
   private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 3.0F).step(1.0F));
   private final BooleanSetting blocks = this.addSetting("blocks", new BooleanSetting("Blocks", true));
   private final BooleanSetting items = this.addSetting("items", new BooleanSetting("Items", true));

   public FastUseModule() {
      super("fast-use", "Fast Use", "Places blocks and uses items faster while use is held.", ModuleCategory.MISC);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.screen == null && mc.options.keyUse.isDown()) {
         if (!player.isUsingItem() && this.covers(player)) {
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

   private boolean covers(LocalPlayer player) {
      ItemStack held = player.getMainHandItem();
      if (held.isEmpty()) {
         held = player.getOffhandItem();
      }

      if (held.isEmpty()) {
         return false;
      } else {
         return held.getItem() instanceof BlockItem ? this.blocks.get() : this.items.get();
      }
   }
}
