package net.champ.addon.spoof;

/**
 * Gedeelde silent-aim state. Modules vragen hier een rotatie aan voor N ticks;
 * PacketSpoofEngine schrijft die op uitgaande move-packets, zodat de speler
 * zelf nooit zichtbaar snapt (anti-flag tegen silent-aim detectie).
 */
public final class SilentAim {
    private static volatile float yaw;
    private static volatile float pitch;
    private static volatile int ticks;

    private SilentAim() {}

    public static void request(float yawDeg, float pitchDeg, int tickCount) {
        yaw = yawDeg;
        pitch = pitchDeg;
        ticks = Math.max(ticks, tickCount);
    }

    public static void clear() { ticks = 0; }

    public static boolean active() { return ticks > 0; }

    public static float yaw() { return yaw; }

    public static float pitch() { return pitch; }

    public static void consume() { if (ticks > 0) ticks--; }
}
