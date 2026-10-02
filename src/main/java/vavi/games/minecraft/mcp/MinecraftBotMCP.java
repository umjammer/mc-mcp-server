/*
 * https://github.com/yuniko-software/minecraft-mcp-server/blob/main/src/bot.ts
 */

package vavi.games.minecraft.mcp;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import vavi.games.minecraft.mcp.MinecraftBot.Block;
import vavi.games.minecraft.mcp.MinecraftBot.Entity;
import vavi.games.minecraft.mcp.MinecraftBot.InventoryItem;

import static java.lang.System.getLogger;
import static vavi.games.minecraft.mcp.MinecraftBot.format;


/**
 * MCP server (stdio) exposing a Minecraft bot as tools.
 *
 * @see "https://claude.ai/chat/d6907144-e54e-4dd9-9e9d-80858d222af6"
 */
public class MinecraftBotMCP {

    private static final Logger logger = getLogger(MinecraftBotMCP.class.getName());

    /** Bot Setup */
    static MinecraftBot setupBot(CommandLine cmd) {
        // Configure bot options based on command line arguments
        String host = cmd.getOptionValue("host", "localhost");
        int port = Integer.parseInt(cmd.getOptionValue("port", "25565"));
        String username = cmd.getOptionValue("username", "LLMBot");

        // Create a bot instance
        MinecraftBot bot = new MinecraftBot(host, port, username);
        try {
            bot.connect();
            bot.sendChat("Claude-powered bot ready to receive instructions!");
        } catch (Exception e) {
            // tools reconnect on demand
            logger.log(Level.WARNING, "Failed to connect: " + e.getMessage());
        }

        return bot;
    }

    /** MCP Server Configuration */
    static McpAsyncServer createMcpServer(MinecraftBot bot) {
        StdioServerTransportProvider provider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        var server = McpServer.async(provider)
                .serverInfo("minecraft-bot", "1.0.0");

        // Register all tool categories
        registerPositionTools(server, bot);
        registerInventoryTools(server, bot);
        registerBlockTools(server, bot);
        registerEntityTools(server, bot);
        registerChatTools(server, bot);
        registerBuildTools(server, bot);

        return server.build();
    }

    static Gson gson = new GsonBuilder().create();

    /**
     * @param properties property name to json schema, a property is required unless it has {@code "optional": true}
     */
    static String createSchema(Map<String, Map<String, ?>> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        if (properties != null) {
            properties.forEach((name, property) -> {
                Map<String, Object> p = new LinkedHashMap<>(property);
                if (!Boolean.TRUE.equals(p.remove("optional"))) required.add(name);
                props.put(name, p);
            });
        }
        schema.put("properties", props);
        if (!required.isEmpty()) schema.put("required", required);
        return gson.toJson(schema);
    }

    static Tool tool(String name, String description, String schema) {
        return Tool.builder(name)
                .description(description)
                .inputSchema(McpJsonDefaults.getMapper(), schema)
                .build();
    }

    static CallToolResult result(String text, boolean isError) {
        return CallToolResult.builder()
                .addContent(TextContent.builder(text).build())
                .isError(isError)
                .build();
    }

