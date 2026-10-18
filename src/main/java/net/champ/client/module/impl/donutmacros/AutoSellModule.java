package net.champ.client.module.impl.donutmacros;

import net.champ.client.chat.ModuleChat;
import net.champ.client.event.EventHandler;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.setting.StringListSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class AutoSellModule extends ClientModule {
   private static final int INVENTORY_SLOTS = 36;
   private static final int CONFIRM_SLOT = 53;
   private static final long POLL_MS = 350L;
   private static final long COMMAND_COOLDOWN_MS = 4000L;
   private static final long OPEN_TIMEOUT_MS = 10000L;
   private static final long CONFIRM_COOLDOWN_MS = 900L;
   private static final long POST_CONFIRM_MS = 250L;
   private static final int STUCK_CONFIRMS = 3;
   private final StringListSetting neverSell = this.addSetting(
      "never-sell", new StringListSetting("Never Sell", "minecraft:netherite_sword", "golden_apple", "minecraft:totem_of_undying").itemPicker()
   );
   private int menuId = -1;
   private long openingUntil;
   private long lastCommandAt;
   private long lastConfirmAt;
   private long nextPollAt;
   private boolean confirmPending;
   private String lastFingerprint = "";
   private int sameAfterConfirm;
   private int sold;
   private int confirmedCount;
   private boolean warnedNoOpen;

   public AutoSellModule() {
      super(
         "auto-sell", "Auto Sell", "Keeps the /sell menu open and keeps selling everything that is not on your Never Sell list.", ModuleCategory.DONUT_MACROS
      );
      this.usesNotifications();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   @Override
   protected void onEnable() {
      this.menuId = -1;
      this.openingUntil = 0L;
      this.lastCommandAt = 0L;
      this.lastConfirmAt = 0L;
      this.nextPollAt = 0L;
      this.confirmPending = false;
      this.lastFingerprint = "";
      this.sameAfterConfirm = 0;
      this.sold = 0;
      this.confirmedCount = 0;
      this.warnedNoOpen = false;
   }

   @Override
   protected void onDisable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null && this.ours(player)) {
         player.closeContainer();
      }

      this.menuId = -1;
      if (this.sold > 0) {
         ModuleChat.send(this, ModuleChat.body().text("Sold ").value(this.sold).text(this.sold == 1 ? " item." : " items."));
      }
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && mc.getConnection() != null && mc.gameMode != null && !player.isDeadOrDying()) {
         long now = System.currentTimeMillis();
         if (this.menuId < 0 && this.openingUntil > 0L) {
            if (player.containerMenu != player.inventoryMenu && mc.screen instanceof AbstractContainerScreen && containerSlots(player.containerMenu) > 53) {
               this.menuId = player.containerMenu.containerId;
               this.openingUntil = 0L;
               this.nextPollAt = now + 150L;
               this.warnedNoOpen = false;
            } else if (now > this.openingUntil) {
               this.openingUntil = 0L;
               if (!this.warnedNoOpen) {
                  ModuleChat.send(this, ModuleChat.body().warn("The sell menu did not open").text(", retrying."));
               }

               this.warnedNoOpen = true;
            }
         } else if (!this.ours(player)) {
            this.menuId = -1;
            if (mc.screen == null && now - this.lastCommandAt >= 4000L) {
               player.connection.sendCommand("sell");
               this.lastCommandAt = now;
               this.openingUntil = now + 10000L;
            }
         } else if (now >= this.nextPollAt) {
            this.nextPollAt = now + 350L;
            AbstractContainerMenu menu = player.containerMenu;
            if (this.confirmedCount > 0) {
               int gone = this.confirmedCount - countWaiting(menu);
               this.confirmedCount = 0;
               if (gone > 0) {
                  this.sold += gone;
                  ModuleChat.actionBar(this, ModuleChat.body().text("Sold ").value(this.sold).text(" items this session"));
               }
            }

            if (this.confirmPending) {
               this.confirmPending = false;
               if (waiting(menu) && now - this.lastConfirmAt >= 900L && this.sameAfterConfirm < 3) {
                  int before = countWaiting(menu);
                  mc.gameMode.handleInventoryMouseClick(menu.containerId, 53, 0, ClickType.PICKUP, player);
                  this.lastConfirmAt = now;
                  this.lastFingerprint = fingerprint(menu);
                  this.confirmedCount = before;
                  this.nextPollAt = now + 250L;
               }
            } else {
               int moved = 0;

               for (Slot slot : menu.slots) {
                  if (slot.container instanceof Inventory
                     && slot.hasItem()
                     && slot.getContainerSlot() < 36
                     && !SellRoutine.neverSell(slot.getItem(), this.neverSell.get())) {
                     mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, player);
                     moved++;
                  }
               }

               String fingerprint = fingerprint(menu);
               if (moved > 0 || !fingerprint.equals(this.lastFingerprint)) {
                  this.sameAfterConfirm = 0;
               } else if (waiting(menu)) {
                  this.sameAfterConfirm++;
               }

               if (moved > 0 || waiting(menu)) {
                  this.confirmPending = true;
                  this.nextPollAt = now + 50L;
               }
            }
         }
      } else {
         this.menuId = -1;
      }
   }

   private boolean ours(LocalPlayer player) {
      return this.menuId >= 0 && player.containerMenu != player.inventoryMenu && player.containerMenu.containerId == this.menuId;
   }

   private static boolean waiting(AbstractContainerMenu menu) {
      return countWaiting(menu) > 0;
   }

   private static int countWaiting(AbstractContainerMenu menu) {
      int count = 0;

      for (Slot slot : menu.slots) {
         if (!(slot.container instanceof Inventory) && slot.index != 53 && slot.hasItem()) {
            count += slot.getItem().getCount();
         }
      }

      return count;
   }

   private static int containerSlots(AbstractContainerMenu menu) {
      int count = 0;

      for (Slot slot : menu.slots) {
         if (!(slot.container instanceof Inventory)) {
            count++;
         }
      }

      return count;
   }

   private static String fingerprint(AbstractContainerMenu menu) {
      StringBuilder text = new StringBuilder();

      for (Slot slot : menu.slots) {
         if (slot.container instanceof Inventory) {
            break;
         }

         ItemStack stack = slot.getItem();
         if (!stack.isEmpty()) {
            text.append(slot.index).append(':').append(stack.getItem()).append('x').append(stack.getCount()).append(';');
         }
      }

      return text.toString();
   }
}
