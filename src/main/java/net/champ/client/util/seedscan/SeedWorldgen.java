package net.champ.client.util.seedscan;

import com.mojang.serialization.Lifecycle;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.IdMap;
import net.minecraft.core.MappedRegistry;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.worldgen.Carvers;
import net.minecraft.data.worldgen.ProcessorLists;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.data.worldgen.biome.BiomeData;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.data.worldgen.NoiseData;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.core.RegistryAccess.ImmutableRegistryAccess;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData;

@Environment(EnvType.CLIENT)
public final class SeedWorldgen {
   public static final int MIN_Y = -64;
   public static final int HEIGHT = 384;
   public static final int MAX_Y = 319;
   public final long seed;
   final NoiseBasedChunkGenerator generator;
   final RandomState randomState;
   final MultiNoiseBiomeSource biomeSource;
   final NoiseGeneratorSettings settings;
   private final List<OreSpec> specs;
   private final PalettedContainerFactory containers;
   private final StructureManager noStructures;
   private final LevelHeightAccessor height = LevelHeightAccessor.create(-64, 384);
   private final long zoomSeed;
   private final Map<Long, BiomeGenerationSettings> carverBiomes = new ConcurrentHashMap<>();
   private final Map<Long, short[]> storedBiomes = new ConcurrentHashMap<>();
   private final List<Holder<Biome>> biomeIds;
   private final Map<Holder<Biome>, Integer> biomeIndex = new IdentityHashMap<>();
   private static final int MIN_QUART = -16;
   private static final int QUARTS_Y = 96;
   private static final int MAX_STORED_BIOMES = 4096;
   private static final int MAX_CARVER_BIOMES = 65536;
   private final BiomeManager biomeManager;
   private static final RegistryAccess CARVER_REGISTRIES = new ImmutableRegistryAccess(List.of(new MappedRegistry(Registries.BIOME, Lifecycle.stable()).freeze()));
   private static final Set<ResourceKey<Biome>> TALL_TREES = Set.of(
      Biomes.JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.MANGROVE_SWAMP
   );

   private SeedWorldgen(long seed) {
      this.seed = seed;
      Provider lookup = new RegistrySetBuilder()
         .add(Registries.CONFIGURED_CARVER, Carvers::method_46843)
         .add(Registries.PROCESSOR_LIST, ProcessorLists::method_46846)
         .add(Registries.CONFIGURED_FEATURE, FeatureUtils::method_39702)
         .add(Registries.PLACED_FEATURE, PlacementUtils::method_39735)
         .add(Registries.BIOME, BiomeData::method_40363)
         .add(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, MultiNoiseBiomeSourceParameterLists::method_49522)
         .add(Registries.NOISE, NoiseData::method_39216)
         .add(Registries.DENSITY_FUNCTION, NoiseRouterData::method_41100)
         .add(Registries.NOISE_SETTINGS, NoiseGeneratorSettings::method_31111)
         .build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
      Holder<NoiseGeneratorSettings> settingsHolder = lookup.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
      this.settings = (NoiseGeneratorSettings)settingsHolder.comp_349();
      this.randomState = RandomState.create(this.settings, lookup.lookupOrThrow(Registries.NOISE), seed);
      Holder<MultiNoiseBiomeSourceParameterList> parameters = lookup.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
      this.biomeSource = MultiNoiseBiomeSource.createFromPreset(parameters);
      this.generator = new NoiseBasedChunkGenerator(this.biomeSource, settingsHolder);
      List<Holder<Biome>> biomes = List.copyOf(this.biomeSource.possibleBiomes());
      List<StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(biomes, holder -> this.generator.getBiomeGenerationSettings(holder).features(), true);
      Map<PlacedFeature, String> names = new IdentityHashMap<>();
      lookup.lookupOrThrow(Registries.PLACED_FEATURE)
         .listElements()
         .forEach(reference -> names.put((PlacedFeature)reference.comp_349(), reference.key().identifier().getPath()));
      this.specs = OreSpec.derive(steps, biomes, this.generator, placed -> names.getOrDefault(placed, "?"), -64, 319);
      Holder<Biome> plains = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
      List<Holder<Biome>> ids = new ArrayList<>(biomes);
      if (!ids.contains(plains)) {
         ids.add(plains);
      }

      this.biomeIds = List.copyOf(ids);

      for (int i = 0; i < this.biomeIds.size(); i++) {
         this.biomeIndex.put(this.biomeIds.get(i), i);
      }

      this.containers = new PalettedContainerFactory(
         Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState(), null, Strategy.createForBiomes(listIds(ids)), plains, null
      );
      this.noStructures = new StructureManager(emptyLevel(this.containers, this.height), new WorldOptions(seed, false, false), null);
      this.zoomSeed = BiomeManager.obfuscateSeed(seed);
      this.biomeManager = new BiomeManager(this::storedBiome, this.zoomSeed);
   }

   public static SeedWorldgen create(long seed) {
      return new SeedWorldgen(seed);
   }

