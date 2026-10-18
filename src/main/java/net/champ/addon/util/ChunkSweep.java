package net.champ.addon.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Incrementele blok-scan over geladen chunks rond de speler.
 *
 * In plaats van miljoenen getBlockState-aanroepen in één tick verwerkt dit een paar chunks per tick
 * en slaat het hele secties over waar het palet het gezochte blok niet eens bevat
 * (LevelChunkSection#maybeHas). Een volledige ronde duurt een aantal ticks; step() geeft true terug
 * als er net een ronde is afgerond.
 */
public final class ChunkSweep {
    private final Predicate<BlockState> filter;
    private final int chunksPerTick;
    private final int yRange;

    private boolean running;
    private int baseX, baseY, baseZ, radius, index, total;

    public ChunkSweep(Predicate<BlockState> filter, int chunksPerTick, int yRange) {
        this.filter = filter;
        this.chunksPerTick = Math.max(1, chunksPerTick);
        this.yRange = yRange;
    }

    public void reset() { running = false; }

    public boolean step(Level level, BlockPos center, int radiusChunks, BiConsumer<BlockPos, BlockState> hit) {
        if (!running) {
            baseX = center.getX() >> 4;
            baseY = center.getY();
            baseZ = center.getZ() >> 4;
            radius = Math.max(1, radiusChunks);
            int width = radius * 2 + 1;
            total = width * width;
            index = 0;
            running = true;
        }
        int width = radius * 2 + 1;
        for (int n = 0; n < chunksPerTick && index < total; n++, index++) {
            int cx = baseX - radius + index % width;
            int cz = baseZ - radius + index / width;
            ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk != null) scanChunk(chunk, cx, cz, hit);
        }
        if (index >= total) { running = false; return true; }
        return false;
    }

    private void scanChunk(ChunkAccess chunk, int cx, int cz, BiConsumer<BlockPos, BlockState> hit) {
        int minY = baseY - yRange, maxY = baseY + yRange;
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSectionY();
        for (int i = 0; i < sections.length; i++) {
            int y0 = (minSection + i) << 4;
            if (y0 + 15 < minY || y0 > maxY) continue;
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(filter)) continue;
            int yFrom = Math.max(0, minY - y0), yTo = Math.min(15, maxY - y0);
            for (int y = yFrom; y <= yTo; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (filter.test(state)) hit.accept(new BlockPos((cx << 4) + x, y0 + y, (cz << 4) + z), state);
                    }
                }
            }
        }
    }
}
