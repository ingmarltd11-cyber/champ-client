package net.champ.client.module.impl.combat;

import com.example.client.mixin.AccessorElytraFlyMultiPlayerGameMode;
import com.example.client.mixin.AccessorLocalPlayer;
import net.champ.client.event.EventHandler;
import net.champ.client.event.EventPriority;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.combat.totem.DoubleHand;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.pvp.InputControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;

@Environment(EnvType.CLIENT)
public final class AutoTotemModule extends ClientModule {
   private static final int MIN_CONFIRM_TICKS = 10;
   private static final int MAX_CONFIRM_TICKS = 40;
   private static final int GIVE_UP_COOLDOWN_TICKS = 10;
   private static AutoTotemModule instance;
   private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
   private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));
   private final NumberSetting delay = this.addSetting("reaction-delay", new NumberSetting("Delay", 0.0F, 0.0F, 10.0F).step(1.0F));
   private final BooleanSetting doubleHand = this.addSetting("double-hand", new BooleanSetting("Double Hand", false));
   private final DoubleHand totemHand = new DoubleHand();
   private int tick;
   private int reactionAt = -1;
   private int idleUntil;
   private int confirmDeadline = -1;
   private int clickedAt = -1;
   private boolean settling;
   private int heldSlot = -1;
   private int previousSlot = -1;

   public AutoTotemModule() {
      super(
         "autototem",
         "Auto Totem",
         "Fills an empty offhand with a totem without opening the inventory; Double Hand also keeps one in the main hand while in danger.",
         ModuleCategory.CRYSTAL
      );
      instance = this;
      this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
   }

   public static boolean wantsHotbar() {
      AutoTotemModule module = instance;
      return module != null && module.isEnabled() && (module.heldSlot != -1 || module.totemHand.holding());
   }

   public static boolean busy() {
      AutoTotemModule module = instance;
      LocalPlayer player = Minecraft.getInstance().player;
      if (module != null && module.isEnabled() && player != null && !player.isSpectator() && !player.hasInfiniteMaterials()) {
         Inventory inventory = player.getInventory();
         return wantsHotbar()
            || module.awaitingServer(player)
            || module.clickedAt == module.tick
            || player.getOffhandItem().isEmpty() && (findStoredTotem(inventory) != -1 || findHotbarTotem(inventory) != -1);
      } else {
         return false;
      }
   }

   @Override
   protected void onEnable() {
      AutoInvTotemModule other = ModuleRegistry.get(AutoInvTotemModule.class);
      if (other != null) {
         other.setEnabled(false);
      }

      this.idleUntil = 0;
   }

   @Override
   protected void onDisable() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         Inventory inventory = player.getInventory();
         if (this.heldSlot != -1) {
            this.restoreHeld(inventory);
         }

         this.totemHand.putBack(inventory);
      }

      this.reset();
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onTick(TickEvent event) {
      this.tick++;
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      boolean settled = this.settling;
      this.settling = false;
      if (player != null
         && mc.level != null
         && mc.gameMode != null
         && mc.getConnection() != null
         && !player.isDeadOrDying()
         && !player.isSpectator()
         && !player.hasInfiniteMaterials()) {
         if (this.heldSlot != -1) {
            this.restoreHeld(player.getInventory());
         }

         this.totemHand.tick(mc, player, this.doubleHand.get());
         if (!player.getOffhandItem().isEmpty()) {
            this.reactionAt = -1;
            this.confirmDeadline = -1;
            if (this.hotbarRefill.get()) {
               this.refillHotbar(mc, player);
            }
         } else if (!this.awaitingServer(player)) {
            if (this.reactionAt < 0) {
               this.reactionAt = this.tick + this.delay.getInt();
            }

            if (this.tick >= this.reactionAt) {
               this.refillOffhand(mc, player, settled);
            }
         }
      } else {
         this.reset();
      }
   }

   private void refillOffhand(Minecraft mc, LocalPlayer player, boolean settled) {
      Inventory inventory = player.getInventory();
      int stored = findStoredTotem(inventory);
      if (stored != -1 && AutoInvTotemModule.clickSafe(player)) {
         click(mc, player, stored, 40);
         this.clickedAt = this.tick;
      } else if (mc.screen == null) {
         int hotbar = findHotbarTotem(inventory);
         if (hotbar != -1) {
            this.swapOffhand(mc, player, hotbar);
         } else if (stored != -1 && !settled && this.tick >= this.idleUntil && !((AccessorLocalPlayer)player).champ$lastSentInput().comp_3164()) {
            InputControl.idle();
            this.settling = true;
         } else if (settled) {
            this.idleUntil = this.tick + 10;
         }
      }
   }

   private void refillHotbar(Minecraft mc, LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int target = this.hotbarSlot.getInt() - 1;
      if (!inventory.getItem(target).is(Items.TOTEM_OF_UNDYING) && AutoInvTotemModule.clickSafe(player)) {
         int stored = findStoredTotem(inventory);
         if (stored != -1) {
            click(mc, player, stored, target);
            this.clickedAt = this.tick;
         }
      }
   }

   private void swapOffhand(Minecraft mc, LocalPlayer player, int slot) {
      ClientPacketListener connection = mc.getConnection();
      Inventory inventory = player.getInventory();
      int previous = inventory.getSelectedSlot();
      inventory.setSelectedSlot(slot);
      ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).champ$elytraFlySyncSelected();
      connection.send(new ServerboundPlayerActionPacket(Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
      this.confirmDeadline = this.tick + confirmWindow(connection, player);
      if (slot != previous) {
         if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            this.heldSlot = slot;
            this.previousSlot = previous;
         } else {
            inventory.setSelectedSlot(previous);
         }
      }
   }

   private void restoreHeld(Inventory inventory) {
      if (inventory.getSelectedSlot() == this.heldSlot) {
         inventory.setSelectedSlot(this.previousSlot);
      }

      this.heldSlot = -1;
      this.previousSlot = -1;
   }

   private static void click(Minecraft mc, LocalPlayer player, int inventorySlot, int button) {
      AbstractContainerMenu menu = player.containerMenu;
      if (menu.getCarried().isEmpty()) {
         ((AccessorElytraFlyMultiPlayerGameMode)mc.gameMode).champ$elytraFlySyncSelected();
         menu.findSlot(player.getInventory(), inventorySlot)
            .ifPresent(slot -> mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, ClickType.SWAP, player));
      }
   }

   private static int findStoredTotem(Inventory inventory) {
      for (int i = 9; i < 36; i++) {
         if (inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return -1;
   }

   private static int findHotbarTotem(Inventory inventory) {
      int selected = inventory.getSelectedSlot();

      for (int i = 0; i < 9; i++) {
         if (i != selected && inventory.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
            return i;
         }
      }

      return inventory.getItem(selected).is(Items.TOTEM_OF_UNDYING) ? selected : -1;
   }

   private static int confirmWindow(ClientPacketListener connection, LocalPlayer player) {
      PlayerInfo info = connection.getPlayerInfo(player.getUUID());
      int latencyMs = info != null ? info.getLatency() : 0;
      return Mth.clamp(latencyMs / 25 + 4, 10, 40);
   }

   private boolean awaitingServer(LocalPlayer player) {
      return this.confirmDeadline >= 0 && this.tick < this.confirmDeadline && !player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
   }

   private void reset() {
      this.reactionAt = -1;
      this.confirmDeadline = -1;
      this.settling = false;
      this.heldSlot = -1;
      this.previousSlot = -1;
      this.totemHand.reset();
   }
}
