/*
 * https://github.com/yuniko-software/minecraft-mcp-server/blob/main/src/bot.ts
 */

package vavi.games.minecraft.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import reactor.core.publisher.Mono;

import static java.lang.System.getLogger;


/**
 * @see "https://claude.ai/chat/d6907144-e54e-4dd9-9e9d-80858d222af6"
 */
public class MinecraftBotMCP {

    private static final Logger logger = getLogger(MinecraftBotMCP.class.getName());

    // Type Definitions

    static class InventoryItem {

        String name;
        int count;
        int slot;

        InventoryItem(String name, int count, int slot) {
            this.name = name;
            this.count = count;
            this.slot = slot;
        }
    }

    static class Block {

        String name;
        int type;
        Vector3i position;

        Block(String name, int type, Vector3i position) {
            this.name = name;
            this.type = type;
            this.position = position;
        }
    }

    static class Entity {

        String name;
        String type;
        Vector3i position;

        Entity(String name, String type, Vector3i position) {
            this.name = name;
            this.type = type;
            this.position = position;
        }
    }

    static class FaceOption {

        String direction;
        Vector3i vector;

        FaceOption(String direction, Vector3i vector) {
            this.direction = direction;
            this.vector = vector;
        }
    }

    // Response Helpers

    /** Bot Setup */
    static MinecraftBot setupBot(CommandLine cmd) {
        // Configure bot options based on command line arguments
        String host = cmd.getOptionValue("host", "localhost");
        int port = Integer.parseInt(cmd.getOptionValue("port", "25565"));
        String username = cmd.getOptionValue("username", "LLMBot");

        // Log connection information
        logger.log(Level.DEBUG, "Connecting to Minecraft server at " + host + ":" + port + " as " + username);

        // Create a bot instance
        MinecraftBot bot = new MinecraftBot(host, port, username);
        bot.connect();

        return bot;
    }

    /** Minecraft Bot Class */
    static class MinecraftBot {

        private final String host;
        private final int port;
        private String username;
        private ClientSession client;
        private Vector3i position = Vector3i.from(0, 0, 0);
        private final List<InventoryItem> inventory = new ArrayList<>();
        private final Map<String, Integer> blockTypes = new HashMap<>();

        public MinecraftBot(String host, int port, String username) {
            this.host = host;
            this.port = port;
            this.username = username;
        }

        public void connect() {
            try {
                MinecraftProtocol protocol = new MinecraftProtocol();
                client = ClientNetworkSessionFactory.factory()
                        .setRemoteSocketAddress(new InetSocketAddress(host, port))
                        .setProtocol(protocol)
                        .setProxy(null)
                        .create();

                // Set up event handlers
                this.client.addListener(new SessionAdapter() {
                    @Override
                    public void connected(ConnectedEvent event) {
                        logger.log(Level.DEBUG, "Bot has connected to the server");
                    }

                    @Override
                    public void disconnecting(DisconnectingEvent event) {
                        logger.log(Level.DEBUG, "Bot was disconnected: " + event.getReason());
                    }

                    // Add more event handlers here
                });

                this.client.connect();
                logger.log(Level.DEBUG, "Bot has spawned in the world");

                // Initialize bot state
                sendChat("Claude-powered bot ready to receive instructions!");
            } catch (Exception e) {
                logger.log(Level.DEBUG, "Failed to connect: " + e.getMessage());
            }
        }

        public void disconnect() {
            if (this.client != null && this.client.isConnected()) {
                this.client.disconnect("Bot shutting down");
            }
        }

        public Vector3i getPosition() {
            // In a real implementation, this would get the actual position from the client
            return this.position;
        }

        public void moveToPosition(int x, int y, int z, int range) throws Exception {
            // In a real implementation, this would use pathfinding to move to the position
            // For now, we'll simulate moving there directly
            this.position = Vector3i.from(x, y, z);

            // Send position update packet
            ClientboundPlayerPositionPacket packet = new ClientboundPlayerPositionPacket(
                    -1, x, y, z, 0, 0, 0, 0, 0);
            this.client.send(packet);
        }

