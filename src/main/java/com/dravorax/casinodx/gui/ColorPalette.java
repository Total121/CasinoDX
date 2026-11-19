package com.dravorax.casinodx.gui;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

public final class ColorPalette {

    private static ColorPalette instance;

    private String primary = "#9B59B6";
    private String secondary = "#1A1A2E";
    private String accent = "#F1C40F";
    private String success = "#2ECC71";
    private String danger = "#E74C3C";
    private String warning = "#F39C12";
    private String neutral = "#BDC3C7";

    private Material borderPrimary = Material.BLACK_STAINED_GLASS_PANE;
    private Material borderSecondary = Material.PURPLE_STAINED_GLASS_PANE;
    private Material borderAccent = Material.YELLOW_STAINED_GLASS_PANE;

    private ColorPalette() {}

    public static ColorPalette get() {
        if (instance == null) {
            instance = new ColorPalette();
            CasinoDX plugin = CasinoDX.get();
            if (plugin != null) {
                instance.reload(plugin.configs());
            }
        }
        return instance;
    }

    public static void reload(ConfigManager configs) {
        ColorPalette palette = get();
        palette.reloadInternal(configs);
    }

    private void reloadInternal(ConfigManager configs) {
        FileConfiguration cfg = configs.colors();
        primary = cfg.getString("primary", primary);
        secondary = cfg.getString("secondary", secondary);
        accent = cfg.getString("accent", accent);
        success = cfg.getString("success", success);
        danger = cfg.getString("danger", danger);
        warning = cfg.getString("warning", warning);
        neutral = cfg.getString("neutral", neutral);

        borderPrimary = parseMaterial(cfg.getString("border-primary"), borderPrimary);
        borderSecondary = parseMaterial(cfg.getString("border-secondary"), borderSecondary);
        borderAccent = parseMaterial(cfg.getString("border-accent"), borderAccent);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) return fallback;
        Material mat = Material.matchMaterial(name);
        return mat != null ? mat : fallback;
    }

    public String primary() { return primary; }
    public String secondary() { return secondary; }
    public String accent() { return accent; }
    public String success() { return success; }
    public String danger() { return danger; }
    public String warning() { return warning; }
    public String neutral() { return neutral; }

    public Material borderPrimary() { return borderPrimary; }
    public Material borderSecondary() { return borderSecondary; }
    public Material borderAccent() { return borderAccent; }
}
