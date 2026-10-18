package net.champ.client;

import net.champ.client.addon.AddonManager;
import net.champ.client.config.HudTheme;
import net.champ.client.config.ModuleConfigStore;
import net.champ.client.config.ProfileAutoLoad;
import net.champ.client.config.ThemeStore;
import net.champ.client.esp.ChunkScanner;
import net.champ.client.event.impl.PacketAppliedEvent;
import net.champ.client.event.impl.PacketReceiveNettyEvent;
import net.champ.client.event.impl.Render2DEvent;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.gui.BorderlessFullscreen;
import net.champ.client.gui.HudLayoutStore;
import net.champ.client.gui.HudRenderer;
import net.champ.client.gui.ImGuiScreen;
import net.champ.client.gui.StaffGhost;
import net.champ.client.gui.ToggleToasts;
import net.champ.client.imgui.ImGuiManager;
import net.champ.client.input.ChampKeyPolling;
import net.champ.client.input.ModuleKeybinds;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.impl.misc.noinvleak.StreamOverlay;
import net.champ.client.util.BlockEntityLeakTracker;
import net.champ.client.util.combat.CombatTarget;
import net.champ.client.util.netherite.NetheriteChunkTracker;
import net.champ.client.util.playerbypass.PlayerBypassLightTracker;
import net.champ.client.util.playerbypass.ProtectedChunkStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.client.KeyMapping.Category;
import com.mojang.blaze3d.platform.InputConstants.Type;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ChampClientMod implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   public static final Identifier SPAWNER_NOTIFY_SOUND_ID = Identifier.fromNamespaceAndPath("champ", "spawner_notify");
   public static final SoundEvent SPAWNER_NOTIFY_SOUND = SoundEvent.createVariableRangeEvent(SPAWNER_NOTIFY_SOUND_ID);
   public static final Category KEY_CATEGORY = Category.register(Identifier.fromNamespaceAndPath("champ", "controls"));
   private static KeyMapping openGuiKey;
   private final ModuleKeybinds keybinds = new ModuleKeybinds();
   private static final Object LEAK_TRACKER_OWNER = new Object();
   private static final Object PLAYER_BYPASS_TRACKER_OWNER = new Object();
   private static final Object NETHERITE_TRACKER_OWNER = new Object();

   public void onInitializeClient() {
      LOGGER.info("[Champ Client] Initialising");
      AddonManager.init();
      new net.champ.addon.Addons().onInitializeClient();
      ImGuiManager.getInstance().setHudRenderer(HudRenderer.getInstance()::render, HudRenderer.getInstance()::hasWork);
      ImGuiManager.getInstance().setStreamRenderer(StreamOverlay::drawFakes, StreamOverlay::hasFakes);
      ImGuiManager.getInstance().setToastRenderer(ToggleToasts::render, ToggleToasts::hasWork);
      ImGuiManager.getInstance().setAlertRenderer(StaffGhost::render, StaffGhost::hasWork);
      Render2DEvent.TYPE.listen(StaffGhost.class, StaffGhost::renderNote);
      ChunkScanner.init();
      PacketReceiveNettyEvent.TYPE.listen(LEAK_TRACKER_OWNER, event -> BlockEntityLeakTracker.ingest(event.packet));
      PacketAppliedEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onPacketApplied(event.packet));
      TickEvent.TYPE.listen(PLAYER_BYPASS_TRACKER_OWNER, event -> PlayerBypassLightTracker.onTick());
      TickEvent.TYPE.listen(BorderlessFullscreen.class, event -> BorderlessFullscreen.tick());
      CombatTarget.install();
      PacketAppliedEvent.TYPE.listen(NETHERITE_TRACKER_OWNER, event -> NetheriteChunkTracker.onPacketApplied(event.packet));
      ToggleToasts.install();
      ProfileAutoLoad.install();
      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         HudLayoutStore.getInstance().save();
         ModuleConfigStore.getInstance().save();
         ThemeStore.get().save();
         HudTheme.get().save();
         ProtectedChunkStore.get().flushAndWait();
         StreamOverlay.stop();
      });
      openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.champ.open_gui", Type.KEYSYM, 344, KEY_CATEGORY));
      ModuleConfigStore.getInstance().load();
      AddonManager.configLoaded();
      ClientTickEvents.END_CLIENT_TICK
         .register(
            (EndTick)client -> {
               ModuleConfigStore.getInstance().applyPendingEnabled();

               while (openGuiKey.consumeClick()) {
                  if (client.screen instanceof ImGuiScreen) {
                     client.setScreen(null);
                  } else {
                     client.setScreen(new ImGuiScreen());
                  }
               }

               this.keybinds
                  .update(ModuleRegistry.all(), ChampClientMod::isBoundKeyDown, client.screen == null && client.isWindowActive() && client.player != null);
               ModuleConfigStore.getInstance().tickAutosave();
            }
         );
   }

   public static boolean isGuiKey(long window, KeyEvent event) {
      return openGuiKey != null && window == Minecraft.getInstance().getWindow().handle() && openGuiKey.matches(event);
   }

   public static boolean toggleGuiFromKey() {
      Minecraft client = Minecraft.getInstance();
      if (client.screen == null) {
         client.setScreen(new ImGuiScreen());
         return true;
      }

      if (client.screen instanceof ImGuiScreen gui && !gui.capturesKeys()) {
         client.setScreen(null);
      }

      return false;
   }

   public static void dropGuiClicks() {
      while (openGuiKey.consumeClick()) {
      }
   }

   private static boolean isBoundKeyDown(int key) {
      Minecraft client = Minecraft.getInstance();
      return key >= 1000
         ? GLFW.glfwGetMouseButton(client.getWindow().handle(), key - 1000) == 1
         : ChampKeyPolling.isKeyDown(client.getWindow(), key);
   }
}
