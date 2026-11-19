package com.dravorax.casinodx.economy;

import java.util.Locale;

/**
 * Parses {@code modules/economy/config.yml} {@code provider} without touching Bukkit, so it stays
 * unit-testable offline.
 */
public final class EconomyProviderModes {

    public enum Mode {
        AUTO,
        VAULT,
        VAULTUNLOCKED
    }

    private EconomyProviderModes() {}

    public static Mode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mode.AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "vault" -> Mode.VAULT;
            case "vaultunlocked", "vault-unlocked", "vault2", "unlocked" -> Mode.VAULTUNLOCKED;
            default -> Mode.AUTO;
        };
    }
}
