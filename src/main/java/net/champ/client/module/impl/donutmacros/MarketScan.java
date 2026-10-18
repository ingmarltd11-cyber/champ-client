package net.champ.client.module.impl.donutmacros;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.class_1713;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_746;
import net.minecraft.class_7923;

/**
 * Scant de veiling (/ah) op prijzen en koopt daarna - alleen op verzoek - precies wat gescand is.
 *
 * Hergebruikt de bestaande AH-hulpmiddelen van de client (Gui, MacroWorker, ServerText, Dialogs).
 * De publieke API gebruikt alleen gewone Java-types, zodat de addon-modules hem kunnen aanroepen.
 *
 * Aannames: /ah <zoekterm> toont de goedkoopste listing eerst, listings staan in slot 0-44 en de
 * prijs staat in de item-tekst. Alleen pagina 1 wordt gelezen.
 */
@Environment(EnvType.CLIENT)
public final class MarketScan {
   /** Wat nodig is: item-id zonder namespace (bv. "oak_planks") en het aantal. */
   public record Need(String itemId, int amount) {}

   /** Prijsoffer voor één materiaal. maxUnit is de duurste stukprijs die voor dit offer nodig was. */
   public record Quote(String itemId, int amount, double total, double maxUnit, boolean complete, String note) {}

   public record Result(List<Quote> quotes, double total, boolean complete) {}

   private record Listing(int slot, int count, double total) {
      double unit() {
         return this.total / Math.max(1, this.count);
      }
   }

   private static final double PRICE_SLACK = 1.05;

   private final MacroWorker worker = new MacroWorker("Champ-MarketScan");
   private final Gui gui = new Gui(this.worker);

   public boolean running() {
      return this.worker.running();
   }

   public void stop() {
      this.worker.stop();
   }

   /** Scant de prijzen. say en done worden op de game-thread aangeroepen. */
   public void scan(List<Need> needs, Consumer<String> say, Consumer<Result> done) {
      List<Need> copy = List.copyOf(needs);
      this.worker.start(() -> {
         List<Quote> quotes = new ArrayList<>();
         double total = 0.0;
         boolean complete = true;
         for (Need need : copy) {
            this.worker.check();
            Quote quote = this.quote(need, say);
            quotes.add(quote);
            total += quote.total();
            complete &= quote.complete();
            this.worker.sleep(700L);
         }

         this.gui.closeAll();
         Result result = new Result(quotes, total, complete);
         this.worker.run(() -> done.accept(result));
      }, failure -> this.report(say, "[Markt] scan mislukt: " + describe(failure)));
   }

   /**
    * Koopt de volledige quotes. Per listing wordt de prijs opnieuw gecontroleerd (max. 5% boven het
    * gescande niveau), het totaal blijft onder budget en bij het eerste probleem wordt gestopt.
    * Succes wordt gemeten aan het aantal items in je inventory, niet aan chatberichten.
    */
   public void buy(List<Quote> quotes, double budget, Consumer<String> say, Consumer<Boolean> done) {
      List<Quote> copy = List.copyOf(quotes);
      this.worker.start(() -> {
         double spent = 0.0;
         boolean allOk = true;
         for (Quote quote : copy) {
            this.worker.check();
            if (!quote.complete()) {
               continue;
            }

            double[] spentBox = new double[]{spent};
            boolean ok = this.buyItem(quote, budget, spentBox, say);
            spent = spentBox[0];
            if (!ok) {
               allOk = false;
               break;
            }
         }

         this.gui.closeAll();
         boolean result = allOk;
         double finalSpent = spent;
         this.worker.run(() -> {
            say.accept("[Markt] klaar - uitgegeven: $" + ServerText.shortMoney(finalSpent));
            done.accept(result);
         });
      }, failure -> {
         this.report(say, "[Markt] kopen gestopt: " + describe(failure));
         try {
            this.worker.run(() -> done.accept(false));
         } catch (RuntimeException ignored) {
         }
      });
   }

