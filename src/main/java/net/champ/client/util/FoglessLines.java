package net.champ.client.util;

import com.example.client.mixin.AccessorGameRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_12179;
import net.minecraft.class_12180;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_3532;
import net.minecraft.class_4184;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public final class FoglessLines {
   private static final double NEAR = 0.05;
   private final class_243 eye;
   private final Vector3fc forward;
   private final double pixelPerDepth;
   private class_12179 lastFill;
   private int lastArgb;

   private FoglessLines(class_243 eye, Vector3fc forward, double pixelPerDepth) {
      this.eye = eye;
      this.forward = forward;
      this.pixelPerDepth = pixelPerDepth;
   }

   public static FoglessLines forFrame(float partialTick) {
      class_4184 camera = class_310.method_1551().field_1773.method_19418();
      return new FoglessLines(camera.method_71156(), camera.method_19335(), pixelScale(partialTick));
   }

   public static double pixelScale(float partialTick) {
      class_310 mc = class_310.method_1551();
      class_4184 camera = mc.field_1773.method_19418();
      float fov = ((AccessorGameRenderer)mc.field_1773).champ$getFov(camera, partialTick, true);
      return 2.0 * Math.tan(Math.toRadians(fov) * 0.5) / Math.max(1, mc.method_22683().method_4506());
   }

   public static FoglessLines forView(class_243 eye, Vector3fc forward, double pixelPerDepth) {
      return new FoglessLines(eye, forward, pixelPerDepth);
   }

   public void line(class_243 from, class_243 to, int argb, float width) {
      if (MobilePerformance.active()) {
         this.scalarLine(from.field_1352, from.field_1351, from.field_1350, to.field_1352, to.field_1351, to.field_1350, argb, width);
      } else {
         double depthFrom = this.depth(from);
         double depthTo = this.depth(to);
         if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
            if (depthFrom < 0.05) {
               from = from.method_35590(to, (0.05 - depthFrom) / (depthTo - depthFrom));
               depthFrom = 0.05;
            } else if (depthTo < 0.05) {
               to = to.method_35590(from, (0.05 - depthTo) / (depthFrom - depthTo));
               depthTo = 0.05;
            }

            class_243 side = from.method_1020(this.eye).method_1036(to.method_1020(from));
            double length = side.method_1033();
            if (!(length < 1.0E-9)) {
               double scale = width * 0.5 * this.pixelPerDepth / length;
               class_243 atFrom = side.method_1021(scale * depthFrom);
               class_243 atTo = side.method_1021(scale * depthTo);
               class_12180.method_75547(
                     from.method_1019(atFrom), to.method_1019(atTo), to.method_1020(atTo), from.method_1020(atFrom), class_12179.method_75539(argb)
                  )
                  .method_75533();
            }
         }
      }
   }

   public void line(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int argb, float width) {
      if (MobilePerformance.active()) {
         this.scalarLine(fromX, fromY, fromZ, toX, toY, toZ, argb, width);
      } else {
         this.line(new class_243(fromX, fromY, fromZ), new class_243(toX, toY, toZ), argb, width);
      }
   }

   private void scalarLine(double fromX, double fromY, double fromZ, double toX, double toY, double toZ, int argb, float width) {
      double depthFrom = this.depth(fromX, fromY, fromZ);
      double depthTo = this.depth(toX, toY, toZ);
      if (!(depthFrom < 0.05) || !(depthTo < 0.05)) {
         if (depthFrom < 0.05) {
            double amount = (0.05 - depthFrom) / (depthTo - depthFrom);
            fromX = class_3532.method_16436(amount, fromX, toX);
            fromY = class_3532.method_16436(amount, fromY, toY);
            fromZ = class_3532.method_16436(amount, fromZ, toZ);
            depthFrom = 0.05;
         } else if (depthTo < 0.05) {
            double amount = (0.05 - depthTo) / (depthFrom - depthTo);
            toX = class_3532.method_16436(amount, toX, fromX);
            toY = class_3532.method_16436(amount, toY, fromY);
            toZ = class_3532.method_16436(amount, toZ, fromZ);
            depthTo = 0.05;
         }

         double ax = fromX - this.eye.field_1352;
         double ay = fromY - this.eye.field_1351;
         double az = fromZ - this.eye.field_1350;
         double bx = toX - fromX;
         double by = toY - fromY;
         double bz = toZ - fromZ;
         double sx = ay * bz - az * by;
         double sy = az * bx - ax * bz;
         double sz = ax * by - ay * bx;
         double length = Math.sqrt(sx * sx + sy * sy + sz * sz);
         if (!(length < 1.0E-9)) {
            double scale = width * 0.5 * this.pixelPerDepth / length;
            double startScale = scale * depthFrom;
            double endScale = scale * depthTo;
            double fx = sx * startScale;
            double fy = sy * startScale;
            double fz = sz * startScale;
            double tx = sx * endScale;
            double ty = sy * endScale;
            double tz = sz * endScale;
            if (this.lastFill == null || this.lastArgb != argb) {
               this.lastFill = class_12179.method_75539(argb);
               this.lastArgb = argb;
            }

            class_12180.method_75547(
                  new class_243(fromX + fx, fromY + fy, fromZ + fz),
                  new class_243(toX + tx, toY + ty, toZ + tz),
                  new class_243(toX - tx, toY - ty, toZ - tz),
                  new class_243(fromX - fx, fromY - fy, fromZ - fz),
                  this.lastFill
               )
               .method_75533();
         }
      }
   }

   public void box(class_238 box, int argb, float width) {
      double x0 = box.field_1323;
      double y0 = box.field_1322;
      double z0 = box.field_1321;
      double x1 = box.field_1320;
      double y1 = box.field_1325;
      double z1 = box.field_1324;
      if (MobilePerformance.active()) {
         for (int level = 0; level < 2; level++) {
            double y = level == 0 ? y0 : y1;
            this.scalarLine(x0, y, z0, x1, y, z0, argb, width);
            this.scalarLine(x1, y, z0, x1, y, z1, argb, width);
            this.scalarLine(x1, y, z1, x0, y, z1, argb, width);
            this.scalarLine(x0, y, z1, x0, y, z0, argb, width);
         }

         this.scalarLine(x0, y0, z0, x0, y1, z0, argb, width);
         this.scalarLine(x1, y0, z0, x1, y1, z0, argb, width);
         this.scalarLine(x1, y0, z1, x1, y1, z1, argb, width);
         this.scalarLine(x0, y0, z1, x0, y1, z1, argb, width);
      } else {
         for (double y : new double[]{y0, y1}) {
            this.line(new class_243(x0, y, z0), new class_243(x1, y, z0), argb, width);
            this.line(new class_243(x1, y, z0), new class_243(x1, y, z1), argb, width);
            this.line(new class_243(x1, y, z1), new class_243(x0, y, z1), argb, width);
            this.line(new class_243(x0, y, z1), new class_243(x0, y, z0), argb, width);
         }

         this.line(new class_243(x0, y0, z0), new class_243(x0, y1, z0), argb, width);
         this.line(new class_243(x1, y0, z0), new class_243(x1, y1, z0), argb, width);
         this.line(new class_243(x1, y0, z1), new class_243(x1, y1, z1), argb, width);
         this.line(new class_243(x0, y0, z1), new class_243(x0, y1, z1), argb, width);
      }
   }

   private double depth(class_243 point) {
      return (point.field_1352 - this.eye.field_1352) * this.forward.x()
         + (point.field_1351 - this.eye.field_1351) * this.forward.y()
         + (point.field_1350 - this.eye.field_1350) * this.forward.z();
   }

   private double depth(double x, double y, double z) {
      return (x - this.eye.field_1352) * this.forward.x() + (y - this.eye.field_1351) * this.forward.y() + (z - this.eye.field_1350) * this.forward.z();
   }
}
