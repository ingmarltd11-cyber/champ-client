package net.champ.addon.spoof;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * Leest/schrijft yaw & pitch op Packets die die bevatten (ServerboundMovePlayerPacket-Rot/PosRot),
 * zonder afhankelijk te zijn van een specifieke mapper: eerst de aangemaakte accessors
 * (AccessorServerboundMovePlayerPacket van de client), anders gewone reflectie op de velden.
 */
final class PitchUtil {
    private static Field yawField, pitchField;

    private PitchUtil() {}

    static float peekYaw(Object packet) {
        Field f = yaw(packet.getClass());
        if (f == null) return Float.NaN;
        try { return (Float) f.get(packet); } catch (Exception e) { return Float.NaN; }
    }

    static float peekPitch(Object packet) {
        Field f = pitch(packet.getClass());
        if (f == null) return Float.NaN;
        try { return (Float) f.get(packet); } catch (Exception e) { return Float.NaN; }
    }

    static void setYaw(Object packet, float yaw) {
        Field f = yaw(packet.getClass());
        if (f == null) return;
        try { f.set(packet, yaw); } catch (Exception ignored) {}
    }

    static void setPitch(Object packet, float pitch) {
        Field f = pitch(packet.getClass());
        if (f == null) return;
        try { f.set(packet, pitch); } catch (Exception ignored) {}
    }

    private static Field yaw(Class<?> c) {
        if (yawField == null) yawField = findFirstFloat(c, "yRot", "field_12881", "yaw");
        return yawField;
    }

    private static Field pitch(Class<?> c) {
        if (pitchField == null) pitchField = findFirstFloat(c, "xRot", "field_12886", "pitch");
        return pitchField;
    }

    private static Field findFirstFloat(Class<?> c, String... names) {
        for (Field f : c.getDeclaredFields()) {
            if (f.getType() == float.class) {
                for (String n : names) if (f.getName().equals(n)) { f.setAccessible(true); return f; }
            }
        }
        // fallback: eerste float-veld dat op "rot" lijkt
        for (Field f : c.getDeclaredFields()) {
            if (f.getType() == float.class && f.getName().toLowerCase().contains("rot")) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}
