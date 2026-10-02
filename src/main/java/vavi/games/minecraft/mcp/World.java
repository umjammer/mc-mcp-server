/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.minecraft.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;


/**
 * Client side copy of the loaded chunks of the current dimension.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-04-10 nsano initial version <br>
 */
class World {

    /** returned for positions in unloaded chunks */
    static final int UNKNOWN = -1;

    private final Map<Long, ChunkSection[]> chunks = new ConcurrentHashMap<>();

    private volatile int minY = -64;
    private volatile int height = 384;

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    /** called on dimension change */
    void reset(int minY, int height) {
        chunks.clear();
        this.minY = minY;
        this.height = height;
    }

    int minY() {
        return minY;
    }

    int maxY() {
        return minY + height - 1;
    }

    void loadChunk(int chunkX, int chunkZ, byte[] data) {
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        List<ChunkSection> sections = new ArrayList<>();
        int count = height >> 4;
        for (int i = 0; i < count && buf.isReadable(); i++) {
            sections.add(MinecraftTypes.readChunkSection(buf));
        }
        chunks.put(key(chunkX, chunkZ), sections.toArray(ChunkSection[]::new));
    }

    void unloadChunk(int chunkX, int chunkZ) {
        chunks.remove(key(chunkX, chunkZ));
    }

    boolean isLoaded(int x, int z) {
        return chunks.containsKey(key(x >> 4, z >> 4));
    }

    /** @return block state id, {@link #UNKNOWN} if the chunk is not loaded */
    int getBlock(int x, int y, int z) {
        if (y < minY || y > maxY()) return 0; // air
        ChunkSection[] sections = chunks.get(key(x >> 4, z >> 4));
        if (sections == null) return UNKNOWN;
        int index = (y - minY) >> 4;
        if (index >= sections.length) return 0;
        synchronized (sections) { // palette may be resized by netty thread
            return sections[index].getBlock(x & 15, y & 15, z & 15);
        }
    }

    void setBlock(int x, int y, int z, int state) {
        if (y < minY || y > maxY()) return;
        ChunkSection[] sections = chunks.get(key(x >> 4, z >> 4));
        if (sections == null) return;
        int index = (y - minY) >> 4;
        if (index >= sections.length) return;
        synchronized (sections) {
            sections[index].setBlock(x & 15, y & 15, z & 15, state);
        }
    }
}