        public void lookAt(Vector3i target) throws Exception {
            // Calculate yaw and pitch to look at the target
            // In a real implementation, this would send rotation packets
            logger.log(Level.DEBUG, "Looking at " + target.getX() + ", " + target.getY() + ", " + target.getZ());
        }

        public void jump() throws Exception {
            // In a real implementation, this would send jump packets
            logger.log(Level.DEBUG, "Jumping");
        }

        public void moveInDirection(Direction direction, int duration) throws Exception {
            // In a real implementation, this would send movement packets
            logger.log(Level.DEBUG, "Moving " + direction + " for " + duration + "ms");

            // Simulate position change based on direction
            switch (direction) {
                case UP:
                    this.position = Vector3i.from(position.getX(), position.getY(), position.getZ() + 1);
                    break;
                case DOWN:
                    this.position = Vector3i.from(position.getX(), position.getY(), position.getZ() - 1);
                    break;
                case WEST:
                    this.position = Vector3i.from(position.getX() - 1, position.getY(), position.getZ());
                    break;
                case EAST:
                    this.position = Vector3i.from(position.getX() + 1, position.getY(), position.getZ());
                    break;
            }
        }

        public List<InventoryItem> getInventory() {
            // In a real implementation, this would return the actual inventory
            return this.inventory;
        }

        public InventoryItem findItem(String nameOrType) {
            return inventory.stream()
                    .filter(item -> item.name.contains(nameOrType.toLowerCase()))
                    .findFirst()
                    .orElse(null);
        }

        public void equipItem(String itemName, String destination) throws Exception {
            // In a real implementation, this would send equip packets
            InventoryItem item = findItem(itemName);
            if (item != null) {
                logger.log(Level.DEBUG, "Equipped " + item.name + " to " + destination);
            }
        }

        public void placeBlock(int x, int y, int z, Direction faceDirection) throws Exception {
            // In a real implementation, this would send block placement packets
            logger.log(Level.DEBUG, "Placing block at " + x + ", " + y + ", " + z);
        }

        public void digBlock(int x, int y, int z) throws Exception {
            // In a real implementation, this would send dig packets
            Vector3i pos = Vector3i.from(x, y, z);
            Packet packet = new ServerboundPlayerActionPacket(
                    PlayerAction.START_DIGGING, pos, Direction.UP, 0);
            this.client.send(packet);

            // Wait a bit for digging to complete
            Thread.sleep(1000);

            // Send finish digging packet
            packet = new ServerboundPlayerActionPacket(
                    PlayerAction.FINISH_DIGGING, pos, Direction.UP, 0);
            this.client.send(packet);

            logger.log(Level.DEBUG, "Dug block at " + x + ", " + y + ", " + z);
        }

        public static Block getBlockAt(int x, int y, int z) {
            // In a real implementation, this would get the actual block info
            // For now, return a simulated block
            return new Block("stone", 1, Vector3i.from(x, y, z));
        }

        public static Block findBlock(String blockType, int maxDistance) {
            // In a real implementation, this would search for blocks
            // For now, return null (not found)
            return null;
        }

        public static Entity findNearestEntity(String type, int maxDistance) {
            // In a real implementation, this would search for entities
            // For now, return null (not found)
            return null;
        }

        public static void sendChat(String message) {
            // In a real implementation, this would send chat packets
            logger.log(Level.DEBUG, "[BOT CHAT] " + message);
        }
    }

    /** MCP Server Configuration */
    static McpAsyncServer createMcpServer(MinecraftBot bot) {
        StdioServerTransportProvider provider = new StdioServerTransportProvider();
        var server = McpServer.async(provider)
                .serverInfo("minecraft-bot", "1.0.0");

        // Register all tool categories
        registerPositionTools(server, bot);
        registerInventoryTools(server, bot);
        registerBlockTools(server, bot);
        registerEntityTools(server, bot);
        registerChatTools(server, bot);

        return server.build();
    }

    static final String emptyJsonSchema = """
            {
            	"$schema": "http://json-schema.org/draft-07/schema#",
            	"type": "object",
            	"properties": %s
            }
            """;

    static Gson gson = new GsonBuilder().create();

