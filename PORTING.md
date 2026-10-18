# V5 porting plan

Bron: `champ-alles-in-een-v5.zip` (750 files) + decompile `champ-source/`.

- Fase 0 (deze commit): bouwbaar Loom-skelet, MC 1.21.11, Mojang mappings, Java 21.
- Fase 1: base-klassen porten (`ModuleBase`, `ModuleCategory`-koppeling, `Targets`, `Slots`, `Inv`).
- Fase 2: `MiscPack` + `MiscPack2` (simpelste packs, geen render-mixins).
- Fase 3: Combat/Crystal/Mace/Spear/Donut packs + mixins + assets.

Niet uit de decompile overnemen zonder remap-check: alles met `class_`, `method_`, `field_` moet tegen Mojang-namen gelegd worden.
