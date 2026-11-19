package com.dravorax.casinodx.util;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads chat messages from {@code modules/messages/messages.yml} with embedded fallbacks so the
 * plugin never sends an empty string when a key is missing.
 */
public final class MessageService {

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("prefix", "&6[CasinoDX] &r");
        DEFAULTS.put("no-economy", "&cNo economy provider is available. Bets are disabled.");
        DEFAULTS.put("insufficient-funds", "&cYou don't have enough money for that bet.");
        DEFAULTS.put("bet-placed", "&aBet placed: &f{amount}");
        DEFAULTS.put("bet-too-low", "&cThe minimum bet is &f{amount}&c.");
        DEFAULTS.put("bet-too-high", "&cThe maximum bet is &f{amount}&c.");
        DEFAULTS.put("bet-invalid", "&cThat bet amount is not valid.");
        DEFAULTS.put("bet-input-prompt",
                "&eType a bet amount in chat for &f{game}&e, or type &fcancel&e. "
                        + "&7(current {amount}, min {min}, max {max}, {seconds}s)");
        DEFAULTS.put("bet-input-invalid", "&cThat is not a valid amount. Try something like &f500 &cor &f1.5k&c.");
        DEFAULTS.put("bet-input-cancelled", "&7Bet entry cancelled.");
        DEFAULTS.put("bet-input-too-low", "&cThe minimum bet is &f{amount}&c. Type another amount or &fcancel&c.");
        DEFAULTS.put("bet-input-too-high", "&cThe maximum bet is &f{amount}&c. Type another amount or &fcancel&c.");
        DEFAULTS.put("bet-input-insufficient",
                "&cYou cannot afford &f{amount}&c — your balance is &f{balance}&c.");
        DEFAULTS.put("roulette-no-bets", "&cPlace at least one chip on the table before you spin.");
        DEFAULTS.put("roulette-board-full", "&cYou already have &f{count} &cchips on the table.");
        DEFAULTS.put("replay-nothing-to-replay", "&7You have no finished round to replay yet.");
        DEFAULTS.put("win", "&a&lWIN &7» &fYou won &a{amount} &7({multiplier})");
        DEFAULTS.put("loss", "&c&lLOSS &7» &fYou lost &c{amount}&f.");
        DEFAULTS.put("push", "&e&lPUSH &7» &fYour bet of &f{amount} &fwas returned.");
        DEFAULTS.put("tax-applied", "&7Tax deducted: &f{amount}");
        DEFAULTS.put("game-disabled", "&cThat game is currently disabled.");
        DEFAULTS.put("no-permission", "&cYou don't have permission to do that.");
        DEFAULTS.put("already-in-game", "&cFinish your current game first.");
        DEFAULTS.put("session-refunded", "&eYour bet of &f{amount} &ewas refunded ({reason}).");
        DEFAULTS.put("payout-failed", "&cPayout failed — contact an administrator. Your bet was refunded.");
        DEFAULTS.put("wheel-cooldown", "&cYour free spin is not ready yet: &f{time}");
        DEFAULTS.put("wheel-result", "&6&lWHEEL &7» &fYou landed on &e{reward}&f.");
        DEFAULTS.put("wheel-no-reward", "&7The wheel stopped on &f{reward}&7. Better luck next time.");
        DEFAULTS.put("wheel-broadcast", "&6{player} &ewon &f{reward} &eon the Wheel of Fortune!");
        DEFAULTS.put("wheel-pending-delivered", "&aYour queued wheel rewards have been delivered (&f{count}&a).");
        DEFAULTS.put("unknown-command", "&cUnknown subcommand. Try &f/casino help&c.");
        DEFAULTS.put("player-not-found", "&cNo such player: &f{player}");
        DEFAULTS.put("stats-header", "&6&lCasinoDX Statistics &7» &f{player}");
        DEFAULTS.put("history-header", "&6&lCasinoDX History &7» &fpage {page}");
        DEFAULTS.put("history-empty", "&7No rounds recorded yet.");
        DEFAULTS.put("reload-success", "&aCasinoDX configuration reloaded.");
        DEFAULTS.put("reload-failed", "&cReload failed — check the console for details.");
        DEFAULTS.put("reload-busy", "&cReload aborted: players are still in an active game.");
        DEFAULTS.put("players-only", "&cThis command can only be used in-game.");
    }

    private final CasinoDX plugin;
    private final ConfigManager configs;
    private final Map<String, String> messages = new HashMap<>();

    public MessageService(CasinoDX plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
        reload();
    }

    public void reload() {
        messages.clear();
        messages.putAll(DEFAULTS);
        YamlConfiguration file = configs.messages();
        for (String key : file.getKeys(true)) {
            if (file.isString(key)) {
                messages.put(key, file.getString(key));
            }
        }
    }

    public String prefix() {
        String mainPrefix = configs.main().getString("prefix");
        if (mainPrefix != null && !mainPrefix.isBlank()) {
            return mainPrefix;
        }
        return messages.getOrDefault("prefix", DEFAULTS.get("prefix"));
    }

    /** Raw message with {@code {placeholder}} tokens intact and no prefix. */
    public String raw(String key) {
        return messages.getOrDefault(key, DEFAULTS.getOrDefault(key, ""));
    }

    /** Colored message with placeholders resolved, without the prefix. */
    public String get(String key, Object... placeholders) {
        return Text.color(applyPlaceholders(raw(key), placeholders));
    }

    /**
     * Sends a prefixed message. Placeholders are supplied as alternating key/value pairs,
     * e.g. {@code send(player, "win", "amount", "$500", "multiplier", "2.00x")}.
     */
    public void send(CommandSender target, String key, Object... placeholders) {
        if (target == null) {
            return;
        }
        String body = raw(key);
        if (body == null || body.isBlank()) {
            return;
        }
        target.sendMessage(Text.color(prefix() + applyPlaceholders(body, placeholders)));
    }

    /** Sends a literal (already-authored) line with the plugin prefix. */
    public void sendLiteral(CommandSender target, String line) {
        if (target == null || line == null) {
            return;
        }
        target.sendMessage(Text.color(prefix() + line));
    }

    public void broadcast(String key, Object... placeholders) {
        String body = raw(key);
        if (body == null || body.isBlank()) {
            return;
        }
        String rendered = Text.color(prefix() + applyPlaceholders(body, placeholders));
        plugin.getServer().broadcast(Text.component(rendered));
    }

    private String applyPlaceholders(String template, Object... placeholders) {
        if (template == null) {
            return "";
        }
        if (placeholders == null || placeholders.length < 2) {
            return template;
        }
        String result = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            Object key = placeholders[i];
            Object value = placeholders[i + 1];
            if (key == null) {
                continue;
            }
            result = result.replace("{" + key + "}", value == null ? "" : value.toString());
        }
        return result;
    }
}
