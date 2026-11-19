package com.dravorax.casinodx.gui;

import com.dravorax.casinodx.CasinoDX;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiManager {

    private final CasinoDX plugin;
    private final Map<UUID, GuiView> openViews = new ConcurrentHashMap<>();

    public GuiManager(CasinoDX plugin) {
        this.plugin = plugin;
    }

    public void open(GuiView view) {
        if (view == null || view.player == null) return;

        Player player = view.player;
        close(player);

        Inventory inventory = view.createInventory();
        view.bindInventory(inventory);
        view.build(inventory);

        openViews.put(player.getUniqueId(), view);
        plugin.sounds().play(player, "menu-open");
        player.openInventory(inventory);
    }

    public GuiView getView(Player player) {
        if (player == null) return null;
        return openViews.get(player.getUniqueId());
    }

    public GuiView getView(UUID playerId) {
        if (playerId == null) return null;
        return openViews.get(playerId);
    }

    public boolean isCasinoInventory(Inventory inventory) {
        if (inventory == null) return false;
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof GuiHolder;
    }

    public void close(Player player) {
        if (player == null) return;
        openViews.remove(player.getUniqueId());
        Inventory top = player.getOpenInventory().getTopInventory();
        if (isCasinoInventory(top)) {
            player.closeInventory();
        }
    }

    public void unregister(Player player) {
        if (player != null) {
            openViews.remove(player.getUniqueId());
        }
    }

    public void unregister(UUID playerId) {
        if (playerId != null) {
            openViews.remove(playerId);
        }
    }
}
