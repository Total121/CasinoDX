package com.dravorax.casinodx.economy;

import com.dravorax.casinodx.money.Money;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Adapter for the original Vault {@link Economy} API.
 *
 * <p>Works with classic Vault and with VaultUnlocked's legacy bridge. Both register under the
 * Bukkit plugin name {@code Vault}.
 */
final class LegacyVaultAdapter implements EconomyAdapter {

    private final Economy economy;

    private LegacyVaultAdapter(Economy economy) {
        this.economy = economy;
    }

    static EconomyAdapter tryLoad() {
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
            return new LegacyVaultAdapter(eco);
        } catch (NoClassDefFoundError | ExceptionInInitializerError missingApi) {
            return null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public String name() {
        return "Vault (" + economy.getName() + ")";
    }

    @Override
    public Money getBalance(OfflinePlayer player) {
        try {
            return Money.of(economy.getBalance(player));
        } catch (Throwable ignored) {
            return Money.ZERO;
        }
    }

    @Override
    public boolean has(OfflinePlayer player, Money amount) {
        try {
            return economy.has(player, amount.toDouble());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean withdraw(OfflinePlayer player, Money amount) {
        try {
            return economy.withdrawPlayer(player, amount.toDouble()).transactionSuccess();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean deposit(OfflinePlayer player, Money amount) {
        try {
            return economy.depositPlayer(player, amount.toDouble()).transactionSuccess();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
