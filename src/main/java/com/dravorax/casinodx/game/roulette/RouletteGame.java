package com.dravorax.casinodx.game.roulette;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * European roulette, played the way a real table is: one chip size, as many cells as you like.
 *
 * <p>Payout multipliers come from {@code modules/roulette/payouts.yml}, falling back to the classic
 * table in {@link RouletteBet.Kind}. Because a single-zero wheel already pays 36-for-1 on a 37-pocket
 * layout, the published odds carry the whole 2.70% edge and no additional {@code house-edge} scaling
 * is applied — that config value is informational for this game.
 *
 * <p>This is the one game that does not escrow anything when it starts. {@link #startSession} opens
 * the table on a {@link Money#ZERO} wager and the chosen amount becomes the <em>chip size</em>
 * instead; {@link RouletteSession#spin()} withdraws {@code chip x chips placed} when the player is
 * ready. Standing at the table, laying chips out and walking away again is free.
 */
public final class RouletteGame extends CasinoGame {

    private static final int RECENT_HISTORY = 8;

    private static final int DEFAULT_MAX_BETS = 15;
    private static final int MIN_MAX_BETS = 10;
    /** One chip per pocket is as far as a 37-pocket wheel can go. */
    private static final int MAX_MAX_BETS = 37;

    private static final List<Double> FALLBACK_CHIP_PRESETS =
            List.of(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1000.0);

    /** How many chip presets the table has room to show. */
    public static final int CHIP_PRESET_SLOTS = 7;

    private final Map<RouletteBet.Kind, Double> multipliers = new EnumMap<>(RouletteBet.Kind.class);

    /** Most recent spun pockets, newest first, for the "recent results" strip. */
    private final Deque<Integer> recentResults = new ArrayDeque<>();

    private int spinTicks;
    private int maxBetsPerRound;

    public RouletteGame(CasinoDX plugin) {
        super(plugin, GameType.ROULETTE);
        reload();
    }

    @Override
    public void reload() {
        multipliers.clear();
        for (RouletteBet.Kind kind : RouletteBet.Kind.values()) {
            double configured = payouts().getDouble(kind.configKey() + ".multiplier", -1);
            if (configured <= 1.0) {
                if (configured >= 0) {
                    plugin.getLogger().warning("[CasinoDX] roulette payout for '" + kind.configKey()
                            + "' must exceed 1.0; using the standard " + kind.defaultMultiplier() + ".");
                }
                configured = kind.defaultMultiplier();
            }
            multipliers.put(kind, configured);
        }
        this.spinTicks = Math.max(40, Math.min(200, config().getInt("spin-duration-ticks", 90)));
        this.maxBetsPerRound = Math.max(MIN_MAX_BETS, Math.min(MAX_MAX_BETS,
                config().getInt("max-bets-per-round", DEFAULT_MAX_BETS)));
    }

    /** Gross multiplier (stake included) for a bet kind. */
    public double multiplierFor(RouletteBet.Kind kind) {
        return multipliers.getOrDefault(kind, kind.defaultMultiplier());
    }

    public Money grossReturn(Money bet, RouletteBet.Kind kind) {
        return bet.multiply(multiplierFor(kind));
    }

    public int spinTicks() {
        return spinTicks;
    }

    /** How many separate chips one player may have on the felt at once. */
    public int maxBetsPerRound() {
        return maxBetsPerRound;
    }

    /** Keeps a chip size inside the table limits. */
    public Money clampChip(Money requested) {
        Money min = minBet();
        Money max = maxBet();
        Money chip = requested == null ? min : requested;
        if (chip.lessThan(min)) {
            return min;
        }
        return chip.greaterThan(max) ? max : chip;
    }

    /** The +/- step for the chip buttons, shared with the bet-selection screen. */
    public Money chipStep() {
        double configured = plugin.configs().gui().getDouble("bet-select.step", 10.0);
        return Money.of(configured > 0 ? configured : 10.0);
    }

    /**
     * Chip denominations offered on the table. Values outside the table limits are dropped, and at
     * most {@link #CHIP_PRESET_SLOTS} survive because that is how many cells the strip has.
     */
    public List<Money> chipPresets() {
        List<Double> configured = config().getDoubleList("chip-presets");
        if (configured == null || configured.isEmpty()) {
            configured = FALLBACK_CHIP_PRESETS;
        }
        Money min = minBet();
        Money max = maxBet();

        List<Money> presets = new ArrayList<>(CHIP_PRESET_SLOTS);
        for (double value : configured) {
            if (value <= 0) {
                continue;
            }
            Money amount = Money.of(value);
            if (amount.lessThan(min) || amount.greaterThan(max) || presets.contains(amount)) {
                continue;
            }
            presets.add(amount);
            if (presets.size() >= CHIP_PRESET_SLOTS) {
                break;
            }
        }
        if (presets.isEmpty()) {
            presets.add(min);
        }
        return presets;
    }

    public List<Integer> recentResults() {
        return new ArrayList<>(recentResults);
    }

    void recordResult(int pocket) {
        recentResults.addFirst(pocket);
        while (recentResults.size() > RECENT_HISTORY) {
            recentResults.removeLast();
        }
    }

    /**
     * Draws the winning pocket. Called once, before the spin animation is created, so the animation
     * is only ever replaying a decision that has already been made.
     */
    int drawPocket() {
        return randomInt(RouletteWheel.POCKETS);
    }

    @Override
    public void startSession(Player player, Money bet) {
        startSession(player, bet, null);
    }

    /**
     * Opens the table. {@code bet} is the chip size the player picked on the way in, not an amount
     * that changes hands here.
     */
    @Override
    public void startSession(Player player, Money bet, String selectionKey) {
        openTable(player, bet, decodeSelections(selectionKey));
    }

    /**
     * Opens the table with a chip size and, optionally, a board carried over from the previous
     * round. No money moves: the session is registered on a zero wager.
     */
    public void openTable(Player player, Money chipSize, List<RouletteBet> carryOver) {
        if (player == null) {
            return;
        }
        Money chip = clampChip(chipSize);
        RouletteSession session = beginSession(player, Money.ZERO,
                wager -> new RouletteSession(plugin, player, this, chip));
        if (session == null) {
            return;
        }
        session.restoreBets(carryOver);
        session.openTable();
    }

    /**
     * Roulette opens on a zero wager, which every other game would reject. The chip size is
     * validated instead when {@link RouletteSession#spin()} withdraws the total.
     */
    @Override
    protected String validateBet(Player player, Money bet) {
        if (!plugin.economy().isAvailable()) {
            return "no-economy";
        }
        if (bet == null || bet.isNegative()) {
            return "bet-invalid";
        }
        if (bet.isZero()) {
            return null;
        }
        return super.validateBet(player, bet);
    }

    @Override
    public boolean supportsSelection() {
        return true;
    }

    @Override
    public String describeSelection(String selectionKey) {
        List<RouletteBet> bets = decodeSelections(selectionKey);
        if (bets.isEmpty()) {
            return "";
        }
        if (bets.size() == 1) {
            return bets.get(0).label();
        }
        StringBuilder text = new StringBuilder(bets.size() + " chips: ");
        for (int i = 0; i < Math.min(3, bets.size()); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(bets.get(i).shortLabel());
        }
        if (bets.size() > 3) {
            text.append(", +").append(bets.size() - 3);
        }
        return text.toString();
    }

    /**
     * Encodes a whole board as {@code kind:number;kind:number;…} for
     * {@link com.dravorax.casinodx.bet.LastPlayContext}, so "play again" can lay the same chips out.
     */
    public static String encodeSelection(List<RouletteBet> bets) {
        if (bets == null || bets.isEmpty()) {
            return null;
        }
        StringBuilder encoded = new StringBuilder();
        for (RouletteBet bet : bets) {
            if (bet == null) {
                continue;
            }
            if (encoded.length() > 0) {
                encoded.append(';');
            }
            encoded.append(bet.encode());
        }
        return encoded.length() == 0 ? null : encoded.toString();
    }

    /** Decodes a board. Unparseable entries are skipped rather than failing the whole restore. */
    public static List<RouletteBet> decodeSelections(String selectionKey) {
        List<RouletteBet> bets = new ArrayList<>();
        if (selectionKey == null || selectionKey.isBlank()) {
            return bets;
        }
        for (String entry : selectionKey.split(";")) {
            RouletteBet bet = decodeSelection(entry);
            if (bet != null && !bets.contains(bet)) {
                bets.add(bet);
            }
        }
        return bets;
    }

    /** Decodes one {@code kind:number} pair. */
    public static RouletteBet decodeSelection(String entry) {
        if (entry == null || entry.isBlank()) {
            return null;
        }
        String[] parts = entry.trim().split(":", 2);
        RouletteBet.Kind kind = RouletteBet.Kind.fromString(parts[0]);
        if (kind == null) {
            return null;
        }
        int number = -1;
        if (parts.length > 1) {
            try {
                number = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return RouletteBet.of(kind, number);
    }
}
