package net.champ.addon.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;


/** Doelwit-helpers: dichtstbijzijnde enemy + kijkhoeken, in client-stijl. */
public final class Targets {
    private Targets() {}

    private static Player cacheOwner;
    private static int cacheTick = Integer.MIN_VALUE;
    private static double cacheRange = -1;
    private static Player cacheResult;

    /**
     * Dichtstbijzijnde andere levende speler binnen range (bol), of null.
     * Loopt over de speler-lijst van het level (klein) en cachet het resultaat per tick,
     * zodat meerdere modules in dezelfde tick niet elk opnieuw zoeken.
     */
    public static Player nearestEnemy(double range) {
        Player self = Slots.player();
        var level = Slots.mc().level;
        if (self == null || level == null) return null;
        if (cacheOwner == self && cacheTick == self.tickCount && cacheRange == range) return cacheResult;
        double best = range * range;
        Player nearest = null;
        for (Player p : level.players()) {
            if (p == self || !p.isAlive() || p.isSpectator()) continue;
            double d = p.distanceToSqr(self);
            if (d <= best) { best = d; nearest = p; }
        }
        cacheOwner = self; cacheTick = self.tickCount; cacheRange = range; cacheResult = nearest;
        return nearest;
    }

    /** {yaw, pitch} in graden vanaf eye-positie naar doel. */
    public static float[] anglesTo(Vec3 eye, Vec3 to) {
        double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(-dx, dz) * 57.295780F);
        float pitch = (float) (Math.atan2(-dy, distXZ) * 57.295780F);
        return new float[]{yaw, pitch};
    }

    public static Vec3 eye(Player p) { return p.getEyePosition(); }

    private static volatile java.lang.reflect.Field fallField;

    /** Val-afstand (fallDistance is protected in Entity: via reflectie). */
    public static float fallDistance(Player p) {
        try {
            java.lang.reflect.Field f = fallField;
            if (f == null) {
                f = findField(p.getClass(), "fallDistance");
                fallField = f;
            }
            // fallDistance is float of double afhankelijk van de versie: via Number lezen.
            if (f != null && f.get(p) instanceof Number n) return n.floatValue();
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {}
        return 0F;
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            try {
                java.lang.reflect.Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
