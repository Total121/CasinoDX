package com.dravorax.casinodx.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;
    private ItemMeta meta;

    public ItemBuilder(Material material) {
        this(material, 1);
    }

    public ItemBuilder(Material material, int amount) {
        int safeAmount = material == null ? 1 : Math.max(1, Math.min(amount, material.getMaxStackSize()));
        this.item = new ItemStack(material == null ? Material.STONE : material, safeAmount);
        this.meta = item.getItemMeta();
    }

    /** Wraps an existing stack, preserving meta such as persistent data. */
    public ItemBuilder(ItemStack base) {
        if (base == null) {
            this.item = new ItemStack(Material.STONE);
        } else {
            this.item = base.clone();
        }
        this.meta = item.getItemMeta();
    }

    /** Convenience alias for {@link #ItemBuilder(Material)}. */
    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    /** Convenience alias for {@link #ItemBuilder(ItemStack)}. */
    public static ItemBuilder of(ItemStack base) {
        return new ItemBuilder(base);
    }

    public ItemBuilder name(String name) {
        ensureMeta();
        if (name == null) {
            meta.setDisplayName(null);
        } else {
            meta.setDisplayName(Text.color(name));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        ensureMeta();
        if (lore == null || lore.isEmpty()) {
            meta.setLore(null);
            return this;
        }
        List<String> colored = new ArrayList<>(lore.size());
        for (String line : lore) {
            if (line != null) {
                colored.add(Text.color(line));
            }
        }
        meta.setLore(colored);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(lines == null ? List.of() : Arrays.asList(lines));
    }

    public ItemBuilder amount(int amount) {
        int max = item.getType().getMaxStackSize();
        item.setAmount(Math.max(1, Math.min(amount, max <= 0 ? 64 : max)));
        return this;
    }

    public ItemBuilder hideAll() {
        ensureMeta();
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    /** Alias for {@link #hideAll()} used throughout the GUI layer. */
    public ItemBuilder hideAllFlags() {
        return hideAll();
    }

    public ItemBuilder glow() {
        return glow(true);
    }

    public ItemBuilder glow(boolean enabled) {
        if (!enabled) {
            return this;
        }
        ensureMeta();
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        ensureMeta();
        meta.setCustomModelData(data);
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    private void ensureMeta() {
        if (meta == null) {
            meta = item.getItemMeta();
        }
    }
}
