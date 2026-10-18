package net.champ.addon;

import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;
import net.champ.client.module.ModuleProvider;
import net.champ.client.module.ModuleRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * Entrypoint van Champ Client Addons. Registreert nieuwe categorieën (Crystal, Mace,
 * Spear) via de officiële ModuleCategory.register-API van de client en levert alle
 * addon-modules aan via één ModuleProvider — precies zoals BuiltinModuleProvider dat doet.
 */
@Environment(EnvType.CLIENT)
public final class Addons implements ClientModInitializer {
    public static final String ID = "champ-addons";

    private static ModuleCategory crystal;
    private static ModuleCategory mace;
    private static ModuleCategory spear;

    @Override
    public void onInitializeClient() {
        registerCategories();
        ModuleRegistry.registerProvider(new Provider());
    }

    private static void registerCategories() {
        // Crystal, Mace en Spear staan nu direct in ModuleCategory (zelfde mod), dus alleen koppelen.
        crystal = ModuleCategory.CRYSTAL;
        mace    = ModuleCategory.MACE;
        spear   = ModuleCategory.SPEAR;
    }

    public static ModuleCategory crystal() { return crystal; }
    public static ModuleCategory mace()    { return mace; }
    public static ModuleCategory spear()   { return spear; }

    private static final class Provider implements ModuleProvider {
        @Override public String id() { return "champ-addons"; }

        @Override public List<ClientModule> createModules() {
            List<ClientModule> out = new ArrayList<>();
            out.addAll(net.champ.addon.AllModules.create());
            return out;
        }
    }
}
