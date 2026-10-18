package net.champ.addon.spoof;

import net.champ.addon.AllModules;
import net.champ.client.event.Stage;
import net.champ.client.event.impl.PacketSendEvent;
import net.champ.client.module.setting.BooleanSetting;
import net.champ.client.module.setting.ModeSetting;
import net.champ.client.module.setting.NumberSetting;

import java.util.Locale;

/**
 * Packet Spoofing.
 *
 * Scanleidden: <b>elk</b> uitgaand packet gaat hier doorheen vóórdat de client het
 * verstuurt (via het PacketSendEvent van de client zelf, HAALT uiteindelijk via
 * mixin net.champ.addon.mixin.MixinClientConnectionSpoof bij class_2596 dispatch).
 *
 * Wat het doet per typ packet:
 *  - ServerboundMovePlayer*: houdt hoek-verschillen menselijk (rotatie smoothing),
 *    normalisatie van pitch naar ±89.999°, en voorkomt "snap"-vlaggen van silent-aim.
 *  - ServerboundPlayerAction / Interact / UseItemOn: vlag-maximaal 1 actie per tick,
 *    voorkomt onmogelijk snelle opeenvolging (anti "impossible order" anticheat).
 *  - alles ignore: gewoon doorlaten.
 *
 * Modus: STRICT (smooth alles), AGGRESSIVE (alleen snap-interceptie), OFF.
 */
public final class PacketSpoofEngine {
    public static final PacketSpoofEngine get = new PacketSpoofEngine();

    public final ModeSetting mode = new ModeSetting("Spoof mode", "Strict", "Off", "Strict", "Aggressive");
    public final NumberSetting maxAngle = new NumberSetting("Max angle delta", 35F, 5F, 180F);
    public final NumberSetting maxActionsPerTick = new NumberSetting("Max actions per tick", 3F, 1F, 12F);
    public final BooleanSetting smoothSilentAim = new BooleanSetting("Smooth silent aim snaps", true);
    public final BooleanSetting normalizePitch = new BooleanSetting("Normalize pitch", true);

    // laatste gestuurde rotatie per packet-type (apart voor move/idle), gewoon августrtrait.
    private float lastYaw = Float.NaN, lastPitch = Float.NaN;

    private PacketSpoofEngine() {
        ModuleEventWire.register(this);
    }

    /** Hoofd-invoer: opgeroepen door packet-wire vóór verzending. */
    public boolean inspect(PacketSendEvent event) {
        String off = mode.get();
        if (("Off").equalsIgnoreCase(off)) return false;
        boolean aggressive = "Aggressive".equalsIgnoreCase(off);

        String cn = event.packet.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        boolean move = cn.contains("moveplayer");
        boolean action = cn.contains("playeraction") || cn.contains("interact")
                || cn.contains("useitem") || cn.contains("swing");
        if (!move && !action) return false;

        if (move) {
            if (normalizePitch.get()) {
                float p = PitchUtil.peekPitch(event.packet);
                if (!Float.isNaN(p)) {
                    float fixed = Math.max(-89.9F, Math.min(89.9F, p));
                    if (fixed != p) { PitchUtil.setPitch(event.packet, fixed); }
                }
            }
            if (SilentAim.active()) {
                PitchUtil.setYaw(event.packet, SilentAim.yaw());
                PitchUtil.setPitch(event.packet, SilentAim.pitch());
                SilentAim.consume();
            }
            if (smoothSilentAim.get()) {
                float y = PitchUtil.peekYaw(event.packet);
                if (!Float.isNaN(y) && !Float.isNaN(lastYaw)) {
                    float delta = Math.abs(wrapDegrees(y - lastYaw));
                    if (delta > maxAngle.get() && !aggressive) {
                        float smooth = lastYaw + Math.signum(wrapDegrees(y - lastYaw)) * maxAngle.get();
                        PitchUtil.setYaw(event.packet, smooth);
                    } else if (delta > 5.0F * maxAngle.get()) {
                        // hard: breken helt, gewel snoep hij vast — cancel dit packet.
                        event.setCancelled(true);
                        return true;
                    }
                }
                if (!Float.isNaN(y)) { lastYaw = y; lastPitch = PitchUtil.peekPitch(event.packet); }
            }
        } else {
            int tick = (int) TickCounter.actions();
            if (tick > maxActionsPerTick.get()) {
                event.setCancelled(true);
                return true;
            }
            TickCounter.markAction();
        }
        return false;
    }

    static float wrapDegrees(float d) {
        d %= 360.0F;
        if (d >= 180.0F) d -= 360.0F;
        if (d < -180.0F) d += 360.0F;
        return d;
    }
}