   private Quote quote(Need need, Consumer<String> say) {
      String search = need.itemId().replace('_', ' ');
      try {
         this.openAh(search);
      } catch (IllegalStateException e) {
         this.worker.sleep(3000L);
         this.openAh(search);
      }

      List<Listing> listings = this.worker.call(() -> readListings(need.itemId()));
      if (listings.isEmpty()) {
         return new Quote(need.itemId(), need.amount(), 0.0, 0.0, false, "niet gevonden op pagina 1 (zoekterm te breed of niet te koop)");
      }

      double cost = 0.0;
      int got = 0;
      double maxUnit = 0.0;
      for (Listing listing : listings) {
         if (got >= need.amount()) {
            break;
         }

         cost += listing.total();
         got += listing.count();
         maxUnit = Math.max(maxUnit, listing.unit());
      }

      if (got >= need.amount()) {
         return new Quote(need.itemId(), need.amount(), cost, maxUnit, true, "");
      }

      double last = listings.get(listings.size() - 1).unit();
      cost += last * (need.amount() - got);
      return new Quote(need.itemId(), need.amount(), cost, last, false, "pagina 1 heeft maar " + got + " stuks, rest geschat");
   }

   private boolean buyItem(Quote quote, double budget, double[] spent, Consumer<String> say) {
      String id = quote.itemId();
      String search = id.replace('_', ' ');
      int start = this.worker.call(() -> countInventory(id));
      for (int attempt = 0; attempt < 80; attempt++) {
         int have = this.worker.call(() -> countInventory(id)) - start;
         if (have >= quote.amount()) {
            this.report(say, "[Markt] " + id + ": " + have + " gekocht");
            return true;
         }

         this.openAh(search);
         double limit = quote.maxUnit() * PRICE_SLACK;
         Listing hit = this.worker.call(() -> cheapest(id, limit));
         if (hit == null) {
            this.report(say, "[Markt] " + id + ": niets meer te koop tegen de gescande prijs, gestopt.");
            return false;
         }

         if (spent[0] + hit.total() > budget) {
            this.report(say, "[Markt] budget bereikt bij " + id + ", gestopt.");
            return false;
         }

         int before = this.worker.call(() -> countInventory(id));
         this.worker.run(() -> {
            class_746 player = Gui.player();
            Gui.mc().field_1761.method_2906(player.field_7512.field_7763, hit.slot(), 0, class_1713.field_7790, player);
         });
         if (!this.confirmAndWait(id, before)) {
            this.report(say, "[Markt] " + id + ": aankoop niet bevestigd (te weinig geld of listing weg), gestopt.");
            return false;
         }

         spent[0] += hit.total();
         this.worker.sleep(600L);
      }

      return false;
   }

   /** Bevestigt het koop-scherm (zelfde aanpak als de sniper) en wacht tot de items binnen zijn. */
   private boolean confirmAndWait(String id, int before) {
      Object[] pressed = new Object[]{null};
      int[] pressedChest = new int[]{-1};
      long end = System.currentTimeMillis() + 5000L;
      while (System.currentTimeMillis() < end) {
         if (this.worker.call(() -> countInventory(id)) > before) {
            return true;
         }

         this.worker.run(() -> {
            Dialogs.View view = Dialogs.current(Gui.mc());
            if (view == null) {
               class_746 player = Gui.mc().field_1724;
               if (player != null && Gui.containerOpenNow() && !ahOpenNow()) {
                  int sync = player.field_7512.field_7763;
                  if (sync != pressedChest[0]) {
                     List<class_1799> stacks = Gui.containerNow();
                     for (int i = 0; i < stacks.size(); i++) {
                        class_1799 stack = stacks.get(i);
                        if (!stack.method_7960() && class_7923.field_41178.method_10221(stack.method_7909()).method_12832().contains("lime")) {
                           Gui.mc().field_1761.method_2906(sync, i, 0, class_1713.field_7790, player);
                           pressedChest[0] = sync;
                           return;
                        }
                     }
                  }
               }
            } else if (view.dialog() != pressed[0] && (view.titled("buy") || view.titled("sure") || view.titled("purchase"))) {
               Dialogs.Button yes = view.labelled("yes", "confirm", "buy");
               if (yes != null && Dialogs.press(Gui.mc(), view, yes, Map.of())) {
                  pressed[0] = view.dialog();
               }
            }
         });
         this.worker.sleep(50L);
      }

      return this.worker.call(() -> countInventory(id)) > before;
   }

