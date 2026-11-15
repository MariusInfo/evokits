package org.evocraft.evokits.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import org.evocraft.evocore.data.CrateKeyManager;
import org.evocraft.evocore.database.DatabaseManager;

import java.io.File;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class KitManager {
    private static final Pattern VALID_KIT_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");
    private static KitManager INSTANCE;

    public static synchronized KitManager get() {
        if (INSTANCE == null) INSTANCE = new KitManager();
        return INSTANCE;
    }

    public static class Kit {
        public String name;
        public String permission;
        public int cooldownHours;
        public ItemStack icon;
        public List<ItemStack> items = new ArrayList<>();
        public List<String> crateKeys = new ArrayList<>();
    }

    public final Map<String, Kit> kits = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Map<UUID, Map<String, Long>> cooldownCache = new ConcurrentHashMap<>();
    private final File file = new File(FMLPaths.CONFIGDIR.get().toFile(), "evocraft_kits.nbt");

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "EvoKits DB Writer");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean shuttingDown = false;

    private static volatile boolean combatMethodChecked = false;
    private static Method combatIsInCombatMethod = null;

    public KitManager() {
        load();
        initializeDB();
    }

    public static boolean isValidKitName(String name) {
        return name != null && VALID_KIT_NAME.matcher(name).matches();
    }

    public List<Kit> getKitsSnapshot() {
        synchronized (kits) {
            return new ArrayList<>(kits.values());
        }
    }

    public void onPlayerLogout(UUID uuid) {
        if (uuid != null) {
            cooldownCache.remove(uuid);
        }
    }

    public void shutdown() {
        shuttingDown = true;
        save();
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dbExecutor.shutdownNow();
        }
        synchronized (KitManager.class) {
            if (INSTANCE == this) {
                INSTANCE = null;
            }
        }
    }

    public void initializeDB() {
        synchronized (DatabaseManager.get()) {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                if (conn == null || conn.isClosed()) return;
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_kit_cooldowns (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "kit_name VARCHAR(64) NOT NULL, " +
                            "last_claimed BIGINT NOT NULL, " +
                            "PRIMARY KEY (uuid, kit_name))");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            CompoundTag root = new CompoundTag();
            ListTag kitList = new ListTag();
            for (Kit kit : getKitsSnapshot()) {
                CompoundTag kTag = new CompoundTag();
                kTag.putString("Name", kit.name);
                kTag.putString("Permission", kit.permission);
                kTag.putInt("Cooldown", kit.cooldownHours);
                kTag.put("Icon", (kit.icon != null ? kit.icon : ItemStack.EMPTY).save(new CompoundTag()));

                ListTag itemList = new ListTag();
                for (ItemStack item : kit.items) {
                    if (item != null && !item.isEmpty()) {
                        itemList.add(item.save(new CompoundTag()));
                    }
                }
                kTag.put("Items", itemList);

                ListTag keyList = new ListTag();
                for (String keyName : kit.crateKeys) {
                    String normalized = normalizeKeyName(keyName);
                    if (!normalized.isBlank()) {
                        keyList.add(StringTag.valueOf(normalized));
                    }
                }
                kTag.put("CrateKeys", keyList);
                kitList.add(kTag);
            }
            root.put("Kits", kitList);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void load() {
        synchronized (kits) {
            kits.clear();
            if (!file.exists()) return;
            try {
                CompoundTag root = NbtIo.readCompressed(file);
                if (root == null) return;
                ListTag kitList = root.getList("Kits", 10);
                for (int i = 0; i < kitList.size(); i++) {
                    CompoundTag kTag = kitList.getCompound(i);
                    String name = kTag.getString("Name");
                    if (name == null || name.isBlank()) continue;

                    Kit kit = new Kit();
                    kit.name = name;
                    kit.permission = kTag.getString("Permission");
                    kit.cooldownHours = Math.max(0, kTag.getInt("Cooldown"));
                    kit.icon = ItemStack.of(kTag.getCompound("Icon"));

                    ListTag itemList = kTag.getList("Items", 10);
                    for (int j = 0; j < itemList.size(); j++) {
                        ItemStack stack = ItemStack.of(itemList.getCompound(j));
                        if (!stack.isEmpty()) {
                            kit.items.add(stack);
                        }
                    }

                    Set<String> keyNames = new LinkedHashSet<>();
                    ListTag keyList = kTag.getList("CrateKeys", 8);
                    for (int j = 0; j < keyList.size(); j++) {
                        String keyName = normalizeKeyName(keyList.getString(j));
                        if (!keyName.isBlank()) keyNames.add(keyName);
                    }

                    kit.crateKeys.addAll(keyNames);
                    kits.put(kit.name.toLowerCase(Locale.ROOT), kit);
                }
                System.out.println("[EvoKits] Loaded " + kits.size() + " kit(s) from config.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public boolean createKit(String name, int cooldownHours, List<ItemStack> inventoryItems) {
        if (!isValidKitName(name)) return false;

        Kit kit = new Kit();
        kit.name = name;
        kit.permission = "kit." + name.toLowerCase(Locale.ROOT);
        kit.cooldownHours = Math.max(0, cooldownHours);

        kit.icon = ItemStack.EMPTY;
        for (ItemStack stack : inventoryItems) {
            if (stack != null && !stack.isEmpty()) {
                ItemStack copy = stack.copy();
                if (kit.icon.isEmpty()) {
                    kit.icon = copy.copy();
                }
                kit.items.add(copy);
            }
        }

        kits.put(name.toLowerCase(Locale.ROOT), kit);
        save();
        return true;
    }

    public boolean addCrateKey(String keyName, String kitName) {
        if (kitName == null) return false;
        Kit kit = kits.get(kitName.toLowerCase(Locale.ROOT));
        if (kit == null) return false;

        String normalizedKey = normalizeKeyName(keyName);
        if (normalizedKey.isBlank()) return false;

        synchronized (kits) {
            if (!kit.crateKeys.contains(normalizedKey)) {
                kit.crateKeys.add(normalizedKey);
            }
        }
        save();
        return true;
    }

    public int grantCrateKeys(ServerPlayer player, Kit kit) {
        if (player == null || kit == null || kit.crateKeys.isEmpty()) return 0;

        int granted = 0;
        for (String keyName : kit.crateKeys) {
            String normalizedKey = normalizeKeyName(keyName);
            if (normalizedKey.isBlank()) continue;
            CrateKeyManager.get().addKeys(player.getGameProfile().getName(), normalizedKey, 1);
            granted++;
        }
        return granted;
    }

    public boolean deleteKit(String name) {
        if (name == null || name.isBlank()) return false;
        String kitKey = name.toLowerCase(Locale.ROOT);
        Kit removed = kits.remove(kitKey);
        if (removed == null) return false;

        save();
        for (Map<String, Long> playerMap : cooldownCache.values()) {
            playerMap.remove(kitKey);
        }

        Runnable deleteDbTask = () -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null || conn.isClosed()) return;
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM player_kit_cooldowns WHERE kit_name = ?")) {
                        ps.setString(1, kitKey);
                        ps.executeUpdate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        if (shuttingDown || dbExecutor.isShutdown()) {
            deleteDbTask.run();
        } else {
            dbExecutor.execute(deleteDbTask);
        }
        return true;
    }

    public long getRemainingCooldownSeconds(UUID uuid, String kitName) {
        if (uuid == null || kitName == null) return 0;
        Kit kit = kits.get(kitName.toLowerCase(Locale.ROOT));
        if (kit == null || kit.cooldownHours <= 0) return 0;

        long lastClaim = getCooldownFromCacheOrDB(uuid, kit.name.toLowerCase(Locale.ROOT));
        if (lastClaim <= 0) return lastClaim < 0 ? -1L : 0L;

        long cooldownMillis = (long) kit.cooldownHours * 3600L * 1000L;
        long elapsed = System.currentTimeMillis() - lastClaim;
        if (elapsed >= cooldownMillis) return 0;

        return (cooldownMillis - elapsed) / 1000L;
    }

    private long getCooldownFromCacheOrDB(UUID uuid, String kitKey) {
        Map<String, Long> playerMap = cooldownCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        Long cached = playerMap.get(kitKey);
        if (cached != null) {
            return cached;
        }

        synchronized (DatabaseManager.get()) {
            try {
                Connection conn = DatabaseManager.get().getConnection();
                if (conn == null || conn.isClosed()) {
                    return -1L;
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT last_claimed FROM player_kit_cooldowns WHERE uuid = ? AND kit_name = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, kitKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long val = rs.getLong("last_claimed");
                            playerMap.put(kitKey, val);
                            return val;
                        }
                        playerMap.put(kitKey, 0L);
                        return 0L;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return -1L;
            }
        }
    }

    private static boolean isPlayerInCombat(ServerPlayer player) {
        if (player == null) return false;
        if (!combatMethodChecked) {
            combatMethodChecked = true;
            if (ModList.get().isLoaded("evocombat")) {
                try {
                    Class<?> cls = Class.forName("org.evocraft.evocombat.combat.CombatLogManager");
                    combatIsInCombatMethod = cls.getMethod("isInCombat", ServerPlayer.class);
                } catch (Throwable ignored) {
                    combatIsInCombatMethod = null;
                }
            }
        }
        if (combatIsInCombatMethod != null) {
            try {
                Object res = combatIsInCombatMethod.invoke(null, player);
                return Boolean.TRUE.equals(res);
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public boolean claimKit(ServerPlayer player, String kitName) {
        if (player == null || kitName == null || kitName.isBlank()) return false;
        String kitKey = kitName.toLowerCase(Locale.ROOT);
        Kit kit = kits.get(kitKey);
        if (kit == null) {
            player.sendSystemMessage(Component.literal("§c✖ Kit §e" + kitName + " §cdoes not exist!"));
            return false;
        }

        boolean isOp = player.hasPermissions(2);
        if (!isOp && isPlayerInCombat(player)) {
            player.sendSystemMessage(Component.literal("§c✖ You cannot claim kits while in combat!"));
            return false;
        }

        if (!isOp && !hasPermission(player, kit.permission)) {
            player.sendSystemMessage(Component.literal("§c✖ You do not have permission to claim kit §e" + kit.name + "§c!"));
            return false;
        }

        long remainingSec = getRemainingCooldownSeconds(player.getUUID(), kit.name);
        if (remainingSec < 0 && !isOp) {
            player.sendSystemMessage(Component.literal("§c✖ Could not verify kit cooldown right now. Please try again shortly."));
            return false;
        }
        if (remainingSec > 0 && !isOp) {
            long hours = remainingSec / 3600;
            long minutes = (remainingSec % 3600) / 60;
            long seconds = remainingSec % 60;
            player.sendSystemMessage(Component.literal("§c✖ You must wait §e" + hours + "h " + minutes + "m " + seconds + "s §cbefore claiming this kit again!"));
            return false;
        }

        for (ItemStack stack : kit.items) {
            if (stack == null || stack.isEmpty()) continue;
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false);
            }
        }

        int keysGranted = grantCrateKeys(player, kit);

        long now = System.currentTimeMillis();
        UUID playerUuid = player.getUUID();
        cooldownCache.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(kitKey, now);

        Runnable saveCooldownTask = () -> {
            synchronized (DatabaseManager.get()) {
                try {
                    Connection conn = DatabaseManager.get().getConnection();
                    if (conn == null || conn.isClosed()) return;
                    boolean mySql = DatabaseManager.get().isMySql();
                    String query = mySql
                            ? "INSERT INTO player_kit_cooldowns (uuid, kit_name, last_claimed) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE last_claimed = ?"
                            : "INSERT INTO player_kit_cooldowns (uuid, kit_name, last_claimed) VALUES (?, ?, ?) ON CONFLICT(uuid, kit_name) DO UPDATE SET last_claimed = excluded.last_claimed";
                    try (PreparedStatement ps = conn.prepareStatement(query)) {
                        ps.setString(1, playerUuid.toString());
                        ps.setString(2, kitKey);
                        ps.setLong(3, now);
                        if (mySql) {
                            ps.setLong(4, now);
                        }
                        ps.executeUpdate();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        if (shuttingDown || dbExecutor.isShutdown()) {
            saveCooldownTask.run();
        } else {
            dbExecutor.execute(saveCooldownTask);
        }

        player.sendSystemMessage(Component.literal("§a✔ Successfully claimed kit §l" + kit.name + "§a!"));
        if (keysGranted > 0) {
            player.sendSystemMessage(Component.literal("§6★ Received §e" + keysGranted + " §6crate key(s)!"));
        }
        return true;
    }

    public static boolean hasPermission(ServerPlayer player, String permission) {
        if (player == null) return false;
        if (player.hasPermissions(2)) return true;
        if (permission == null || permission.isBlank()) return false;
        try {
            net.luckperms.api.LuckPerms lp = net.luckperms.api.LuckPermsProvider.get();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUUID());
            if (user != null) {
                return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private String normalizeKeyName(String keyName) {
        if (keyName == null) return "";
        return keyName.trim().toLowerCase(Locale.ROOT);
    }
}
