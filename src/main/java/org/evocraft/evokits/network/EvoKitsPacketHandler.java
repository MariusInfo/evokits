package org.evocraft.evokits.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.evocraft.evokits.EvoKits;
import org.evocraft.evokits.client.gui.KitDefinition;
import org.evocraft.evokits.data.KitManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class EvoKitsPacketHandler {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(EvoKits.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered = false;

    public static void register() {
        if (registered) return;
        registered = true;

        int id = 0;
        INSTANCE.registerMessage(
                id++,
                S2C_SyncKitsPacket.class,
                S2C_SyncKitsPacket::toBytes,
                S2C_SyncKitsPacket::new,
                S2C_SyncKitsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        INSTANCE.registerMessage(
                id++,
                C2S_RequestKitsPacket.class,
                C2S_RequestKitsPacket::toBytes,
                C2S_RequestKitsPacket::new,
                C2S_RequestKitsPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        INSTANCE.registerMessage(
                id++,
                C2S_ClaimKitPacket.class,
                C2S_ClaimKitPacket::toBytes,
                C2S_ClaimKitPacket::new,
                C2S_ClaimKitPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    public static void sendKitsToPlayer(ServerPlayer player, boolean openScreen) {
        if (player == null) return;
        List<KitDefinition> kits = buildKitsForPlayer(player);
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2C_SyncKitsPacket(openScreen, kits));
    }

    public static List<KitDefinition> buildKitsForPlayer(ServerPlayer player) {
        List<KitDefinition> result = new ArrayList<>();
        boolean isOp = player.hasPermissions(2);
        for (KitManager.Kit kit : KitManager.get().kits.values()) {
            boolean unlocked = isOp || KitManager.hasPermission(player, kit.permission);
            long remainingSec = isOp ? 0L : KitManager.get().getRemainingCooldownSeconds(player.getUUID(), kit.name);
            result.add(new KitDefinition(
                    kit.name,
                    KitDefinition.formatDisplayName(kit.name),
                    kit.permission,
                    kit.icon,
                    kit.items,
                    kit.crateKeys,
                    kit.cooldownHours,
                    remainingSec,
                    unlocked
            ));
        }
        return result;
    }

    public static class S2C_SyncKitsPacket {
        private final boolean openScreen;
        private final List<KitDefinition> kits;

        public S2C_SyncKitsPacket(boolean openScreen, List<KitDefinition> kits) {
            this.openScreen = openScreen;
            this.kits = kits != null ? kits : new ArrayList<>();
        }

        public S2C_SyncKitsPacket(FriendlyByteBuf buf) {
            this.openScreen = buf.readBoolean();
            int count = buf.readVarInt();
            this.kits = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String id = buf.readUtf(128);
                String displayName = buf.readUtf(128);
                String permission = buf.readUtf(256);
                ItemStack icon = buf.readItem();
                int cooldownHours = buf.readVarInt();
                long remainingSeconds = buf.readVarLong();
                boolean unlocked = buf.readBoolean();

                int itemCount = buf.readVarInt();
                List<ItemStack> items = new ArrayList<>(itemCount);
                for (int j = 0; j < itemCount; j++) {
                    items.add(buf.readItem());
                }

                int keyCount = buf.readVarInt();
                List<String> crateKeys = new ArrayList<>(keyCount);
                for (int j = 0; j < keyCount; j++) {
                    crateKeys.add(buf.readUtf(128));
                }

                this.kits.add(new KitDefinition(
                        id, displayName, permission, icon, items, crateKeys,
                        cooldownHours, remainingSeconds, unlocked
                ));
            }
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(openScreen);
            buf.writeVarInt(kits.size());
            for (KitDefinition kit : kits) {
                buf.writeUtf(kit.getId(), 128);
                buf.writeUtf(kit.getDisplayName(), 128);
                buf.writeUtf(kit.getPermission(), 256);
                buf.writeItem(kit.getIcon());
                buf.writeVarInt(kit.getCooldownHours());
                buf.writeVarLong(kit.getInitialRemainingCooldownSeconds());
                buf.writeBoolean(kit.isUnlocked());

                List<ItemStack> items = kit.getItems();
                buf.writeVarInt(items.size());
                for (ItemStack stack : items) {
                    buf.writeItem(stack != null ? stack : ItemStack.EMPTY);
                }

                List<String> keys = kit.getCrateKeys();
                buf.writeVarInt(keys.size());
                for (String key : keys) {
                    buf.writeUtf(key != null ? key : "", 128);
                }
            }
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            ClientKitPacketHandler.handleSyncKits(openScreen, kits)
                    )
            );
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_RequestKitsPacket {
        private final boolean openScreen;

        public C2S_RequestKitsPacket(boolean openScreen) {
            this.openScreen = openScreen;
        }

        public C2S_RequestKitsPacket(FriendlyByteBuf buf) {
            this.openScreen = buf.readBoolean();
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeBoolean(openScreen);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    sendKitsToPlayer(player, openScreen);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class C2S_ClaimKitPacket {
        private final String kitId;

        public C2S_ClaimKitPacket(String kitId) {
            this.kitId = kitId != null ? kitId : "";
        }

        public C2S_ClaimKitPacket(FriendlyByteBuf buf) {
            this.kitId = buf.readUtf(128);
        }

        public void toBytes(FriendlyByteBuf buf) {
            buf.writeUtf(kitId, 128);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player != null) {
                    KitManager.get().claimKit(player, kitId);
                    sendKitsToPlayer(player, false);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
