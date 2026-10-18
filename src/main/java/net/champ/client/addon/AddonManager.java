package net.champ.client.addon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.champ.client.config.ModuleConfigStore;
import net.champ.client.module.ClientModule;
import net.champ.client.module.ModuleRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class AddonManager {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   public static final String ENTRYPOINT = "champ";
   private static final String CLIENT_PACKAGE = "net.champ.client";
   private static List<ChampAddon> addons;
   private static boolean configLoaded;

   private AddonManager() {
   }

   public static List<ChampAddon> addons() {
      List<ChampAddon> loaded = addons;
      return loaded != null ? loaded : List.of();
   }

   public static void init() {
      if (addons == null) {
         List<ChampAddon> loaded = create();
         loaded.removeIf(addonx -> !run(addonx, "register its categories", ChampAddon::onRegisterCategories));
         loaded.removeIf(addonx -> {
            if (run(addonx, "initialise", ChampAddon::onInitialize)) {
               return false;
            }

            removeModules(addonx);
            return true;
         });

         for (ChampAddon addon : loaded) {
            LOGGER.info(
               "[Champ Client] Loaded addon {} v{} by {}",
               new Object[]{addon.name(), addon.version(), addon.authors().isEmpty() ? "unknown" : String.join(", ", addon.authors())}
            );
         }

         addons = List.copyOf(loaded);
      }
   }

   public static void configLoaded() {
      if (addons != null && !configLoaded) {
         configLoaded = true;
         List<ChampAddon> loaded = new ArrayList<>(addons);
         loaded.removeIf(addon -> {
            if (run(addon, "start", ChampAddon::onConfigLoaded)) {
               return false;
            }

            removeModules(addon);
            return true;
         });
         addons = List.copyOf(loaded);
      }
   }

   private static List<ChampAddon> create() {
      List<ChampAddon> created = new ArrayList<>();

      List<EntrypointContainer<ChampAddon>> containers;
      try {
         containers = FabricLoader.getInstance().getEntrypointContainers("champ", ChampAddon.class);
      } catch (RuntimeException | LinkageError e) {
         LOGGER.error("[Champ Client] Could not list addons; starting without them", e);
         return created;
      }

      for (EntrypointContainer<ChampAddon> container : containers) {
         ModMetadata metadata = container.getProvider().getMetadata();

         ChampAddon addon;
         String pkg;
         try {
            addon = (ChampAddon)container.getEntrypoint();
            pkg = addon.getPackage();
         } catch (RuntimeException | LinkageError e) {
            LOGGER.error("[Champ Client] Addon {} ({}) could not be created and is skipped", new Object[]{metadata.getName(), metadata.getId(), e});
            continue;
         }

         addon.bind(metadata);
         String problem = packageProblem(addon, pkg, created);
         if (problem != null) {
            LOGGER.error("[Champ Client] Addon {} is skipped: {}", addon.name(), problem);
         } else {
            addon.packageName = pkg;
            created.add(addon);
         }
      }

      return created;
   }

   private static String packageProblem(ChampAddon addon, String pkg, List<ChampAddon> created) {
      if (pkg != null && addon.getClass().getName().startsWith(pkg + ".")) {
         if (overlap(pkg, "net.champ.client")) {
            return "its package " + pkg + " overlaps Champ' own (net.champ.client)";
         }

         for (ChampAddon other : created) {
            if (overlap(pkg, other.packageName)) {
               return "its package "
                  + pkg
                  + " overlaps "
                  + other.name()
                  + "'s ("
                  + other.packageName
                  + "); an addon made from the template needs its package renamed";
            }
         }

         return null;
      } else {
         return "getPackage() is \"" + pkg + "\", which doesn't hold its class " + addon.getClass().getName();
      }
   }

   private static boolean overlap(String a, String b) {
      return (a + ".").startsWith(b + ".") || (b + ".").startsWith(a + ".");
   }

   private static boolean run(ChampAddon addon, String step, Consumer<ChampAddon> action) {
      try {
         action.accept(addon);
         return true;
      } catch (RuntimeException | LinkageError e) {
         LOGGER.error("[Champ Client] Addon {} failed to {} and is skipped{}", new Object[]{addon.name(), step, reportHint(addon), e});
         return false;
      }
   }

   private static String reportHint(ChampAddon addon) {
      try {
         ChampAddon.GithubRepo repo = addon.getRepo();
         String where = repo != null ? repo.url() + "/issues" : addon.getWebsite();
         return where != null ? " - report it at " + where : "";
      } catch (RuntimeException | LinkageError e) {
         return "";
      }
   }

   private static void removeModules(ChampAddon addon) {
      String prefix = addon.packageName + ".";

      for (ClientModule module : ModuleRegistry.all()) {
         if (module.getClass().getName().startsWith(prefix)) {
            ModuleConfigStore.getInstance().release(module);
            if (module.isEnabled()) {
               module.setEnabled(false);
            }

            ModuleRegistry.remove(module);
         }
      }
   }
}
