package org.evocraft.evokits.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.evocraft.evokits.EvoKits;
import org.evocraft.evokits.network.EvoKitsPacketHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 2: "KIT PREVIEW" with 6x3 item slots (paginated if > 18 items),
 * LuckPerms permission / crate key requirements, live SQL cooldown, BACK, UNLOCKED/LOCKED badge, and CLAIM KIT.
 */
public class KitPreviewScreen extends Screen {

    private static final ResourceLocation BG_TEXTURE =
            new ResourceLocation(EvoKits.MODID, "textures/gui/kit_preview_bg.png");
    private static final ResourceLocation WIDGETS_TEXTURE =
            new ResourceLocation(EvoKits.MODID, "textures/gui/widgets.png");

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 214;
    private static final int ITEMS_PER_PAGE = 18;

    private static final int SLOTS_START_X = 44;
    private static final int SLOTS_START_Y = 70;
    private static final int SLOT_STRIDE_X = 24;
    private static final int SLOT_STRIDE_Y = 26;
    private static final int SLOT_BOX_SIZE = 23;

    private static final int ITEM_PREV_X = 76;
    private static final int ITEM_NEXT_X = 140;
    private static final int ITEM_NAV_Y = 152;
    private static final int ITEM_NAV_W = 16;
    private static final int ITEM_NAV_H = 14;

    private static final int REQ_BOX_X = 208;
    private static final int REQ_BOX_Y = 49;
    private static final int REQ_BOX_W = 74;
    private static final int REQ_BOX_H = 54;

    private static final int CLOSE_X = 293;
    private static final int CLOSE_Y = 6;
    private static final int CLOSE_SIZE = 20;

    private static final int BACK_X = 34;
    private static final int BACK_Y = 192;
    private static final int BACK_W = 54;
    private static final int BACK_H = 16;

    private static final int STATUS_X = 117;
    private static final int STATUS_Y = 193;
    private static final int STATUS_W = 86;
    private static final int STATUS_H = 15;

    private static final int CLAIM_X = 234;
    private static final int CLAIM_Y = 192;
    private static final int CLAIM_W = 54;
    private static final int CLAIM_H = 16;

    private List<KitDefinition> kits;
    private int selectedIndex;
    private int itemPage = 0;
    private int leftPos;
    private int topPos;
    private boolean claimPending = false;
    private long claimSentAtMs = 0L;

    public KitPreviewScreen(List<KitDefinition> kits, int selectedIndex) {
        super(Component.literal("Kit Preview"));
        this.kits = kits != null ? new ArrayList<>(kits) : new ArrayList<>();
        this.selectedIndex = this.kits.isEmpty()
                ? 0
                : Math.max(0, Math.min(selectedIndex, this.kits.size() - 1));
    }

    public void updateKits(List<KitDefinition> updatedKits) {
        String currentId = (!kits.isEmpty() && selectedIndex >= 0 && selectedIndex < kits.size())
                ? kits.get(selectedIndex).getId()
                : null;
        this.kits = updatedKits != null ? new ArrayList<>(updatedKits) : new ArrayList<>();
        this.claimPending = false;

        if (this.kits.isEmpty()) {
            Minecraft.getInstance().setScreen(new SelectKitScreen(this.kits, 0));
            return;
        }

        int newSelected = 0;
        if (currentId != null) {
            for (int i = 0; i < this.kits.size(); i++) {
                if (this.kits.get(i).getId().equalsIgnoreCase(currentId)) {
                    newSelected = i;
                    break;
                }
            }
        }
        this.selectedIndex = Math.max(0, Math.min(newSelected, this.kits.size() - 1));
        int totalItemPages = getTotalItemPages(this.kits.get(this.selectedIndex));
        if (this.itemPage >= totalItemPages) {
            this.itemPage = Math.max(0, totalItemPages - 1);
        }
    }

