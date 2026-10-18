package net.champ.addon.util;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Draait de camera vloeiend naar een punt (ease-out), zodat het er als normaal kijken uitziet. */
public final class LookAt {
    private LookAt() {}

    /** Draait maximaal maxStep graden per tick; true zodra de camera (bijna) op het punt staat. */
    public static boolean smooth(Player p, Vec3 target, float maxStep) {
        float[] a = Targets.anglesTo(Targets.eye(p), target);
        float dyaw = Mth.wrapDegrees(a[0] - p.getYRot());
        float dpitch = a[1] - p.getXRot();
        float sy = ease(dyaw, maxStep), sp = ease(dpitch, maxStep);
        p.setYRot(p.getYRot() + sy);
        p.setXRot(Mth.clamp(p.getXRot() + sp, -90F, 90F));
        return Math.abs(dyaw - sy) < 4F && Math.abs(dpitch - sp) < 4F;
    }

    private static float ease(float delta, float max) {
        float abs = Math.abs(delta);
        if (abs <= 3F) return delta;
        return Math.copySign(Math.min(max, abs * 0.5F + 1.5F), delta);
    }
}
