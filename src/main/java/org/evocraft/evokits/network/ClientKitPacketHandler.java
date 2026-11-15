package org.evocraft.evokits.network;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.evocraft.evokits.client.gui.KitDefinition;
import org.evocraft.evokits.client.gui.KitPreviewScreen;
import org.evocraft.evokits.client.gui.SelectKitScreen;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientKitPacketHandler {

    public static List<KitDefinition> cachedKits = new ArrayList<>();

    public static void handleSyncKits(boolean openScreen, List<KitDefinition> kits) {
        cachedKits = kits != null ? new ArrayList<>(kits) : new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        if (openScreen) {
            mc.setScreen(new SelectKitScreen(cachedKits, 0));
            return;
        }

        if (mc.screen instanceof KitPreviewScreen previewScreen) {
            previewScreen.updateKits(cachedKits);
        } else if (mc.screen instanceof SelectKitScreen selectScreen) {
            selectScreen.updateKits(cachedKits);
        }
    }
}
