package com.dravorax.casinodx.gui;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public abstract class GuiView {

    protected final CasinoDX plugin;
    protected final Player player;
    protected final GuiHolder holder;
    protected Inventory inventory;

    protected GuiView(CasinoDX plugin, Player player, String holderId) {
        this.plugin = plugin;
        this.player = player;
        this.holder = new GuiHolder(holderId, player.getUniqueId(), new HashMap<>());
    }

    public abstract String title();

    public abstract int size();

    public abstract void build(Inventory inv);

    public abstract void onClick(InventoryClickEvent event);

    public void onClose(InventoryCloseEvent event) {}

    public GuiHolder holder() {
        return holder;
    }

    public Inventory inventory() {
        return inventory;
    }

    public void open() {
        plugin.guis().open(this);
    }

    public void refresh() {
        if (inventory == null) return;
        inventory.clear();
        clearActions();
        build(inventory);
        player.updateInventory();
    }

    /** Drops every slot→action binding so a rebuild cannot leave stale clickable slots behind. */
    protected void clearActions() {
        for (int slot = 0; slot < size(); slot++) {
            holder.putMeta("slot-" + slot, null);
        }
    }

    public void playClickSound() {
        plugin.sounds().play(player, "click");
    }

    protected void fillBorder(Inventory inv, Material material) {
        int size = inv.getSize();
        int rows = size / 9;
        ItemStack pane = ItemBuilder.of(material).name(" ").hideAllFlags().build();
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(slot, pane);
            }
        }
    }

    /** Fill non-border slots with blank panes so the menu never shows empty air. */
    protected void fillBlankInterior(Inventory inv) {
        int size = inv.getSize();
        int rows = size / 9;
        ItemStack blank = GuiComponents.blankPane();
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (!(row == 0 || row == rows - 1 || col == 0 || col == 8)) {
                inv.setItem(slot, blank);
            }
        }
    }

    protected void fill(Inventory inv, Material material) {
        ItemStack pane = ItemBuilder.of(material).name(" ").hideAllFlags().build();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    protected void set(Inventory inv, int slot, ItemStack item) {
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, item);
        }
    }

    protected void setAction(int slot, String action) {
        holder.putMeta("slot-" + slot, action);
    }

    protected String action(int slot) {
        Object value = holder.meta("slot-" + slot);
        return value == null ? null : value.toString();
    }

    /** Whether a slot currently carries a click binding, so decoration can leave it alone. */
    public boolean hasAction(int slot) {
        return action(slot) != null;
    }

    protected void bindActions(Map<Integer, String> actions) {
        actions.forEach(this::setAction);
    }

    void bindInventory(Inventory inventory) {
        this.inventory = inventory;
        holder.bindInventory(inventory);
    }

    protected String guiTitle() {
        return title();
    }

    protected Inventory createInventory() {
        return Bukkit.createInventory(holder, size(), Text.component(guiTitle()));
    }
}
