package net.champ.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;

@Environment(EnvType.CLIENT)
final class Gui {
   private static final int COMMAND_GAP_MS = 200;
   final MacroWorker worker;

   Gui(MacroWorker worker) {
      this.worker = worker;
   }

   static Minecraft mc() {
      return Minecraft.getInstance();
   }

   static LocalPlayer player() {
      LocalPlayer player = mc().player;
      if (player == null) {
         throw new IllegalStateException("Not in a world");
      } else {
         return player;
      }
   }

   String title() {
      return this.worker.call(() -> mc().screen == null ? "" : ServerText.normalize(mc().screen.getTitle()));
   }

   boolean containerOpen() {
      return this.worker.call(Gui::containerOpenNow);
   }

   static boolean containerOpenNow() {
      LocalPlayer player = mc().player;
      return player != null && player.containerMenu != player.inventoryMenu && mc().screen instanceof AbstractContainerScreen;
   }

   int syncId() {
      return this.worker.call(() -> player().containerMenu.containerId);
   }

   static List<ItemStack> containerNow() {
      List<ItemStack> out = new ArrayList<>();
      LocalPlayer player = mc().player;
      if (player != null && player.containerMenu != player.inventoryMenu) {
         for (Slot slot : player.containerMenu.slots) {
            if (slot.container instanceof Inventory) {
               break;
            }

            out.add(slot.getItem().copy());
         }

         return out;
      } else {
         return out;
      }
   }

   List<ItemStack> container() {
      return this.worker.call(Gui::containerNow);
   }

   static String fingerprintNow() {
      StringBuilder text = new StringBuilder();
      LocalPlayer player = mc().player;
      if (player != null) {
         text.append(player.containerMenu.containerId).append('#');
      }

      for (ItemStack stack : containerNow()) {
         text.append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
            .append(':')
            .append(stack.getCount())
            .append(':')
            .append(stack.getHoverName().getString())
            .append('|');
      }

      return text.toString();
   }

   String fingerprint() {
      return this.worker.call(Gui::fingerprintNow);
   }

   static ItemStack slotNow(int index) {
      List<ItemStack> stacks = containerNow();
      return index >= 0 && index < stacks.size() ? stacks.get(index) : ItemStack.EMPTY;
   }

   static int findNow(Predicate<ItemStack> test, int limit) {
      List<ItemStack> stacks = containerNow();

      for (int i = 0; i < stacks.size() && i < limit; i++) {
         if (!stacks.get(i).isEmpty() && test.test(stacks.get(i))) {
            return i;
         }
      }

      return -1;
   }

   static String nameOf(ItemStack stack) {
      return ServerText.normalize(stack.getHoverName());
   }

   void click(int slot, int button, ClickType type) {
      this.worker.run(() -> {
         LocalPlayer player = player();
         AbstractContainerMenu menu = player.containerMenu;
         mc().gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, player);
      });
   }

   void clickInventory(int slot, int button, ClickType type) {
      this.worker.run(() -> {
         LocalPlayer player = player();
         mc().gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, button, type, player);
      });
   }

   void closeInventory() {
      this.worker.run(() -> {
         LocalPlayer player = player();
         if (player.containerMenu == player.inventoryMenu) {
            player.connection.send(new ServerboundContainerClosePacket(player.inventoryMenu.containerId));
         }
      });
   }

   void command(String command) {
      this.worker.run(() -> player().connection.sendCommand(command));
      this.worker.sleep(200L);
   }

   void closeAll() {
      for (int i = 0; i < 4; i++) {
         boolean open = this.worker.call(() -> {
            LocalPlayer player = mc().player;
            if (player == null) {
               return false;
            } else if (player.containerMenu != player.inventoryMenu) {
               player.closeContainer();
               return true;
            } else if (mc().screen != null && !(mc().screen instanceof DeathScreen)) {
               mc().screen.onClose();
               return true;
            } else {
               return false;
            }
         });
         if (!open) {
            return;
         }

         this.worker.ticks(2);
      }
   }

   boolean waitChange(String before, long timeoutMs) {
      return this.worker.waitFor(() -> !fingerprintNow().equals(before), timeoutMs);
   }
}
