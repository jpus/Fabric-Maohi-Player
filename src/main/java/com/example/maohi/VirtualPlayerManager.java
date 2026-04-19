package com.example.maohi;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.util.math.random.Random;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 虚拟玩家管理器
 * 增加随机聊天消息，防止面板空闲检测误判
 */
public class VirtualPlayerManager {

    private static final int MAX_VIRTUAL_PLAYERS = 5;
    private static final int RESPAWN_DELAY_TICKS = 100;

    private static final String[] NAME_PREFIXES = {
        "Craft", "Mine", "Pixel", "Block", "Diamond", "Emerald", "Red", "Blue",
        "Dark", "Light", "Fire", "Ice", "Shadow", "Storm", "Thunder", "Dragon",
        "Wolf", "Bear", "Fox", "Eagle", "Hawk", "Phoenix", "Titan", "Nova",
        "Iron", "Gold", "Copper", "Steel", "Crystal", "Frost", "Blaze", "Ender",
        "Sky", "Moon", "Star", "Sun", "Void", "Nether", "Ocean", "Lava",
        "Ninja", "Cyber", "Alpha", "Omega", "Turbo", "Ultra", "Mega", "Hyper"
    };

    private static final String[] NAME_MIDDLES = {
        "Master", "King", "Lord", "Pro", "Gamer", "Hunter", "Knight",
        "Warrior", "Mage", "Rogue", "Archer", "Slayer", "Builder",
        "Crafter", "Runner", "Rider", "Seeker", "Breaker", "Striker", "Legend",
        "Chief", "Boss", "Captain", "Champ", "Hero", "Ace", "Warden"
    };

    private static final String[] NAME_SUFFIXES = {
        "2024", "2025", "2026", "_xp", "_mc", "HD", "Pro", "YT", "XD", "LP",
        "99", "77", "42", "Gaming", "Real", "007", "123", "GG", "OP", "TV",
        "_TTV", "Live", "Plays", "FTW", "OG", "Jr", "Sr", "_x", "_v2", "Max"
    };

    // 随机聊天消息库（模仿真人）
    private static final String[] CHAT_MESSAGES = {
        "gg", "lol", "nice", "hello", "hi", "what's up", "let's go",
        "brb", "afk", "omw", "ty", "thanks", "wow", "cool", "epic",
        "i'm lagging", "so lag", "rip", "haha", "xd", "oof", "wait what",
        "where is everyone", "anyone here?", "hello?", "gg wp", "easy",
        "i need food", "back in a sec", "afk moment", "nice shot"
    };

    private final MinecraftServer server;
    private final List<UUID> virtualPlayerUUIDs = new CopyOnWriteArrayList<>();
    private final Map<UUID, String> virtualPlayerNames = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRespawn = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> deathTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, net.minecraft.network.ClientConnection> fakeConnections = new ConcurrentHashMap<>();

    private Thread managerThread;
    private volatile boolean running = true;

    public VirtualPlayerManager(MinecraftServer server) {
        this.server = server;
    }

    public void start() {
        if (managerThread != null && managerThread.isAlive()) return;
        running = true;
        managerThread = new Thread(this::manageLoop, "VirtualPlayer-Manager");
        managerThread.setDaemon(true);
        managerThread.start();
    }

    public void stop() {
        running = false;
        if (managerThread != null) managerThread.interrupt();
        for (UUID uuid : new ArrayList<>(virtualPlayerUUIDs)) {
            kickVirtualPlayer(uuid);
        }
    }

