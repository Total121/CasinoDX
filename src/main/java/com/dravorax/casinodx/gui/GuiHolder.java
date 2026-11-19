package com.dravorax.casinodx.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class GuiHolder implements InventoryHolder {

    private final String id;
    private final UUID owner;
    private final Map<String, Object> metadata;
    private Inventory inventory;

    public GuiHolder(String id, UUID owner) {
        this(id, owner, new HashMap<>());
    }

    public GuiHolder(String id, UUID owner, Map<String, Object> metadata) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.metadata = new HashMap<>(metadata == null ? Map.of() : metadata);
    }

    public String id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public Map<String, Object> metadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public Object meta(String key) {
        return metadata.get(key);
    }

    public void putMeta(String key, Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    void bindInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("GuiHolder inventory not bound yet: " + id);
        }
        return inventory;
    }
}
