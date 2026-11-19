package com.dravorax.casinodx.gui;

import com.dravorax.casinodx.CasinoDX;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class GuiListener implements Listener {

    private final CasinoDX plugin;

    public GuiListener(CasinoDX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (!plugin.guis().isCasinoInventory(top)) return;

        event.setCancelled(true);
        event.setResult(org.bukkit.event.Event.Result.DENY);

        sanitizeClickState(event);

        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(top)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;

        GuiView view = plugin.guis().getView(player);
        if (view == null) return;

        view.onClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!plugin.guis().isCasinoInventory(top)) return;

        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                event.setResult(org.bukkit.event.Event.Result.DENY);
                sanitizeDragState(event);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (!plugin.guis().isCasinoInventory(top)) return;

        GuiView view = plugin.guis().getView(player);
        plugin.guis().unregister(player);
        if (view != null) {
            view.onClose(event);
        }
    }

    private void sanitizeClickState(InventoryClickEvent event) {
        InventoryView view = event.getView();
        Player player = (Player) event.getWhoClicked();

        if (event.getClick() == ClickType.SHIFT_LEFT
                || event.getClick() == ClickType.SHIFT_RIGHT
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.HOTBAR_SWAP
                || isHotbarMoveAndReadd(event.getAction())
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            player.setItemOnCursor(null);
            player.updateInventory();
            return;
        }

        if (event.getRawSlot() >= view.getTopInventory().getSize()) {
            player.setItemOnCursor(null);
        }
    }

    private static boolean isHotbarMoveAndReadd(InventoryAction action) {
        return action != null && "HOTBAR_MOVE_AND_READD".equals(action.toString());
    }

    private void sanitizeDragState(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            player.setItemOnCursor(null);
            player.updateInventory();
        }
    }
}
