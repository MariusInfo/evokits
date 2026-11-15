package org.evocraft.evokits.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.evocraft.evokits.EvoKits;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen 1: "SELECT YOUR KIT" (3x2 card grid with pagination when > 6 kits).
 * Coordinates match select_kit_bg.png (320x214 on 512x256) and widgets.png (256x256).
 */
public class SelectKitScreen extends Screen {

    private static final ResourceLocation BG_TEXTURE =
            new ResourceLocation(EvoKits.MODID, "textures/gui/select_kit_bg.png");
    private static final ResourceLocation WIDGETS_TEXTURE =
            new ResourceLocation(EvoKits.MODID, "textures/gui/widgets.png");

    private static final int GUI_WIDTH = 320;
    private static final int GUI_HEIGHT = 214;
    private static final int KITS_PER_PAGE = 6;

    private static final int CARD_W = 76;
    private static final int CARD_H = 60;
    private static final int GRID_START_X = 36;
    private static final int GRID_START_Y = 47;
    private static final int CARD_STRIDE_X = 86;
    private static final int CARD_STRIDE_Y = 69;

    private static final int CLOSE_X = 293;
    private static final int CLOSE_Y = 6;
    private static final int CLOSE_SIZE = 20;

    private static final int PREVIEW_BTN_X = 117;
    private static final int PREVIEW_BTN_Y = 193;
    private static final int PREVIEW_BTN_W = 86;
    private static final int PREVIEW_BTN_H = 15;

    private static final int PREV_BTN_X = 34;
    private static final int NEXT_BTN_X = 234;
    private static final int PAGE_BTN_Y = 192;
    private static final int PAGE_BTN_W = 54;
    private static final int PAGE_BTN_H = 16;

    private List<KitDefinition> kits;
    private int selectedIndex;
    private int currentPage;
    private int leftPos;
    private int topPos;
    private long lastClickTimeMs;
    private int lastClickedIndex = -1;

    public SelectKitScreen(List<KitDefinition> kits, int selectedIndex) {
        super(Component.literal("Select Your Kit"));
        this.kits = kits != null ? new ArrayList<>(kits) : new ArrayList<>();
        this.selectedIndex = this.kits.isEmpty()
                ? 0
                : Math.max(0, Math.min(selectedIndex, this.kits.size() - 1));
        this.currentPage = this.selectedIndex / KITS_PER_PAGE;
    }

    public void updateKits(List<KitDefinition> updatedKits) {
        String selectedId = (!kits.isEmpty() && selectedIndex >= 0 && selectedIndex < kits.size())
                ? kits.get(selectedIndex).getId()
                : null;
        this.kits = updatedKits != null ? new ArrayList<>(updatedKits) : new ArrayList<>();

        int newSelected = 0;
        if (selectedId != null) {
            for (int i = 0; i < this.kits.size(); i++) {
                if (this.kits.get(i).getId().equalsIgnoreCase(selectedId)) {
                    newSelected = i;
                    break;
                }
            }
        }
        this.selectedIndex = this.kits.isEmpty()
                ? 0
                : Math.max(0, Math.min(newSelected, this.kits.size() - 1));
        int totalPages = getTotalPages();
        if (this.currentPage >= totalPages) {
            this.currentPage = Math.max(0, totalPages - 1);
        }
    }

