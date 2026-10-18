package net.champ.addon.spoof;

import net.champ.client.event.Stage;
import net.champ.client.event.impl.TickEvent;

/** Telt acties per gametick voor de "max actions per tick"-vlag van de spoofing-engine. */
public final class TickCounter {
    private static volatile int tick;
    private static volatile int actionsThisTick;
    private static volatile long lastTick, actionTick;
    private static long actionCount;

    private TickCounter() {}

    static void bump() {
        long now = System.nanoTime();
        if (now - lastTick > 50_000_000L) { tick++; lastTick = now; actionsThisTick = 0; }
    }

    public static int actions() {
        bump();
        return actionsThisTick;
    }

    public static void markAction() {
        bump();
        actionsThisTick++;
        actionTick = tick;
        actionCount++;
    }

    public static long total() { return actionCount; }
    public static int currentTick() { return tick; }
}
