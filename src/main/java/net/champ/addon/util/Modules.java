package net.champ.addon.util;

import net.champ.client.module.ClientModule;


/**
 * Zoekt sibling-modules uit de client op id, zonder harde compile-koppeling
 * aan registry-methodenamen (alleen ClientModule#setEnabled/isEnabled/id,
 * die stabiel in de client-API zitten).
 */
public final class Modules {
    private Modules() {}

    private static final java.util.Map<String, ClientModule> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Eerste module met dit id, of null als onbekend/niet geladen. Resultaat wordt gecachet. */
    public static ClientModule findById(String id) {
        ClientModule hit = CACHE.get(id);
        if (hit != null) return hit;
        ClientModule found = net.champ.client.module.ModuleRegistry.resolve(id);
        if (found == null) {
            for (ClientModule m : net.champ.client.module.ModuleRegistry.all()) {
                if (m.id().equalsIgnoreCase(id)) { found = m; break; }
            }
        }
        if (found != null) CACHE.put(id, found);
        return found;
    }

    public static boolean setEnabled(String id, boolean state) {
        ClientModule mod = findById(id);
        if (mod == null) return false;
        mod.setEnabled(state);
        return true;
    }

    public static boolean isEnabled(String id) {
        ClientModule mod = findById(id);
        return mod != null && mod.isEnabled();
    }
}
