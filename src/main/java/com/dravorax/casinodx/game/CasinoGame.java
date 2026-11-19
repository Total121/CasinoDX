package com.dravorax.casinodx.game;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.gui.menu.BetSelectGui;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.function.Function;

/**
 * Base class for the six casino games.
 *
 * <p>Subclasses supply the rules; this class owns the parts that must not vary between games:
 * bet validation, the wager-escrow handshake, and randomness. {@link #beginSession} is the only
 * sanctioned way to start a round, and it either escrows the wager and registers the session or
 * leaves the player's balance completely untouched.
 */
public abstract class CasinoGame {

    /**
     * Cryptographically strong RNG shared by every game.
     *
     * <p>Outcomes are always drawn here <em>before</em> any animation starts, so what the player
     * sees is a replay of a decision the server already committed to. Nothing about the animation
     * (tick count, click timing, packet order) can influence the result.
     */
    protected static final SecureRandom RANDOM = new SecureRandom();

    protected final CasinoDX plugin;
    protected final GameType type;

    protected CasinoGame(CasinoDX plugin, GameType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public GameType type() {
        return type;
    }

    /** Enabled when both {@code config.yml} and the game's own module allow it. */
    public boolean isEnabled() {
        boolean mainEnabled = plugin.configs().main().getBoolean("games." + type.configKey() + ".enabled", true);
        boolean moduleEnabled = config().getBoolean("enabled", true);
        return mainEnabled && moduleEnabled;
    }

    /** Re-reads cached configuration. Called from {@code /casino reload}. */
    public void reload() {}

    /** Opens the game's entry point. Defaults to the shared bet-selection screen. */
    public void openLobby(Player player) {
        if (player == null) {
            return;
        }
        if (!isEnabled()) {
            plugin.messages().send(player, "game-disabled");
            return;
        }
        if (!player.hasPermission(permission())) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        plugin.guis().open(new BetSelectGui(plugin, player, type));
    }

    public String permission() {
        return type.permission();
    }

    public YamlConfiguration config() {
        return plugin.configs().module(type.moduleName());
    }

    public YamlConfiguration payouts() {
        return plugin.configs().payouts(type.moduleName());
    }

    /** House edge as a fraction (config stores percent, e.g. {@code 2.7} → {@code 0.027}). */
    public double houseEdge() {
        double percent = config().getDouble("house-edge", 1.0);
        if (percent < 0 || percent >= 100) {
            percent = 1.0;
        }
        return percent / 100.0;
    }

    /** Module minimum, clamped to the global economy minimum. */
    public Money minBet() {
        double configured = config().getDouble("minimum-bet", -1);
        if (configured < 0) {
            return plugin.economy().minBet();
        }
        return Money.of(configured);
    }

    /** Module maximum; {@code 0} or missing falls back to the global economy maximum. */
    public Money maxBet() {
        double configured = config().getDouble("maximum-bet", -1);
        if (configured <= 0) {
            return plugin.economy().maxBet();
        }
        return Money.of(configured);
    }

    /** Starts a round for {@code bet}. Implementations must delegate to {@link #beginSession}. */
    public abstract void startSession(Player player, Money bet);

    /**
     * Starts a round and restores a wager the player picked <em>inside</em> the previous round —
     * a roulette bet type, a coinflip call — so "play again" really means the same play again.
     *
     * <p>Games that have no such selection ignore the key. The selection is cosmetic pre-filling
     * only: the round still cannot resolve until the player confirms it, and the amount is
     * validated by {@link #beginSession} exactly as any other bet would be.
     */
    public void startSession(Player player, Money bet, String selectionKey) {
        startSession(player, bet);
    }

    /** Whether this game has an in-round selection worth remembering between rounds. */
    public boolean supportsSelection() {
        return false;
    }

    /** Human-readable form of a {@link #startSession(Player, Money, String) selection key}. */
    public String describeSelection(String selectionKey) {
        return selectionKey == null ? "" : selectionKey;
    }

    /**
     * Server-side bet validation. Runs before any money moves and is never driven by client input
     * beyond the requested amount.
     *
     * @return {@code null} when the bet is acceptable, otherwise the message key to send
     */
    protected String validateBet(Player player, Money bet) {
        if (!plugin.economy().isAvailable()) {
            return "no-economy";
        }
        if (bet == null || bet.isNegative()) {
            return "bet-invalid";
        }
        Money min = minBet();
        Money max = maxBet();

        // A zero wager is only meaningful for the free wheel spin, which overrides minBet() to 0.
        if (!bet.isPositive() && min.isPositive()) {
            return "bet-invalid";
        }
        boolean bypass = player.hasPermission("casinodx.bypass.limits");
        if (!bypass) {
            if (bet.lessThan(min)) {
                return "bet-too-low";
            }
            if (bet.greaterThan(max)) {
                return "bet-too-high";
            }
        }
        if (bet.isPositive() && !plugin.economy().has(player, bet)) {
            return "insufficient-funds";
        }
        return null;
    }

    /**
     * The critical path shared by every game.
     *
     * <ol>
     *   <li>reject the round if the player already has a session,</li>
     *   <li>validate the bet server-side,</li>
     *   <li>take the player's transaction lock,</li>
     *   <li>withdraw the wager <em>up front</em>,</li>
     *   <li>register the session, refunding immediately if registration loses a race.</li>
     * </ol>
     *
     * Any failure after the withdrawal refunds the wager before returning, so the player's balance
     * is either debited with a live session to show for it or not debited at all.
     *
     * @param factory builds the session for an already-validated wager
     * @return the registered session, or {@code null} when the round did not start
     */
    protected <S extends GameSession> S beginSession(Player player, Money bet, Function<Money, S> factory) {
        if (player == null) {
            return null;
        }
        if (!isEnabled()) {
            plugin.messages().send(player, "game-disabled");
            return null;
        }
        if (!player.hasPermission(permission())) {
            plugin.messages().send(player, "no-permission");
            return null;
        }
        if (plugin.sessions().has(player.getUniqueId())) {
            plugin.messages().send(player, "already-in-game");
            return null;
        }

        Money wager = bet == null ? Money.ZERO : bet;
        String error = validateBet(player, wager);
        if (error != null) {
            Money amountHint = switch (error) {
                case "bet-too-high" -> maxBet();
                case "bet-too-low" -> minBet();
                default -> wager;
            };
            plugin.messages().send(player, error,
                    "amount", plugin.economy().format(amountHint));
            return null;
        }

        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            plugin.messages().send(player, "already-in-game");
            return null;
        }

        S session;
        try {
            if (wager.isPositive() && !plugin.economy().withdraw(player, wager)) {
                plugin.messages().send(player, "insufficient-funds");
                return null;
            }

            session = factory.apply(wager);
            if (session == null || !plugin.sessions().start(session)) {
                plugin.economy().refund(player, wager);
                plugin.messages().send(player, "already-in-game");
                return null;
            }
            session.markEscrowed();
        } catch (Throwable t) {
            plugin.economy().refund(player, wager);
            plugin.getLogger().severe("[CasinoDX] Failed to start " + type.displayName()
                    + " for " + player.getName() + ": " + t);
            plugin.messages().send(player, "payout-failed");
            return null;
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
        }

        if (wager.isPositive()) {
            plugin.messages().send(player, "bet-placed", "amount", plugin.economy().format(wager));
        }
        plugin.sounds().play(player, SoundService.BET);
        return session;
    }

    /** Uniform integer in {@code [0, bound)} from {@link #RANDOM}. */
    protected static int randomInt(int bound) {
        return RANDOM.nextInt(Math.max(1, bound));
    }

    /** Uniform double in {@code [0, 1)} from {@link #RANDOM}. */
    protected static double randomDouble() {
        return RANDOM.nextDouble();
    }
}