   private void openAh(String search) {
      this.gui.closeAll();
      this.gui.command("ah " + search);
      if (!this.worker.waitFor(MarketScan::ahOpenNow, 10000L)) {
         throw new IllegalStateException("de AH opende niet voor '" + search + "'");
      }

      this.worker.sleep(450L);
   }

   private static boolean ahOpenNow() {
      if (!Gui.containerOpenNow()) {
         return false;
      }

      String title = ServerText.normalize(Gui.mc().field_1755.method_25440());
      return title.contains("order") ? false : title.contains("auction") || refreshSlot(Gui.containerNow()) >= 0;
   }

   private static int refreshSlot(List<class_1799> stacks) {
      for (int i = 45; i < stacks.size(); i++) {
         class_1799 stack = stacks.get(i);
         if (!stack.method_7960() && stack.method_31574(class_1802.field_8782)) {
            return i;
         }
      }

      return -1;
   }

   /** Alle listings van dit item op pagina 1, goedkoopste stukprijs eerst. */
   private static List<Listing> readListings(String itemId) {
      String want = "minecraft:" + itemId;
      List<class_1799> stacks = Gui.containerNow();
      List<Listing> out = new ArrayList<>();
      for (int i = 0; i < 45 && i < stacks.size(); i++) {
         class_1799 stack = stacks.get(i);
         if (stack.method_7960() || !want.equals(String.valueOf(class_7923.field_41178.method_10221(stack.method_7909())))) {
            continue;
         }

         double total = priceOf(stack);
         if (total > 0.0) {
            out.add(new Listing(i, stack.method_7947(), total));
         }
      }

      out.sort(Comparator.comparingDouble(Listing::unit));
      return out;
   }

   private static Listing cheapest(String itemId, double maxUnit) {
      for (Listing listing : readListings(itemId)) {
         if (listing.unit() <= maxUnit) {
            return listing;
         }
      }

      return null;
   }

   /** Totaalprijs van een listing uit de item-tekst (regels met price/cost/buy, anders alles met $). */
   private static double priceOf(class_1799 stack) {
      List<String> lore = ServerText.lore(stack);
      double best = -1.0;
      for (String line : lore) {
         String clean = ServerText.normalize(line);
         if (clean.contains("price") || clean.contains("cost") || clean.contains("buy")) {
            for (double value : ServerText.moneyAll(line)) {
               if (value > 0.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      if (best > 0.0) {
         return best;
      }

      for (String line : lore) {
         if (line.contains("$")) {
            for (double value : ServerText.moneyAll(line)) {
               if (value > 0.0 && (best < 0.0 || value < best)) {
                  best = value;
               }
            }
         }
      }

      return best;
   }

   private static int countInventory(String itemId) {
      class_746 player = Gui.mc().field_1724;
      if (player == null) {
         return 0;
      }

      String want = "minecraft:" + itemId;
      int count = 0;
      for (class_1735 slot : player.field_7498.field_7761) {
         class_1799 stack = slot.method_7677();
         if (!stack.method_7960() && want.equals(String.valueOf(class_7923.field_41178.method_10221(stack.method_7909())))) {
            count += stack.method_7947();
         }
      }

      return count;
   }

   private void report(Consumer<String> say, String text) {
      try {
         this.worker.run(() -> say.accept(text));
      } catch (RuntimeException ignored) {
      }
   }

   private static String describe(Throwable failure) {
      return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
   }
}
