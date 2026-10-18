package net.champ.client.module.impl.client;

import imgui.ImDrawList;
import imgui.ImFont;
import java.util.Locale;
import net.champ.client.esp.ChunkScanner;
import net.champ.client.event.impl.Render3DEvent;
import net.champ.client.gui.ChampWidgets;
import net.champ.client.imgui.ImGuiManager;
import net.champ.client.module.ClientModule;
import net.champ.client.module.HudOverlayProvider;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.ModuleRegistry;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.MobilePerformance;
import net.champ.client.util.PerformanceBudget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public final class PerformanceModule extends ClientModule implements HudOverlayProvider {
   private static volatile boolean optimizing;
   private static volatile boolean mobileEffectsOptimizing;
   private final BooleanSetting optimize = this.addSetting("optimize-client", new BooleanSetting("Optimize Client", true));
   private final BooleanSetting mobileEffects = this.addSetting("mobile-effects", new BooleanSetting("Optimize Mobile Effects", true));
   private final BooleanSetting showStats = this.addSetting("show-stats", new BooleanSetting("Show Stats", true));
   private final BooleanSetting frustumCulling = this.addSetting("frustum-culling", new BooleanSetting("Frustum Culling", true));
   private final NumberSetting scanBudget = this.addSetting("scan-budget-ms", new NumberSetting("Scan Budget ms", 2.0F, 0.5F, 8.0F).step(0.5F));
   private long smoothedNanos;
   private long nextStatsUpdate;
   private String statsText = "";

   public PerformanceModule() {
      super(
         "performance",
         "Performance",
         "Reduces background work and image memory while keeping effects and features. Disable to compare FPS.",
         ModuleCategory.CLIENT
      );
      this.optimize.onChange(this::updateOptimizationState);
      this.mobileEffects.onChange(this::updateOptimizationState);
      this.mobileEffects.visibleWhen(() -> MobilePerformance.isAndroid() && this.optimize.get());
   }

   public static boolean optimizationsEnabled() {
      return optimizing;
   }

   public static boolean mobileEffectsEnabled() {
      return mobileEffectsOptimizing;
   }

   @Override
   public boolean enabledByDefault() {
      return MobilePerformance.isAndroid();
   }

   @Override
   protected void onEnable() {
      this.updateOptimizationState();
      this.nextStatsUpdate = 0L;
   }

   @Override
   protected void onDisable() {
      optimizing = false;
      mobileEffectsOptimizing = false;
   }

   private void updateOptimizationState() {
      optimizing = this.isEnabled() && this.optimize.get();
      mobileEffectsOptimizing = optimizing && this.mobileEffects.get();
      this.nextStatsUpdate = 0L;
   }

   public static boolean frustumCulling() {
      PerformanceModule module = ModuleRegistry.get(PerformanceModule.class);
      return module == null || module.frustumCulling.get();
   }

   public static long scanBudgetNanos() {
      PerformanceModule module = ModuleRegistry.get(PerformanceModule.class);
      return module == null ? 2000000L : (long)(module.scanBudget.get() * 1000000.0F);
   }

   @Override
   public boolean isHudOverlayEnabled() {
      return this.isEnabled() && this.showStats.get();
   }

   @Override
   public void renderHudOverlay(ImDrawList dl, float screenW, float screenH) {
      this.smoothedNanos = this.smoothedNanos + (Render3DEvent.lastDurationNanos() - this.smoothedNanos) / 8L;
      long now = System.nanoTime();
      if (now >= this.nextStatsUpdate) {
         String scans = optimizationsEnabled() ? String.format(Locale.ROOT, "  ·  scans %.2f ms", PerformanceBudget.lastTickNanos() / 1000000.0) : "";
         this.statsText = String.format(
            Locale.ROOT,
            "%s  ·  %d FPS  ·  3D CPU %.2f ms%s  ·  queue %d",
            optimizationsEnabled() ? (MobilePerformance.active() ? "Mobile Optimized" : "Optimized") : "Standard",
            Minecraft.getInstance().getFps(),
            this.smoothedNanos / 1000000.0,
            scans,
            ChunkScanner.pending()
         );
         this.nextStatsUpdate = now + 250000000L;
      }

      String text = this.statsText;
      ImFont font = ImGuiManager.getInstance().hudLabelFont();
      float x = screenW - ChampWidgets.textWidth(font, text) - 8.0F;
      float y = screenH - ChampWidgets.lineHeight(font) - 6.0F;
      ChampWidgets.text(dl, font, x + 1.0F, y + 1.0F, -1610612736, text);
      ChampWidgets.text(dl, font, x, y, -3618616, text);
   }
}
