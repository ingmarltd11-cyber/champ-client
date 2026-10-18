package net.champ.client.module.impl.combat;

import java.util.concurrent.ThreadLocalRandom;
import net.champ.client.event.EventHandler;
import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.impl.combat.melee.MeleeAttacks;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.NumberSetting;
import net.champ.client.util.combat.CombatTarget;
import net.champ.client.util.pvp.Rotation;
import net.champ.client.util.pvp.RotationUtils;
import net.champ.client.util.pvp.TargetingUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.client.player.LocalPlayer;

@Environment(EnvType.CLIENT)
public final class AimAssistModule extends ClientModule {
   private static final long LAST_HIT_MS = 1500L;
   private static final double STICK_RANGE = 0.5;
   private static final double STICK_FOV = 1.3;
   private static final long MOUSE_IDLE_MS = 150L;
   private static final double[] STILL = new double[]{0.0, 0.0};
   private final NumberSetting range = this.addSetting("range", new NumberSetting("Range", 4.0F, 3.0F, 6.0F).step(0.1F));
   private final NumberSetting fov = this.addSetting("fov", new NumberSetting("FOV", 60.0F, 10.0F, 180.0F).step(5.0F));
   private final NumberSetting speed = this.addSetting("speed", new NumberSetting("Speed", 60.0F, 10.0F, 180.0F).step(5.0F));
   private final BooleanSetting weaponsOnly = this.addSetting("weapons-only", new BooleanSetting("Weapons Only", true));
   private final BooleanSetting vertical = this.addSetting("vertical", new BooleanSetting("Vertical", true));
   private Player target;
   private double pace;
   private double pendingYaw;
   private double pendingPitch;
   private long movedAt;
   private double userYawSign;
   private double userPitchSign;

   public AimAssistModule() {
      super("aim-assist", "Aim Assist", "Eases your crosshair onto the nearest enemy player while you move the mouse.", ModuleCategory.COMBAT);
   }

   @Override
   public int settingsVersion() {
      return 1;
   }

   @Override
   public void migrateSettings(int savedVersion) {
      if (savedVersion < 1) {
         this.speed.set(60.0F);
      }
   }

   @Override
   protected void onDisable() {
      this.follow(null);
   }

   @EventHandler
   public void onTick(TickEvent event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (MeleeAttacks.canAct(mc)
         && !player.isUsingItem()
         && !mc.options.keyUse.isDown()
         && (!this.weaponsOnly.get() || MeleeAttacks.isMeleeWeapon(player.getMainHandItem()))) {
         this.follow(this.choose(mc, player));
      } else {
         this.follow(null);
      }
   }

   public double[] nudge(LocalPlayer player, double userYaw, double userPitch, double seconds) {
      long now = Util.getMillis();
      if (userYaw != 0.0 || userPitch != 0.0) {
         this.movedAt = now;
      }

      if (userYaw != 0.0) {
         this.userYawSign = Math.signum(userYaw);
      }

      if (userPitch != 0.0) {
         this.userPitchSign = Math.signum(userPitch);
      }

      Rotation want = this.target != null && now - this.movedAt <= 150L ? this.aim(player, player.getEyePosition(), core(this.target)) : null;
      if (want == null) {
         this.pendingYaw = 0.0;
         this.pendingPitch = 0.0;
         this.userYawSign = 0.0;
         this.userPitchSign = 0.0;
         return STILL;
      }

      double dt = Math.min(seconds, 0.05);
      float yawError = Mth.wrapDegrees(want.yaw() - player.getYRot());
      float pitchError = this.vertical.get() ? want.pitch() - player.getXRot() : 0.0F;
      if (this.userYawSign * yawError < 0.0) {
         yawError = 0.0F;
      }

      if (this.userPitchSign * pitchError < 0.0) {
         pitchError = 0.0F;
      }

      double cap = this.speed.get() * this.pace * dt;
      double ease = 1.0 - Math.exp(-12.0 * dt);
      double k = RotationUtils.mouseScale(Minecraft.getInstance(), player);
      double degreesPerCount = (float)k * 0.15F;
      this.pendingYaw = this.pendingYaw + Mth.clamp(yawError * ease, -cap, cap) / degreesPerCount;
      this.pendingPitch = this.pendingPitch + Mth.clamp(pitchError * ease * 0.6, -cap, cap) / degreesPerCount;
      long yawCounts = (long)this.pendingYaw;
      long pitchCounts = (long)this.pendingPitch;
      this.pendingYaw -= yawCounts;
      this.pendingPitch -= pitchCounts;
      return new double[]{yawCounts * k, pitchCounts * k};
   }

   private void follow(Player next) {
      if (next != this.target) {
         this.target = next;
         this.pace = ThreadLocalRandom.current().nextDouble(0.85, 1.15);
         this.pendingYaw = 0.0;
         this.pendingPitch = 0.0;
      }
   }

   private Player choose(Minecraft mc, LocalPlayer player) {
      Vec3 eye = player.getEyePosition();
      double halfFov = this.fov.get() / 2.0;
      if (CombatTarget.lastHit(1500L) instanceof Player hit && this.offAngle(player, eye, hit, this.range.get()) <= halfFov) {
         return hit;
      } else {
         if (this.target != null
            && this.target.level() == player.level()
            && this.offAngle(player, eye, this.target, this.range.get() + 0.5) <= halfFov * 1.3) {
            return this.target;
         }

         Player best = null;
         double bestAngle = halfFov;

         for (Player candidate : mc.level.players()) {
            double angle = this.offAngle(player, eye, candidate, this.range.get());
            if (!(angle > bestAngle)) {
               best = candidate;
               bestAngle = angle;
            }
         }

         return best;
      }
   }

   private double offAngle(LocalPlayer player, Vec3 eye, Player candidate, double reach) {
      if (MeleeAttacks.isEnemy(player, candidate) && !candidate.isInvisible()) {
         if (eye.distanceTo(TargetingUtils.closestPoint(eye, candidate.getBoundingBox())) > reach) {
            return Double.MAX_VALUE;
         }

         AABB core = core(candidate);
         if (!MeleeAttacks.lineIsClear(player, eye, core.getCenter())) {
            return Double.MAX_VALUE;
         }

         Rotation want = this.aim(player, eye, core);
         return want == null ? 0.0 : RotationUtils.angleTo(RotationUtils.entityRotation(player), want);
      } else {
         return Double.MAX_VALUE;
      }
   }

   private Rotation aim(LocalPlayer player, Vec3 eye, AABB core) {
      Vec3 view = player.getViewVector(1.0F);
      if (core.clip(eye, eye.add(view.scale(this.range.get() + 1.0))).isPresent()) {
         return null;
      }

      Vec3 near = TargetingUtils.closestPoint(eye.add(view.scale(eye.distanceTo(core.getCenter()))), core);
      return RotationUtils.fromTo(eye, near);
   }

   private static AABB core(Player player) {
      AABB box = player.getBoundingBox();
      return box.deflate(box.getXsize() * 0.25, box.getYsize() * 0.1, box.getXsize() * 0.25);
   }
}
