package net.champ.addon.spoof;

import net.champ.client.event.impl.PacketSendEvent;
import net.champ.client.event.impl.TickEvent;

/** Verbindt de engine met de client's event-bus (PacketSendEvent, pre-priority). */
final class ModuleEventWire {
    private static boolean bound;

    private ModuleEventWire() {}

    static void register(PacketSpoofEngine engine) {
        if (bound) return;
        bound = true;
        PacketSendEvent.TYPE.listen(engine, net.champ.client.event.EventPriority.HIGH, ev -> {
            engine.inspect(ev);
        });
        TickEvent.TYPE.listen(engine, net.champ.client.event.EventPriority.LOW, ev -> TickCounter.bump());
    }
}
