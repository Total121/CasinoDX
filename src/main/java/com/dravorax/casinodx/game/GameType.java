package com.dravorax.casinodx.game;

import org.bukkit.Material;

public enum GameType {

    MINES("Mines", "mines", Material.TNT, "Reveal gems, avoid mines."),
    ROULETTE("Roulette", "roulette", Material.REDSTONE, "Spin the wheel of fortune."),
    BLACKJACK("Blackjack", "blackjack", Material.PAPER, "Beat the dealer to 21."),
    COINFLIP("Coinflip", "coinflip", Material.GOLD_NUGGET, "Double or nothing."),
    CRASH("Crash", "crash", Material.FIREWORK_ROCKET, "Cash out before the crash."),
    WHEEL("Wheel", "wheel", Material.NETHER_STAR, "Spin for rewards."),
    SLOTS("Slots", "slots", Material.LEVER, "Match the reels for a jackpot.");

    private final String displayName;
    private final String configKey;
    private final Material iconMaterial;
    private final String description;

    GameType(String displayName, String configKey, Material iconMaterial, String description) {
        this.displayName = displayName;
        this.configKey = configKey;
        this.iconMaterial = iconMaterial;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String configKey() {
        return configKey;
    }

    /** Module folder name under {@code modules/}. */
    public String moduleName() {
        return configKey;
    }

    public Material iconMaterial() {
        return iconMaterial;
    }

    public String description() {
        return description;
    }

    public String permission() {
        return "casinodx.play." + configKey;
    }

    public String headKey() {
        return configKey;
    }

    public static GameType fromModule(String module) {
        if (module == null) {
            return null;
        }
        for (GameType type : values()) {
            if (type.configKey.equalsIgnoreCase(module)) {
                return type;
            }
        }
        return null;
    }
}
