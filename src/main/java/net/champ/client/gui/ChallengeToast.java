package net.champ.client.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;

@Environment(EnvType.CLIENT)
public class ChallengeToast implements Toast {
   private static final int BACKGROUND = -266921698;
   private static final int TITLE_COLOR = -986891;
   private static final int MESSAGE_COLOR = -5656902;
   private static final int COUNT_COLOR = -6643540;
   private static final int TIMER_TRACK = 654311423;
   private static final int TIMER_FILL = -1711276033;
   private static final int TEXT_X = 30;
   private static final int TEXT_PAD = 7;
   private static final int RIGHT_GAP = 3;
   private static final int MAX_WIDTH = 240;
   private static final long DISPLAY_MILLIS = 5000L;
   private final Object token;
   private final ItemStack icon;
   private final boolean sound;
   private Component title;
   private Component message;
   private int width;
   private int count = 1;
   private boolean changed;
   private boolean hiding;
   private long changedAt;
   private Visibility wanted = Visibility.HIDE;

   public ChallengeToast(ItemStack icon, Component title, Component message, boolean sound) {
      this(field_2208, icon, title, message, sound);
   }

   private ChallengeToast(Object token, ItemStack icon, Component title, Component message, boolean sound) {
      this.token = token;
      this.icon = icon.copy();
      this.title = title;
      this.message = message;
      this.sound = sound;
      this.fit();
   }

   public static void addOrUpdate(ItemStack icon, String title, Component message, boolean sound) {
      Minecraft mc = Minecraft.getInstance();
      if (!mc.isSameThread()) {
         mc.execute(() -> addOrUpdate(icon, title, message, sound));
      } else {
         ToastManager toasts = mc.getToastManager();
         ChallengeToast toast = (ChallengeToast)toasts.getToast(ChallengeToast.class, title);
         if (toast == null) {
            toasts.addToast(new ChallengeToast(title, icon, Component.literal(title), message, sound));
         } else {
            toast.count++;
            toast.message = message;
            toast.changed = true;
            toast.fit();
         }
      }
   }

   private String countLabel() {
      return this.count > 1 ? " x" + this.count : "";
   }

   private void fit() {
      Font font = Minecraft.getInstance().font;
      int text = Math.max(font.width(this.title) + font.width(this.countLabel()), font.width(this.message));
      this.width = Mth.clamp(30 + text + 7, 160, 240);
   }

   public Object method_1987() {
      return this.token;
   }

   public SoundEvent method_66530() {
      return this.sound ? (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.comp_349() : null;
   }

   public Visibility method_61988() {
      return this.wanted;
   }

   public int method_29049() {
      return this.width;
   }

   public void method_61989(ToastManager toasts, long visibleFor) {
      if (!this.hiding) {
         if (this.changed) {
            this.changedAt = visibleFor;
            this.changed = false;
         }

         this.hiding = visibleFor - this.changedAt >= 5000.0 * toasts.getNotificationDisplayTimeMultiplier();
         this.wanted = this.hiding ? Visibility.HIDE : Visibility.SHOW;
      }
   }

   public void method_71995() {
      if (this.changed) {
         this.hiding = false;
         Minecraft.getInstance().getToastManager().addToast(this);
      }
   }

   public void method_1986(GuiGraphics graphics, Font font, long visibleFor) {
      int right = this.width - 3;
      int bottom = 31;
      rounded(graphics, 0, 1, right, bottom, -266921698);
      this.drawTimer(graphics, visibleFor, right, bottom);
      boolean single = this.message.getString().isEmpty();
      this.drawText(graphics, font, this.title, this.countLabel(), single ? 12 : 7, -986891);
      if (!single) {
         this.drawText(graphics, font, this.message, "", 18, -5656902);
      }

      graphics.renderFakeItem(this.icon, 8, 8);
   }

   private void drawTimer(GuiGraphics graphics, long visibleFor, int right, int bottom) {
      double duration = 5000.0 * Minecraft.getInstance().getToastManager().getNotificationDisplayTimeMultiplier();
      float left = 1.0F - (float)Mth.clamp((visibleFor - this.changedAt) / duration, 0.0, 1.0);
      int x0 = 4;
      int x1 = right - 4;
      int y = bottom - 2;
      graphics.fill(x0, y, x1, y + 1, 654311423);
      graphics.fill(x0, y, x0 + Math.round((x1 - x0) * left), y + 1, -1711276033);
   }

   private static void rounded(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
      graphics.fill(x0 + 2, y0, x1 - 2, y0 + 1, color);
      graphics.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, color);
      graphics.fill(x0, y0 + 2, x1, y1 - 2, color);
      graphics.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, color);
      graphics.fill(x0 + 2, y1 - 1, x1 - 2, y1, color);
   }

   private void drawText(GuiGraphics graphics, Font font, Component text, String suffix, int y, int color) {
      int room = this.width - 30 - 7;
      int textWidth = font.width(text) + font.width(suffix);
      graphics.pose().pushMatrix();
      graphics.pose().translate(30.0F, y);
      if (textWidth > room) {
         graphics.pose().scale((float)room / textWidth, 1.0F);
      }

      graphics.drawString(font, text, 0, 0, color, true);
      if (!suffix.isEmpty()) {
         graphics.drawString(font, suffix, font.width(text), 0, -6643540, true);
      }

      graphics.pose().popMatrix();
   }
}
