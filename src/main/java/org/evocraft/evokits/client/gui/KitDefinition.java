package org.evocraft.evokits.client.gui;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared kit view model synchronized from the server for SelectKitScreen and KitPreviewScreen.
 */
public class KitDefinition {

    private final String id;
    private final String displayName;
    private final String permission;
    private final ItemStack icon;
    private final List<ItemStack> items;
    private final List<String> crateKeys;
    private final int cooldownHours;
    private final long initialRemainingCooldownSeconds;

    private boolean unlocked;
    private long cooldownUntilMillis;

    public KitDefinition(
            String id,
            String displayName,
            String permission,
            ItemStack icon,
            List<ItemStack> items,
            List<String> crateKeys,
            int cooldownHours,
            long remainingCooldownSeconds,
            boolean unlocked
    ) {
        this.id = id != null ? id : "";
        this.displayName = (displayName != null && !displayName.isBlank())
                ? displayName
                : formatDisplayName(this.id);
        this.permission = permission != null ? permission : "";
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.crateKeys = crateKeys != null ? new ArrayList<>(crateKeys) : new ArrayList<>();
        this.icon = resolveIcon(icon, this.items);
        this.cooldownHours = Math.max(0, cooldownHours);
        this.initialRemainingCooldownSeconds = Math.max(0L, remainingCooldownSeconds);
        this.unlocked = unlocked;
        this.cooldownUntilMillis = this.initialRemainingCooldownSeconds > 0L
                ? System.currentTimeMillis() + (this.initialRemainingCooldownSeconds * 1000L)
                : 0L;
    }

    private static ItemStack resolveIcon(ItemStack rawIcon, List<ItemStack> items) {
        if (rawIcon != null && !rawIcon.isEmpty()) {
            return rawIcon.copy();
        }
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) {
                return stack.copy();
            }
        }
        return new ItemStack(Items.CHEST);
    }

    public static String formatDisplayName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "KIT";
        }
        String cleaned = rawName.trim().replace('_', ' ').toUpperCase(Locale.ROOT);
        if (cleaned.endsWith("KIT") || cleaned.startsWith("KIT ")) {
            return cleaned;
        }
        return cleaned + " KIT";
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPermission() {
        return permission;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public List<ItemStack> getItems() {
        return Collections.unmodifiableList(items);
    }

    public List<String> getCrateKeys() {
        return Collections.unmodifiableList(crateKeys);
    }

    public int getCooldownHours() {
        return cooldownHours;
    }

    public long getInitialRemainingCooldownSeconds() {
        return initialRemainingCooldownSeconds;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public long getRemainingCooldownSeconds() {
        long diff = cooldownUntilMillis - System.currentTimeMillis();
        return diff > 0L ? (long) Math.ceil(diff / 1000.0) : 0L;
    }

    public static String formatDuration(long totalSeconds) {
        long safe = Math.max(0L, totalSeconds);
        long hrs = safe / 3600L;
        long mins = (safe % 3600L) / 60L;
        long secs = safe % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hrs, mins, secs);
    }
}