    private void manageLoop() {
        while (running) {
            try {
                if (server.getOverworld() != null && server.getPlayerManager() != null) break;
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
        }

        while (running) {
            try {
                server.execute(() -> {
                    try {
                        checkAndRemoveDisconnectedPlayers();
                        int currentCount = getOnlineVirtualPlayerCount();
                        if (currentCount < MAX_VIRTUAL_PLAYERS) {
                            int toSpawn = MAX_VIRTUAL_PLAYERS - currentCount;
                            for (int i = 0; i < toSpawn; i++) spawnVirtualPlayer();
                        }
                        processRespawnQueue();

                        // 假人随机行为（每 tick 执行一些，但实际 manageLoop 每10秒才循环一次，所以这里用 tick 频率不够）
                        // 注意：下面的随机动作是在主线程执行的，但频率较低（每10秒一次），为了增加活跃度，我们在每个假人上执行更多动作。
                        for (UUID uuid : virtualPlayerUUIDs) {
                            ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                            if (p != null) {
                                // 随机转头
                                p.setYaw(p.getYaw() + (float)(Math.random() * 180 - 90));
                                p.setPitch((float)(Math.random() * 90 - 45));
                                // 随机潜行
                                p.setSneaking(Math.random() > 0.8);
                                // 随机挥手
                                if (Math.random() > 0.5) {
                                    p.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);
                                }
                                // 随机跳跃
                                if (p.isOnGround() && Math.random() > 0.85) {
                                    p.jump();
                                }
                                // 随机冲刺
                                if (Math.random() > 0.85) {
                                    p.setSprinting(true);
                                    double radianYaw = Math.toRadians(p.getYaw());
                                    double thrustX = -Math.sin(radianYaw) * 0.8;
                                    double thrustZ = Math.cos(radianYaw) * 0.8;
                                    p.addVelocity(thrustX, 0.2, thrustZ);
                                } else if (Math.random() > 0.3) {
                                    p.setSprinting(false);
                                }

                                // ***** 关键新增：随机发送聊天消息（约 5% 概率每次循环，即平均每 200 秒一次，提高至 15% 更活跃）*****
                                if (Math.random() > 0.85) {  // 15% 概率
                                    String randomMsg = CHAT_MESSAGES[Random.create().nextInt(CHAT_MESSAGES.length)];
                                    // 通过 networkHandler 发送聊天消息（模拟玩家说话）
                                    try {
                                        p.networkHandler.sendChatMessage(randomMsg);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Throwable t) {}
                });

                Thread.sleep(10000); // 每10秒执行一次上面的动作（同时聊天也会每10秒约15%概率触发，即平均每66秒一条消息）
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void checkAndRemoveDisconnectedPlayers() {
        Iterator<UUID> iterator = virtualPlayerUUIDs.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (server.getPlayerManager().getPlayer(uuid) == null) {
                virtualPlayerNames.remove(uuid);
                iterator.remove();
            }
        }
    }

    private int getOnlineVirtualPlayerCount() {
        int count = 0;
        for (UUID uuid : virtualPlayerUUIDs) {
            if (server.getPlayerManager().getPlayer(uuid) != null) count++;
        }
        return count;
    }

    private String generateRandomName() {
        Random random = Random.create();
        int style = random.nextInt(6);
        switch (style) {
            case 0: return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] + random.nextInt(1000);
            case 1: return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] + NAME_MIDDLES[random.nextInt(NAME_MIDDLES.length)] + random.nextInt(1000);
            case 2: return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] + NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
            case 3: return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] + NAME_MIDDLES[random.nextInt(NAME_MIDDLES.length)] + NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
            case 4: return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] + random.nextInt(100);
            default: return NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)] + NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)];
        }
    }

    private String generateUniqueName() {
        Set<String> existingNames = new HashSet<>(virtualPlayerNames.values());
        String name;
        int attempts = 0;
        do {
            name = generateRandomName();
            attempts++;
            if (attempts > 100) {
                name = "VirtualPlayer_" + System.currentTimeMillis() % 10000;
                break;
            }
        } while (existingNames.contains(name) || server.getPlayerManager().getPlayer(name) != null);
        return name;
    }

    private com.mojang.authlib.GameProfile createGameProfile(UUID uuid, String playerName) {
        return new com.mojang.authlib.GameProfile(uuid, playerName);
    }

    private void setPlayerSpawnLocation(ServerPlayerEntity player) {
        double targetX = 0, targetZ = 0;
        boolean gotSpawnPos = false;
        try {
            BlockPos spawnPos = server.getOverworld().getSpawnPos();
            if (spawnPos != null) {
                targetX = spawnPos.getX();
                targetZ = spawnPos.getZ();
                gotSpawnPos = true;
            }
        } catch (Throwable ignored) {}
        targetX += (Math.random() * 30) - 15;
        targetZ += (Math.random() * 30) - 15;
        try {
            double groundY = server.getOverworld().getTopY(Heightmap.Type.MOTION_BLOCKING, (int)targetX, (int)targetZ);
            if (groundY <= -60) groundY = 64;
            player.setPosition(targetX, groundY + 1.0, targetZ);
        } catch (Throwable t) {
            player.setPosition(targetX, 65.0, targetZ);
        }
    }

    private void spawnVirtualPlayer() {
        if (server.getOverworld() == null || server.getPlayerManager() == null) return;
        try {
            String playerName = generateUniqueName();
            UUID uuid = UUID.randomUUID();
            com.mojang.authlib.GameProfile profile = createGameProfile(uuid, playerName);
            net.minecraft.network.packet.c2s.common.SyncedClientOptions options = net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault();
            ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(), profile, options);
            setPlayerSpawnLocation(player);
            net.minecraft.network.ClientConnection connection = new FakeClientConnection();
            try { server.getNetworkIo().getConnections().add(connection); } catch (Throwable ignored) {}
            net.minecraft.server.network.ConnectedClientData clientData = net.minecraft.server.network.ConnectedClientData.createDefault(profile, false);
            server.getPlayerManager().onPlayerConnect(connection, player, clientData);
            UUID actualUuid = player.getUuid();
            virtualPlayerUUIDs.add(actualUuid);
            virtualPlayerNames.put(actualUuid, playerName);
            fakeConnections.put(actualUuid, connection);
        } catch (Throwable t) {
            System.err.println("[Maohi Debug] Error in spawnVirtualPlayer: " + t.getMessage());
        }
    }

    private void kickVirtualPlayer(UUID uuid) {
        server.execute(() -> {
            try {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    try {
                        net.minecraft.network.ClientConnection conn = fakeConnections.remove(uuid);
                        if (conn != null) server.getNetworkIo().getConnections().remove(conn);
                    } catch (Throwable ignored) {}
                    try { server.getPlayerManager().remove(player); } catch (Throwable ignored) {}
                }
                virtualPlayerNames.remove(uuid);
                virtualPlayerUUIDs.remove(uuid);
                fakeConnections.remove(uuid);
                deathTimestamps.remove(uuid);
            } catch (Throwable t) {}
        });
    }

    private void processRespawnQueue() {
        Iterator<UUID> iterator = pendingRespawn.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            Long deathTime = deathTimestamps.get(uuid);
            if (deathTime != null && System.currentTimeMillis() - deathTime < RESPAWN_DELAY_TICKS * 50L) continue;
            deathTimestamps.remove(uuid);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null && player.isAlive()) {
                iterator.remove();
                continue;
            }
            if (player != null && !player.isAlive()) {
                try { player.networkHandler.disconnect(Text.of("Respawning")); } catch (Throwable ignored) {}
            }
            respawnVirtualPlayer(uuid);
            iterator.remove();
        }
    }

    private void respawnVirtualPlayer(UUID uuid) {
        if (server.getOverworld() == null || server.getPlayerManager() == null) return;
        String playerName = virtualPlayerNames.get(uuid);
        if (playerName == null) playerName = generateUniqueName();
        final String finalName = playerName;
        server.execute(() -> {
            try {
                virtualPlayerUUIDs.remove(uuid);
                UUID newUuid = UUID.randomUUID();
                com.mojang.authlib.GameProfile profile = createGameProfile(newUuid, finalName);
                net.minecraft.network.packet.c2s.common.SyncedClientOptions options = net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault();
                ServerPlayerEntity player = new ServerPlayerEntity(server, server.getOverworld(), profile, options);
                setPlayerSpawnLocation(player);
                net.minecraft.network.ClientConnection connection = new FakeClientConnection();
                try { server.getNetworkIo().getConnections().add(connection); } catch (Throwable ignored) {}
                net.minecraft.server.network.ConnectedClientData clientData = net.minecraft.server.network.ConnectedClientData.createDefault(profile, false);
                server.getPlayerManager().onPlayerConnect(connection, player, clientData);
                UUID actualUuid = player.getUuid();
                virtualPlayerUUIDs.add(actualUuid);
                virtualPlayerNames.put(actualUuid, finalName);
                fakeConnections.put(actualUuid, connection);
            } catch (Throwable t) {}
        });
    }

    public void onVirtualPlayerDeath(UUID uuid) {
        if (virtualPlayerUUIDs.contains(uuid)) {
            pendingRespawn.add(uuid);
            deathTimestamps.put(uuid, System.currentTimeMillis());
        }
    }

    public boolean isVirtualPlayer(UUID uuid) {
        return virtualPlayerUUIDs.contains(uuid);
    }

    public int getVirtualPlayerCount() {
        return getOnlineVirtualPlayerCount();
    }

    public Set<UUID> getVirtualPlayerUUIDs() {
        return new HashSet<>(virtualPlayerUUIDs);
    }

    public String getStatusSummary() {
        return String.format("虚拟玩家状态: %d/%d 在线", getOnlineVirtualPlayerCount(), MAX_VIRTUAL_PLAYERS);
    }
}