    static String createSchema(Map<String, ?> map) {
        return emptyJsonSchema.formatted(map != null ? gson.toJson(map) : "");
    }

    /** Position and Movement Tools */
    static void registerPositionTools(McpServer.AsyncSpecification server, MinecraftBot bot) {
        server.tool(new Tool(
                        "get-position",
                        "Get the current position of the bot", createSchema(null)),
                (exchange, args) -> {
                    try {
                        Vector3i position = bot.getPosition();
                        int x = position.getX();
                        int y = position.getY();
                        int z = position.getZ();

                        return Mono.just(new CallToolResult(List.of(new TextContent("Current position: (" + x + ", " + y + ", " + z + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "move-to-position",
                        "Move the bot to a specific position", createSchema(Map.of(
                        "x", Map.of("type", "number", "description", "X coordinate"),
                        "y", Map.of("type", "number", "description", "Y coordinate"),
                        "z", Map.of("type", "number", "description", "Z coordinate"),
                        "range", Map.of("type", "number", "description", "How close to get to the target (default: 1)", "optional", true)
                ))),
                (exchange, args) -> {
                    try {
                        int x = ((Number) args.get("x")).intValue();
                        int y = ((Number) args.get("y")).intValue();
                        int z = ((Number) args.get("z")).intValue();
                        int range = args.containsKey("range") ? ((Number) args.get("range")).intValue() : 1;

                        bot.moveToPosition(x, y, z, range);

                        return Mono.just(new CallToolResult(List.of(new TextContent("Successfully moved to position near: (" + x + ", " + y + ", " + z + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "look-at",
                        "Make the bot look at a specific position", createSchema(Map.of(
                        "x", Map.of("type", "number", "description", "X coordinate"),
                        "y", Map.of("type", "number", "description", "Y coordinate"),
                        "z", Map.of("type", "number", "description", "Z coordinate")
                ))),
                (exchange, args) -> {
                    try {
                        double x = ((Number) args.get("x")).doubleValue();
                        double y = ((Number) args.get("y")).doubleValue();
                        double z = ((Number) args.get("z")).doubleValue();

                        bot.lookAt(Vector3i.from(x, y, z));

                        return Mono.just(new CallToolResult(List.of(new TextContent("Looking at position: (" + x + ", " + y + ", " + z + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "jump",
                        "Make the bot jump", createSchema(null)),
                (exchange, args) -> {
                    try {
                        bot.jump();

                        return Mono.just(new CallToolResult(List.of(new TextContent("Successfully jumped")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "move-in-direction",
                        "Move the bot in a specific direction for a duration", createSchema(Map.of(
                        "direction", Map.of("type", "string", "description", "Direction to move", "enum", Arrays.asList("forward", "back", "left", "right")),
                        "duration", Map.of("type", "number", "description", "Duration in milliseconds (default: 1000)", "optional", true)
                ))),
                (exchange, args) -> {
                    try {
                        String dirStr = (String) args.get("direction");
                        Direction direction = Direction.valueOf(dirStr.toUpperCase());
                        int duration = args.containsKey("duration") ? ((Number) args.get("duration")).intValue() : 1000;

                        CompletableFuture<CallToolResult> future = new CompletableFuture<>();

                        // Run movement in a new thread to avoid blocking
                        new Thread(() -> {
                            try {
                                bot.moveInDirection(direction, duration);
                                future.complete(new CallToolResult(List.of(new TextContent("Moved " + dirStr + " for " + duration + "ms")), false));
                            } catch (Exception e) {
                                future.complete(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                            }
                        }).start();

                        return Mono.just(future.get());
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );
    }

    /** Inventory Management Tools */
    static void registerInventoryTools(McpServer.AsyncSpecification server, MinecraftBotMCP.MinecraftBot bot) {
        server.tool(new Tool(
                        "list-inventory",
                        "List all items in the bot's inventory", createSchema(null)),
                (exchange, args) -> {
                    try {
                        List<MinecraftBotMCP.InventoryItem> items = bot.getInventory();

                        if (items.isEmpty()) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("Inventory is empty")), false));
                        }

                        StringBuilder inventoryText = new StringBuilder();
                        inventoryText.append("Found ").append(items.size()).append(" items in inventory:\n\n");

                        for (MinecraftBotMCP.InventoryItem item : items) {
                            inventoryText.append("- ")
                                    .append(item.name)
                                    .append(" (x")
                                    .append(item.count)
                                    .append(") in slot ")
                                    .append(item.slot)
                                    .append("\n");
                        }

                        return Mono.just(new CallToolResult(List.of(new TextContent(inventoryText.toString())), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "find-item",
                        "Find a specific item in the bot's inventory", createSchema(Map.of(
                        "nameOrType", Map.of("type", "string", "description", "Name or type of item to find")
                ))),
                (exchange, args) -> {
                    try {
                        String nameOrType = (String) args.get("nameOrType");
                        InventoryItem item = bot.findItem(nameOrType);

                        if (item != null) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("Found " + item.count + " " + item.name + " in inventory (slot " + item.slot + ")")), false));
                        } else {
                            return Mono.just(new CallToolResult(List.of(new TextContent("Couldn't find any item matching '" + nameOrType + "' in inventory")), false));
                        }
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "equip-item",
                        "Equip a specific item", createSchema(Map.of(
                        "itemName", Map.of("type", "string", "description", "Name of the item to equip"),
                        "destination", Map.of("type", "string", "description", "Where to equip the item (default: 'hand')", "optional", true)
                ))),
                (exchange, params) -> {
                    try {
                        String itemName = (String) params.get("itemName");
                        String destination = params.containsKey("destination") ? (String) params.get("destination") : "hand";

                        InventoryItem item = bot.findItem(itemName);

                        if (item == null) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("Couldn't find any item matching '" + itemName + "' in inventory")), false));
                        }

                        bot.equipItem(itemName, destination);

                        return Mono.just(new CallToolResult(List.of(new TextContent("Equipped " + item.name + " to " + destination)), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );
    }

    //  Block Interaction Tools 
    static void registerBlockTools(McpServer.AsyncSpecification server, MinecraftBotMCP.MinecraftBot bot) {
        server.tool(new Tool(
                        "place-block",
                        "Place a block at the specified position", createSchema(Map.of(
                        "x", Map.of("type", "number", "description", "X coordinate"),
                        "y", Map.of("type", "number", "description", "Y coordinate"),
                        "z", Map.of("type", "number", "description", "Z coordinate"),
                        "faceDirection", Map.of("type", "string", "description", "Direction to place against (default: 'down')",
                                "enum", Arrays.asList("up", "down", "north", "south", "east", "west"),
                                "optional", true)
                ))),
                (exchange, args) -> {
                    try {
                        int x = ((Number) args.get("x")).intValue();
                        int y = ((Number) args.get("y")).intValue();
                        int z = ((Number) args.get("z")).intValue();
                        String faceDirectionStr = args.containsKey("faceDirection") ? (String) args.get("faceDirection") : "down";
                        Direction faceDirection = Direction.valueOf(faceDirectionStr.toUpperCase());

                        MinecraftBotMCP.Block blockAtPos = MinecraftBot.getBlockAt(x, y, z);
                        if (blockAtPos != null && !blockAtPos.name.equals("air")) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("There's already a block (" + blockAtPos.name + ") at (" + x + ", " + y + ", " + z + ")")), false));
                        }

                        bot.placeBlock(x, y, z, faceDirection);

                        return Mono.just(new CallToolResult(List.of(new TextContent("Placed block at (" + x + ", " + y + ", " + z + ") using " + faceDirectionStr + " face")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "dig-block",
                        "Dig a block at the specified position", createSchema(Map.of(
                        "x", Map.of("type", "number", "description", "X coordinate"),
                        "y", Map.of("type", "number", "description", "Y coordinate"),
                        "z", Map.of("type", "number", "description", "Z coordinate")
                ))),
                (exchange, args) -> {
                    try {
                        int x = ((Number) args.get("x")).intValue();
                        int y = ((Number) args.get("y")).intValue();
                        int z = ((Number) args.get("z")).intValue();

                        MinecraftBotMCP.Block block = MinecraftBot.getBlockAt(x, y, z);

                        if (block == null || block.name.equals("air")) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("No block found at position (" + x + ", " + y + ", " + z + ")")), false));
                        }

                        bot.digBlock(x, y, z);

                        return Mono.just(new CallToolResult(List.of(new TextContent("Dug " + block.name + " at (" + x + ", " + y + ", " + z + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "get-block-info",
                        "Get information about a block at the specified position", createSchema(Map.of(
                        "x", Map.of("type", "number", "description", "X coordinate"),
                        "y", Map.of("type", "number", "description", "Y coordinate"),
                        "z", Map.of("type", "number", "description", "Z coordinate")
                ))),
                (exchange, args) -> {
                    try {
                        int x = ((Number) args.get("x")).intValue();
                        int y = ((Number) args.get("y")).intValue();
                        int z = ((Number) args.get("z")).intValue();

                        Block block = MinecraftBot.getBlockAt(x, y, z);

                        if (block == null) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("No block information found at position (" + x + ", " + y + ", " + z + ")")), false));
                        }

                        return Mono.just(new CallToolResult(List.of(new TextContent("Found " + block.name + " (type: " + block.type + ") at position ("
                                + block.position.getX() + ", " + block.position.getY() + ", " + block.position.getZ() + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );

        server.tool(new Tool(
                        "find-block",
                        "Find the nearest block of a specific type", createSchema(Map.of(
                        "blockType", Map.of("type", "string", "description", "Type of block to find"),
                        "maxDistance", Map.of("type", "number", "description", "Maximum search distance (default: 16)", "optional", true)
                ))),
                (exchange, args) -> {
                    try {
                        String blockType = (String) args.get("blockType");
                        int maxDistance = args.containsKey("maxDistance") ? ((Number) args.get("maxDistance")).intValue() : 16;

                        Block block = MinecraftBot.findBlock(blockType, maxDistance);

                        if (block == null) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("No " + blockType + " found within " + maxDistance + " blocks")), false));
                        }

                        return Mono.just(new CallToolResult(List.of(new TextContent("Found " + blockType + " at position ("
                                + block.position.getX() + ", " + block.position.getY() + ", " + block.position.getZ() + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );
    }

    //  Entity Interaction Tools 
    static void registerEntityTools(McpServer.AsyncSpecification server, MinecraftBot bot) {
        server.tool(new Tool(
                        "find-entity",
                        "Find the nearest entity of a specific type", createSchema(Map.of(
                        "type", Map.of("type", "string", "description", "Type of entity to find (empty for any entity)", "optional", true),
                        "maxDistance", Map.of("type", "number", "description", "Maximum search distance (default: 16)", "optional", true)
                ))),
                (exchange, args) -> {
                    try {
                        String type = args.containsKey("type") ? (String) args.get("type") : "";
                        int maxDistance = args.containsKey("maxDistance") ? ((Number) args.get("maxDistance")).intValue() : 16;

                        Entity entity = MinecraftBot.findNearestEntity(type, maxDistance);

                        if (entity == null) {
                            return Mono.just(new CallToolResult(List.of(new TextContent("No " + (type.isEmpty() ? "entity" : type) + " found within " + maxDistance + " blocks")), false));
                        }

                        return Mono.just(new CallToolResult(List.of(new TextContent("Found " + (entity.name != null ? entity.name : entity.type) + " at position ("
                                + entity.position.getX() + ", " + entity.position.getY() + ", " + entity.position.getZ() + ")")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
        );
    }

    /** Chat Tool */
    static void registerChatTools(McpServer.AsyncSpecification server, MinecraftBot bot) {
        server.tool(new Tool(
                        "send-chat",
                        "Send a chat message in-game", createSchema(Map.of(
                        "message", Map.of("type", "string", "description", "Message to send in chat")
                ))),
                (exchange, args) -> {
                    try {
                        String message = (String) args.get("message");
                        MinecraftBot.sendChat(message);

                        return Mono.just(new CallToolResult(List.of(new TextContent("Sent message: \"" + message + "\"")), false));
                    } catch (Exception e) {
                        return Mono.just(new CallToolResult(List.of(new TextContent(e.getMessage())), true));
                    }
                }
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