    private int getTotalItemPages(KitDefinition kit) {
        return Math.max(1, (int) Math.ceil((double) kit.getItems().size() / ITEMS_PER_PAGE));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        if (kits.isEmpty()) {
            Minecraft.getInstance().setScreen(new SelectKitScreen(kits, 0));
            return;
        }

        if (claimPending && (System.currentTimeMillis() - claimSentAtMs > 3000L)) {
            claimPending = false;
        }

        this.renderBackground(gfx);
        KitDefinition kit = kits.get(selectedIndex);

        // Background panel
        gfx.blit(BG_TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, 512, 256);

        // Subtitle on parchment (flat, without drop shadow)
        String subtitleText = "Previewing: " + kit.getDisplayName();
        drawFittedCenteredFlat(gfx, subtitleText, leftPos + 160.0f, topPos + 33.0f, 230.0f, 0x3B2415);

        // Panel headers
        gfx.drawCenteredString(this.font, Component.literal("KIT CONTENTS"),
                leftPos + 116, topPos + 53, 0xF4EFE6);

        gfx.pose().pushPose();
        gfx.pose().translate(leftPos + 245.0f, topPos + 51.0f, 0.0f);
        gfx.pose().scale(0.8f, 0.8f, 1.0f);
        gfx.drawCenteredString(this.font, Component.literal("REQUIREMENTS"), 0, 0, 0xF4EFE6);
        gfx.drawCenteredString(this.font, Component.literal("TO UNLOCK"), 0, 10, 0xF4EFE6);
        gfx.pose().popPose();

        gfx.pose().pushPose();
        gfx.pose().translate(leftPos + 245.0f, topPos + 121.0f, 0.0f);
        gfx.pose().scale(0.8f, 0.8f, 1.0f);
        gfx.drawCenteredString(this.font, Component.literal("KIT COOLDOWN"), 0, 0, 0xF4EFE6);
        gfx.pose().popPose();

        // Render Requirements (LuckPerms permission + optional crate key rewards)
        int reqY = topPos + 70;
        if (kit.isUnlocked()) {
            drawFittedLeft(gfx, "\u2713 Kit Unlocked", leftPos + 210.0f, reqY, 70.0f, 0.76f, 0x83F29F);
            reqY += 9;
            if (!kit.getPermission().isBlank()) {
                drawFittedLeft(gfx, "\u2713 " + kit.getPermission(), leftPos + 210.0f, reqY, 70.0f, 0.72f, 0xB8C2CE);
                reqY += 9;
            }
        } else {
            drawFittedLeft(gfx, "\u2717 No Permission", leftPos + 210.0f, reqY, 70.0f, 0.76f, 0xFF9C94);
            reqY += 9;
            if (!kit.getPermission().isBlank()) {
                drawFittedLeft(gfx, "\u2717 " + kit.getPermission(), leftPos + 210.0f, reqY, 70.0f, 0.72f, 0xFF9C94);
                reqY += 9;
            }
        }

        if (!kit.getCrateKeys().isEmpty()) {
            String keyLabel = "\u2605 Keys: " + String.join(", ", kit.getCrateKeys());
            drawFittedLeft(gfx, keyLabel, leftPos + 210.0f, reqY, 70.0f, 0.72f, 0xFFD470);
        }

        // Render Cooldown below pixel clock (clock sits at 239, 136..148)
        long rem = kit.getRemainingCooldownSeconds();
        gfx.pose().pushPose();
        gfx.pose().translate(leftPos + 245.0f, topPos + 152.0f, 0.0f);
        gfx.pose().scale(0.72f, 0.72f, 1.0f);
        gfx.drawCenteredString(this.font, Component.literal("NEXT USE IN:"), 0, 0, 0xD6DCE6);
        gfx.drawCenteredString(this.font,
                Component.literal(rem > 0 ? KitDefinition.formatDuration(rem) : "READY NOW"),
                0, 11, rem > 0 ? 0xFFD470 : 0x78F59B);
        gfx.pose().popPose();

        // Render 18 Item Slots (6x3) for current item page & track hovered stack
        ItemStack hoveredStack = ItemStack.EMPTY;
        List<ItemStack> items = kit.getItems();
        int totalItemPages = getTotalItemPages(kit);
        if (itemPage >= totalItemPages) {
            itemPage = Math.max(0, totalItemPages - 1);
        }
        int itemStart = itemPage * ITEMS_PER_PAGE;

        for (int idx = 0; idx < ITEMS_PER_PAGE; idx++) {
            int itemIndex = itemStart + idx;
            int col = idx % 6;
            int row = idx / 6;
            int sx = leftPos + SLOTS_START_X + col * SLOT_STRIDE_X;
            int sy = topPos + SLOTS_START_Y + row * SLOT_STRIDE_Y;

            if (itemIndex < items.size()) {
                ItemStack stack = items.get(itemIndex);
                gfx.renderItem(stack, sx + 3, sy + 3);
                gfx.renderItemDecorations(this.font, stack, sx + 3, sy + 3);

                if (isInBounds(mouseX, mouseY, sx, sy, SLOT_BOX_SIZE, SLOT_BOX_SIZE)) {
                    gfx.fill(sx + 1, sy + 1, sx + SLOT_BOX_SIZE - 1, sy + SLOT_BOX_SIZE - 1, 0x40FFFFFF);
                    hoveredStack = stack;
                }
            }
        }

        // Item page controls inside bottom margin of left box if > 18 items
        if (totalItemPages > 1) {
            boolean canPrev = itemPage > 0;
            boolean prevHov = canPrev && isInBounds(mouseX, mouseY, leftPos + ITEM_PREV_X, topPos + ITEM_NAV_Y, ITEM_NAV_W, ITEM_NAV_H);
            gfx.drawCenteredString(this.font, Component.literal("<"),
                    leftPos + ITEM_PREV_X + ITEM_NAV_W / 2, topPos + ITEM_NAV_Y + 3,
                    canPrev ? (prevHov ? 0xFFE19E : 0xF4EFE6) : 0x6F7885);

            gfx.pose().pushPose();
            gfx.pose().translate(leftPos + 116.0f, topPos + 156.0f, 0.0f);
            gfx.pose().scale(0.8f, 0.8f, 1.0f);
            gfx.drawCenteredString(this.font, Component.literal((itemPage + 1) + "/" + totalItemPages), 0, 0, 0xD6DCE6);
            gfx.pose().popPose();

            boolean canNext = itemPage < totalItemPages - 1;
            boolean nextHov = canNext && isInBounds(mouseX, mouseY, leftPos + ITEM_NEXT_X, topPos + ITEM_NAV_Y, ITEM_NAV_W, ITEM_NAV_H);
            gfx.drawCenteredString(this.font, Component.literal(">"),
                    leftPos + ITEM_NEXT_X + ITEM_NAV_W / 2, topPos + ITEM_NAV_Y + 3,
                    canNext ? (nextHov ? 0xFFE19E : 0xF4EFE6) : 0x6F7885);
        }

        // Close Button (X)
        boolean closeHovered = isInBounds(mouseX, mouseY, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE);
        gfx.blit(WIDGETS_TEXTURE, leftPos + CLOSE_X, topPos + CLOSE_Y,
                closeHovered ? 24 : 0, 64, CLOSE_SIZE, CLOSE_SIZE, 256, 256);

        // Bottom Left: < BACK
        boolean backHovered = isInBounds(mouseX, mouseY, leftPos + BACK_X, topPos + BACK_Y, BACK_W, BACK_H);
        gfx.drawString(this.font, Component.literal("< BACK"),
                leftPos + BACK_X + 6, topPos + BACK_Y + 4, backHovered ? 0xFFE19E : 0xD6A470, true);

        // Bottom Center: UNLOCKED / LOCKED status badge (LuckPerms permission-based)
        gfx.blit(WIDGETS_TEXTURE, leftPos + STATUS_X, topPos + STATUS_Y, 92, 92, STATUS_W, STATUS_H, 256, 256);
        gfx.drawCenteredString(this.font,
                Component.literal(kit.isUnlocked() ? "UNLOCKED" : "LOCKED"),
                leftPos + STATUS_X + STATUS_W / 2, topPos + STATUS_Y + 4,
                kit.isUnlocked() ? 0xA9F5BC : 0xFF9C94);

        // Bottom Right: CLAIM KIT
        boolean canClaim = kit.isUnlocked() && rem == 0 && !claimPending;
        boolean claimHovered = isInBounds(mouseX, mouseY, leftPos + CLAIM_X, topPos + CLAIM_Y, CLAIM_W, CLAIM_H);
        int claimColor = canClaim ? (claimHovered ? 0xFFF3B0 : 0xFFCA4B) : 0x8B7B66;
        gfx.drawString(this.font, Component.literal("CLAIM KIT"),
                leftPos + CLAIM_X + 2, topPos + CLAIM_Y + 4, claimColor, true);

        super.render(gfx, mouseX, mouseY, partialTick);

        if (!hoveredStack.isEmpty()) {
            gfx.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        } else if (isInBounds(mouseX, mouseY, leftPos + STATUS_X, topPos + STATUS_Y, STATUS_W, STATUS_H) && !kit.isUnlocked()) {
            gfx.renderTooltip(this.font, Component.literal("Requires permission: " + kit.getPermission()), mouseX, mouseY);
        } else if (claimHovered && !canClaim && !claimPending) {
            String reason = !kit.isUnlocked()
                    ? "Requires permission: " + kit.getPermission()
                    : "Available in " + KitDefinition.formatDuration(rem);
            gfx.renderTooltip(this.font, Component.literal(reason), mouseX, mouseY);
        } else if (isInBounds(mouseX, mouseY, leftPos + REQ_BOX_X, topPos + REQ_BOX_Y, REQ_BOX_W, REQ_BOX_H)) {
            List<Component> reqTips = new ArrayList<>();
            reqTips.add(Component.literal((kit.isUnlocked() ? "§a\u2713 " : "§c\u2717 ") + "Permission: §f" + kit.getPermission()));
            if (!kit.getCrateKeys().isEmpty()) {
                reqTips.add(Component.literal("§6\u2605 Crate keys: §e" + String.join(", ", kit.getCrateKeys())));
            }
            if (kit.getCooldownHours() > 0) {
                reqTips.add(Component.literal("§7Cooldown: §e" + kit.getCooldownHours() + "h"));
            }
            gfx.renderComponentTooltip(this.font, reqTips, mouseX, mouseY);
        }
    }