    private int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) kits.size() / KITS_PER_PAGE));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);

        // Background panel (320x214 inside 512x256 texture)
        gfx.blit(BG_TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, 512, 256);

        // Subtitle on parchment (flat, without drop shadow)
        Component subtitle = Component.literal("Choose your starting equipment");
        gfx.drawString(this.font, subtitle,
                leftPos + 160 - this.font.width(subtitle) / 2, topPos + 33, 0x3B2415, false);

        if (kits.isEmpty()) {
            Component emptyMsg = Component.literal("No kits are currently configured.");
            gfx.drawString(this.font, emptyMsg,
                    leftPos + 160 - this.font.width(emptyMsg) / 2, topPos + 108, 0x3B2415, false);
        } else {
            int startIndex = currentPage * KITS_PER_PAGE;
            int endIndex = Math.min(startIndex + KITS_PER_PAGE, kits.size());

            for (int i = startIndex; i < endIndex; i++) {
                int localIdx = i - startIndex;
                int col = localIdx % 3;
                int row = localIdx / 3;
                int cx = leftPos + GRID_START_X + col * CARD_STRIDE_X;
                int cy = topPos + GRID_START_Y + row * CARD_STRIDE_Y;

                boolean isSelected = (i == selectedIndex);
                int u = isSelected ? 80 : 0;
                gfx.blit(WIDGETS_TEXTURE, cx, cy, u, 0, CARD_W, CARD_H, 256, 256);

                KitDefinition kit = kits.get(i);

                // Render kit icon scaled slightly for card readability
                gfx.pose().pushPose();
                gfx.pose().translate(cx + 26.0f, cy + 12.0f, 0.0f);
                gfx.pose().scale(1.5f, 1.5f, 1.0f);
                gfx.renderItem(kit.getIcon(), 0, 0);
                gfx.pose().popPose();

                // Kit title fitted safely inside 76px card
                int titleColor = isSelected ? 0xFFE19E : 0xF4EBD8;
                drawCenteredFitted(gfx, kit.getDisplayName(), cx + CARD_W / 2.0f, cy + 40.0f, 68.0f, titleColor);

                // Status badge
                long rem = kit.getRemainingCooldownSeconds();
                String statusText = !kit.isUnlocked()
                        ? "LOCKED"
                        : (rem > 0 ? KitDefinition.formatDuration(rem) : "UNLOCKED");
                int statusColor = !kit.isUnlocked()
                        ? 0xFF9C94
                        : (rem > 0 ? 0xFFD470 : 0x83F29F);

                gfx.pose().pushPose();
                gfx.pose().translate(cx + CARD_W / 2.0f, cy + 50.0f, 0.0f);
                gfx.pose().scale(0.75f, 0.75f, 1.0f);
                gfx.drawCenteredString(this.font, Component.literal(statusText), 0, 0, statusColor);
                gfx.pose().popPose();
            }
        }

        // Close Button (X)
        boolean closeHovered = isInBounds(mouseX, mouseY, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE);
        gfx.blit(WIDGETS_TEXTURE, leftPos + CLOSE_X, topPos + CLOSE_Y,
                closeHovered ? 24 : 0, 64, CLOSE_SIZE, CLOSE_SIZE, 256, 256);

        // Optional Page Buttons & indicator when > 6 kits
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            String pageText = "Page " + (currentPage + 1) + " / " + totalPages;
            gfx.pose().pushPose();
            gfx.pose().translate(leftPos + 160.0f, topPos + 179.0f, 0.0f);
            gfx.pose().scale(0.75f, 0.75f, 1.0f);
            int pw = this.font.width(pageText);
            gfx.drawString(this.font, Component.literal(pageText), -pw / 2, 0, 0x3B2415, false);
            gfx.pose().popPose();

            boolean canPrev = currentPage > 0;
            boolean prevHovered = canPrev && isInBounds(mouseX, mouseY, leftPos + PREV_BTN_X, topPos + PAGE_BTN_Y, PAGE_BTN_W, PAGE_BTN_H);
            int prevColor = canPrev ? (prevHovered ? 0xFFE19E : 0xD6A470) : 0x8B7B66;
            gfx.drawString(this.font, Component.literal("< PREV"),
                    leftPos + PREV_BTN_X + 6, topPos + PAGE_BTN_Y + 4, prevColor, true);

            boolean canNext = currentPage < totalPages - 1;
            boolean nextHovered = canNext && isInBounds(mouseX, mouseY, leftPos + NEXT_BTN_X, topPos + PAGE_BTN_Y, PAGE_BTN_W, PAGE_BTN_H);
            int nextColor = canNext ? (nextHovered ? 0xFFE19E : 0xD6A470) : 0x8B7B66;
            gfx.drawString(this.font, Component.literal("NEXT >"),
                    leftPos + NEXT_BTN_X + 10, topPos + PAGE_BTN_Y + 4, nextColor, true);
        }

        // Bottom Center Preview Button
        boolean hasKits = !kits.isEmpty();
        boolean previewHovered = hasKits && isInBounds(mouseX, mouseY,
                leftPos + PREVIEW_BTN_X, topPos + PREVIEW_BTN_Y, PREVIEW_BTN_W, PREVIEW_BTN_H);
        gfx.blit(WIDGETS_TEXTURE, leftPos + PREVIEW_BTN_X, topPos + PREVIEW_BTN_Y,
                92, previewHovered ? 110 : 92, PREVIEW_BTN_W, PREVIEW_BTN_H, 256, 256);
        gfx.drawCenteredString(this.font, Component.literal("PREVIEW KIT"),
                leftPos + PREVIEW_BTN_X + PREVIEW_BTN_W / 2, topPos + PREVIEW_BTN_Y + 4,
                hasKits ? 0xFFF6DE : 0x8B7B66);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawCenteredFitted(GuiGraphics gfx, String text, float centerX, float y, float maxWidth, int color) {
        int rawWidth = this.font.width(text);
        if (rawWidth <= maxWidth) {
            gfx.drawCenteredString(this.font, Component.literal(text), (int) centerX, (int) y, color);
            return;
        }
        float scale = Math.max(0.55f, maxWidth / rawWidth);
        gfx.pose().pushPose();
        gfx.pose().translate(centerX, y, 0.0f);
        gfx.pose().scale(scale, scale, 1.0f);
        gfx.drawCenteredString(this.font, Component.literal(text), 0, 0, color);
        gfx.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Close X
        if (isInBounds(mouseX, mouseY, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            playClick();
            this.onClose();
            return true;
        }

        // Pagination buttons when > 6 kits
        int totalPages = getTotalPages();
        if (totalPages > 1) {
            if (currentPage > 0 && isInBounds(mouseX, mouseY, leftPos + PREV_BTN_X, topPos + PAGE_BTN_Y, PAGE_BTN_W, PAGE_BTN_H)) {
                playClick();
                currentPage--;
                selectedIndex = currentPage * KITS_PER_PAGE;
                lastClickedIndex = -1;
                return true;
            }
            if (currentPage < totalPages - 1 && isInBounds(mouseX, mouseY, leftPos + NEXT_BTN_X, topPos + PAGE_BTN_Y, PAGE_BTN_W, PAGE_BTN_H)) {
                playClick();
                currentPage++;
                selectedIndex = currentPage * KITS_PER_PAGE;
                lastClickedIndex = -1;
                return true;
            }
        }

        if (kits.isEmpty()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Preview button
        if (isInBounds(mouseX, mouseY, leftPos + PREVIEW_BTN_X, topPos + PREVIEW_BTN_Y, PREVIEW_BTN_W, PREVIEW_BTN_H)) {
            playClick();
            openPreviewScreen();
            return true;
        }

        // Kit Cards on current page
        int startIndex = currentPage * KITS_PER_PAGE;
        int endIndex = Math.min(startIndex + KITS_PER_PAGE, kits.size());
        for (int i = startIndex; i < endIndex; i++) {
            int localIdx = i - startIndex;
            int col = localIdx % 3;
            int row = localIdx / 3;
            int cx = leftPos + GRID_START_X + col * CARD_STRIDE_X;
            int cy = topPos + GRID_START_Y + row * CARD_STRIDE_Y;

            if (isInBounds(mouseX, mouseY, cx, cy, CARD_W, CARD_H)) {
                playClick();
                long now = System.currentTimeMillis();
                boolean isDoubleClick = (i == lastClickedIndex) && (now - lastClickTimeMs < 350L);
                this.selectedIndex = i;
                this.lastClickedIndex = i;
                this.lastClickTimeMs = now;

                if (isDoubleClick) {
                    openPreviewScreen();
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void openPreviewScreen() {
        if (!kits.isEmpty() && selectedIndex >= 0 && selectedIndex < kits.size()) {
            Minecraft.getInstance().setScreen(new KitPreviewScreen(kits, selectedIndex));
        }
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
