package net.champ.client.module.impl.misc;

import com.sun.jna.Platform;
import net.champ.client.config.ModuleConfigStore;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.misc.noinvleak.FakeLoadout;
import net.champ.client.module.impl.misc.noinvleak.StreamOverlay;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ModeSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1304;
import net.minecraft.class_1661;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;
import net.minecraft.class_746;

@Environment(EnvType.CLIENT)
public final class NoInvLeakModule extends ClientModule {
   private static final String STYLE_TOTEM = "Totem";
   private static final String STYLE_RANDOM = "Random";
   private static final String STYLE_EMPTY = "Empty";
   private static final int OFFHAND_SEED = 10;
   private static volatile NoInvLeakModule active;
   private static class_1799 totem;
   private final ModeSetting style = this.addSetting("style", new ModeSetting("Style", "Totem", "Totem", "Random", "Empty"));
   private final BooleanSetting fillEmpty = this.addSetting("fill-empty", new BooleanSetting("Fill Empty Slots", true));
   private final BooleanSetting streamOnly = this.addSetting("stream-only", new BooleanSetting("Stream Only", false));
   private final BooleanSetting borderless = this.addSetting("borderless-fullscreen", new BooleanSetting("Borderless Fullscreen", true));
   private final BooleanSetting tooltips = this.addSetting("hide-tooltips", new BooleanSetting("Hide Tooltips", true));
   private final BooleanSetting itemName = this.addSetting("hide-item-name", new BooleanSetting("Hide Item Name", true));
   private final BooleanSetting selectedSlot = this.addSetting("hide-selected-slot", new BooleanSetting("Hide Selected Slot", true));
   private final BooleanSetting hud = this.addSetting("champ-hud", new BooleanSetting("Champ Client HUD", true));
   private final FakeLoadout loadout = new FakeLoadout();

   public NoInvLeakModule() {
      super("no-inv-leak", "No Inv Leak", "Hides your items on screen for screen shares. Stream Only keeps them visible to you alone.", ModuleCategory.MISC);
      this.fillEmpty.visibleWhen(() -> !this.style.is("Empty"));
      this.streamOnly.visibleWhen(Platform::isWindows);
      this.streamOnly.onChange(() -> {
         if (this.streamOnly.get()) {
            StreamOverlay.retry();
         } else {
            StreamOverlay.stop();
         }
      });
      this.borderless.visibleWhen(() -> Platform.isWindows() && this.streamOnly.get());
   }

   public static boolean wantsBorderless() {
      NoInvLeakModule module = ModuleRegistry.get(NoInvLeakModule.class);
      return module != null && module.streamOnly.get() && module.borderless.get()
         ? module.isEnabled() || ModuleConfigStore.getInstance().enablesOnLoad(module)
         : false;
   }

   @Override
   protected void onEnable() {
      this.loadout.reseed();
      active = this;
   }

   @Override
   protected void onDisable() {
      active = null;
      StreamOverlay.stop();
   }

   public static class_1799 hotbar(class_1799 stack, int x, int y, int seed) {
      NoInvLeakModule module = active;
      if (module != null && stack != null) {
         int slot = seed == 10 ? 40 : (seed >= 1 && seed <= 9 ? seed - 1 : 41);
         if (module.streamOverlaying()) {
            StreamOverlay.hotbar(slot, x, y, stack);
            return class_1799.field_8037;
         } else {
            return module.shown(stack, slot);
         }
      } else {
         return stack;
      }
   }

   public static class_1799 container(class_1799 stack, class_1735 slot, int x, int y, boolean hovered) {
      NoInvLeakModule module = active;
      if (module == null || stack == null) {
         return stack;
      } else if (module.streamOverlaying()) {
         StreamOverlay.container(slot.method_34266(), x, y, stack, hovered);
         return class_1799.field_8037;
      } else {
         return module.shown(stack, slot.method_34266());
      }
   }

   public static class_1799 carried(class_1799 stack, int x, int y) {
      NoInvLeakModule module = active;
      if (module == null || stack == null || stack.method_7960()) {
         return stack;
      } else if (module.streamOverlaying()) {
         StreamOverlay.carried(stack, x, y);
         return class_1799.field_8037;
      } else {
         return module.fake(stack, 41);
      }
   }

   public static class_1799 shownInHud(class_1799 stack, class_1304 slot) {
      NoInvLeakModule module = active;
      return module != null && module.hud.get() && stack != null && !stack.method_7960() ? module.fake(stack, inventorySlot(slot)) : stack;
   }

   public static class_1799 fakeFor(int slot) {
      NoInvLeakModule module = active;
      return module == null ? class_1799.field_8037 : module.fake(class_1799.field_8037, slot);
   }

   public static boolean fillsEmpty() {
      NoInvLeakModule module = active;
      return module != null && module.fillEmpty.get() && !module.style.is("Empty");
   }

   public static boolean overlaying() {
      NoInvLeakModule module = active;
      return module != null && module.streamOnly.get() && StreamOverlay.usable();
   }

   private boolean streamOverlaying() {
      return this.streamOnly.get() && StreamOverlay.usable();
   }

   public static boolean ownSlot(class_1735 slot) {
      return active != null && slot != null && slot.field_7871 instanceof class_1661;
   }

   public static boolean hidesTooltips() {
      NoInvLeakModule module = active;
      return module != null && module.tooltips.get();
   }

   public static boolean hidesItemName() {
      NoInvLeakModule module = active;
      return module != null && module.itemName.get();
   }

   public static boolean hidesSelectedSlot() {
      NoInvLeakModule module = active;
      return module != null && module.selectedSlot.get();
   }

   public static boolean overlayWanted() {
      NoInvLeakModule module = active;
      return module != null && module.streamOnly.get();
   }

   public static boolean opaqueCells() {
      NoInvLeakModule module = active;
      return module != null && !module.style.is("Empty");
   }

   private class_1799 shown(class_1799 stack, int slot) {
      return stack.method_7960() && !this.fillEmpty.get() ? stack : this.fake(stack, slot);
   }

   private class_1799 fake(class_1799 stack, int slot) {
      if (this.style.is("Empty")) {
         return class_1799.field_8037;
      }

      if (this.style.is("Random")) {
         return this.loadout.get(slot);
      }

      class_1799 stand = totem;
      if (stand == null) {
         totem = stand = new class_1799(class_1802.field_8288);
      }

      return stand;
   }

   private static int inventorySlot(class_1304 slot) {
      return switch (slot) {
         case field_6169 -> 39;
         case field_6174 -> 38;
         case field_6172 -> 37;
         case field_6166 -> 36;
         case field_6171 -> 40;
         case field_6173 -> {
            class_746 player = class_310.method_1551().field_1724;
            yield player != null ? player.method_31548().method_67532() : 41;
         }
         default -> 41;
      };
   }
}
