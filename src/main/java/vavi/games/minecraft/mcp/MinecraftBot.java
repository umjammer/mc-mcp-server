/*
 * Copyright (c) 2025 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

package vavi.games.minecraft.mcp;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PositionElement;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerActionType;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.MoveToHotbarAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockChangeEntry;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundPingPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundPlayerInfoUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAddEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityPositionSyncPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundMoveEntityPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveEntitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundTeleportEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHealthPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundSetHeldSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetContentPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundContainerSetSlotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundSetPlayerInventoryPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundChunkBatchFinishedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundForgetLevelChunkPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundChunkBatchReceivedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import static java.lang.System.getLogger;


/**
 * A headless Minecraft client (offline mode) that keeps its own copy of
 * the world, inventory and entities and moves with vanilla-like physics.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2025-04-10 nsano initial version <br>
 */
class MinecraftBot {

    private static final Logger logger = getLogger(MinecraftBot.class.getName());

    // Type Definitions

    record InventoryItem(String name, int count, int slot) {}

    record Block(String name, int type, Vector3i position) {}

    record Entity(String name, String type, Vector3d position) {}

    /** tracked entity */
    private static class EntityState {
        final UUID uuid;
        final EntityType type;
        volatile double x, y, z;

        EntityState(UUID uuid, EntityType type, double x, double y, double z) {
            this.uuid = uuid;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    // physics constants (vanilla)

    static final long TICK_MS = 50;
    static final double WALK_SPEED = 4.317 / 20; // blocks per tick
    static final double GRAVITY = 0.08;
    static final double DRAG = 0.98;
    static final double JUMP_VELOCITY = 0.42;
    static final double EYE_HEIGHT = 1.62;
    static final double HALF_WIDTH = 0.3;
    static final double HEIGHT = 1.8;
    /** a bit less than vanilla block interaction range 4.5 */
    static final double REACH = 4.3;
    private static final double EPSILON = 1e-4;

    /** blocks that cannot be broken in survival */
    private static final Set<String> UNBREAKABLE = Set.of("bedrock", "barrier", "command_block", "chain_command_block",
            "repeating_command_block", "structure_block", "jigsaw", "end_portal", "end_portal_frame", "end_gateway",
            "nether_portal", "light", "reinforced_deepslate", "water", "lava", "air", "cave_air", "void_air");

    private final String host;
    private final int port;
    private final String username;

    private volatile ClientSession client;
    private volatile CountDownLatch spawned = new CountDownLatch(1);
    private volatile String lastDisconnectReason;

    private final World world = new World();
    /** [min_y, height] of minecraft:dimension_type registry entries */
    private final List<int[]> dimensionTypes = new ArrayList<>();

    // own state
    private volatile int entityId;
    private volatile double x, y, z;
    private volatile float yaw, pitch;
    private volatile boolean onGround;
    private volatile double velocityY;
    private volatile GameMode gameMode = GameMode.SURVIVAL;
    private volatile float health = 20;
    /** incremented on every server side teleport (including position corrections) */
    private final AtomicInteger teleports = new AtomicInteger();

    /** player inventory container (id 0), 46 slots */
    private final ItemStack[] inventory = new ItemStack[46];
    private volatile int inventoryStateId;
    private volatile int selectedSlot;

    private final Map<Integer, EntityState> entities = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

    /** recent chat/system messages with receive time */
    private final Deque<Map.Entry<Long, String>> messages = new ArrayDeque<>();

    /** block interaction sequence number */
    private final AtomicInteger sequence = new AtomicInteger();

    /** serializes actions, the idle physics tick runs only when no action holds this */
    private final ReentrantLock actionLock = new ReentrantLock();
    private int idleTicks;

    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bot-tick");
        t.setDaemon(true);
        return t;
    });

    public MinecraftBot(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
        ticker.scheduleAtFixedRate(this::idleTick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // connection

    public boolean isConnected() {
        return client != null && client.isConnected() && spawned.getCount() == 0;
    }

    /** connects and waits until the bot has spawned in the world */
    public synchronized void connect() throws IOException, InterruptedException {
        if (isConnected()) return;
        if (client != null) client.disconnect("Reconnecting");

        spawned = new CountDownLatch(1);
        lastDisconnectReason = null;
        ClientSession session = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(host, port))
                .setProtocol(new MinecraftProtocol(username))
                .create();
        session.addListener(new SessionAdapter() {
            @Override
            public void packetReceived(Session session, Packet packet) {
                try {
                    handle(packet);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "error handling " + packet.getClass().getSimpleName(), e);
                }
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                lastDisconnectReason = toPlainText(event.getReason());
                logger.log(Level.INFO, "Bot was disconnected: " + lastDisconnectReason, event.getCause());
            }
        });
        client = session;
        logger.log(Level.INFO, "Connecting to Minecraft server at " + host + ":" + port + " as " + username);
        session.connect(true);
        if (!spawned.await(30, TimeUnit.SECONDS)) {
            session.disconnect("Timed out");
            throw new IOException("Could not join " + host + ":" + port +
                    (lastDisconnectReason != null ? ": " + lastDisconnectReason : ": timed out"));
        }
        logger.log(Level.INFO, "Bot has spawned in the world at " + getPosition());
    }

    public void disconnect() {
        if (client != null && client.isConnected()) {
            client.disconnect("Bot shutting down");
        }
    }

    /** (re)connects on demand */
    private void ensureConnected() throws IOException, InterruptedException {
        if (!isConnected()) connect();
    }

