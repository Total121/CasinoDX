package com.dravorax.casinodx.tax;

import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Computes casino winnings tax from {@code modules/tax/config.yml}.
 */
public final class TaxService {

    private static final String EXEMPT_PERMISSION = "casinodx.tax.exempt";

    private final ConfigManager configs;

    private volatile boolean enabled;
    private volatile double defaultPercentage;
    private volatile ApplyMode defaultApplyMode;
    private volatile Money minimumTaxable;
    private volatile Money maximumTax;

    public TaxService(ConfigManager configs) {
        this.configs = configs;
        reload();
    }

    public void reload() {
        YamlConfiguration tax = configs.module("tax");
        this.enabled = tax.getBoolean("enabled", false);
        this.defaultPercentage = tax.getDouble("percentage", 0);
        this.defaultApplyMode = ApplyMode.fromString(tax.getString("apply-to", "profit"));
        this.minimumTaxable = Money.of(tax.getDouble("minimum-taxable", 0));
        this.maximumTax = Money.of(tax.getDouble("maximum-tax", 0));
    }

    public TaxResult compute(Player player, GameType game, Money bet, Money grossReturn) {
        boolean exempt = player != null && player.hasPermission(EXEMPT_PERMISSION);
        return computeInternal(exempt, game, bet, grossReturn);
    }

    /**
     * Tax for an offline settlement. Offline players are never treated as tax-exempt, so
     * disconnecting mid-animation cannot skip the house cut.
     */
    public TaxResult computeOffline(org.bukkit.OfflinePlayer player, GameType game, Money bet, Money grossReturn) {
        return computeInternal(false, game, bet, grossReturn);
    }

    private TaxResult computeInternal(boolean exempt, GameType game, Money bet, Money grossReturn) {
        GameOverride override = resolveOverride(game);
        double percentage = override.percentage() >= 0 ? override.percentage() : defaultPercentage;
        ApplyMode applyMode = override.applyMode() != null ? override.applyMode() : defaultApplyMode;
        String applyTo = applyMode == ApplyMode.GROSS ? "gross" : "profit";

        return TaxMath.calculate(
                enabled,
                exempt,
                percentage,
                applyTo,
                minimumTaxable,
                maximumTax,
                bet,
                grossReturn);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isExempt(Player player) {
        return player != null && player.hasPermission(EXEMPT_PERMISSION);
    }

    /** Effective tax percentage for a game, taking per-game overrides into account. */
    public double percentageFor(GameType game) {
        if (!enabled) {
            return 0;
        }
        GameOverride override = resolveOverride(game);
        return override.percentage() >= 0 ? override.percentage() : defaultPercentage;
    }

    /** Short human-readable summary for GUI info panels. */
    public String infoLine() {
        if (!enabled || defaultPercentage <= 0) {
            return "No tax on winnings.";
        }
        String basis = defaultApplyMode == ApplyMode.GROSS ? "total payout" : "profit";
        return String.format(Locale.US, "%.1f%% tax on %s.", defaultPercentage, basis);
    }

    public String infoLine(GameType game) {
        double percentage = percentageFor(game);
        if (percentage <= 0) {
            return "No tax on winnings.";
        }
        GameOverride override = resolveOverride(game);
        ApplyMode mode = override.applyMode() != null ? override.applyMode() : defaultApplyMode;
        String basis = mode == ApplyMode.GROSS ? "total payout" : "profit";
        return String.format(Locale.US, "%.1f%% tax on %s.", percentage, basis);
    }

    private GameOverride resolveOverride(GameType game) {
        if (game == null) {
            return GameOverride.none();
        }
        ConfigurationSection section = configs.module("tax").getConfigurationSection("per-game");
        if (section == null) {
            return GameOverride.none();
        }
        ConfigurationSection gameSection = section.getConfigurationSection(game.configKey());
        if (gameSection == null) {
            return GameOverride.none();
        }
        double percentage = gameSection.contains("percentage")
                ? gameSection.getDouble("percentage")
                : -1;
        ApplyMode mode = gameSection.contains("apply-to")
                ? ApplyMode.fromString(gameSection.getString("apply-to"))
                : null;
        return new GameOverride(percentage, mode);
    }

    private enum ApplyMode {
        PROFIT,
        GROSS;

        static ApplyMode fromString(String raw) {
            if (raw == null) {
                return PROFIT;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "gross" -> GROSS;
                default -> PROFIT;
            };
        }
    }

    private record GameOverride(double percentage, ApplyMode applyMode) {
        static GameOverride none() {
            return new GameOverride(-1, null);
        }
    }
}
