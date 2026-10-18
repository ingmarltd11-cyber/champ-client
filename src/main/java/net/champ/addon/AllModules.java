package net.champ.addon;

import net.champ.client.module.impl.combat.AutoCartModule;
import net.champ.client.module.impl.combat.CartPack;
import net.champ.client.module.impl.combat.InstantPack;
import net.champ.client.module.impl.crystal.CrystalPack;
import net.champ.client.module.impl.donut.DonutPack;
import net.champ.client.module.impl.mace.MacePack;
import net.champ.client.module.impl.misc.MiscPack;
import net.champ.client.module.impl.misc.MiscPack2;
import net.champ.client.module.impl.movement.MovementPack;
import net.champ.client.module.impl.spear.SpearPack;
import net.champ.client.module.impl.visual.VisualPack;
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
