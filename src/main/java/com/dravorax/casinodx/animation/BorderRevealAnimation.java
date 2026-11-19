package com.dravorax.casinodx.animation;

import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BorderRevealAnimation extends AbstractAnimation {

    private final GuiView view;
    private final List<Integer> borderSlots;
    private final Set<Integer> reserved;
    private int index;

    public BorderRevealAnimation(Player player, GuiView view, int inventorySize) {
        this(player, view, inventorySize, new int[0]);
    }

    /**
     * @param reservedSlots border slots holding real content (panels, buttons) that the sweep must
     *                      leave alone; clickable slots are detected automatically
     */
    public BorderRevealAnimation(Player player, GuiView view, int inventorySize, int... reservedSlots) {
        super(player.getUniqueId(), Math.max(20, countBorderSlots(inventorySize) + 5));
        this.view = view;
        this.borderSlots = borderSlots(inventorySize);
        this.reserved = new HashSet<>();
        for (int slot : reservedSlots) {
            this.reserved.add(slot);
        }
    }

    private boolean isProtected(int slot) {
        return reserved.contains(slot) || view.hasAction(slot);
    }

    @Override
    public void tick(int elapsedTicks) {
        if (isFinished() || isCancelled()) return;
        Inventory inv = view.inventory();
        if (inv == null) return;

        ColorPalette palette = ColorPalette.get();
        Material[] mats = {
                palette.borderPrimary(),
                palette.borderSecondary(),
                palette.borderAccent(),
                palette.borderSecondary()
        };

        int steps = Math.min(2, borderSlots.size() - index);
        for (int s = 0; s < steps && index < borderSlots.size(); s++) {
            int slot = borderSlots.get(index++);
            // The border runs through the bottom row, which is where the nav buttons live.
            if (isProtected(slot)) {
                continue;
            }
            Material mat = mats[index % mats.length];
            ItemStack pane = GuiComponents.decorativePane(mat);
            inv.setItem(slot, pane);
        }

        if (index >= borderSlots.size()) {
            accentCorners(inv, palette);
            markFinished();
        }
    }

    private void accentCorners(Inventory inv, ColorPalette palette) {
        ItemStack accent = GuiComponents.decorativePane(palette.borderAccent());
        ItemStack secondary = GuiComponents.decorativePane(palette.borderSecondary());
        int[] accentSlots = {0, 8, 45, 53};
        int[] secondarySlots = {1, 7, 9, 17, 36, 44, 46, 52};
        for (int slot : accentSlots) {
            if (!isProtected(slot)) inv.setItem(slot, accent);
        }
        for (int slot : secondarySlots) {
            if (!isProtected(slot)) inv.setItem(slot, secondary);
        }
    }

    private static int countBorderSlots(int size) {
        return borderSlots(size).size();
    }

    private static List<Integer> borderSlots(int size) {
        List<Integer> slots = new ArrayList<>();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                slots.add(slot);
            }
        }
        return slots;
    }
}
