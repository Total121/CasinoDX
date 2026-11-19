package com.dravorax.casinodx.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Static validation helpers for CasinoDX YAML configuration.
 */
public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static Optional<String> validatePositiveMoney(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        double value = config.getDouble(path);
        if (value < 0) {
            return Optional.of("must be zero or positive (got " + value + ")");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Optional.of("must be a finite number");
        }
        return Optional.empty();
    }

    public static Optional<String> validateMoneyRange(YamlConfiguration config, String minPath, String maxPath) {
        if (!config.contains(minPath) && !config.contains(maxPath)) {
            return Optional.empty();
        }
        double min = config.getDouble(minPath, 0);
        double max = config.getDouble(maxPath, Double.MAX_VALUE);
        if (min < 0) {
            return Optional.of(minPath + " must be zero or positive (got " + min + ")");
        }
        if (max < 0) {
            return Optional.of(maxPath + " must be zero or positive (got " + max + ")");
        }
        if (min > max) {
            return Optional.of("minimum (" + min + ") cannot exceed maximum (" + max + ")");
        }
        return Optional.empty();
    }

    public static Optional<String> validatePercentage(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        double value = config.getDouble(path);
        if (value < 0 || value > 100) {
            return Optional.of("must be between 0 and 100 (got " + value + ")");
        }
        return Optional.empty();
    }

    public static Optional<String> validatePercentageRange(YamlConfiguration config, String minPath, String maxPath) {
        Optional<String> minErr = validatePercentage(config, minPath);
        if (minErr.isPresent()) {
            return minErr;
        }
        Optional<String> maxErr = validatePercentage(config, maxPath);
        if (maxErr.isPresent()) {
            return maxErr;
        }
        if (config.contains(minPath) && config.contains(maxPath)) {
            double min = config.getDouble(minPath);
            double max = config.getDouble(maxPath);
            if (min > max) {
                return Optional.of("minimum percentage (" + min + ") cannot exceed maximum (" + max + ")");
            }
        }
        return Optional.empty();
    }

    public static Optional<String> validateSlot(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        int slot = config.getInt(path, -1);
        if (slot < 0 || slot > 53) {
            return Optional.of("inventory slot must be between 0 and 53 (got " + slot + ")");
        }
        return Optional.empty();
    }

    public static Optional<String> validateSlotList(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        List<Integer> slots = config.getIntegerList(path);
        for (int slot : slots) {
            if (slot < 0 || slot > 53) {
                return Optional.of("inventory slot must be between 0 and 53 (got " + slot + " in " + path + ")");
            }
        }
        return Optional.empty();
    }

    public static Optional<String> validateMaterial(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return Optional.of("material cannot be empty");
        }
        Material material = Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            return Optional.of("unknown or non-item material '" + materialName + "'");
        }
        return Optional.empty();
    }

    public static Optional<String> validateMaterialPath(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        return validateMaterial(config.getString(path));
    }

    public static Optional<String> validateSound(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return Optional.of("sound cannot be empty");
        }
        String normalized = soundName.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        try {
            Sound.valueOf(normalized);
            return Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.of("unknown sound '" + soundName + "'");
        }
    }

    public static Optional<String> validateSoundPath(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        return validateSound(config.getString(path));
    }

    public static Optional<String> validateEnum(YamlConfiguration config, String path, String... allowed) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        String value = config.getString(path, "").trim().toLowerCase(Locale.ROOT);
        for (String option : allowed) {
            if (option.equalsIgnoreCase(value)) {
                return Optional.empty();
            }
        }
        return Optional.of("must be one of " + String.join(", ", allowed) + " (got '" + value + "')");
    }

    public static Optional<String> validateMultiplier(YamlConfiguration config, String path) {
        if (!config.contains(path)) {
            return Optional.empty();
        }
        double value = config.getDouble(path);
        if (value < 0) {
            return Optional.of("multiplier must be zero or positive (got " + value + ")");
        }
        return Optional.empty();
    }

    public static List<String> validateSectionMaterials(ConfigurationSection section) {
        List<String> errors = new ArrayList<>();
        if (section == null) {
            return errors;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item != null && item.contains("material")) {
                validateMaterial(item.getString("material")).ifPresent(reason ->
                        errors.add(key + ".material: " + reason));
            }
        }
        return errors;
    }

    public static List<String> validateSectionSlots(ConfigurationSection section) {
        List<String> errors = new ArrayList<>();
        if (section == null) {
            return errors;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item != null && item.contains("slot")) {
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot > 53) {
                    errors.add(key + ".slot: inventory slot must be between 0 and 53 (got " + slot + ")");
                }
            }
        }
        return errors;
    }
}
