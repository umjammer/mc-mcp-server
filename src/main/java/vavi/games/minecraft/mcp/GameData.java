/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.minecraft.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Static game data (block states, items) for the protocol version mcprotocollib speaks.
 * <p>
 * Resources are generated from the vanilla server's data reports
 * ({@code java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports})
 * and collision shapes from PrismarineJS minecraft-data.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-04-10 nsano initial version <br>
 */
final class GameData {

    private GameData() {}

    /** block name and its state id range */
    record BlockInfo(String name, int minStateId, int maxStateId, int defaultStateId, float[] collisionHeights) {

        /** @return the height of the collision shape top, 0 for no collision */
        float collisionHeight(int stateId) {
            return collisionHeights.length == 1 ? collisionHeights[0] : collisionHeights[stateId - minStateId];
        }
    }

    private static final List<BlockInfo> blocks = new ArrayList<>();
    /** index: minStateId of {@link #blocks}, for binary search */
    private static int[] minStateIds;
    private static final Map<String, BlockInfo> blocksByName = new HashMap<>();

    private static final List<String> items = new ArrayList<>();
    private static final Map<String, Integer> itemsByName = new HashMap<>();

    static {
        for (String line : lines("blocks.tsv")) {
            String[] c = line.split("\t");
            String[] hs = c[4].split(",");
            float[] heights = new float[hs.length];
            for (int i = 0; i < hs.length; i++) heights[i] = Float.parseFloat(hs[i]);
            BlockInfo info = new BlockInfo(c[0], Integer.parseInt(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3]), heights);
            blocks.add(info);
            blocksByName.put(info.name(), info);
        }
        blocks.sort((a, b) -> Integer.compare(a.minStateId(), b.minStateId()));
        minStateIds = blocks.stream().mapToInt(BlockInfo::minStateId).toArray();

        for (String line : lines("items.txt")) {
            itemsByName.put(line, items.size());
            items.add(line);
        }
    }

    private static List<String> lines(String name) {
        try (InputStream is = GameData.class.getResourceAsStream(name)) {
            if (is == null) throw new IllegalStateException("resource not found: " + name);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            return reader.lines().filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return null when unknown */
    static BlockInfo block(int stateId) {
        int i = Arrays.binarySearch(minStateIds, stateId);
        if (i < 0) i = -i - 2;
        if (i < 0) return null;
        BlockInfo info = blocks.get(i);
        return stateId <= info.maxStateId() ? info : null;
    }

    /** @param name without "minecraft:" */
    static BlockInfo block(String name) {
        return blocksByName.get(stripNamespace(name));
    }

    static String blockName(int stateId) {
        BlockInfo info = block(stateId);
        return info != null ? info.name() : "unknown(" + stateId + ")";
    }

    /** @return the height of the collision shape top (0 .. 1.5), 0 for no collision */
    static float collisionHeight(int stateId) {
        BlockInfo info = block(stateId);
        return info != null ? info.collisionHeight(stateId) : 1;
    }

    static boolean isAir(int stateId) {
        String name = blockName(stateId);
        return name.equals("air") || name.equals("cave_air") || name.equals("void_air");
    }

    static boolean isDangerous(int stateId) {
        String name = blockName(stateId);
        return name.equals("lava") || name.equals("fire") || name.equals("soul_fire") || name.equals("magma_block")
                || name.contains("campfire") || name.equals("sweet_berry_bush") || name.equals("cactus") || name.equals("powder_snow");
    }

    static boolean isFluid(int stateId) {
        String name = blockName(stateId);
        return name.equals("water") || name.equals("lava") || name.equals("bubble_column");
    }

    static String itemName(int id) {
        return id >= 0 && id < items.size() ? items.get(id) : "unknown(" + id + ")";
    }

    /** @return -1 when unknown */
    static int itemId(String name) {
        return itemsByName.getOrDefault(stripNamespace(name), -1);
    }

    /** @return true if a block with the item's name exists, i.e. the item can be placed */
    static boolean isBlockItem(int itemId) {
        return blocksByName.containsKey(itemName(itemId));
    }

    static String stripNamespace(String name) {
        name = name.toLowerCase();
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }
}
