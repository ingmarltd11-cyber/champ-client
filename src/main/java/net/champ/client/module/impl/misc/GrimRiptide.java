package net.champ.client.module.impl.misc;

import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_1887;
import net.minecraft.class_1893;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2680;
import net.minecraft.class_310;
import net.minecraft.class_3612;
import net.minecraft.class_6880;

@Environment(EnvType.CLIENT)
public final class GrimRiptide {
   public static final int MIN_TICKS = 9;

   private GrimRiptide() {
   }

   public static boolean clientAccepts() {
      class_310 mc = class_310.method_1551();
      class_1657 player = mc.field_1724;
      if (player != null && mc.field_1687 != null && mc.method_18854()) {
         if (!player.method_5721() && !mc.field_1687.method_8419()) {
            class_238 box = player.method_5829().method_1014(0.1);
            class_2338 min = class_2338.method_49637(box.field_1323, box.field_1322, box.field_1321);
            class_2338 max = class_2338.method_49637(box.field_1320, box.field_1325, box.field_1324);

            for (class_2338 pos : class_2338.method_10097(min, max)) {
               class_2680 state = mc.field_1687.method_8320(pos);
               if (state.method_26227().method_39360(class_3612.field_15910)) {
                  return true;
               }

               if (state.method_26227().method_39360(class_3612.field_15909)) {
                  return true;
               }
            }

            return false;
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   public static boolean isRiptide(class_1799 stack) {
      if (!stack.method_31574(class_1802.field_8547)) {
         return false;
      }

      for (Entry<class_6880<class_1887>> entry : stack.method_58657().method_57539()) {
         class_6880<class_1887> enchantment = (class_6880<class_1887>)entry.getKey();
         if (enchantment.method_40225(class_1893.field_9104) && entry.getIntValue() > 0) {
            return true;
         }
      }

      return false;
   }
}
