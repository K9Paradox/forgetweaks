package com.rlutility.gui;

import com.rlutility.modules.FeatureConfig;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Generic in-game editor for a comma-separated id list (XRay blocks, ESP entities, magnet filters).
 *
 * <p>Three ways to add an entry, because typing {@code iceandfire:dragon_fire} by hand is nobody's
 * idea of fun: the "Add looked-at" button grabs whatever your crosshair is on, the search box
 * filters a live list of every registered id with click-to-toggle, and you can still type a raw
 * entry (including {@code modid:*} wildcards) and press Enter.</p>
 */
public class GuiTargetListEditor extends GuiScreen {

    private static final int PANEL_W = 440;
    private static final int PANEL_H = 264;
    private static final int MAX_VISIBLE = 8;
    private static final int ROW_H = 18;

    private final GuiScreen parent;
    private final String title;
    private final Supplier<String> getter;
    private final Consumer<String> setter;
    private final Supplier<List<String>> candidateSupplier;
    private final Supplier<String> lookedAtSupplier;

    private GuiTextField searchBox;
    private int scroll = 0;
    private boolean showAll = false;
    private final List<String> visible = new ArrayList<>();

    /**
     * @param candidateSupplier every id that could be added (the full registry)
     * @param lookedAtSupplier  id under the crosshair, or null
     */
    public GuiTargetListEditor(GuiScreen parent, String title,
                               Supplier<String> getter, Consumer<String> setter,
                               Supplier<List<String>> candidateSupplier,
                               Supplier<String> lookedAtSupplier) {
        this.parent = parent;
        this.title = title;
        this.getter = getter;
        this.setter = setter;
        this.candidateSupplier = candidateSupplier;
        this.lookedAtSupplier = lookedAtSupplier;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        searchBox = new GuiTextField(0, this.fontRenderer, x + 10, y + 28, PANEL_W - 130, 16);
        searchBox.setMaxStringLength(120);
        searchBox.setFocused(true);

        rebuild();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String raw() {
        String value = getter.get();
        return value == null ? "" : value;
    }

    private List<String> selected() {
        List<String> out = new ArrayList<>();
        for (String token : raw().split(",")) {
            String entry = token.trim();
            if (!entry.isEmpty()) out.add(entry);
        }
        return out;
    }

    private void rebuild() {
        visible.clear();
        String query = searchBox == null ? "" : searchBox.getText().toLowerCase().trim();

        if (showAll) {
            // Whole registry, filtered by the search box. Selected entries float to the top.
            List<String> chosen = selected();
            for (String id : chosen) {
                if (query.isEmpty() || id.toLowerCase().contains(query)) visible.add(id);
            }
            try {
                for (String id : candidateSupplier.get()) {
                    if (chosen.contains(id)) continue;
                    if (!query.isEmpty() && !id.toLowerCase().contains(query)) continue;
                    visible.add(id);
                    if (visible.size() > 600) break; // keep the list responsive
                }
            } catch (Throwable ignored) {}
        } else {
            for (String id : selected()) {
                if (query.isEmpty() || id.toLowerCase().contains(query)) visible.add(id);
            }
        }

        if (scroll > Math.max(0, visible.size() - MAX_VISIBLE)) {
            scroll = Math.max(0, visible.size() - MAX_VISIBLE);
        }
        rebuildButtons();
    }

    private void rebuildButtons() {
        this.buttonList.clear();
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        this.buttonList.add(new GuiButton(1, x + PANEL_W - 114, y + 27, 104, 18,
                showAll ? TextFormatting.GOLD + "Browsing all" : TextFormatting.GRAY + "My list only"));

        int listTop = y + 52;
        for (int i = 0; i < MAX_VISIBLE; i++) {
            int index = scroll + i;
            if (index >= visible.size()) break;
            String id = visible.get(index);
            boolean on = isSelected(id);
            this.buttonList.add(new GuiButton(100 + i, x + PANEL_W - 78, listTop + i * ROW_H, 68, 16,
                    on ? TextFormatting.RED + "Remove" : TextFormatting.GREEN + "Add"));
        }

        if (scroll > 0) {
            this.buttonList.add(new GuiButton(2, x + PANEL_W - 24, listTop, 14, 16, "^"));
        }
        if (scroll + MAX_VISIBLE < visible.size()) {
            this.buttonList.add(new GuiButton(3, x + PANEL_W - 24, listTop + (MAX_VISIBLE - 1) * ROW_H, 14, 16, "v"));
        }

        int bottomY = y + PANEL_H - 26;
        String looked = lookedAt();
        GuiButton addLooked = new GuiButton(4, x + 10, bottomY, 150, 20,
                looked == null ? TextFormatting.DARK_GRAY + "Look at a target" : TextFormatting.AQUA + "Add " + shortId(looked));
        addLooked.enabled = looked != null;
        this.buttonList.add(addLooked);

        this.buttonList.add(new GuiButton(5, x + 165, bottomY, 95, 20, TextFormatting.YELLOW + "Add typed"));
        this.buttonList.add(new GuiButton(6, x + 265, bottomY, 80, 20, TextFormatting.GRAY + "Clear all"));
        this.buttonList.add(new GuiButton(7, x + 350, bottomY, 80, 20, TextFormatting.WHITE + "Done"));
    }

    private String lookedAt() {
        try {
            return lookedAtSupplier == null ? null : lookedAtSupplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String shortId(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    private boolean isSelected(String id) {
        for (String entry : selected()) {
            if (entry.equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    private void toggle(String id) {
        setter.accept(com.rlutility.modules.TargetList.toggle(raw(), id));
        FeatureConfig.saveConfig();
        rebuild();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 100) {
            int index = scroll + (button.id - 100);
            if (index < visible.size()) toggle(visible.get(index));
            return;
        }
        switch (button.id) {
            case 1:
                showAll = !showAll;
                scroll = 0;
                rebuild();
                return;
            case 2:
                scroll = Math.max(0, scroll - 1);
                rebuildButtons();
                return;
            case 3:
                scroll = Math.min(Math.max(0, visible.size() - MAX_VISIBLE), scroll + 1);
                rebuildButtons();
                return;
            case 4: {
                String looked = lookedAt();
                if (looked != null) toggle(looked);
                return;
            }
            case 5: {
                String typed = searchBox.getText().trim();
                if (!typed.isEmpty()) {
                    toggle(typed);
                    searchBox.setText("");
                    rebuild();
                }
                return;
            }
            case 6:
                setter.accept("");
                FeatureConfig.saveConfig();
                rebuild();
                return;
            case 7:
                this.mc.displayGuiScreen(parent);
                return;
            default:
                break;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            String typed = searchBox.getText().trim();
            if (!typed.isEmpty() && !showAll) {
                toggle(typed);
                searchBox.setText("");
            }
            rebuild();
            return;
        }
        if (searchBox.textboxKeyTyped(typedChar, keyCode)) {
            scroll = 0;
            rebuild();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        searchBox.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (wheel == 0) return;
        scroll = wheel > 0
                ? Math.max(0, scroll - 1)
                : Math.min(Math.max(0, visible.size() - MAX_VISIBLE), scroll + 1);
        rebuildButtons();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        searchBox.updateCursorCounter();
        // The crosshair target changes as the player looks around, so the button label must follow.
        rebuildButtons();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - PANEL_H) / 2;

        drawDefaultBackground();
        drawRect(x, y, x + PANEL_W, y + PANEL_H, 0xD012161E);
        drawRect(x + 2, y + 2, x + PANEL_W - 2, y + 22, 0xEE1E2430);

        drawCenteredString(this.fontRenderer,
                TextFormatting.GOLD + "" + TextFormatting.BOLD + title
                        + TextFormatting.DARK_GRAY + "  |  " + TextFormatting.WHITE + selected().size() + " selected",
                this.width / 2, y + 7, 0xFFFFFF);

        searchBox.drawTextBox();
        if (searchBox.getText().isEmpty() && !searchBox.isFocused()) {
            this.fontRenderer.drawString(TextFormatting.DARK_GRAY + "search or type an id...", x + 14, y + 32, 0xFFFFFF);
        }

        int listTop = y + 52;
        if (visible.isEmpty()) {
            drawCenteredString(this.fontRenderer,
                    TextFormatting.GRAY + (showAll ? "Nothing matches that search." : "List is empty - add something below."),
                    this.width / 2, listTop + 24, 0xFFFFFF);
        }

        for (int i = 0; i < MAX_VISIBLE; i++) {
            int index = scroll + i;
            if (index >= visible.size()) break;
            String id = visible.get(index);
            int rowY = listTop + i * ROW_H;
            boolean on = isSelected(id);

            drawRect(x + 10, rowY, x + PANEL_W - 82, rowY + 16, on ? 0x2233FF77 : 0x14FFFFFF);
            String label = (on ? TextFormatting.GREEN + "\u2713 " : TextFormatting.DARK_GRAY + "  ")
                    + TextFormatting.WHITE + id;
            this.fontRenderer.drawStringWithShadow(
                    this.fontRenderer.trimStringToWidth(label, PANEL_W - 100), x + 14, rowY + 4, 0xFFFFFF);
        }

        this.fontRenderer.drawStringWithShadow(
                TextFormatting.DARK_GRAY + "Wildcards work: \"iceandfire:*\" matches everything from that mod.",
                x + 10, y + PANEL_H - 40, 0xFFFFFF);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
