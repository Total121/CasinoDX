package com.dravorax.casinodx.bet;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Chat-driven bet entry.
 *
 * <p>A GUI that wants an arbitrary amount closes itself and parks a {@link PendingBetInput} here;
 * {@link ChatBetListener} feeds the player's next chat line back in through
 * {@link #handleInput(Player, String)}, which validates it against the game's own limits and the
 * player's balance before handing the amount to the callback.
 *
 * <p>Nothing in this class moves money. The callback re-enters the normal
 * {@link CasinoGame#startSession} path, so a crafted amount is still validated server-side.
 */
public final class BetInputService {

    /** Longest a prompt stays armed before the player's chat goes back to being chat. */
    private static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;

    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "exit", "quit", "stop");
    private static final Set<String> ALL_IN_WORDS = Set.of("all", "max", "allin", "all-in");

    /**
     * One armed prompt.
     *
     * @param selection      game-specific selection metadata carried across the prompt (roulette bet
     *                       type, coinflip side), so a cancelled prompt does not lose it
     * @param returnToReplay whether cancelling should reopen the game's bet screen rather than do
     *                       nothing
     */
    public record PendingBetInput(GameType gameType,
                                  Map<String, String> selection,
                                  Money suggestedBet,
                                  boolean returnToReplay,
                                  Consumer<Money> after,
                                  long startedAt,
                                  long expiresAt) {

        public PendingBetInput {
            selection = selection == null ? Map.of() : Map.copyOf(selection);
            suggestedBet = suggestedBet == null ? Money.ZERO : suggestedBet;
        }

        /** Cheap enough to evaluate from the async chat thread — no config lookup involved. */
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final CasinoDX plugin;
    private final Map<UUID, PendingBetInput> pending = new ConcurrentHashMap<>();

    public BetInputService(CasinoDX plugin) {
        this.plugin = plugin;
    }

    public long timeoutMillis() {
        int seconds = plugin.configs().gui().getInt("bet-input.timeout-seconds", 60);
        if (seconds < 5 || seconds > 600) {
            return DEFAULT_TIMEOUT_MILLIS;
        }
        return seconds * 1_000L;
    }

    /** Arms a prompt whose accepted amount is handed to {@code after}. */
    public void startPrompt(Player player, GameType gameType, Money suggestedBet, Consumer<Money> after) {
        startPrompt(player, gameType, suggestedBet, Map.of(), false, after);
    }

    /**
     * Arms a prompt that falls back to the game's bet screen when the player cancels it.
     *
     * <p>Passing a {@code null} callback is the "just reopen the bet screen with the new amount"
     * shorthand. Games that continue in place — the slot machine, the crash cockpit — pass their
     * own callback instead so the player lands back where they were.
     */
    public void startPrompt(Player player, GameType gameType, Money suggestedBet,
                            Map<String, String> selection, boolean returnToReplay, Consumer<Money> after) {
        if (player == null || gameType == null) {
            return;
        }
        Consumer<Money> callback = after != null
                ? after
                : amount -> reopenBetScreen(player, gameType, amount);
        long now = System.currentTimeMillis();
        pending.put(player.getUniqueId(), new PendingBetInput(gameType, selection, suggestedBet,
                returnToReplay, callback, now, now + timeoutMillis()));

        Money min = minBet(gameType);
        Money max = maxBet(gameType);
        plugin.messages().send(player, "bet-input-prompt",
                "game", gameType.displayName(),
                "amount", plugin.economy().format(suggestedBet == null ? min : suggestedBet),
                "min", plugin.economy().format(min),
                "max", plugin.economy().format(max),
                "seconds", String.valueOf(timeoutMillis() / 1000L));
    }

    /** Safe to call from the async chat thread. */
    public boolean isWaiting(UUID playerId) {
        PendingBetInput input = pending.get(playerId);
        if (input == null) {
            return false;
        }
        if (input.isExpired()) {
            pending.remove(playerId);
            return false;
        }
        return true;
    }

    public PendingBetInput peek(UUID playerId) {
        return playerId == null ? null : pending.get(playerId);
    }

    /** Removes and returns the armed prompt, if any. */
    public PendingBetInput consume(UUID playerId) {
        return playerId == null ? null : pending.remove(playerId);
    }

    public void cancel(UUID playerId) {
        if (playerId != null) {
            pending.remove(playerId);
        }
    }

    public void cancel(Player player) {
        if (player != null) {
            cancel(player.getUniqueId());
        }
    }

    public void clearAll() {
        pending.clear();
    }

    /**
     * Processes one chat line for a player with an armed prompt. Must run on the main thread.
     *
     * <p>An unusable amount re-arms the prompt rather than dropping it, so a typo does not force the
     * player back through the menu.
     */
    public void handleInput(Player player, String rawInput) {
        if (player == null) {
            return;
        }
        PendingBetInput input = consume(player.getUniqueId());
        if (input == null) {
            return;
        }
        String raw = rawInput == null ? "" : rawInput.trim();

        if (isCancelWord(raw)) {
            plugin.messages().send(player, "bet-input-cancelled");
            if (input.returnToReplay()) {
                reopenBetScreen(player, input.gameType(), input.suggestedBet());
            }
            return;
        }

        Money amount = isAllInWord(raw)
                ? plugin.economy().getBalance(player)
                : parseAmount(raw, plugin.economy().currencySymbol());

        if (amount == null || !amount.isPositive()) {
            rearm(player, input, "bet-input-invalid");
            return;
        }

        Money min = minBet(input.gameType());
        Money max = maxBet(input.gameType());
        boolean bypass = player.hasPermission("casinodx.bypass.limits");

        if (!bypass && amount.lessThan(min)) {
            rearm(player, input, "bet-input-too-low", "amount", plugin.economy().format(min));
            return;
        }
        if (!bypass && amount.greaterThan(max)) {
            rearm(player, input, "bet-input-too-high", "amount", plugin.economy().format(max));
            return;
        }
        if (!plugin.economy().has(player, amount)) {
            rearm(player, input, "bet-input-insufficient",
                    "amount", plugin.economy().format(amount),
                    "balance", plugin.economy().format(plugin.economy().getBalance(player)));
            return;
        }

        input.after().accept(amount);
    }

    /** A typo should not cost the player the prompt, so the window restarts instead of closing. */
    private void rearm(Player player, PendingBetInput input, String messageKey, Object... placeholders) {
        long now = System.currentTimeMillis();
        pending.put(player.getUniqueId(), new PendingBetInput(input.gameType(), input.selection(),
                input.suggestedBet(), input.returnToReplay(), input.after(), now, now + timeoutMillis()));
        plugin.messages().send(player, messageKey, placeholders);
    }

    /**
     * Default landing place for a prompt with no callback of its own, and for one the player
     * cancels. Games continue in place now rather than through a replay screen, so the honest
     * fallback is the bet screen for whichever game the prompt belonged to.
     */
    private void reopenBetScreen(Player player, GameType gameType, Money amount) {
        Money bet = amount == null ? Money.ZERO : amount;
        if (bet.isPositive()) {
            plugin.lastPlay().updateBet(player.getUniqueId(), bet);
        }
        plugin.guis().open(new com.dravorax.casinodx.gui.menu.BetSelectGui(plugin, player, gameType, bet));
    }

    private Money minBet(GameType type) {
        CasinoGame game = plugin.games().get(type);
        return game == null ? plugin.economy().minBet() : game.minBet();
    }

    private Money maxBet(GameType type) {
        CasinoGame game = plugin.games().get(type);
        return game == null ? plugin.economy().maxBet() : game.maxBet();
    }

    private static boolean isCancelWord(String raw) {
        return CANCEL_WORDS.contains(raw.toLowerCase(Locale.ROOT));
    }

    private static boolean isAllInWord(String raw) {
        return ALL_IN_WORDS.contains(raw.toLowerCase(Locale.ROOT));
    }

    /**
     * Parses a player-typed amount, tolerating the ways people actually write money:
     * {@code $1,500}, {@code 1500}, {@code 1.5k}, {@code 2M}.
     *
     * @return the amount, or {@code null} when the text is not a usable positive number
     */
    public static Money parseAmount(String rawInput, String currencySymbol) {
        if (rawInput == null) {
            return null;
        }
        String text = rawInput.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (currencySymbol != null && !currencySymbol.isBlank()) {
            text = text.replace(currencySymbol, "");
        }
        text = text.replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace(",", "")
                .replace("_", "")
                .replace(" ", "")
                .replace("+", "");
        if (text.isEmpty()) {
            return null;
        }

        double scale = 1.0;
        char suffix = Character.toLowerCase(text.charAt(text.length() - 1));
        switch (suffix) {
            case 'k' -> scale = 1_000d;
            case 'm' -> scale = 1_000_000d;
            case 'b' -> scale = 1_000_000_000d;
            case 't' -> scale = 1_000_000_000_000d;
            default -> { }
        }
        if (scale > 1.0) {
            text = text.substring(0, text.length() - 1);
            if (text.isEmpty()) {
                return null;
            }
        }

        try {
            BigDecimal value = new BigDecimal(text);
            if (scale > 1.0) {
                value = value.multiply(BigDecimal.valueOf(scale));
            }
            if (value.signum() <= 0) {
                return null;
            }
            return Money.of(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