    private void send(Packet packet) {
        ClientSession session = client;
        if (session == null || !session.isConnected()) throw new IllegalStateException("Not connected to the server");
        session.send(packet);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // packet handling (netty thread)

    private void handle(Packet packet) {
        if (packet instanceof ClientboundRegistryDataPacket p) {
            if (p.getRegistry().asString().equals("minecraft:dimension_type")) {
                dimensionTypes.clear();
                for (RegistryEntry e : p.getEntries()) {
                    NbtMap data = e.getData();
                    dimensionTypes.add(data != null ? new int[] {data.getInt("min_y", -64), data.getInt("height", 384)} : new int[] {-64, 384});
                }
            }
        } else if (packet instanceof ClientboundLoginPacket p) {
            entityId = p.getEntityId();
            applySpawnInfo(p.getCommonPlayerSpawnInfo());
        } else if (packet instanceof ClientboundRespawnPacket p) {
            applySpawnInfo(p.getCommonPlayerSpawnInfo());
        } else if (packet instanceof ClientboundPlayerPositionPacket p) {
            List<PositionElement> r = p.getRelatives();
            x = (r.contains(PositionElement.X) ? x : 0) + p.getPosition().getX();
            y = (r.contains(PositionElement.Y) ? y : 0) + p.getPosition().getY();
            z = (r.contains(PositionElement.Z) ? z : 0) + p.getPosition().getZ();
            yaw = (r.contains(PositionElement.Y_ROT) ? yaw : 0) + p.getYRot();
            pitch = (r.contains(PositionElement.X_ROT) ? pitch : 0) + p.getXRot();
            velocityY = 0;
            teleports.incrementAndGet();
            send(new ServerboundAcceptTeleportationPacket(p.getId()));
            send(new ServerboundMovePlayerPosRotPacket(false, false, x, y, z, yaw, pitch));
            logger.log(Level.DEBUG, "teleported to " + getPosition());
            if (spawned.getCount() > 0) {
                send(ServerboundPlayerLoadedPacket.INSTANCE);
                spawned.countDown();
            }
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket p) {
            world.loadChunk(p.getX(), p.getZ(), p.getChunkData());
        } else if (packet instanceof ClientboundForgetLevelChunkPacket p) {
            world.unloadChunk(p.getX(), p.getZ());
        } else if (packet instanceof ClientboundChunkBatchFinishedPacket p) {
            send(new ServerboundChunkBatchReceivedPacket(20));
        } else if (packet instanceof ClientboundBlockUpdatePacket p) {
            setBlock(p.getEntry());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            for (BlockChangeEntry e : p.getEntries()) setBlock(e);
        } else if (packet instanceof ClientboundPingPacket p) {
            send(new ServerboundPongPacket(p.getId()));
        } else if (packet instanceof ClientboundContainerSetContentPacket p) {
            if (p.getContainerId() == 0) {
                synchronized (inventory) {
                    for (int i = 0; i < inventory.length && i < p.getItems().length; i++) inventory[i] = p.getItems()[i];
                }
                inventoryStateId = p.getStateId();
            }
        } else if (packet instanceof ClientboundContainerSetSlotPacket p) {
            if (p.getContainerId() == 0 && p.getSlot() >= 0 && p.getSlot() < inventory.length) {
                synchronized (inventory) {
                    inventory[p.getSlot()] = p.getItem();
                }
                inventoryStateId = p.getStateId();
            }
        } else if (packet instanceof ClientboundSetPlayerInventoryPacket p) {
            int slot = toContainerSlot(p.getSlot());
            if (slot >= 0) {
                synchronized (inventory) {
                    inventory[slot] = p.getContents();
                }
            }
        } else if (packet instanceof ClientboundSetHeldSlotPacket p) {
            selectedSlot = p.getSlot();
        } else if (packet instanceof ClientboundSetHealthPacket p) {
            health = p.getHealth();
            if (health <= 0) {
                logger.log(Level.INFO, "Bot died, respawning");
                send(new ServerboundClientCommandPacket(ClientCommand.RESPAWN));
            }
        } else if (packet instanceof ClientboundGameEventPacket p) {
            if (p.getNotification() == GameEvent.CHANGE_GAME_MODE && p.getValue() instanceof GameMode mode) {
                gameMode = mode;
            }
        } else if (packet instanceof ClientboundAddEntityPacket p) {
             entities.put(p.getEntityId(), new EntityState(p.getUuid(), p.getType(), p.getX(), p.getY(), p.getZ()));
        } else if (packet instanceof ClientboundMoveEntityPosPacket p) {
            moveEntity(p.getEntityId(), p.getMoveX(), p.getMoveY(), p.getMoveZ());
        } else if (packet instanceof ClientboundMoveEntityPosRotPacket p) {
            moveEntity(p.getEntityId(), p.getMoveX(), p.getMoveY(), p.getMoveZ());
        } else if (packet instanceof ClientboundEntityPositionSyncPacket p) {
            EntityState e = entities.get(p.getId());
            if (e != null) {
                e.x = p.getPosition().getX();
                e.y = p.getPosition().getY();
                e.z = p.getPosition().getZ();
            }
        } else if (packet instanceof ClientboundTeleportEntityPacket p) {
            EntityState e = entities.get(p.getId());
            if (e != null) {
                List<PositionElement> r = p.getRelatives();
                e.x = (r.contains(PositionElement.X) ? e.x : 0) + p.getPosition().getX();
                e.y = (r.contains(PositionElement.Y) ? e.y : 0) + p.getPosition().getY();
                e.z = (r.contains(PositionElement.Z) ? e.z : 0) + p.getPosition().getZ();
            }
        } else if (packet instanceof ClientboundRemoveEntitiesPacket p) {
            for (int id : p.getEntityIds()) entities.remove(id);
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket p) {
            for (PlayerListEntry e : p.getEntries()) {
                if (e.getProfile() != null && e.getProfile().getName() != null) {
                    playerNames.put(e.getProfileId(), e.getProfile().getName());
                }
            }
        } else if (packet instanceof ClientboundSystemChatPacket p) {
            if (!p.isOverlay()) addMessage(toPlainText(p.getContent()));
        } else if (packet instanceof ClientboundPlayerChatPacket p) {
            String sender = p.getName() != null ? toPlainText(p.getName()) : playerNames.getOrDefault(p.getSender(), "?");
            addMessage("<" + sender + "> " + (p.getContent() != null ? p.getContent() : toPlainText(p.getUnsignedContent())));
        }
    }

    private void applySpawnInfo(PlayerSpawnInfo info) {
        gameMode = info.getGameMode();
        int[] dimension = info.getDimension() < dimensionTypes.size() ? dimensionTypes.get(info.getDimension()) : new int[] {-64, 384};
        world.reset(dimension[0], dimension[1]);
        entities.clear();
        logger.log(Level.DEBUG, "dimension " + info.getWorldName() + ", minY: " + dimension[0] + ", height: " + dimension[1] + ", " + gameMode);
    }

    private void setBlock(BlockChangeEntry e) {
        world.setBlock(e.getPosition().getX(), e.getPosition().getY(), e.getPosition().getZ(), e.getBlock());
    }

    private void moveEntity(int id, double dx, double dy, double dz) {
        EntityState e = entities.get(id);
        if (e != null) {
            e.x += dx;
            e.y += dy;
            e.z += dz;
        }
    }

    /** player inventory index (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand) to container 0 slot */
    private static int toContainerSlot(int index) {
        if (index >= 0 && index <= 8) return 36 + index;
        if (index >= 9 && index <= 35) return index;
        if (index >= 36 && index <= 39) return 8 - (index - 36);
        if (index == 40) return 45;
        return -1;
    }

    private void addMessage(String message) {
        logger.log(Level.INFO, "[CHAT] " + message);
        synchronized (messages) {
            messages.addLast(Map.entry(System.currentTimeMillis(), message));
            while (messages.size() > 100) messages.removeFirst();
        }
    }

    static String toPlainText(Component component) {
        if (component == null) return "";
        StringBuilder sb = new StringBuilder();
        appendPlainText(component, sb);
        return sb.toString();
    }

    private static void appendPlainText(Component component, StringBuilder sb) {
        if (component instanceof TextComponent t) {
            sb.append(t.content());
        } else if (component instanceof TranslatableComponent t) {
            List<String> args = t.arguments().stream().map(TranslationArgument::asComponent).map(MinecraftBot::toPlainText).toList();
            // no language file is bundled, so render known patterns and "key: args" for the rest
            if (t.key().equals("chat.type.text") && args.size() == 2) {
                sb.append('<').append(args.get(0)).append("> ").append(args.get(1));
            } else if (t.key().equals("chat.square_brackets") && args.size() == 1) {
                sb.append('[').append(args.get(0)).append(']');
            } else if (t.fallback() != null) {
                sb.append(t.fallback());
            } else if (args.isEmpty() && t.key().matches("(block|item|entity)\\.minecraft\\.[a-z0-9_]+")) {
                sb.append(t.key().substring(t.key().lastIndexOf('.') + 1));
            } else {
                sb.append(t.key());
                if (!args.isEmpty()) sb.append(": ").append(String.join(", ", args));
            }
        }
        for (Component child : component.children()) appendPlainText(child, sb);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // physics

    /** collision top of the block, unknown (unloaded) blocks are treated as solid */
    private float collisionHeight(int bx, int by, int bz) {
        int state = world.getBlock(bx, by, bz);
        return state == World.UNKNOWN ? 1 : GameData.collisionHeight(state);
    }

    /** @return true if the player's bounding box at the feet position doesn't intersect any block */
    private boolean bodyFree(double px, double py, double pz) {
        int x0 = (int) Math.floor(px - HALF_WIDTH + EPSILON), x1 = (int) Math.floor(px + HALF_WIDTH - EPSILON);
        int z0 = (int) Math.floor(pz - HALF_WIDTH + EPSILON), z1 = (int) Math.floor(pz + HALF_WIDTH - EPSILON);
        int y0 = (int) Math.floor(py + EPSILON) - 1, y1 = (int) Math.floor(py + HEIGHT - EPSILON);
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = y0; by <= y1; by++) {
                    float h = collisionHeight(bx, by, bz);
                    if (h > 0 && by + h > py + EPSILON && by < py + HEIGHT - EPSILON) return false;
                }
            }
        }
        return true;
    }

    /** @return the highest collision top under the player's bounding box at or below py */
    private double supportY(double px, double py, double pz) {
        int x0 = (int) Math.floor(px - HALF_WIDTH + EPSILON), x1 = (int) Math.floor(px + HALF_WIDTH - EPSILON);
        int z0 = (int) Math.floor(pz - HALF_WIDTH + EPSILON), z1 = (int) Math.floor(pz + HALF_WIDTH - EPSILON);
        double support = world.minY() - 64;
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                for (int by = (int) Math.floor(py + EPSILON); by >= world.minY() && by > support - 1; by--) {
                    float h = collisionHeight(bx, by, bz);
                    if (h > 0 && by + h <= py + EPSILON) {
                        support = Math.max(support, by + h);
                        break;
                    }
                }
            }
        }
        return support;
    }

    private void sendPosition() {
        send(new ServerboundMovePlayerPosRotPacket(onGround, false, x, y, z, yaw, pitch));
    }

    /** one tick of falling, @return true when landed */
    private boolean fallTick() {
        double support = supportY(x, y, z);
        if (y - support <= EPSILON) {
            y = Math.max(y, support);
            velocityY = 0;
            onGround = true;
            return true;
        }
        velocityY = (velocityY - GRAVITY) * DRAG;
        y = Math.max(y + velocityY, support);
        onGround = y - support <= EPSILON;
        if (onGround) velocityY = 0;
        return onGround;
    }

    /** gravity and keep-alive position updates while no action is running */
    private void idleTick() {
        if (!isConnected() || !actionLock.tryLock()) return;
        try {
            boolean wasOnGround = onGround;
            fallTick();
            if (!wasOnGround || !onGround || ++idleTicks >= 20) {
                idleTicks = 0;
                sendPosition();
            }
        } catch (Exception e) {
            logger.log(Level.DEBUG, "tick: " + e);
        } finally {
            actionLock.unlock();
        }
    }

    /** waits one tick, throws if the server corrected our position meanwhile */
    private void tick(int teleportsBefore) throws InterruptedException {
        sendPosition();
        Thread.sleep(TICK_MS);
        if (teleports.get() != teleportsBefore) {
            throw new MovementCorrectedException("Movement was corrected by the server, now at " + format(getPosition()));
        }
    }

    private void fallToGround(int teleportsBefore) throws InterruptedException {
        for (int i = 0; i < 200 && !fallTick(); i++) tick(teleportsBefore);
        tick(teleportsBefore);
    }

    /** jumps up onto a block that's {@code height} higher */
    private void climb(double targetY, int teleportsBefore) throws InterruptedException {
        double vy = JUMP_VELOCITY;
        onGround = false;
        while (y < targetY - EPSILON) {
            double ny = Math.min(y + vy, targetY);
            if (!bodyFree(x, ny, z)) throw new IllegalStateException("No head room to jump");
            y = ny;
            vy = (vy - GRAVITY) * DRAG;
            if (vy <= 0) throw new IllegalStateException("Too high to jump");
            tick(teleportsBefore);
        }
    }

    /** walks straight in the horizontal plane at the current height */
    private void walkTo(double tx, double tz, int teleportsBefore) throws InterruptedException {
        lookAt(Vector3d.from(tx, y + EYE_HEIGHT, tz), false);
        while (true) {
            double dx = tx - x, dz = tz - z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < EPSILON) break;
            double step = Math.min(d, WALK_SPEED);
            double nx = x + dx / d * step, nz = z + dz / d * step;
            if (!bodyFree(nx, y, nz)) throw new IllegalStateException("Path is blocked at " + format(Vector3d.from(nx, y, nz)));
            x = nx;
            z = nz;
            onGround = y - supportY(x, y, z) <= EPSILON;
            tick(teleportsBefore);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // path finding

    private boolean passable(int bx, int by, int bz) {
        int state = world.getBlock(bx, by, bz);
        return state != World.UNKNOWN && GameData.collisionHeight(state) == 0 && !GameData.isFluid(state) && !GameData.isDangerous(state);
    }

    /** feet at block (bx, by, bz) */
    private boolean canStand(int bx, int by, int bz) {
        if (!passable(bx, by, bz) || !passable(bx, by + 1, bz)) return false;
        int below = world.getBlock(bx, by - 1, bz);
        if (below == World.UNKNOWN || GameData.isDangerous(below) || breakable(bx, by - 1, bz)) return false; // not on tree tops
        float h = GameData.collisionHeight(below);
        return h > 0 && h <= 1;
    }

    /** extra path cost of digging a block out of the way */
    private static final double DIG_COST = 3;

    /** blocks the path finder may dig through */
    private boolean breakable(int bx, int by, int bz) {
        int state = world.getBlock(bx, by, bz);
        return state != World.UNKNOWN && GameData.blockName(state).endsWith("_leaves");
    }

    /** @return extra cost to make the cell passable, -1 if impossible */
    private double clearCost(int bx, int by, int bz) {
        return passable(bx, by, bz) ? 0 : breakable(bx, by, bz) ? DIG_COST : -1;
    }

    /** @return extra cost of standing with feet at the block including digging leaves out of the way, -1 if impossible */
    private double standCost(int bx, int by, int bz) {
        double feet = clearCost(bx, by, bz), head = clearCost(bx, by + 1, bz);
        if (feet < 0 || head < 0) return -1;
        int below = world.getBlock(bx, by - 1, bz);
        if (below == World.UNKNOWN || GameData.isDangerous(below) || breakable(bx, by - 1, bz)) return -1; // not on tree tops
        float h = GameData.collisionHeight(below);
        return h > 0 && h <= 1 ? feet + head : -1;
    }

    /** feet height when standing on block (bx, by, bz) */
    private double standY(int bx, int by, int bz) {
        return by - 1 + collisionHeight(bx, by - 1, bz);
    }

    private record Node(int x, int y, int z) {
        Vector3d center() { return Vector3d.from(x + 0.5, y, z + 0.5); }
    }

    private Node currentNode() {
        return new Node((int) Math.floor(x), (int) Math.floor(y + 0.5), (int) Math.floor(z));
    }

    /**
     * A* over standable block positions: walk, diagonal walk, step up 1 block, drop up to 3 blocks.
     * @return path from the start (exclusive) to the reachable node nearest to the goal
     */
    private List<Node> findPath(Vector3d goal, double range, int maxNodes) {
        return search(n -> n.center().distance(goal) <= range, n -> n.center().distance(goal), maxNodes, true);
    }

    /**
     * A* over standable block positions.
     * @param heuristic estimated cost to a goal, also used to pick the best node for a partial path
     * @param partial when no goal is found, return the path to the node with the smallest heuristic instead of null
     * @return path from the start (exclusive) to a goal node, empty when the start is a goal, null when not found
     */
    private List<Node> search(Predicate<Node> isGoal, ToDoubleFunction<Node> heuristic, int maxNodes, boolean partial) {
        Node start = currentNode();
        Map<Node, Node> cameFrom = new HashMap<>();
        Map<Node, Double> cost = new HashMap<>();
        PriorityQueue<Map.Entry<Node, Double>> open = new PriorityQueue<>(Map.Entry.comparingByValue());
        Set<Node> closed = new HashSet<>();
        cost.put(start, 0.0);
        open.add(Map.entry(start, heuristic.applyAsDouble(start)));
        Node best = start;
        double bestDistance = heuristic.applyAsDouble(start);
        boolean found = false;
        // safe drop height: fall damage is (height - 3), keep at least 6 health
        int maxDrop = Math.max(3, Math.min(12, 3 + (int) (health - 6)));

        while (!open.isEmpty() && closed.size() < maxNodes) {
            Node n = open.poll().getKey();
            if (!closed.add(n)) continue;
            double distance = heuristic.applyAsDouble(n);
            if (distance < bestDistance) {
                best = n;
                bestDistance = distance;
            }
            if (isGoal.test(n)) {
                best = n;
                found = true;
                break;
            }

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    int nx = n.x + dx, nz = n.z + dz;
                    boolean diagonal = dx != 0 && dz != 0;
                    if (diagonal && !(passable(n.x + dx, n.y, n.z) && passable(n.x + dx, n.y + 1, n.z) &&
                            passable(n.x, n.y, n.z + dz) && passable(n.x, n.y + 1, n.z + dz))) continue;
                    double base = diagonal ? Math.sqrt(2) : 1;
                    List<Map.Entry<Node, Double>> next = new ArrayList<>();
                    double same = diagonal ? (canStand(nx, n.y, nz) ? 0 : -1) : standCost(nx, n.y, nz);
                    double up = diagonal ? -1 : standCost(nx, n.y + 1, nz);
                    double headRoom = clearCost(n.x, n.y + 2, n.z);
                    if (same >= 0) {
                        next.add(Map.entry(new Node(nx, n.y, nz), base + same));
                    } else if (up >= 0 && headRoom >= 0) {
                        next.add(Map.entry(new Node(nx, n.y + 1, nz), base + 1 + up + headRoom));
                    } else if (!diagonal && passable(nx, n.y, nz) && passable(nx, n.y + 1, nz)) {
                        for (int d = 1; d <= maxDrop; d++) {
                            if (canStand(nx, n.y - d, nz)) {
                                // drops over 3 blocks hurt, take them only when there's no other way
                                next.add(Map.entry(new Node(nx, n.y - d, nz), base + d * 0.5 + Math.max(0, d - 3) * 20));
                                break;
                            }
                            if (!passable(nx, n.y - d, nz)) break;
                        }
                    }
                    for (Map.Entry<Node, Double> e : next) {
                        Node m = e.getKey();
                        double c = cost.get(n) + e.getValue();
                        if (!closed.contains(m) && c < cost.getOrDefault(m, Double.MAX_VALUE)) {
                            cost.put(m, c);
                            cameFrom.put(m, n);
                            open.add(Map.entry(m, c + heuristic.applyAsDouble(m)));
                        }
                    }
                }
            }
        }

        if (!found && !partial) return null;
        List<Node> path = new ArrayList<>();
        for (Node n = best; !n.equals(start); n = cameFrom.get(n)) path.add(0, n);
        return path;
    }

    /** follows the path node by node with walking, jumping and falling, digs leaves in the way */
    private void followPath(List<Node> path) throws Exception {
        for (Node n : path) {
            List<Vector3i> obstacles = new ArrayList<>(List.of(Vector3i.from(n.x, n.y + 1, n.z), Vector3i.from(n.x, n.y, n.z)));
            if (n.y > Math.floor(y + EPSILON)) obstacles.addFirst(Vector3i.from((int) Math.floor(x), (int) Math.floor(y + EPSILON) + 2, (int) Math.floor(z)));
            for (Vector3i p : obstacles) {
                if (breakable(p.getX(), p.getY(), p.getZ())) digAt(p, false);
            }
            int teleportsBefore = teleports.get();
            double ny = standY(n.x, n.y, n.z);
            if (ny > y + EPSILON) climb(ny, teleportsBefore);
            walkTo(n.x + 0.5, n.z + 0.5, teleportsBefore);
            if (ny < y - EPSILON) fallToGround(teleportsBefore);
            onGround = true;
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // actions

    public Vector3d getPosition() {
        return Vector3d.from(x, y, z);
    }

    public boolean isChunkLoaded(int x, int z) {
        return world.isLoaded(x, z);
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public float getHealth() {
        return health;
    }

    static String format(Vector3d v) {
        return String.format("(%.1f, %.1f, %.1f)", v.getX(), v.getY(), v.getZ());
    }

    /**
     * walks to within {@code range} blocks of the block position.
     * @return distance left to the target, 0 if within range
     */
    public double moveToPosition(int x, int y, int z, double range) throws Exception {
        ensureConnected();
        Vector3d goal = Vector3d.from(x + 0.5, y, z + 0.5);
        actionLock.lock();
        try {
            if (getPosition().distance(goal) <= range) return 0;
            List<Node> path = findPath(goal, range, 20000);
            if (path.isEmpty()) throw new IllegalStateException("No path found from " + format(getPosition()) + " to " + format(goal));
            followPath(path);
            double left = getPosition().distance(goal);
            return left <= range + 0.5 ? 0 : left;
        } finally {
            actionLock.unlock();
        }
    }

    public void lookAt(Vector3d target) throws Exception {
        ensureConnected();
        lookAt(target, true);
    }

    private void lookAt(Vector3d target, boolean send) {
        double dx = target.getX() - x, dy = target.getY() - (y + EYE_HEIGHT), dz = target.getZ() - z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > EPSILON) yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        if (send) send(new ServerboundMovePlayerRotPacket(onGround, false, yaw, pitch));
    }

    public void jump() throws Exception {
        ensureConnected();
        actionLock.lock();
        try {
            int teleportsBefore = teleports.get();
            if (!onGround) throw new IllegalStateException("Not on the ground");
            velocityY = JUMP_VELOCITY;
            onGround = false;
            do {
                double ny = y + velocityY;
                if (bodyFree(x, ny, z)) {
                    y = ny;
                } else {
                    if (velocityY > 0) {
                        velocityY = 0; // bumped head
                        continue;
                    }
                    y = supportY(x, y, z);
                    break;
                }
                velocityY = (velocityY - GRAVITY) * DRAG;
                tick(teleportsBefore);
            } while (y - supportY(x, y, z) > EPSILON);
            velocityY = 0;
            onGround = true;
            tick(teleportsBefore);
        } finally {
            actionLock.unlock();
        }
    }

    /**
     * walks relative to the facing direction.
     * @param direction forward, back, left, right
     * @return distance moved
     */
    public double moveInDirection(String direction, int duration) throws Exception {
        ensureConnected();
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r); // forward
        double vx, vz;
        switch (direction.toLowerCase()) {
            case "forward" -> { vx = fx; vz = fz; }
            case "back" -> { vx = -fx; vz = -fz; }
            case "left" -> { vx = fz; vz = -fx; }
            case "right" -> { vx = -fz; vz = fx; }
            default -> throw new IllegalArgumentException("Unknown direction: " + direction);
        }
        actionLock.lock();
        try {
            int teleportsBefore = teleports.get();
            Vector3d start = getPosition();
            for (long t = 0; t < duration; t += TICK_MS) {
                double nx = x + vx * WALK_SPEED, nz = z + vz * WALK_SPEED;
                if (!bodyFree(nx, y, nz)) {
                    // step up onto a one block high obstacle
                    double top = supportY(nx, y + 1, nz);
                    if (onGround && top - y <= 1 + EPSILON && bodyFree(nx, top, nz)) {
                        climb(top, teleportsBefore);
                    } else {
                        break; // blocked
                    }
                }
                x = nx;
                z = nz;
                fallTick();
                tick(teleportsBefore);
            }
            fallToGround(teleportsBefore);
            return start.distance(getPosition());
        } finally {
            actionLock.unlock();
        }
    }

    public List<InventoryItem> getInventory() {
        List<InventoryItem> items = new ArrayList<>();
        synchronized (inventory) {
            for (int slot = 0; slot < inventory.length; slot++) {
                ItemStack item = inventory[slot];
                if (slot != 0 && item != null && item.getAmount() > 0) { // slot 0: crafting result
                    items.add(new InventoryItem(GameData.itemName(item.getId()), item.getAmount(), slot));
                }
            }
        }
        return items;
    }

    public InventoryItem findItem(String nameOrType) {
        String name = GameData.stripNamespace(nameOrType);
        List<InventoryItem> items = getInventory();
        return items.stream().filter(item -> item.name().equals(name)).findFirst()
                .orElse(items.stream().filter(item -> item.name().contains(name)).findFirst().orElse(null));
    }

    /** @return item held in the main hand, null if none */
    public InventoryItem getHeldItem() {
        int slot = 36 + selectedSlot;
        ItemStack item = inventory[slot];
        return item != null && item.getAmount() > 0 ? new InventoryItem(GameData.itemName(item.getId()), item.getAmount(), slot) : null;
    }

    /**
     * @param destination hand, off-hand, head, torso, legs, feet
     */
    public InventoryItem equipItem(String itemName, String destination) throws Exception {
        ensureConnected();
        InventoryItem item = findItem(itemName);
        if (item == null) throw new IllegalArgumentException("Couldn't find any item matching '" + itemName + "' in inventory");
        int itemId = GameData.itemId(item.name());
        actionLock.lock();
        try {
            int targetSlot;
            switch (destination.toLowerCase()) {
                case "hand", "main-hand", "mainhand" -> {
                    if (item.slot() >= 36 && item.slot() <= 44) {
                        selectedSlot = item.slot() - 36;
                        send(new ServerboundSetCarriedItemPacket(selectedSlot));
                        return item;
                    }
                    targetSlot = 36 + selectedSlot;
                    click(item.slot(), ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.from(selectedSlot));
                }
                case "off-hand", "offhand" -> {
                    targetSlot = 45;
                    if (item.slot() == targetSlot) return item;
                    click(item.slot(), ContainerActionType.MOVE_TO_HOTBAR_SLOT, MoveToHotbarAction.OFF_HAND);
                }
                case "head", "torso", "legs", "feet" -> {
                    targetSlot = switch (destination.toLowerCase()) {
                        case "head" -> 5; case "torso" -> 6; case "legs" -> 7; default -> 8;
                    };
                    click(item.slot(), ContainerActionType.SHIFT_CLICK_ITEM, ShiftClickItemAction.LEFT_CLICK);
                }
                default -> throw new IllegalArgumentException("Unknown destination: " + destination);
            }
            if (!waitFor(() -> inventory[targetSlot] != null && inventory[targetSlot].getId() == itemId, 2000)) {
                throw new IllegalStateException("Server didn't move " + item.name() + " to " + destination);
            }
            return new InventoryItem(item.name(), inventory[targetSlot].getAmount(), targetSlot);
        } finally {
            actionLock.unlock();
        }
    }

    private void click(int slot, ContainerActionType type, org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerAction action) {
        // changed slots are not predicted, the server resyncs the inventory
        send(new ServerboundContainerClickPacket(0, inventoryStateId, slot, type, action, null, Map.of()));
    }

    private static boolean waitFor(BooleanSupplier condition, long timeout) throws InterruptedException {
        long until = System.currentTimeMillis() + timeout;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > until) return false;
            Thread.sleep(TICK_MS);
        }
        return true;
    }

    private static Vector3i offset(Direction d) {
        return switch (d) {
            case DOWN -> Vector3i.from(0, -1, 0);
            case UP -> Vector3i.from(0, 1, 0);
            case NORTH -> Vector3i.from(0, 0, -1);
            case SOUTH -> Vector3i.from(0, 0, 1);
            case WEST -> Vector3i.from(-1, 0, 0);
            case EAST -> Vector3i.from(1, 0, 0);
        };
    }

    private static Direction opposite(Direction d) {
        return switch (d) {
            case DOWN -> Direction.UP;
            case UP -> Direction.DOWN;
            case NORTH -> Direction.SOUTH;
            case SOUTH -> Direction.NORTH;
            case WEST -> Direction.EAST;
            case EAST -> Direction.WEST;
        };
    }

    private double eyeDistance(Vector3d target) {
        return Vector3d.from(x, y + EYE_HEIGHT, z).distance(target);
    }

    /** thrown when no standing position within reach of a block was found */
    static class OutOfReachException extends IllegalStateException {
        OutOfReachException(String message) { super(message); }
    }

    /** thrown when the server teleported the bot back during a movement */
    static class MovementCorrectedException extends IllegalStateException {
        MovementCorrectedException(String message) { super(message); }
    }

    /** thrown when a needed item is not in the inventory */
    static class MissingItemException extends IllegalStateException {
        final String item;
        MissingItemException(String item) {
            super("No " + item + " in inventory");
            this.item = item;
        }
    }

    static String format(Vector3i p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }

    private static Vector3d center(Vector3i p) {
        return Vector3d.from(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private boolean inReach(Vector3i p) {
        return eyeDistance(center(p)) <= REACH;
    }

    /** @return true if the bot standing at the node would intersect the block cell */
    private static boolean nodeIntersects(Node n, double standY, Vector3i p) {
        return p.getX() == n.x && p.getZ() == n.z && p.getY() + 1 > standY && p.getY() < standY + HEIGHT;
    }

    /** walks to a position where the block is within reach and not inside the bot */
    private void approach(Vector3i p) throws Exception {
        if (inReach(p) && !intersectsBody(p.getX(), p.getY(), p.getZ())) return;
        Vector3d c = center(p);
        double reach = REACH - 0.3;
        List<Node> path = search(n -> {
                    double sy = standY(n.x, n.y, n.z);
                    return Vector3d.from(n.x + 0.5, sy + EYE_HEIGHT, n.z + 0.5).distance(c) <= reach && !nodeIntersects(n, sy, p);
                },
                n -> Math.max(0, Vector3d.from(n.x + 0.5, n.y + EYE_HEIGHT, n.z + 0.5).distance(c) - reach),
                4000, false);
        if (path == null) throw new OutOfReachException("Couldn't get within reach of " + format(p));
        followPath(path);
    }

    /** @return true if the block cell intersects the player's bounding box */
    private boolean intersectsBody(int bx, int by, int bz) {
        return bx + 1 > x - HALF_WIDTH && bx < x + HALF_WIDTH && bz + 1 > z - HALF_WIDTH && bz < z + HALF_WIDTH &&
                by + 1 > y && by < y + HEIGHT;
    }

    private boolean isSolid(Vector3i p) {
        int state = world.getBlock(p.getX(), p.getY(), p.getZ());
        return state != World.UNKNOWN && GameData.collisionHeight(state) > 0;
    }

    /** @return true if a neighbor block can be clicked to place a block at the position */
    private boolean hasSupport(Vector3i p) {
        for (Direction side : Direction.values()) {
            if (isSolid(p.add(offset(side)))) return true;
        }
        return false;
    }

    /** equips the item to the main hand if it's not held yet */
    private void ensureHolding(String item) throws Exception {
        InventoryItem held = getHeldItem();
        if (held != null && held.name().equals(item)) return;
        InventoryItem found = getInventory().stream().filter(i -> i.name().equals(item)).findFirst().orElse(null);
        if (found == null) throw new MissingItemException(item);
        equipItem(item, "hand");
    }

    /**
     * places the held block at the position against a neighbor block.
     * @param faceDirection the side of the target where the supporting block is (e.g. DOWN: on top of the block below)
     * @return the placed block
     */
    public Block placeBlock(int bx, int by, int bz, Direction faceDirection) throws Exception {
        ensureConnected();
        actionLock.lock();
        try {
            return placeAt(Vector3i.from(bx, by, bz), faceDirection, true);
        } finally {
            actionLock.unlock();
        }
    }

    /** @param allowMove walk within reach if needed, otherwise {@link OutOfReachException} */
    private Block placeAt(Vector3i p, Direction faceDirection, boolean allowMove) throws Exception {
        int bx = p.getX(), by = p.getY(), bz = p.getZ();
        int before = world.getBlock(bx, by, bz);
        if (before == World.UNKNOWN) throw new IllegalStateException("Chunk at " + format(p) + " is not loaded");
        if (breakable(bx, by, bz)) { // leaves in the way
            digAt(p, allowMove);
            before = world.getBlock(bx, by, bz);
        }
        if (GameData.collisionHeight(before) > 0) throw new IllegalStateException("There's already a block (" + GameData.blockName(before) + ") at " + format(p));
        InventoryItem held = getHeldItem();
        if (held == null) throw new IllegalStateException("Nothing in hand, equip a block first");

        // solid neighbors to click on, the requested side first
        List<Direction> candidates = new ArrayList<>(List.of(Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP));
        candidates.remove(faceDirection);
        candidates.addFirst(faceDirection);
        candidates.removeIf(side -> !isSolid(p.add(offset(side))));
        if (candidates.isEmpty()) throw new IllegalStateException("No adjacent block to place against at " + format(p));

        if (allowMove) {
            approach(p);
        } else if (!inReach(p)) {
            throw new OutOfReachException(format(p) + " is out of reach");
        }
        if (intersectsBody(bx, by, bz)) throw new IllegalStateException("The bot is standing at " + format(p));

        for (int attempt = 0; ; attempt++) {
            int current = world.getBlock(bx, by, bz);
            Boolean placed = clickToPlace(p, candidates, current);
            if (placed == null) throw new OutOfReachException("Blocks to place against are out of reach at " + format(p));
            if (placed) {
                int after = world.getBlock(bx, by, bz);
                return new Block(GameData.blockName(after), after, p);
            }
            // plants etc. that are not replaceable by placing: remove and retry
            if (attempt == 0 && !GameData.isAir(current) && !GameData.isFluid(current)) {
                digAt(p, false);
                continue;
            }
            throw new IllegalStateException("The server didn't accept placing " + held.name() + " at " + format(p));
        }
    }

    /** @return true: placed, false: rejected by the server, null: no candidate in reach */
    private Boolean clickToPlace(Vector3i p, List<Direction> candidates, int before) throws InterruptedException {
        for (Direction side : candidates) {
            Vector3i against = p.add(offset(side));
            Direction face = opposite(side);
            Vector3d hit = center(against).add(offset(face).toDouble().mul(0.5));
            if (eyeDistance(hit) > REACH + 0.5) continue;
            lookAt(hit, true);
            // sneak so that clicking on chests, doors etc. places instead of using them
            send(new ServerboundPlayerInputPacket(false, false, false, false, false, true, false));
            send(new ServerboundUseItemOnPacket(against, face, Hand.MAIN_HAND,
                    (float) (hit.getX() - against.getX()), (float) (hit.getY() - against.getY()), (float) (hit.getZ() - against.getZ()),
                    false, false, sequence.incrementAndGet()));
            send(new ServerboundSwingPacket(Hand.MAIN_HAND));
            send(new ServerboundPlayerInputPacket(false, false, false, false, false, false, false));
            return waitFor(() -> world.getBlock(p.getX(), p.getY(), p.getZ()) != before, 2000);
        }
        return null;
    }

    private static final List<String> TOOL_MATERIALS = List.of("netherite", "diamond", "iron", "stone", "golden", "wooden");

    /** @return the tool suffix (e.g. "_pickaxe") that digs the block fastest, null if none helps */
    private static String toolFor(String block) {
        if (block.endsWith("_leaves")) return "_hoe";
        if (block.matches(".*(_log|_wood|_stem|_hyphae|_planks|mushroom_block|bookshelf|crafting_table|chest|barrel)$")
                || block.matches("(oak|spruce|birch|jungle|acacia|dark_oak|mangrove|cherry|pale_oak|bamboo|crimson|warped)_.*")) return "_axe";
        if (block.matches(".*(stone|brick|bricks|cobble.*|_ore|deepslate|andesite|diorite|granite|terracotta|concrete|tuff|calcite|basalt|blackstone|obsidian|netherrack|sandstone|prismarine|purpur|quartz.*|ice)$")) return "_pickaxe";
        if (block.matches("(dirt|coarse_dirt|rooted_dirt|grass_block|podzol|mycelium|farmland|dirt_path|sand|red_sand|gravel|clay|mud|muddy_mangrove_roots|snow|snow_block|soul_sand|soul_soil|powder_snow)")) return "_shovel";
        return null;
    }

    /** holds the best tool in the inventory for the block, if any */
    private void equipBestTool(String block) {
        String suffix = toolFor(block);
        if (suffix == null) return;
        InventoryItem best = getInventory().stream()
                .filter(i -> i.name().endsWith(suffix) && !(suffix.equals("_axe") && i.name().endsWith("_pickaxe")))
                .min(Comparator.comparingInt(i -> {
                    int rank = TOOL_MATERIALS.indexOf(i.name().substring(0, i.name().length() - suffix.length()));
                    return rank < 0 ? TOOL_MATERIALS.size() : rank;
                }))
                .orElse(null);
        InventoryItem held = getHeldItem();
        if (best == null || held != null && held.name().equals(best.name())) return;
        try {
            equipItem(best.name(), "hand");
        } catch (Exception e) {
            logger.log(Level.DEBUG, "couldn't equip " + best.name() + ": " + e);
        }
    }

    /** @return the face of the block that faces the bot's eye */
    private Direction faceToward(int bx, int by, int bz) {
        double dx = x - (bx + 0.5), dy = y + EYE_HEIGHT - (by + 0.5), dz = z - (bz + 0.5);
        if (Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz)) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** @return the block that was dug */
    public Block digBlock(int bx, int by, int bz) throws Exception {
        ensureConnected();
        actionLock.lock();
        try {
            return digAt(Vector3i.from(bx, by, bz), true);
        } finally {
            actionLock.unlock();
        }
    }

    /** @param allowMove walk within reach if needed, otherwise {@link OutOfReachException} */
    private Block digAt(Vector3i pos, boolean allowMove) throws Exception {
        int bx = pos.getX(), by = pos.getY(), bz = pos.getZ();
        int state = world.getBlock(bx, by, bz);
        if (state == World.UNKNOWN) throw new IllegalStateException("Chunk at " + format(pos) + " is not loaded");
        Block block = new Block(GameData.blockName(state), state, pos);
        if (GameData.isAir(state)) throw new IllegalStateException("No block found at position " + format(pos));
        if (gameMode != GameMode.CREATIVE && UNBREAKABLE.contains(block.name())) throw new IllegalStateException(block.name() + " can't be dug");

        if (allowMove) {
            if (!inReach(pos)) approach(pos);
        } else if (!inReach(pos)) {
            throw new OutOfReachException(format(pos) + " is out of reach");
        }
        equipBestTool(block.name());
        Direction face = faceToward(bx, by, bz);
        lookAt(center(pos), true);
        send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, pos, face, sequence.incrementAndGet()));
        send(new ServerboundSwingPacket(Hand.MAIN_HAND));
        if (gameMode != GameMode.CREATIVE) {
            // the server finishes the block by itself when breaking time has passed (delayed destroy)
            Thread.sleep(TICK_MS);
            send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, pos, face, sequence.incrementAndGet()));
        }
        long until = System.currentTimeMillis() + 60_000;
        for (int i = 0; world.getBlock(bx, by, bz) == state; i++) {
            if (System.currentTimeMillis() > until) {
                send(new ServerboundPlayerActionPacket(PlayerAction.CANCEL_DIGGING, pos, face, sequence.incrementAndGet()));
                throw new IllegalStateException("Timed out digging " + block.name());
            }
            if (i % 5 == 4) send(new ServerboundSwingPacket(Hand.MAIN_HAND));
            Thread.sleep(TICK_MS);
        }
        return block;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // scaffolding

    /**
     * builds a pillar under itself by jumping and placing blocks below.
     * @return placed positions, bottom first
     */
    private List<Vector3i> pillarUp(int height, String item) throws Exception {
        List<Vector3i> placed = new ArrayList<>();
        int teleportsBefore = teleports.get();
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        walkTo(cx + 0.5, cz + 0.5, teleportsBefore);
        GameData.BlockInfo info = GameData.block(item);
        int predicted = info != null ? info.defaultStateId() : 1;
        for (int i = 0; i < height; i++) {
            int by = (int) Math.floor(y + EPSILON);
            if (breakable(cx, by + 2, cz)) digAt(Vector3i.from(cx, by + 2, cz), false);
            if (!passable(cx, by + 2, cz)) break; // no head room
            ensureHolding(item);
            teleportsBefore = teleports.get();
            Vector3i against = Vector3i.from(cx, by - 1, cz);
            lookAt(Vector3d.from(cx + 0.5, by, cz + 0.5), true);
            velocityY = JUMP_VELOCITY;
            onGround = false;
            while (y < by + 1 + EPSILON) {
                y += velocityY;
                velocityY = (velocityY - GRAVITY) * DRAG;
                tick(teleportsBefore);
            }
            send(new ServerboundUseItemOnPacket(against, Direction.UP, Hand.MAIN_HAND, 0.5f, 1f, 0.5f, false, false, sequence.incrementAndGet()));
            send(new ServerboundSwingPacket(Hand.MAIN_HAND));
            world.setBlock(cx, by, cz, predicted); // client side prediction, the server sends the real state
            placed.add(Vector3i.from(cx, by, cz));
            fallToGround(teleportsBefore);
            if (!isSolid(Vector3i.from(cx, by, cz))) throw new IllegalStateException("The server didn't accept the scaffold block at " + format(Vector3i.from(cx, by, cz)));
        }
        return placed;
    }

    /** digs the pillar under itself from the top */
    private void pillarDown(List<Vector3i> pillar) throws Exception {
        for (Vector3i p : pillar.reversed()) {
            if (!isSolid(p)) continue;
            digAt(p, true);
            fallToGround(teleports.get());
        }
    }

    @FunctionalInterface
    interface Work {
        void run() throws Exception;
    }

    /**
     * stands on a temporary pillar near the target, runs the work there and removes the pillar.
     * @param avoid positions the pillar and the bot must not occupy
     * @return false if no place for a pillar was found
     */
    private boolean withScaffold(Vector3i target, Set<Vector3i> avoid, String item, Work work) throws Exception {
        record Candidate(Node base, int height, double score) {}
        List<Candidate> candidates = new ArrayList<>();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) continue;
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                if (horizontal > REACH - 0.8) continue;
                int cx = target.getX() + dx, cz = target.getZ() + dz;
                for (int gy = target.getY(); gy >= target.getY() - 16; gy--) {
                    if (standCost(cx, gy, cz) < 0) continue;
                    int height = Math.max(0, target.getY() - gy - 1); // eye a bit above the target
                    boolean free = true;
                    for (int cy = gy; cy <= gy + height + 1 && free; cy++) {
                        free = clearCost(cx, cy, cz) >= 0 && !avoid.contains(Vector3i.from(cx, cy, cz)); // leaves are dug
                    }
                    if (free) candidates.add(new Candidate(new Node(cx, gy, cz), height, height * 2 + horizontal));
                }
            }
        }
        logger.log(Level.DEBUG, candidates.size() + " scaffold candidates for " + format(target));
        if (candidates.isEmpty()) return false;
        // one search to the nearest reachable base, the extra cost of a taller pillar works as a goal penalty
        Map<Node, Candidate> bases = new HashMap<>();
        candidates.forEach(c -> bases.merge(c.base(), c, (a, b) -> a.score() <= b.score() ? a : b));
        List<Node> path = search(bases::containsKey, n -> 0, 30000, false);
        if (path == null) {
            logger.log(Level.DEBUG, "no path to any scaffold base for " + format(target));
            return false;
        }
        Candidate c = bases.get(path.isEmpty() ? currentNode() : path.getLast());
        followPath(path);
        logger.log(Level.DEBUG, "scaffold " + c.height() + " blocks at " + c.base() + " for " + format(target));
        List<Vector3i> pillar = pillarUp(c.height(), item);
        try {
            work.run();
        } finally {
            pillarDown(pillar);
        }
        return true;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // batch operations

    /** a block to place, {@code block} is an item name */
    record Placement(Vector3i position, String block) {}

    /**
     * @param done placed or dug
     * @param alreadyDone already in the desired state at the start
     * @param remaining left undone
     * @param missingItems item name to count which were not in the inventory
     */
    record BatchResult(int done, int alreadyDone, int remaining, Map<String, Integer> missingItems, List<String> problems, boolean timedOut) {}

    /** wall variants of items, e.g. torch is placed as wall_torch against a wall */
    private static boolean matches(int state, String item) {
        String name = GameData.blockName(state);
        return name.equals(item) || name.equals(item.replaceFirst("(torch|sign|banner|head|skull|fan)$", "wall_$1"));
    }

    private Comparator<Vector3i> nearest() {
        Vector3d eye = Vector3d.from(x, y + EYE_HEIGHT, z);
        return Comparator.comparingDouble(p -> center(p).distance(eye));
    }

    /**
     * places many blocks: equips items, orders placements so that every block has a neighbor to place against,
     * walks within reach and stands on temporary pillars of {@code scaffoldItem} for high positions.
     */
    public BatchResult buildBlocks(List<Placement> placements, String scaffoldItem, long maxMillis) throws Exception {
        ensureConnected();
        actionLock.lock();
        try {
            long deadline = System.currentTimeMillis() + maxMillis;
            Map<Vector3i, String> pending = new LinkedHashMap<>();
            List<String> problems = new ArrayList<>();
            Set<String> missing = new HashSet<>();
            int alreadyDone = 0;
            int[] done = {0};
            for (Placement p : placements) {
                String item = GameData.stripNamespace(p.block());
                if (GameData.itemId(item) < 0) {
                    problems.add("Unknown item " + item + " at " + format(p.position()));
                } else if (matches(world.getBlock(p.position().getX(), p.position().getY(), p.position().getZ()), item)) {
                    alreadyDone++;
                } else {
                    pending.put(p.position(), item);
                }
            }

            boolean timedOut = false;
            while (!pending.isEmpty()) {
                if (System.currentTimeMillis() > deadline) {
                    timedOut = true;
                    break;
                }
                List<Vector3i> unreachable = new ArrayList<>();
                int placed = placePass(pending, true, deadline, problems, missing, unreachable);
                done[0] += placed;
                if (placed > 0) continue;
                if (unreachable.isEmpty()) break;

                Vector3i target = unreachable.stream().min(Comparator.comparingInt(Vector3i::getY).thenComparing(nearest())).orElseThrow();
                int before = done[0];
                boolean scaffolded = withScaffold(target, pending.keySet(), scaffoldItem, () -> {
                    int n;
                    do {
                        n = placePass(pending, false, deadline, problems, missing, new ArrayList<>());
                        done[0] += n;
                    } while (n > 0);
                });
                if (!scaffolded || done[0] == before) {
                    problems.add("Couldn't reach " + format(target) + (scaffolded ? "" : ", no place for a scaffold"));
                    pending.remove(target);
                }
            }

            Map<String, Integer> missingItems = new TreeMap<>();
            for (Map.Entry<Vector3i, String> e : pending.entrySet()) {
                if (missing.contains(e.getValue())) {
                    missingItems.merge(e.getValue(), 1, Integer::sum);
                } else if (!timedOut) {
                    problems.add("No block to place against at " + format(e.getKey()));
                }
            }
            return new BatchResult(done[0], alreadyDone, pending.size(), missingItems, problems, timedOut);
        } finally {
            actionLock.unlock();
        }
    }

    private static boolean isDoor(String item) {
        return item.endsWith("_door") || item.endsWith("_fence_gate") || item.endsWith("_trapdoor");
    }

    /** @return number of placed blocks */
    private int placePass(Map<Vector3i, String> pending, boolean allowMove, long deadline,
                          List<String> problems, Set<String> missing, List<Vector3i> unreachable) throws Exception {
        int placed = 0;
        List<Vector3i> order = new ArrayList<>(pending.keySet());
        order.sort(Comparator.comparingInt(Vector3i::getY).thenComparing(nearest()));
        // doors and gates last, so that the bot doesn't shut itself in
        boolean onlyDoors = pending.values().stream().allMatch(item -> isDoor(item) || missing.contains(item));
        for (Vector3i p : order) {
            if (System.currentTimeMillis() > deadline) break;
            String item = pending.get(p);
            if (item == null || missing.contains(item) || isDoor(item) && !onlyDoors) continue;
            int state = world.getBlock(p.getX(), p.getY(), p.getZ());
            if (state == World.UNKNOWN) {
                problems.add("Chunk at " + format(p) + " is not loaded");
                pending.remove(p);
            } else if (matches(state, item)) {
                pending.remove(p);
            } else if (GameData.collisionHeight(state) > 0 && !breakable(p.getX(), p.getY(), p.getZ())) {
                problems.add("Occupied by " + GameData.blockName(state) + " at " + format(p));
                pending.remove(p);
            } else if (hasSupport(p) && (allowMove || inReach(p) && !intersectsBody(p.getX(), p.getY(), p.getZ()))) {
                try {
                    ensureHolding(item);
                    placeAt(p, Direction.DOWN, allowMove);
                    placed++;
                    pending.remove(p);
                } catch (MissingItemException e) {
                    missing.add(e.item);
                } catch (OutOfReachException e) {
                    unreachable.add(p);
                } catch (MovementCorrectedException e) {
                    logger.log(Level.DEBUG, e.getMessage());
                } catch (IllegalStateException e) {
                    problems.add(e.getMessage());
                    pending.remove(p);
                }
            }
        }
        return placed;
    }

    /**
     * digs every block in the box (both corners inclusive) from the top, using pillars of {@code scaffoldItem} for high blocks.
     * @param only dig only blocks whose names contain one of these, empty for all
     */
    public BatchResult clearRegion(Vector3i from, Vector3i to, List<String> only, String scaffoldItem, long maxMillis) throws Exception {
        ensureConnected();
        actionLock.lock();
        try {
            long deadline = System.currentTimeMillis() + maxMillis;
            Vector3i min = from.min(to), max = from.max(to);
            Set<Vector3i> pending = new LinkedHashSet<>();
            List<String> problems = new ArrayList<>();
            int alreadyDone = 0;
            for (int bx = min.getX(); bx <= max.getX(); bx++) {
                for (int by = min.getY(); by <= max.getY(); by++) {
                    for (int bz = min.getZ(); bz <= max.getZ(); bz++) {
                        int state = world.getBlock(bx, by, bz);
                        if (state == World.UNKNOWN) {
                            problems.add("Chunk at " + format(Vector3i.from(bx, by, bz)) + " is not loaded");
                        } else if (GameData.isAir(state) || GameData.isFluid(state)) {
                            alreadyDone++;
                        } else if (!only.isEmpty() && only.stream().noneMatch(GameData.blockName(state)::contains)) {
                            continue;
                        } else if (gameMode != GameMode.CREATIVE && UNBREAKABLE.contains(GameData.blockName(state))) {
                            problems.add(GameData.blockName(state) + " can't be dug at " + format(Vector3i.from(bx, by, bz)));
                        } else {
                            pending.add(Vector3i.from(bx, by, bz));
                        }
                    }
                }
            }

            int[] done = {0};
            boolean timedOut = false;
            while (!pending.isEmpty()) {
                if (System.currentTimeMillis() > deadline) {
                    timedOut = true;
                    break;
                }
                List<Vector3i> unreachable = new ArrayList<>();
                int dug = digPass(pending, true, deadline, problems, unreachable);
                done[0] += dug;
                if (dug > 0) continue;
                if (unreachable.isEmpty()) break;

                Vector3i target = unreachable.stream().min(nearest()).orElseThrow();
                int before = done[0];
                boolean scaffolded = withScaffold(target, Set.of(), scaffoldItem, () -> {
                    int n;
                    do {
                        n = digPass(pending, false, deadline, problems, new ArrayList<>());
                        done[0] += n;
                    } while (n > 0);
                });
                if (!scaffolded || done[0] == before) {
                    problems.add("Couldn't reach " + format(target) + (scaffolded ? "" : ", no place for a scaffold"));
                    pending.remove(target);
                }
            }
            if (!timedOut) pending.forEach(p -> problems.add("Couldn't dig " + GameData.blockName(world.getBlock(p.getX(), p.getY(), p.getZ())) + " at " + format(p)));
            return new BatchResult(done[0], alreadyDone, pending.size(), Map.of(), problems, timedOut);
        } finally {
            actionLock.unlock();
        }
    }

    /** @return number of dug blocks */
    private int digPass(Set<Vector3i> pending, boolean allowMove, long deadline, List<String> problems, List<Vector3i> unreachable) throws Exception {
        int dug = 0;
        List<Vector3i> order = new ArrayList<>(pending);
        order.sort(Comparator.comparingInt(Vector3i::getY).reversed().thenComparing(nearest()));
        for (Vector3i p : order) {
            if (System.currentTimeMillis() > deadline) break;
            int state = world.getBlock(p.getX(), p.getY(), p.getZ());
            if (GameData.isAir(state) || GameData.isFluid(state)) { // e.g. leaves decayed
                pending.remove(p);
                continue;
            }
            // don't dig the floor under the bot, it'd fall into the region
            if (p.getX() == (int) Math.floor(x) && p.getZ() == (int) Math.floor(z) && p.getY() < y) continue;
            if (!allowMove && !inReach(p)) continue;
            try {
                digAt(p, allowMove);
                fallToGround(teleports.get());
                dug++;
                pending.remove(p);
            } catch (OutOfReachException e) {
                unreachable.add(p);
            } catch (MovementCorrectedException e) {
                logger.log(Level.DEBUG, e.getMessage());
            } catch (IllegalStateException e) {
                problems.add(e.getMessage());
                pending.remove(p);
            }
        }
        return dug;
    }

    /** logs, leaves and other parts of trees */
    private static boolean isTree(int state) {
        String name = GameData.blockName(state);
        return name.endsWith("_log") || name.endsWith("_leaves") || name.endsWith("_wood") || name.endsWith("mushroom_block")
                || name.equals("mushroom_stem") || name.equals("vine") || name.equals("bee_nest") || name.equals("cocoa");
    }

    /**
     * a height map of the area around the bot's height.
     * @param ground ignore trees
     * @return text with one row per z, each cell is the height of the top block relative to a base and its legend letter
     */
    public String scanArea(int x1, int z1, int x2, int z2, boolean ground) throws Exception {
        ensureConnected();
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        if (maxX - minX > 63 || maxZ - minZ > 63) throw new IllegalArgumentException("Area must be at most 64x64");
        int top = Math.min(world.maxY(), (int) y + 32), bottom = Math.max(world.minY(), (int) y - 32);
        int[][] heights = new int[maxZ - minZ + 1][maxX - minX + 1];
        String[][] names = new String[maxZ - minZ + 1][maxX - minX + 1];
        int base = Integer.MAX_VALUE;
        for (int bz = minZ; bz <= maxZ; bz++) {
            for (int bx = minX; bx <= maxX; bx++) {
                heights[bz - minZ][bx - minX] = Integer.MIN_VALUE;
                if (!world.isLoaded(bx, bz)) continue;
                for (int by = top; by >= bottom; by--) {
                    int state = world.getBlock(bx, by, bz);
                    if ((GameData.collisionHeight(state) > 0 || GameData.isFluid(state)) && !(ground && isTree(state))) {
                        heights[bz - minZ][bx - minX] = by;
                        names[bz - minZ][bx - minX] = GameData.blockName(state);
                        base = Math.min(base, by);
                        break;
                    }
                }
            }
        }
        Map<String, Character> legend = new LinkedHashMap<>();
        String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();
        sb.append("Height map: each cell is (top block y - ").append(base).append(") + block letter, '??' not loaded, '--' nothing within ")
                .append(bottom).append("..").append(top).append("\n");
        sb.append("x:     ");
        for (int bx = minX; bx <= maxX; bx++) sb.append(String.format("%4d", Math.floorMod(bx, 1000)));
        sb.append('\n');
        for (int bz = minZ; bz <= maxZ; bz++) {
            sb.append(String.format("z=%-5d", bz));
            for (int bx = minX; bx <= maxX; bx++) {
                int h = heights[bz - minZ][bx - minX];
                String name = names[bz - minZ][bx - minX];
                if (!world.isLoaded(bx, bz)) sb.append("  ??");
                else if (h == Integer.MIN_VALUE) sb.append("  --");
                else {
                    char c = legend.computeIfAbsent(name, k -> legend.size() < letters.length() ? letters.charAt(legend.size()) : '?');
                    sb.append(String.format("%3d%c", h - base, c));
                }
            }
            sb.append('\n');
        }
        sb.append("legend: ").append(legend.entrySet().stream().map(e -> e.getValue() + "=" + e.getKey()).collect(Collectors.joining(", ")));
        return sb.toString();
    }

    /** @return null if the chunk is not loaded */
    public Block getBlockAt(int bx, int by, int bz) throws Exception {
        ensureConnected();
        int state = world.getBlock(bx, by, bz);
        return state == World.UNKNOWN ? null : new Block(GameData.blockName(state), state, Vector3i.from(bx, by, bz));
    }

    /** @param blockType exact block name, or a part of names */
    public Block findBlock(String blockType, int maxDistance) throws Exception {
        ensureConnected();
        String name = GameData.stripNamespace(blockType);
        GameData.BlockInfo exact = GameData.block(name);
        maxDistance = Math.min(maxDistance, 64);
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y), cz = (int) Math.floor(z);
        Block nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int bx = cx - maxDistance; bx <= cx + maxDistance; bx++) {
            for (int bz = cz - maxDistance; bz <= cz + maxDistance; bz++) {
                if (!world.isLoaded(bx, bz)) continue;
                for (int by = Math.max(world.minY(), cy - maxDistance); by <= Math.min(world.maxY(), cy + maxDistance); by++) {
                    double d = Vector3d.from(bx + 0.5, by + 0.5, bz + 0.5).distance(x, y + EYE_HEIGHT, z);
                    if (d > maxDistance || d >= nearestDistance) continue;
                    int state = world.getBlock(bx, by, bz);
                    GameData.BlockInfo info = GameData.block(state);
                    if (info == null || !(exact != null ? info == exact : info.name().contains(name))) continue;
                    nearest = new Block(info.name(), state, Vector3i.from(bx, by, bz));
                    nearestDistance = d;
                }
            }
        }
        return nearest;
    }

    /** @param type entity type (e.g. "cow", "player") or player name, empty for any */
    public Entity findNearestEntity(String type, int maxDistance) throws Exception {
        ensureConnected();
        String query = type == null ? "" : GameData.stripNamespace(type);
        return entities.values().stream()
                .map(e -> {
                    String typeName = e.type.name().toLowerCase();
                    String name = e.type == EntityType.PLAYER ? playerNames.getOrDefault(e.uuid, typeName) : typeName;
                    return new Entity(name, typeName, Vector3d.from(e.x, e.y, e.z));
                })
                .filter(e -> query.isEmpty() || e.type().equals(query) || e.name().equalsIgnoreCase(query) || e.type().contains(query))
                .filter(e -> e.position().distance(getPosition()) <= maxDistance)
                .min(Comparator.comparingDouble(e -> e.position().distance(getPosition())))
                .orElse(null);
    }

    /**
     * sends a chat message, or a command if it starts with '/'.
     * @return chat/system messages received shortly after sending (e.g. command feedback)
     */
    public List<String> sendChat(String message) throws Exception {
        ensureConnected();
        long since = System.currentTimeMillis();
        long timestamp = System.currentTimeMillis();
        if (message.startsWith("/")) {
            // signed variant with no signatures is accepted for any command from offline profiles
            send(new ServerboundChatCommandSignedPacket(message.substring(1), timestamp, 0L, List.of(), 0, new BitSet(20), (byte) 0));
        } else {
            send(new ServerboundChatPacket(message, timestamp, 0L, null, 0, new BitSet(20), 0));
        }
        Thread.sleep(500);
        synchronized (messages) {
            return messages.stream().filter(e -> e.getKey() >= since).map(Map.Entry::getValue).collect(Collectors.toList());
        }
    }
}