    /** runs a (blocking) tool body off the transport thread, exceptions become error results */
    static BiFunction<McpAsyncServerExchange, CallToolRequest, Mono<CallToolResult>> handler(Function<Map<String, Object>, Callable<String>> body) {
        return (exchange, request) -> Mono.fromCallable(() -> {
                    try {
                        return result(body.apply(request.arguments() != null ? request.arguments() : Map.of()).call(), false);
                    } catch (Exception e) {
                        logger.log(Level.DEBUG, request.name() + ": " + e, e);
                        return result(e.getMessage() != null ? e.getMessage() : e.toString(), true);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    static int intArg(Map<String, Object> args, String name, int defaultValue) {
        Object value = args.get(name);
        if (value == null) return defaultValue;
        return value instanceof Number n ? (int) Math.floor(n.doubleValue()) : (int) Math.floor(Double.parseDouble(value.toString()));
    }

    static int intArg(Map<String, Object> args, String name) {
        if (!args.containsKey(name)) throw new IllegalArgumentException("'" + name + "' is required");
        return intArg(args, name, 0);
    }

    static double doubleArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) throw new IllegalArgumentException("'" + name + "' is required");
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
    }

    static String stringArg(Map<String, Object> args, String name, String defaultValue) {
        Object value = args.get(name);
        return value != null ? value.toString() : defaultValue;
    }

    static final Map<String, ?> X = Map.of("type", "number", "description", "X coordinate");
    static final Map<String, ?> Y = Map.of("type", "number", "description", "Y coordinate");
    static final Map<String, ?> Z = Map.of("type", "number", "description", "Z coordinate");

    /** Position and Movement Tools */
    static void registerPositionTools(McpServer.AsyncSpecification<?> server, MinecraftBot bot) {
        server.toolCall(tool(
                        "get-position",
                        "Get the current position of the bot", createSchema(null)),
                handler(args -> () -> {
                    if (!bot.isConnected()) bot.connect();
                    return "Current position: " + format(bot.getPosition()) + ", game mode: " + bot.getGameMode().name().toLowerCase() + ", health: " + bot.getHealth();
                })
        );

        server.toolCall(tool(
                        "move-to-position",
                        "Move the bot to a specific position (walks there using path finding)", createSchema(Map.of(
                        "x", X,
                        "y", Y,
                        "z", Z,
                        "range", Map.of("type", "number", "description", "How close to get to the target (default: 1)", "optional", true)
                ))),
                handler(args -> () -> {
                    int x = intArg(args, "x");
                    int y = intArg(args, "y");
                    int z = intArg(args, "z");
                    int range = intArg(args, "range", 1);

                    double left = bot.moveToPosition(x, y, z, range);

                    if (left > 0) {
                        return "Couldn't reach (" + x + ", " + y + ", " + z + "), got as close as " + String.format("%.1f", left) + " blocks at " + format(bot.getPosition()) +
                                (bot.isChunkLoaded(x, z) ? "" : " (the target is outside the loaded area, call again to continue)");
                    }
                    return "Successfully moved to position near: (" + x + ", " + y + ", " + z + "), now at " + format(bot.getPosition());
                })
        );

        server.toolCall(tool(
                        "look-at",
                        "Make the bot look at a specific position", createSchema(Map.of(
                        "x", X,
                        "y", Y,
                        "z", Z
                ))),
                handler(args -> () -> {
                    double x = doubleArg(args, "x");
                    double y = doubleArg(args, "y");
                    double z = doubleArg(args, "z");

                    bot.lookAt(Vector3d.from(x, y, z));

                    return "Looking at position: (" + x + ", " + y + ", " + z + ")";
                })
        );

        server.toolCall(tool(
                        "jump",
                        "Make the bot jump", createSchema(null)),
                handler(args -> () -> {
                    bot.jump();

                    return "Successfully jumped";
                })
        );

        server.toolCall(tool(
                        "move-in-direction",
                        "Move the bot in a specific direction for a duration", createSchema(Map.of(
                        "direction", Map.of("type", "string", "description", "Direction to move", "enum", Arrays.asList("forward", "back", "left", "right")),
                        "duration", Map.of("type", "number", "description", "Duration in milliseconds (default: 1000)", "optional", true)
                ))),
                handler(args -> () -> {
                    String direction = stringArg(args, "direction", "forward");
                    int duration = intArg(args, "duration", 1000);

                    double moved = bot.moveInDirection(direction, duration);
                    boolean blocked = moved < Math.ceil(duration / (double) MinecraftBot.TICK_MS) * MinecraftBot.WALK_SPEED - 0.5;

                    return "Moved " + direction + " " + String.format("%.1f", moved) + " blocks" + (blocked ? " (stopped by an obstacle)" : "") + ", now at " + format(bot.getPosition());
                })
        );
    }

    /** Inventory Management Tools */
    static void registerInventoryTools(McpServer.AsyncSpecification<?> server, MinecraftBot bot) {
        server.toolCall(tool(
                        "list-inventory",
                        "List all items in the bot's inventory", createSchema(null)),
                handler(args -> () -> {
                    if (!bot.isConnected()) bot.connect();
                    List<InventoryItem> items = bot.getInventory();

                    if (items.isEmpty()) {
                        return "Inventory is empty";
                    }

                    StringBuilder inventoryText = new StringBuilder();
                    inventoryText.append("Found ").append(items.size()).append(" items in inventory:\n\n");

                    for (InventoryItem item : items) {
                        inventoryText.append("- ")
                                .append(item.name())
                                .append(" (x")
                                .append(item.count())
                                .append(") in slot ")
                                .append(item.slot())
                                .append("\n");
                    }

                    InventoryItem held = bot.getHeldItem();
                    inventoryText.append("\nHolding: ").append(held != null ? held.name() : "nothing");

                    return inventoryText.toString();
                })
        );

        server.toolCall(tool(
                        "find-item",
                        "Find a specific item in the bot's inventory", createSchema(Map.of(
                        "nameOrType", Map.of("type", "string", "description", "Name or type of item to find")
                ))),
                handler(args -> () -> {
                    if (!bot.isConnected()) bot.connect();
                    String nameOrType = stringArg(args, "nameOrType", "");
                    InventoryItem item = bot.findItem(nameOrType);

                    if (item != null) {
                        return "Found " + item.count() + " " + item.name() + " in inventory (slot " + item.slot() + ")";
                    } else {
                        return "Couldn't find any item matching '" + nameOrType + "' in inventory";
                    }
                })
        );

        server.toolCall(tool(
                        "equip-item",
                        "Equip a specific item", createSchema(Map.of(
                        "itemName", Map.of("type", "string", "description", "Name of the item to equip"),
                        "destination", Map.of("type", "string", "description", "Where to equip the item (default: 'hand')",
                                "enum", Arrays.asList("hand", "off-hand", "head", "torso", "legs", "feet"),
                                "optional", true)
                ))),
                handler(args -> () -> {
                    String itemName = stringArg(args, "itemName", "");
                    String destination = stringArg(args, "destination", "hand");

                    InventoryItem item = bot.equipItem(itemName, destination);

                    return "Equipped " + item.name() + " to " + destination;
                })
        );
    }

    //  Block Interaction Tools
    static void registerBlockTools(McpServer.AsyncSpecification<?> server, MinecraftBot bot) {
        server.toolCall(tool(
                        "place-block",
                        "Place the held block at the specified position (walks within reach if needed)", createSchema(Map.of(
                        "x", X,
                        "y", Y,
                        "z", Z,
                        "faceDirection", Map.of("type", "string", "description", "Side of the target where the block to place against is (default: 'down', i.e. on top of the block below)",
                                "enum", Arrays.asList("up", "down", "north", "south", "east", "west"),
                                "optional", true)
                ))),
                handler(args -> () -> {
                    int x = intArg(args, "x");
                    int y = intArg(args, "y");
                    int z = intArg(args, "z");
                    String faceDirectionStr = stringArg(args, "faceDirection", "down");
                    Direction faceDirection = Direction.valueOf(faceDirectionStr.toUpperCase());

                    Block block = bot.placeBlock(x, y, z, faceDirection);

                    return "Placed " + block.name() + " at (" + x + ", " + y + ", " + z + ")";
                })
        );

        server.toolCall(tool(
                        "dig-block",
                        "Dig a block at the specified position (walks within reach if needed)", createSchema(Map.of(
                        "x", X,
                        "y", Y,
                        "z", Z
                ))),
                handler(args -> () -> {
                    int x = intArg(args, "x");
                    int y = intArg(args, "y");
                    int z = intArg(args, "z");

                    Block block = bot.digBlock(x, y, z);

                    return "Dug " + block.name() + " at (" + x + ", " + y + ", " + z + ")";
                })
        );

        server.toolCall(tool(
                        "get-block-info",
                        "Get information about a block at the specified position", createSchema(Map.of(
                        "x", X,
                        "y", Y,
                        "z", Z
                ))),
                handler(args -> () -> {
                    int x = intArg(args, "x");
                    int y = intArg(args, "y");
                    int z = intArg(args, "z");

                    Block block = bot.getBlockAt(x, y, z);

                    if (block == null) {
                        return "No block information found at position (" + x + ", " + y + ", " + z + "), the chunk is not loaded";
                    }

                    return "Found " + block.name() + " (type: " + block.type() + ") at position ("
                            + block.position().getX() + ", " + block.position().getY() + ", " + block.position().getZ() + ")";
                })
        );

        server.toolCall(tool(
                        "find-block",
                        "Find the nearest block of a specific type", createSchema(Map.of(
                        "blockType", Map.of("type", "string", "description", "Type of block to find"),
                        "maxDistance", Map.of("type", "number", "description", "Maximum search distance (default: 16)", "optional", true)
                ))),
                handler(args -> () -> {
                    String blockType = stringArg(args, "blockType", "");
                    int maxDistance = intArg(args, "maxDistance", 16);

                    Block block = bot.findBlock(blockType, maxDistance);

                    if (block == null) {
                        return "No " + blockType + " found within " + maxDistance + " blocks";
                    }

                    return "Found " + block.name() + " at position ("
                            + block.position().getX() + ", " + block.position().getY() + ", " + block.position().getZ() + ")";
                })
        );
    }

    static final Map<String, ?> SCAFFOLD = Map.of("type", "string", "description", "Block for temporary pillars to reach high positions, removed afterwards (default: 'dirt')", "optional", true);
    static final Map<String, ?> MAX_SECONDS = Map.of("type", "number", "description", "Time limit, call again to continue the rest (default: 300)", "optional", true);

    static String summarize(MinecraftBot.BatchResult r, String verb) {
        StringBuilder sb = new StringBuilder();
        sb.append(verb).append(" ").append(r.done()).append(" blocks, ").append(r.alreadyDone()).append(" were already done, ")
                .append(r.remaining()).append(" remaining");
        if (r.timedOut()) sb.append(" (time limit reached, call again to continue)");
        if (!r.missingItems().isEmpty()) {
            sb.append("\nMissing items: ").append(r.missingItems().entrySet().stream()
                    .map(e -> e.getKey() + " x" + e.getValue()).collect(Collectors.joining(", ")));
        }
        if (!r.problems().isEmpty()) {
            sb.append("\nProblems:\n- ").append(String.join("\n- ", r.problems().subList(0, Math.min(20, r.problems().size()))));
            if (r.problems().size() > 20) sb.append("\n- ... and ").append(r.problems().size() - 20).append(" more");
        }
        return sb.toString();
    }

    /** Building Tools */
    static void registerBuildTools(McpServer.AsyncSpecification<?> server, MinecraftBot bot) {
        server.toolCall(tool(
                        "build-blocks",
                        "Place many blocks in one call. The bot equips items, orders the placements so every block has a neighbor " +
                                "to place against, walks within reach and builds temporary pillars for high positions. " +
                                "Blocks already in place are skipped, so the same list can be sent again to resume.", createSchema(Map.of(
                        "blocks", Map.of("type", "array", "description", "Blocks to place",
                                "items", Map.of("type", "object",
                                        "properties", Map.of("x", X, "y", Y, "z", Z, "block", Map.of("type", "string", "description", "Item name, e.g. stone_bricks")),
                                        "required", List.of("x", "y", "z", "block"))),
                        "scaffoldBlock", SCAFFOLD,
                        "maxSeconds", MAX_SECONDS
                ))),
                handler(args -> () -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> blocks = (List<Map<String, Object>>) args.get("blocks");
                    if (blocks == null) throw new IllegalArgumentException("'blocks' is required");
                    List<MinecraftBot.Placement> placements = blocks.stream()
                            .map(b -> new MinecraftBot.Placement(Vector3i.from(intArg(b, "x"), intArg(b, "y"), intArg(b, "z")), stringArg(b, "block", "")))
                            .toList();

                    var result = bot.buildBlocks(placements, stringArg(args, "scaffoldBlock", "dirt"), intArg(args, "maxSeconds", 300) * 1000L);

                    return summarize(result, "Placed");
                })
        );

        server.toolCall(tool(
                        "fill-region",
                        "Fill a box (both corners inclusive) with a block by placing them one by one like build-blocks", createSchema(Map.of(
                        "x1", X, "y1", Y, "z1", Z,
                        "x2", X, "y2", Y, "z2", Z,
                        "block", Map.of("type", "string", "description", "Item name, e.g. stone_bricks"),
                        "mode", Map.of("type", "string", "description", "solid: whole box, hollow: outer shell, walls: four sides without floor and ceiling (default: solid)",
                                "enum", List.of("solid", "hollow", "walls"), "optional", true),
                        "scaffoldBlock", SCAFFOLD,
                        "maxSeconds", MAX_SECONDS
                ))),
                handler(args -> () -> {
                    Vector3i a = Vector3i.from(intArg(args, "x1"), intArg(args, "y1"), intArg(args, "z1"));
                    Vector3i b = Vector3i.from(intArg(args, "x2"), intArg(args, "y2"), intArg(args, "z2"));
                    Vector3i min = a.min(b), max = a.max(b);
                    String block = stringArg(args, "block", "");
                    String mode = stringArg(args, "mode", "solid");
                    List<MinecraftBot.Placement> placements = new ArrayList<>();
                    for (int y = min.getY(); y <= max.getY(); y++) {
                        for (int x = min.getX(); x <= max.getX(); x++) {
                            for (int z = min.getZ(); z <= max.getZ(); z++) {
                                boolean side = x == min.getX() || x == max.getX() || z == min.getZ() || z == max.getZ();
                                boolean include = switch (mode) {
                                    case "solid" -> true;
                                    case "hollow" -> side || y == min.getY() || y == max.getY();
                                    case "walls" -> side;
                                    default -> throw new IllegalArgumentException("Unknown mode: " + mode);
                                };
                                if (include) placements.add(new MinecraftBot.Placement(Vector3i.from(x, y, z), block));
                            }
                        }
                    }

                    var result = bot.buildBlocks(placements, stringArg(args, "scaffoldBlock", "dirt"), intArg(args, "maxSeconds", 300) * 1000L);

                    return summarize(result, "Placed");
                })
        );

        server.toolCall(tool(
                        "clear-region",
                        "Dig every block in a box (both corners inclusive) from the top, e.g. to cut trees or flatten ground", createSchema(Map.of(
                        "x1", X, "y1", Y, "z1", Z,
                        "x2", X, "y2", Y, "z2", Z,
                        "only", Map.of("type", "array", "items", Map.of("type", "string"), "optional", true,
                                "description", "Dig only blocks whose names contain one of these, e.g. [\"_log\"] to cut trees and let the leaves decay (default: all)"),
                        "scaffoldBlock", SCAFFOLD,
                        "maxSeconds", MAX_SECONDS
                ))),
                handler(args -> () -> {
                    Vector3i a = Vector3i.from(intArg(args, "x1"), intArg(args, "y1"), intArg(args, "z1"));
                    Vector3i b = Vector3i.from(intArg(args, "x2"), intArg(args, "y2"), intArg(args, "z2"));
                    @SuppressWarnings("unchecked")
                    List<String> only = args.get("only") instanceof List<?> l ? (List<String>) l : List.of();

                    var result = bot.clearRegion(a, b, only, stringArg(args, "scaffoldBlock", "dirt"), intArg(args, "maxSeconds", 300) * 1000L);

                    return summarize(result, "Dug");
                })
        );

        server.toolCall(tool(
                        "scan-area",
                        "Get a height map of the top blocks in an area (at most 64x64) to plan building", createSchema(Map.of(
                        "x1", X, "z1", Z,
                        "x2", X, "z2", Z,
                        "ground", Map.of("type", "boolean", "description", "Ignore trees to see the ground (default: false)", "optional", true)
                ))),
                handler(args -> () -> bot.scanArea(intArg(args, "x1"), intArg(args, "z1"), intArg(args, "x2"), intArg(args, "z2"),
                        Boolean.parseBoolean(stringArg(args, "ground", "false"))))
        );
    }

    //  Entity Interaction Tools
    static void registerEntityTools(McpServer.AsyncSpecification<?> server, MinecraftBot bot) {
        server.toolCall(tool(
                        "find-entity",
                        "Find the nearest entity of a specific type", createSchema(Map.of(
                        "type", Map.of("type", "string", "description", "Type of entity to find (empty for any entity)", "optional", true),
                        "maxDistance", Map.of("type", "number", "description", "Maximum search distance (default: 16)", "optional", true)
                ))),
                handler(args -> () -> {
                    String type = stringArg(args, "type", "");
                    int maxDistance = intArg(args, "maxDistance", 16);

                    Entity entity = bot.findNearestEntity(type, maxDistance);

                    if (entity == null) {
                        return "No " + (type.isEmpty() ? "entity" : type) + " found within " + maxDistance + " blocks";
                    }

                    return "Found " + entity.name() + " (" + entity.type() + ") at position " + format(entity.position());
                })
        );
    }

    /** Chat Tool */
    static void registerChatTools(McpServer.AsyncSpecification<?> server, MinecraftBot bot) {
        server.toolCall(tool(
                        "send-chat",
                        "Send a chat message in-game, or a command if it starts with '/'", createSchema(Map.of(
                        "message", Map.of("type", "string", "description", "Message to send in chat")
                ))),
                handler(args -> () -> {
                    String message = stringArg(args, "message", "");
                    List<String> responses = bot.sendChat(message);

                    return "Sent message: \"" + message + "\"" + (responses.isEmpty() ? "" : "\nServer messages:\n" + String.join("\n", responses));
                })
        );
    }

    /** Main Application */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            Options options = new Options();
            options.addOption("h", "host", true, "Minecraft server host");
            options.addOption("p", "port", true, "Minecraft server port");
            options.addOption("u", "username", true, "Bot username");
            options.addOption("help", false, "Show help");

            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("MinecraftBotMCP", options);
                System.exit(0);
            }

            // Set up the Minecraft bot
            MinecraftBot bot = setupBot(cmd);

            // Create and configure MCP server
            var server = createMcpServer(bot);

            // Handle stdin end - this will detect when Claude Desktop is closed
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.log(Level.DEBUG, "Claude has disconnected. Shutting down...");
                bot.disconnect();
            }));

            // Connect to the transport
            logger.log(Level.DEBUG, "Minecraft MCP Server running on stdio");

            // Keep the main thread alive
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.log(Level.ERROR, "Failed to start server: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}
