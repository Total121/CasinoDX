package com.dravorax.casinodx.economy;

import com.dravorax.casinodx.money.Money;
import net.milkbowl.vault2.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Adapter for VaultUnlockedAPI 2.x ({@code net.milkbowl.vault2.economy.Economy}).
 *
 * <p>VaultUnlocked ships under the Bukkit plugin name {@code Vault} (same as classic Vault).
 * Detection therefore keys off the vault2 service registration, not a plugin named
 * {@code VaultUnlocked}.
 */
final class VaultUnlockedAdapter implements EconomyAdapter {

    private static final String PLUGIN_NAME = "CasinoDX";

    private final Economy economy;
    /** Empty means the provider's default currency. */
    private final String currency;

    private VaultUnlockedAdapter(Economy economy, String currency) {
        this.economy = economy;
        this.currency = currency == null ? "" : currency.trim();
    }

    /**
     * @param currency optional VaultUnlocked currency id; blank uses the provider default
     * @return adapter when a vault2 Economy service is registered, otherwise {@code null}
     */
    static EconomyAdapter tryLoad(String currency) {
        try {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return null;
            }
            Economy eco = rsp.getProvider();
            if (eco == null) {
                return null;
            }
            return new VaultUnlockedAdapter(eco, currency);
        } catch (NoClassDefFoundError | ExceptionInInitializerError missingApi) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String name() {
        String base = "VaultUnlocked (" + economy.getName() + ")";
        return currency.isEmpty() ? base : base + " [" + currency + "]";
    }

    @Override
    public Money getBalance(OfflinePlayer player) {
        try {
            return Money.of(readBalance(player.getUniqueId()));
        } catch (Throwable ignored) {
            return Money.ZERO;
        }
    }

    @Override
    public boolean has(OfflinePlayer player, Money amount) {
        try {
            BigDecimal value = amount.toBigDecimal();
            UUID id = player.getUniqueId();
            if (!currency.isEmpty()) {
                return economy.has(PLUGIN_NAME, id, "", currency, value);
            }
            return economy.has(PLUGIN_NAME, id, value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean withdraw(OfflinePlayer player, Money amount) {
        try {
            BigDecimal value = amount.toBigDecimal();
            UUID id = player.getUniqueId();
            if (!currency.isEmpty()) {
                return economy.withdraw(PLUGIN_NAME, id, "", currency, value).transactionSuccess();
            }
            return economy.withdraw(PLUGIN_NAME, id, value).transactionSuccess();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, Money amount) {
        try {
            BigDecimal value = amount.toBigDecimal();
            UUID id = player.getUniqueId();
            if (!currency.isEmpty()) {
                return economy.deposit(PLUGIN_NAME, id, "", currency, value).transactionSuccess();
            }
            return economy.deposit(PLUGIN_NAME, id, value).transactionSuccess();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private BigDecimal readBalance(UUID id) {
        try {
            if (!currency.isEmpty()) {
                return economy.balance(PLUGIN_NAME, id, "", currency);
            }
            return economy.balance(PLUGIN_NAME, id);
        } catch (NoSuchMethodError | AbstractMethodError oldApi) {
            if (!currency.isEmpty()) {
                return economy.getBalance(PLUGIN_NAME, id, "", currency);
            }
            return economy.getBalance(PLUGIN_NAME, id);
        }
    }
}
