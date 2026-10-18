package net.champ.client.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.v1.pack.ModPackResources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModDependency.Kind;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class TranslationGuard {
   private static final Logger LOGGER = LoggerFactory.getLogger("Champ Client");
   private static final String MOD_ID = "champplus";
   private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
   private static final ThreadLocal<TranslationGuard.Load> LOAD = new ThreadLocal<>();
   private static volatile int activeCount;
   private static volatile Set<String> hidden;
   private static volatile Set<String> ourMods;
   private static volatile TranslationGuard.Snapshot snapshot;
   private static Language wrapped;
   private static Set<String> guardedKeys;
   private static Language guarded;

   private TranslationGuard() {
   }

   public static boolean active() {
      return activeCount > 0 && DEPTH.get()[0] > 0;
   }

   public static String plain(Component component) {
      enter();

      try {
         return component.getString();
      } finally {
         exit();
      }
   }

   public static void run(Runnable action) {
      enter();

      try {
         action.run();
      } finally {
         exit();
      }
   }

   public static synchronized Language language(Language real) {
      if (real == null) {
         return null;
      }

      Set<String> keys = hidden();
      if (real != wrapped || keys != guardedKeys) {
         wrapped = real;
         guardedKeys = keys;
         guarded = new TranslationGuard.GuardedLanguage(real, keys);
      }

      return guarded;
   }

   public static Component keybind(String name) {
      KeyMapping mapping = KeyMapping.get(name);
      return (Component)(mapping != null && !hidden().contains(name) ? mapping.getTranslatedKeyMessage() : Component.translatable(name));
   }

   public static void beginLoad() {
      LOAD.set(new TranslationGuard.Load());
   }

   public static BiConsumer<String, String> tap(Resource resource, BiConsumer<String, String> output) {
      TranslationGuard.Load load = LOAD.get();
      if (load == null) {
         return output;
      }

      load.seen = true;
      return ours(resource) ? (key, value) -> {
         output.accept(key, value);
         load.provided.add(key);
      } : (key, value) -> {
         output.accept(key, value);
         load.clean.put(key, value);
      };
   }

   public static void endLoad() {
      TranslationGuard.Load load = LOAD.get();
      LOAD.remove();
      if (load != null && load.seen) {
         load.clean.keySet().retainAll(load.provided);
         snapshot = new TranslationGuard.Snapshot(Map.copyOf(load.clean), Set.copyOf(load.provided));
      }
   }

   private static void enter() {
      int[] depth = DEPTH.get();
      if (depth[0]++ == 0) {
         synchronized (TranslationGuard.class) {
            activeCount++;
         }
      }
   }

   private static void exit() {
      int[] depth = DEPTH.get();
      if (depth[0] > 0 && --depth[0] == 0) {
         synchronized (TranslationGuard.class) {
            activeCount--;
         }
      }
   }

   private static boolean ours(Resource resource) {
      return resource.source() instanceof ModPackResources pack ? ours(pack.getFabricModMetadata()) : ourMods().contains(resource.sourcePackId());
   }

   private static Set<String> ourMods() {
      Set<String> ids = ourMods;
      if (ids != null) {
         return ids;
      }

      Set<String> collected = new HashSet<>();

      for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
         if (ours(mod.getMetadata())) {
            collected.add(mod.getMetadata().getId());
         }
      }

      ids = Set.copyOf(collected);
      ourMods = ids;
      return ids;
   }

   private static Set<String> hidden() {
      Set<String> keys = hidden;
      if (keys != null) {
         return keys;
      }

      synchronized (TranslationGuard.class) {
         if (hidden != null) {
            return hidden;
         }

         Set<String> collected = new HashSet<>();
         Set<String> namespaces = new HashSet<>();
         namespaces.add("champ");

         for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (ours(mod.getMetadata())) {
               collect(mod, collected, namespaces);
            }
         }

         Options options = Minecraft.getInstance().options;
         if (options != null) {
            for (KeyMapping mapping : options.keyMappings) {
               Identifier category = mapping.getCategory().comp_4879();
               if (namespaces.contains(category.getNamespace())) {
                  collected.add(mapping.getName());
                  collected.add("key.category." + category.getNamespace() + "." + category.getPath());
               }
            }
         }

         Set<String> result = Set.copyOf(collected);
         if (options != null) {
            hidden = result;
         }

         return result;
      }
   }

   private static boolean ours(ModMetadata metadata) {
      if ("champplus".equals(metadata.getId())) {
         return true;
      }

      for (ModDependency dependency : metadata.getDependencies()) {
         if (dependency.getKind() == Kind.DEPENDS && "champplus".equals(dependency.getModId())) {
            return true;
         }
      }

      return false;
   }

   private static void collect(ModContainer mod, Set<String> keys, Set<String> namespaces) {
      for (Path root : mod.getRootPaths()) {
         Path assets = root.resolve("assets");
         if (Files.isDirectory(assets)) {
            for (Path space : list(assets)) {
               if (Files.isDirectory(space)) {
                  namespaces.add(space.getFileName().toString().replace("/", ""));
                  Path lang = space.resolve("lang");
                  if (Files.isDirectory(lang)) {
                     for (Path file : list(lang)) {
                        if (file.getFileName().toString().endsWith(".json")) {
                           try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                              JsonElement json = JsonParser.parseReader(reader);
                              if (json.isJsonObject()) {
                                 keys.addAll(json.getAsJsonObject().keySet());
                              }
                           } catch (IOException | RuntimeException e) {
                              LOGGER.warn("Could not read {}", file, e);
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static List<Path> list(Path directory) {
      try (Stream<Path> entries = Files.list(directory)) {
         return entries.toList();
      } catch (IOException e) {
         LOGGER.warn("Could not list {}", directory, e);
         return List.of();
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class GuardedLanguage extends Language {
      private final Language real;
      private final Set<String> hidden;

      GuardedLanguage(Language real, Set<String> hidden) {
         this.real = real;
         this.hidden = hidden;
      }

      public String method_4679(String key, String fallback) {
         TranslationGuard.Snapshot current = TranslationGuard.snapshot;
         if (current != null) {
            return current.provided().contains(key) ? current.clean().getOrDefault(key, fallback) : this.real.getOrDefault(key, fallback);
         } else {
            return this.hidden.contains(key) ? fallback : this.real.getOrDefault(key, fallback);
         }
      }

      public boolean method_4678(String key) {
         TranslationGuard.Snapshot current = TranslationGuard.snapshot;
         if (current != null) {
            return current.provided().contains(key) ? current.clean().containsKey(key) : this.real.has(key);
         } else {
            return !this.hidden.contains(key) && this.real.has(key);
         }
      }

      public boolean method_29428() {
         return this.real.isDefaultRightToLeft();
      }

      public FormattedCharSequence method_30934(FormattedText text) {
         return this.real.getVisualOrder(text);
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Load {
      private final Map<String, String> clean = new HashMap<>();
      private final Set<String> provided = new HashSet<>();
      private boolean seen;
   }

   @Environment(EnvType.CLIENT)
   private record Snapshot(Map<String, String> clean, Set<String> provided) {
   }
}