    private void drawFittedLeft(GuiGraphics gfx, String text, float x, float y, float maxWidth, float defaultScale, int color) {
        int rawW = Math.max(1, this.font.width(text));
        float scale = Math.min(defaultScale, maxWidth / rawW);
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0.0f);
        gfx.pose().scale(scale, scale, 1.0f);
        gfx.drawString(this.font, Component.literal(text), 0, 0, color, false);
        gfx.pose().popPose();
    }

    private void drawFittedCenteredFlat(GuiGraphics gfx, String text, float centerX, float y, float maxWidth, int color) {
        int rawW = Math.max(1, this.font.width(text));
        if (rawW <= maxWidth) {
            gfx.drawString(this.font, Component.literal(text), (int) (centerX - rawW / 2.0f), (int) y, color, false);
            return;
        }
        float scale = Math.max(0.6f, maxWidth / rawW);
        gfx.pose().pushPose();
        gfx.pose().translate(centerX, y, 0.0f);
        gfx.pose().scale(scale, scale, 1.0f);
        gfx.drawString(this.font, Component.literal(text), -rawW / 2, 0, color, false);
        gfx.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || kits.isEmpty()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        KitDefinition kit = kits.get(selectedIndex);

        if (isInBounds(mouseX, mouseY, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            playClick();
            this.onClose();
            return true;
        }

        if (isInBounds(mouseX, mouseY, leftPos + BACK_X, topPos + BACK_Y, BACK_W, BACK_H)) {
            playClick();
            Minecraft.getInstance().setScreen(new SelectKitScreen(kits, selectedIndex));
            return true;
        }

        int totalItemPages = getTotalItemPages(kit);
        if (totalItemPages > 1) {
            if (itemPage > 0 && isInBounds(mouseX, mouseY, leftPos + ITEM_PREV_X, topPos + ITEM_NAV_Y, ITEM_NAV_W, ITEM_NAV_H)) {
                playClick();
                itemPage--;
                return true;
            }
            if (itemPage < totalItemPages - 1 && isInBounds(mouseX, mouseY, leftPos + ITEM_NEXT_X, topPos + ITEM_NAV_Y, ITEM_NAV_W, ITEM_NAV_H)) {
                playClick();
                itemPage++;
                return true;
            }
        }

        if (isInBounds(mouseX, mouseY, leftPos + CLAIM_X, topPos + CLAIM_Y, CLAIM_W, CLAIM_H)) {
            if (kit.isUnlocked() && kit.getRemainingCooldownSeconds() == 0 && !claimPending) {
                playClick();
                claimPending = true;
                claimSentAtMs = System.currentTimeMillis();
                EvoKitsPacketHandler.INSTANCE.sendToServer(new EvoKitsPacketHandler.C2S_ClaimKitPacket(kit.getId()));
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean isInBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
