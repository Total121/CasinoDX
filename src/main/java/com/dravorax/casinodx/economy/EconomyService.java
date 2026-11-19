package com.dravorax.casinodx.economy;

import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.money.Money;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Unified economy facade with per-player transaction locking.
 *
 * <p>Supports classic Vault (legacy double API) and VaultUnlocked (vault2 BigDecimal/UUID API).
 * VaultUnlocked's Bukkit plugin name is {@code Vault}; the vault2 path is selected by service
 * registration and by {@code modules/economy/config.yml} {@code provider}.
 */
public final class EconomyService {

    private final ConfigManager configs;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Object> playerLocks = new ConcurrentHashMap<>();

    private EconomyAdapter adapter;
    private boolean available;
    private EconomyProviderModes.Mode mode = EconomyProviderModes.Mode.AUTO;

    public EconomyService(JavaPlugin plugin, ConfigManager configs) {
        this.configs = configs;
        this.logger = plugin.getLogger();
        setup();
    }

    public void reloadHints() {
        setup();
    }

    private void setup() {
        this.adapter = null;
        this.available = false;
        this.mode = EconomyProviderModes.parse(
                configs.module("economy").getString("provider", "auto"));
        String currency = readCurrency();

        switch (mode) {
            case VAULTUNLOCKED -> hookVaultUnlocked(currency, true);
            case VAULT -> hookLegacyVault(true);
            case AUTO -> {
                if (!hookVaultUnlocked(currency, false)) {
                    hookLegacyVault(false);
                }
            }
        }

        if (!available) {
            logger.warning("[CasinoDX] No economy provider found (Vault / VaultUnlocked). "
                    + "CasinoDX will stay enabled but all games will refuse bets until an economy is available.");
        }
    }

    private boolean hookVaultUnlocked(String currency, boolean required) {
        try {
            EconomyAdapter unlocked = VaultUnlockedAdapter.tryLoad(currency);
            if (unlocked != null) {
                this.adapter = unlocked;
                this.available = true;
                logger.info("[CasinoDX] Hooked into economy: " + unlocked.name());
                return true;
            }
            if (required) {
                logger.warning("[CasinoDX] provider=vaultunlocked but no vault2 Economy service is registered. "
                        + "Install VaultUnlocked with an economy plugin that supports VaultUnlockedAPI.");
            }
        } catch (Throwable t) {
            logger.warning("[CasinoDX] VaultUnlocked hook failed: " + t.getMessage());
        }
        return false;
    }

    private boolean hookLegacyVault(boolean required) {
        try {
            EconomyAdapter legacy = LegacyVaultAdapter.tryLoad();
            if (legacy != null) {
                this.adapter = legacy;
                this.available = true;
                logger.info("[CasinoDX] Hooked into economy: " + legacy.name());
                return true;
            }
            if (required) {
                logger.warning("[CasinoDX] provider=vault but no legacy Vault Economy service is registered.");
            }
        } catch (Throwable t) {
            logger.warning("[CasinoDX] Vault hook failed: " + t.getMessage());
        }
        return false;
    }

    private String readCurrency() {
        String configured = configs.module("economy").getString("currency", "");
        return configured == null ? "" : configured.trim();
    }

    public boolean isAvailable() {
        return available && adapter != null;
    }

    public EconomyProviderModes.Mode providerMode() {
        return mode;
    }

    public String providerName() {
        return adapter == null ? "none" : adapter.name();
    }

    public Money getBalance(Player player) {
        return getBalance((OfflinePlayer) player);
    }

    public Money getBalance(OfflinePlayer player) {
        if (!isAvailable() || player == null) {
            return Money.ZERO;
        }
        synchronized (lockFor(player.getUniqueId())) {
            return adapter.getBalance(player);
        }
    }

    public Money minBet() {
        return Money.of(configs.module("economy").getDouble("minimum-bet", 10));
    }

    public Money maxBet() {
        return Money.of(configs.module("economy").getDouble("maximum-bet", 1_000_000));
    }

    public String format(Money amount) {
        Money safe = amount == null ? Money.ZERO : amount;
        int decimals = Math.max(0, configs.module("economy").getInt("decimal-places", 2));
        String symbol = currencySymbol();
        return symbol + String.format(Locale.US, "%,." + decimals + "f", safe.toDouble());
    }

    public boolean has(OfflinePlayer player, Money amount) {
        if (!isAvailable() || player == null || amount == null || amount.isNegative()) {
            return false;
        }
        synchronized (lockFor(player.getUniqueId())) {
            return adapter.has(player, amount);
        }
    }

    public boolean withdraw(OfflinePlayer player, Money amount) {
        if (!isAvailable() || player == null || amount == null || !amount.isPositive()) {
            return false;
        }
        synchronized (lockFor(player.getUniqueId())) {
            if (!adapter.has(player, amount)) {
                return false;
            }
            return adapter.withdraw(player, amount);
        }
    }

    public boolean deposit(OfflinePlayer player, Money amount) {
        if (!isAvailable() || player == null || amount == null || !amount.isPositive()) {
            return false;
        }
        synchronized (lockFor(player.getUniqueId())) {
            return adapter.deposit(player, amount);
        }
    }

    /**
     * Atomically withdraws a wager if the player has sufficient funds.
     *
     * @return true when the wager was successfully withdrawn
     */
    public boolean tryWager(OfflinePlayer player, Money amount) {
        if (!isAvailable()) {
            return false;
        }
        return withdraw(player, amount);
    }

    public void refund(OfflinePlayer player, Money amount) {
        if (!isAvailable() || player == null || amount == null || amount.isZero()) {
            return;
        }
        deposit(player, amount);
    }

    public String currencySymbol() {
        return configs.module("economy").getString("currency-symbol", "$");
    }

    /** Drops the per-player lock object so reconnects do not grow the map forever. */
    public void cleanup(UUID uuid) {
        if (uuid != null) {
            playerLocks.remove(uuid);
        }
    }

    private Object lockFor(UUID uuid) {
        return playerLocks.computeIfAbsent(uuid, ignored -> new Object());
    }
}
