package net.champ.addon.module;

import net.champ.client.event.impl.TickEvent;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;

/**
 * Kleine add-on-laag boven ClientModule met shortcuts waar mód élke
 * addon-module hetzelfde patroon gebruikt: een tick-handler die alleen op
 * kommt tijdens `in-game` staat en een Veiluige active-reference.
 */
public abstract class ModuleBase extends ClientModule {
    private static volatile ModuleBase activeTick;

    protected ModuleBase(String id, String name, ModuleCategory category) {
        super(id, name, category);
    }

    protected ModuleBase(String id, String name, String desc, ModuleCategory category) {
        super(id, name, desc, category);
    }

    @Override protected void onEnable() {
        on((TickEvent.TYPE), ev -> tick());
        onEnableInternal();
    }

    @Override protected void onDisable() { onDisableInternal(); }

    protected void onEnableInternal() {}

    protected void onDisableInternal() {}

    /** Zonder game-tick (ui/inv-open) niet in breng — compat met client-timers. */
    protected final boolean inGame() {
        var p = net.champ.addon.util.Slots.player();
        return p != null && !p.isDeadOrDying();
    }

    protected abstract void tick();
}
