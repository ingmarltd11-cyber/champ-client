package net.champ.addon;

import net.champ.addon.module.combat.AutoCartModule;
import net.champ.addon.module.combat.CartPack;
import net.champ.addon.module.combat.InstantPack;
import net.champ.addon.module.crystal.CrystalPack;
import net.champ.addon.module.donut.DonutPack;
import net.champ.addon.module.mace.MacePack;
import net.champ.addon.module.misc.MiscPack;
import net.champ.addon.module.misc.MiscPack2;
import net.champ.addon.module.movement.MovementPack;
import net.champ.addon.module.spear.SpearPack;
import net.champ.addon.module.visual.VisualPack;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleCategory;

import java.util.ArrayList;
import java.util.List;

/** Overzicht-lijst van alle addon-modules, gesorteerd per categorie zoals de client dat toont. */
public final class AllModules {
    private AllModules() {}

    public static List<ClientModule> create() {
        List<ClientModule> out = new ArrayList<>();
        // COMBAT (bestaande categorie)
        out.add(new AutoCartModule());
        out.addAll(CartPack.list());
        out.addAll(InstantPack.list());
        // CRYSTAL / MACE / SPEAR (nieuwe categorieën uit Addons)
        out.addAll(CrystalPack.list());
        out.addAll(MacePack.list());
        out.addAll(SpearPack.list());
        // MOVEMENT (bestaande categorie)
        out.addAll(MovementPack.list());
        // MISC (bestaande categorie)
        out.addAll(MiscPack.list());
        out.addAll(MiscPack2.list());
        // VISUALS (bestaande categorie, twee-ogen icoon)
        out.addAll(VisualPack.list());
        // DONUT SMP (bestaande DONUT-categorie + warn-only buiten Donut)
        out.addAll(DonutPack.list());
        return out;
    }

    /** Gebruikte categorieën checken bij opstart. */
    static void sanity() {
        ModuleCategory c = Addons.crystal();
        if (c == null) throw new IllegalStateException("crystal category ontbreekt");
    }
}
