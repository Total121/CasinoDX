package com.dravorax.casinodx.economy;

import com.dravorax.casinodx.money.Money;
import org.bukkit.OfflinePlayer;

/**
 * Strategy interface so CasinoDX can talk to either legacy Vault or VaultUnlocked
 * without dragging both class hierarchies into a single hot file.
 */
public interface EconomyAdapter {

    String name();

    Money getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, Money amount);

    boolean withdraw(OfflinePlayer player, Money amount);

    boolean deposit(OfflinePlayer player, Money amount);
}
