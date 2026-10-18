# Champ Client + Addons in één mod

- De broncode van `champ-addons` staat nu in `net/champ/addon/` (zelfde package-namen als eerst).
- `ChampClientMod.onInitializeClient()` start `Addons` direct na `AddonManager.init()`, dus vóór het register wordt bevroren.
- `champ-addons.mixins.json` staat in `fabric.mod.json` van de client; de `AddonMixinPlugin` is weg (dezelfde mod, altijd actief).
- Categorie-id's in `Addons.java` zijn hoofdletters gemaakt (CRYSTAL, MACE, SPEAR). `ModuleCategory.register` weigert id's met kleine letters.
- Zonder de aparte `champ-addons` mod en zonder `depends: champplus`.

Let op bij bouwen:
- De addon-code is in Mojang-namen geschreven (`net.minecraft.world.phys.Vec3`), de gedecompileerde client in intermediary (`class_310`). Bouw in een Loom-project met Mojang mappings op de echte repo (github.com/4ldenz/Champ-client), niet op deze decompile.
- `MixinEntityBoxExpand` heeft een refmap (`champ-addons-refmap.json`); controleer dat Loom die genereert.

## Snelheid / kwaliteit (niet gecompileerd of gemeten)
- `Slots.itemId`: item-id's gecachet (werd honderden keren per tick opnieuw uit de registry gehaald).
- `Targets.nearestEnemy`: loopt over `level.players()` met `distanceToSqr` i.p.v. AABB-query + stream, resultaat per tick gecachet. Zoekt nu in een bol (was een kubus, dus soms verder dan `range`).
- `Targets.fallDistance`: werkt voor float én double (`getFloat` op een double-veld gooit een exception).
- `Modules.findById`: `ModuleRegistry.resolve` + cache i.p.v. reflectie over alle methodes van de registry bij elke aanroep.
- `Inv`: klikt/zoekt alleen als het speler-inventory open is (slotnummers kloppen niet in een kist).
- Nieuw `ChunkSweep`: scant een paar chunks per tick en slaat secties zonder het gezochte blok over. Gebruikt door Seed Xray (was ~2 miljoen `getBlockState` per 2 s, tot ~8 miljoen bij radius 8) en Base Finder (was ~1 miljoen `getBlockState` elke tick).
- GUI: zacht klikgeluidje (`UiSound`) als je over een module-rij gaat, max. 1x per 45 ms.
- Niet aangepast: `PacketSpoofEngine`, Auto Mine Out (loopt het hele gebied elke tick opnieuw door).

## Auto Schematic Builder en Auto Mine Out (herschreven, niet gecompileerd)
Auto Schematic Builder
- Bouwpunt ligt vast bij het aanzetten (verschoof eerst mee met de speler, waardoor de bouw uit elkaar liep).
- Plaatst nu echt: klikt op de zijde van een aangrenzend vast blok. Eerder werd op het lege doelblok zelf geklikt, wat niets plaatst.
- Alleen binnen bereik (`canInteractWithBlock`), nooit in jezelf, laag voor laag, per tick hooguit 96 blokken bekeken (constante kosten).
- Materiaal exact matchen (was substring: "stone" pakte ook "cobblestone"); haalt ontbrekend materiaal uit het hoofdinventory.
- .litematic wordt op een achtergrond-thread ingelezen (geen hapering); NBT-aanroepen volgens de API van 1.21.5+ (`getCompoundOrEmpty`, `getIntOr`, `keySet`).
- Bekende beperking: alleen bloktype, geen draai-richting (trappen, platen), en geen auto-buy.
Auto Mine Out
- Liep het hele gebied elke tick opnieuw door (tot 262k blokken); nu alleen de bereik-kubus rond de speler, en alleen als er een nieuw doelblok gekozen moet worden.
- Brak niet echt: `startDestroyBlock` met elk tick een ander blok zette de voortgang steeds terug. Nu `continueDestroyBlock` op één doel tot het kapot is.
- Radius-modus blijft rond het punt van aanzetten (verschoof eerst mee).
- Mijnt alleen blokken met een open zijde (niet door muren), bovenste lagen eerst, slaat bedrock en vloeistoffen over, zet zichzelf uit als het gebied leeg is.
Beide: "Look at blocks" draait de camera vloeiend (ease-out) naar het blok, plus arm-swing.

## Categorieën, iconen en /ah-scan (niet gecompileerd)
Categorieën (in jouw volgorde): Combat, Crystal, Mace, Spear, Movement, Misc, Visual, Donut; daarachter de extra's Render, Base Finding, Macro, Client.
- Crystal, Mace en Spear staan nu in `ModuleCategory` zelf (niet meer apart via `Addons` geregistreerd).
- Verplaatst: Crystal Aura, Crystal Macro, Auto Totem, Anchor Aura, Single Anchor Macro -> Crystal; Mace -> Mace; W-Tap, Jump Reset, Freecam -> Movement; Crystal Optimizer, Fast XP, Friend Guard -> Misc; ESP, Storage ESP, Item ESP, Spawner Nametags, No Force Texture Pack -> Visual.
- "Auto Crystal" uit je lijst = Crystal Aura (zet CrystalMacro in je lijst als je die bedoelde).
- Nieuwe iconen (`ChampWidgets`): zwaard, kristal, mace, speer, rennende persoon, vonk, twee ogen, donut.
Auto Schematic Builder + /ah
- Nieuw `donutmacros/MarketScan`: opent per materiaal `/ah <naam>`, leest pagina 1 (goedkoopste eerst), telt hele listings tot het aantal gedekt is en geeft een totaalprijs. Hergebruikt Gui, MacroWorker, ServerText en Dialogs van de bestaande AH-bot.
- Kopen alleen na jouw bevestiging: 'Accept purchase key' binnen 60 s na de scan, anders niets. Bij het kopen: prijs max. 5% boven de scan, totaal onder budget (scan +5%), succes gemeten aan je inventory, stop bij het eerste probleem.
- Aannames die je in-game moet controleren: prijs staat in de item-tekst; listings staan in slot 0-44; alleen pagina 1; een te brede zoekterm ("stone") kan exacte listings van pagina 1 verdringen.
Nog niet gedaan: Resell (is nog een stub), Auto Drain (wacht op afbeelding), Seed Xray gebruikt de seed niet.

## Auto Drain, Seed Xray, Resell (niet gecompileerd)
- Resell: verwijderd.
- Auto Drain (herschreven): zoekt water/lava-bronnen binnen bereik met vrij zicht, pakt ze op met een LEGE bucket (exacte match, dus geen water_bucket/lava_bucket) via een gewone rechtsklik (`useItem`), anders een cobweb ín het liquid. Silent aim aan = server-rotatie via de bestaande SilentAim-state, uit = vloeiende camera-draai. Scan elke 4 ticks, 3 pogingen per bron, daarna 10 s overgeslagen. Of de server-rotatie ook echt op het use-item-packet komt hangt van `PacketSpoofEngine` af en is niet gecontroleerd.
- Seed Xray: nu een client-module (`impl/render/SeedXrayModule`) op de Block ESP-motor: kleur per erts, tracers per erts, merged shapes. De seed wordt NIET gebruikt (wereldgeneratie nabouwen is niet gedaan); het toont ertsen in de chunks die de client heeft.
- Netherite Finder: niet aangepast.