   public List<OreSpec> specs() {
      return this.specs;
   }

   public int treeAllowance(int chunkX, int chunkZ) {
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            int x = (chunkX + dx << 4) + 8;
            int z = (chunkZ + dz << 4) + 8;

            for (int y = 60; y <= 130; y += 10) {
               if (this.biomeAt(x, y, z).unwrapKey().filter(TALL_TREES::contains).isPresent()) {
                  return GateHeights.TALL_TREE_ALLOWANCE;
               }
            }
         }
      }

      return GateHeights.TREE_ALLOWANCE;
   }

   public Holder<Biome> biomeAt(int x, int y, int z) {
      return this.biomeManager.getBiome(new BlockPos(x, y, z));
   }

   Holder<Biome> storedBiome(int qx, int qy, int qz) {
      int clamped = Math.max(-16, Math.min(qy, 79));
      short[] quarts = this.storedBiomes(QuartPos.toSection(qx), QuartPos.toSection(qz));
      return this.biomeIds.get(quarts[((clamped - -16) * 4 + (qz & 3)) * 4 + (qx & 3)]);
   }

   private short[] storedBiomes(int chunkX, int chunkZ) {
      long key = ChunkPos.asLong(chunkX, chunkZ);
      short[] quarts = this.storedBiomes.get(key);
      if (quarts != null) {
         return quarts;
      }

      ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, this.height, this.containers, null);
      return this.fillBiomes(chunk);
   }

   private short[] fillBiomes(ProtoChunk chunk) {
      this.generator.createBiomes(this.randomState, Blender.empty(), this.noStructures, chunk).join();
      chunk.setPersistedStatus(ChunkStatus.BIOMES);
      int x0 = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
      int z0 = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
      short[] quarts = new short[1536];

      for (int qy = 0; qy < 96; qy++) {
         for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
               Integer id = this.biomeIndex.get(chunk.getNoiseBiome(x0 + qx, -16 + qy, z0 + qz));
               quarts[(qy * 4 + qz) * 4 + qx] = (short)(id == null ? 0 : id);
            }
         }
      }

      if (this.storedBiomes.size() > 4096) {
         this.storedBiomes.clear();
      }

      this.storedBiomes.put(chunk.getPos().toLong(), quarts);
      return quarts;
   }

   public ProtoChunk generateTerrain(int chunkX, int chunkZ) {
      ProtoChunk chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, this.height, this.containers, null);
      this.fillBiomes(chunk);
      this.generator.fillFromNoise(Blender.empty(), this.randomState, this.noStructures, chunk).join();
      chunk.setPersistedStatus(ChunkStatus.NOISE);
      this.generator
         .buildSurface(chunk, new WorldGenerationContext(this.generator, chunk), this.randomState, this.noStructures, this.biomeManager, null, Blender.empty());
      chunk.setPersistedStatus(ChunkStatus.SURFACE);
      this.carve(chunk);
      chunk.setPersistedStatus(ChunkStatus.CARVERS);
      return chunk;
   }

   public TerrainChunk terrain(int chunkX, int chunkZ) {
      return TerrainChunk.of(this.generateTerrain(chunkX, chunkZ));
   }

   void carve(ProtoChunk chunk) {
      if (this.carverBiomes.size() > 65536) {
         this.carverBiomes.clear();
      }

      BiomeManager carverBiomeManager = new BiomeManager((x, y, z) -> this.biomeSource.getNoiseBiome(x, y, z, this.randomState.sampler()), this.zoomSeed);
      WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
      ChunkPos pos = chunk.getPos();
      NoiseChunk noise = chunk.getOrCreateNoiseChunk(c -> {
         throw new IllegalStateException("carving before the noise fill");
      });
      Aquifer aquifer = noise.aquifer();
      CarvingContext context = new CarvingContext(this.generator, CARVER_REGISTRIES, chunk.getHeightAccessorForGeneration(), noise, this.randomState, this.settings.comp_478());
      CarvingMask mask = chunk.getOrCreateCarvingMask();

      for (int dx = -8; dx <= 8; dx++) {
         for (int dz = -8; dz <= 8; dz++) {
            ChunkPos from = new ChunkPos(pos.x + dx, pos.z + dz);
            BiomeGenerationSettings biome = this.carverBiomes
               .computeIfAbsent(
                  from.toLong(),
                  k -> this.generator
                     .getBiomeGenerationSettings(
                        this.biomeSource
                           .getNoiseBiome(
                              QuartPos.fromBlock(from.getMinBlockX()), 0, QuartPos.fromBlock(from.getMinBlockZ()), this.randomState.sampler()
                           )
                     )
               );
            int index = 0;

            for (Holder<ConfiguredWorldCarver<?>> holder : biome.getCarvers()) {
               random.setLargeFeatureSeed(this.seed + index, from.x, from.z);
               if (((ConfiguredWorldCarver)holder.comp_349()).isStartChunk(random)) {
                  ((ConfiguredWorldCarver)holder.comp_349()).carve(context, chunk, carverBiomeManager::method_22393, random, aquifer, from, mask);
               }

               index++;
            }
         }
      }
   }

   private static IdMap<Holder<Biome>> listIds(List<Holder<Biome>> list) {
      final Map<Holder<Biome>, Integer> ids = new IdentityHashMap<>();

      for (int i = 0; i < list.size(); i++) {
         ids.put(list.get(i), i);
      }

      return new IdMap<Holder<Biome>>() {
         public int getId(Holder<Biome> biome) {
            Integer id = ids.get(biome);
            return id == null ? -1 : id;
         }

         public Holder<Biome> byId(int id) {
            return id >= 0 && id < list.size() ? list.get(id) : null;
         }

         public int method_10204() {
            return list.size();
         }

         public Iterator<Holder<Biome>> iterator() {
            return list.iterator();
         }
      };
   }

   private static LevelAccessor emptyLevel(PalettedContainerFactory containers, LevelHeightAccessor height) {
      ProtoChunk empty = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, height, containers, null);
      InvocationHandler handler = (proxy, method, args) -> {
         // java.lang.RuntimeException: invalid constant type: Ljava/io/Serializable; with value SeedWorldgen.emptyLevel
         //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent.toJava(ConstExprent.java:364)
         //   at org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent.toJava(SwitchExprent.java:152)
         //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
         //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent.toJava(ExitExprent.java:85)
         //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
         //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
         //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:860)
         //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
         //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:860)
         //   at org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement.toJava(IfStatement.java:238)
         //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
         //   at org.jetbrains.java.decompiler.main.ClassWriter.methodLambdaToJava(ClassWriter.java:1017)
         //
         // Bytecode:
         // 00: aload 2
         // 01: invokevirtual java/lang/reflect/Method.getDeclaringClass ()Ljava/lang/Class;
         // 04: ldc java/lang/Object
         // 06: if_acmpne 8c
         // 09: aload 2
         // 0a: invokevirtual java/lang/reflect/Method.getName ()Ljava/lang/String;
         // 0d: astore 4
         // 0f: bipush -1
         // 10: istore 5
         // 12: aload 4
         // 14: invokevirtual java/lang/String.hashCode ()I
         // 17: lookupswitch 56 2 -1295482945 25 147696667 42
         // 30: aload 4
         // 32: ldc_w "equals"
         // 35: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
         // 38: ifeq 4f
         // 3b: bipush 0
         // 3c: istore 5
         // 3e: goto 4f
         // 41: aload 4
         // 43: ldc_w "hashCode"
         // 46: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
         // 49: ifeq 4f
         // 4c: bipush 1
         // 4d: istore 5
         // 4f: iload 5
         // 51: lookupswitch 55 2 0 27 1 45
         // 6c: aload 1
         // 6d: aload 3
         // 6e: bipush 0
         // 6f: aaload
         // 70: if_acmpne 77
         // 73: bipush 1
         // 74: goto 78
         // 77: bipush 0
         // 78: invokestatic java/lang/Boolean.valueOf (Z)Ljava/lang/Boolean;
         // 7b: goto 8b
         // 7e: aload 1
         // 7f: invokestatic java/lang/System.identityHashCode (Ljava/lang/Object;)I
         // 82: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
         // 85: goto 8b
         // 88: ldc_w "SeedWorldgen.emptyLevel"
         // 8b: areturn
         // 8c: ldc_w net/minecraft/class_2791
         // 8f: aload 2
         // 90: invokevirtual java/lang/reflect/Method.getReturnType ()Ljava/lang/Class;
         // 93: invokevirtual java/lang/Class.isAssignableFrom (Ljava/lang/Class;)Z
         // 96: ifeq a3
         // 99: aload 2
         // 9a: invokevirtual java/lang/reflect/Method.getParameterCount ()I
         // 9d: bipush 4
         // 9e: if_icmpne a3
         // a1: aload 0
         // a2: areturn
         // a3: aload 2
         // a4: invokevirtual java/lang/reflect/Method.isDefault ()Z
         // a7: ifeq b1
         // aa: aload 1
         // ab: aload 2
         // ac: aload 3
         // ad: invokestatic java/lang/reflect/InvocationHandler.invokeDefault (Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;
         // b0: areturn
         // b1: new java/lang/UnsupportedOperationException
         // b4: dup
         // b5: aload 2
         // b6: invokestatic java/lang/String.valueOf (Ljava/lang/Object;)Ljava/lang/String;
         // b9: invokedynamic makeConcatWithConstants (Ljava/lang/String;)Ljava/lang/String; bsm=java/lang/invoke/StringConcatFactory.makeConcatWithConstants (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite; args=[ "structure-free level: \u0001" ]
         // be: invokespecial java/lang/UnsupportedOperationException.<init> (Ljava/lang/String;)V
         // c1: athrow
      };
      return (LevelAccessor)Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class[]{LevelAccessor.class}, handler);
   }
}
