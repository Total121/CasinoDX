package com.dravorax.casinodx.game.slots;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Slots — spin three independent weighted reels and get paid for matching symbols.
 *
 * <h2>How a spin is decided</h2>
 * Each reel is an independent weighted draw from the symbol list in
 * {@code modules/slots/payouts.yml}. For a symbol {@code s} with weight {@code w_s} and a total
 * weight {@code W}, the chance of {@code s} landing on any one reel is
 * <pre>
 *   p_s = w_s / W
 * </pre>
 * All {@code n} reels are drawn in {@link #spinReels()} <em>before</em>
 * {@link SlotsSpinAnimation} is created, so the animation is a replay of a decision the server has
 * already committed to. Nothing about tick timing, lag, or a cancelled animation can move a reel.
 *
 * <h2>Where the house edge lives</h2>
 * The reels are not biased against the player beyond the published weights; the edge is the gap
 * between what the paytable pays and what the outcome probabilities are worth. Because the reels
 * are independent:
 * <pre>
 *   P(all n reels show s)        = p_s^n
 *   P(exactly n-1 reels show s)  = n * p_s^(n-1) * (1 - p_s)
 * </pre>
 * so the return to player is the sum over every symbol of what each of those events pays:
 * <pre>
 *   RTP = Σ_s [ p_s^n * threeMatch_s  +  n * p_s^(n-1) * (1 - p_s) * twoMatch_s ]
 *   houseEdge = 1 - RTP
 * </pre>
 * {@link #theoreticalRtp()} evaluates exactly that expression against the live config, which is
 * what the GUI and the {@code /casino reload} log line report. The shipped defaults come out at
 * roughly 94% RTP.
 *
 * <p>With {@code scale-to-house-edge: true} the plugin multiplies every paytable entry by a single
 * factor so the measured RTP lands on {@code 1 - house-edge}. That is a convenience for operators
 * who want to set a target and stop doing arithmetic; with the default {@code false} the numbers in
 * {@code payouts.yml} are paid exactly as written.
 */
public final class SlotsGame extends CasinoGame {

    /**
     * Reels on the machine. Three is the classic layout and the default; the GUI supports up to
     * five because that is what fits inside one framed row of a 54-slot menu.
     */
    private static final int MIN_REELS = 3;
    private static final int MAX_REELS = 5;

    private List<SlotsSymbol> symbols = List.of();
    private int reels = MIN_REELS;
    private int spinTicks = 60;
    private double payoutScale = 1.0;

    public SlotsGame(CasinoDX plugin) {
        super(plugin, GameType.SLOTS);
        reload();
    }

    @Override
    public void reload() {
        this.reels = Math.max(MIN_REELS, Math.min(MAX_REELS, config().getInt("reels", MIN_REELS)));
        this.spinTicks = Math.max(20, Math.min(200, config().getInt("spin-ticks", 60)));
        this.payoutScale = 1.0;
        this.symbols = loadSymbols();

        if (config().getBoolean("scale-to-house-edge", false)) {
            double raw = theoreticalRtp();
            double target = 1.0 - houseEdge();
            // A zero raw RTP means every multiplier is zero, which no scale factor can fix.
            this.payoutScale = raw > 0 ? target / raw : 1.0;
            plugin.getLogger().info(String.format(Locale.US,
                    "[CasinoDX] slots payouts scaled by %.4f to hit a %.2f%% house edge.",
                    payoutScale, houseEdge() * 100.0));
        }
    }

    private List<SlotsSymbol> loadSymbols() {
        ConfigurationSection section = payouts().getConfigurationSection("symbols");
        if (section == null) {
            plugin.getLogger().warning("[CasinoDX] modules/slots/payouts.yml has no 'symbols' section; "
                    + "using the built-in reel strip.");
            return fallbackSymbols();
        }

        List<SlotsSymbol> loaded = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            double weight = entry.getDouble("weight", 0);
            if (weight <= 0) {
                plugin.getLogger().warning("[CasinoDX] slots symbol '" + key
                        + "' has no positive weight and can never land; skipping it.");
                continue;
            }
            loaded.add(new SlotsSymbol(
                    key,
                    entry.getString("display-name", prettify(key)),
                    parseMaterial(entry.getString("material"), Material.PAPER),
                    weight,
                    Math.max(0, entry.getDouble("three-match", 0)),
                    Math.max(0, entry.getDouble("two-match", 0))
            ));
        }

        if (loaded.size() < 2) {
            plugin.getLogger().warning("[CasinoDX] modules/slots/payouts.yml needs at least two usable "
                    + "symbols; using the built-in reel strip.");
            return fallbackSymbols();
        }
        return List.copyOf(loaded);
    }

    /** Keeps the machine playable when the payout file is empty or unreadable. */
    private static List<SlotsSymbol> fallbackSymbols() {
        return List.of(
                new SlotsSymbol("coal", "Coal", Material.COAL, 30, 7, 0),
                new SlotsSymbol("apple", "Apple", Material.APPLE, 25, 12, 0),
                new SlotsSymbol("gold", "Gold", Material.GOLD_INGOT, 20, 22, 0),
                new SlotsSymbol("emerald", "Emerald", Material.EMERALD, 13, 58, 0),
                new SlotsSymbol("diamond", "Diamond", Material.DIAMOND, 8, 210, 0),
                new SlotsSymbol("star", "Star", Material.NETHER_STAR, 4, 1000, 20)
        );
    }

    private static String prettify(String key) {
        String spaced = key.replace('-', ' ').replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material != null && material.isItem() ? material : fallback;
    }

    public List<SlotsSymbol> symbols() {
        return symbols;
    }

    public int reels() {
        return reels;
    }

    public int spinTicks() {
        return spinTicks;
    }

    public double totalWeight() {
        double total = 0;
        for (SlotsSymbol symbol : symbols) {
            total += symbol.weight();
        }
        return total <= 0 ? 1 : total;
    }

    /** Chance of {@code symbol} landing on any one reel. */
    public double probabilityOf(SlotsSymbol symbol) {
        return symbol == null ? 0 : symbol.weight() / totalWeight();
    }

    /** Gross multiplier paid for a full match of {@code symbol}, after any house-edge scaling. */
    public double fullMatchMultiplier(SlotsSymbol symbol) {
        return symbol == null ? 0 : symbol.threeMatch() * payoutScale;
    }

    /** Gross multiplier paid when all but one reel show {@code symbol}. */
    public double partialMatchMultiplier(SlotsSymbol symbol) {
        return symbol == null ? 0 : symbol.twoMatch() * payoutScale;
    }

    /** Chance that a spin pays anything at all, for display on the machine. */
    public double hitChance() {
        double total = totalWeight();
        double chance = 0;
        for (SlotsSymbol symbol : symbols) {
            double p = symbol.weight() / total;
            if (fullMatchMultiplier(symbol) > 0) {
                chance += Math.pow(p, reels);
            }
            if (partialMatchMultiplier(symbol) > 0) {
                chance += reels * Math.pow(p, reels - 1) * (1 - p);
            }
        }
        return chance;
    }

    /**
     * Return to player implied by the live weights and paytable. See the class documentation for
     * the derivation.
     */
    public double theoreticalRtp() {
        double total = totalWeight();
        double rtp = 0;
        for (SlotsSymbol symbol : symbols) {
            double p = symbol.weight() / total;
            rtp += Math.pow(p, reels) * symbol.threeMatch() * payoutScale;
            if (symbol.paysPartial()) {
                rtp += reels * Math.pow(p, reels - 1) * (1 - p) * symbol.twoMatch() * payoutScale;
            }
        }
        return rtp;
    }

    /** Edge implied by the live paytable, as a fraction. Negative means the paytable overpays. */
    public double effectiveHouseEdge() {
        return 1.0 - theoreticalRtp();
    }

    /** Highest multiplier on the machine, used to advertise the jackpot. */
    public double topMultiplier() {
        double top = 0;
        for (SlotsSymbol symbol : symbols) {
            top = Math.max(top, fullMatchMultiplier(symbol));
        }
        return top;
    }

    /** The symbol the jackpot belongs to, or {@code null} when the paytable pays nothing. */
    public SlotsSymbol topSymbol() {
        SlotsSymbol best = null;
        for (SlotsSymbol symbol : symbols) {
            if (best == null || fullMatchMultiplier(symbol) > fullMatchMultiplier(best)) {
                best = symbol;
            }
        }
        return best;
    }

    /**
     * Draws one independent weighted symbol per reel. Called exactly once per round, before the
     * animation exists.
     */
    SlotsSymbol[] spinReels() {
        SlotsSymbol[] result = new SlotsSymbol[reels];
        for (int i = 0; i < reels; i++) {
            result[i] = drawSymbol();
        }
        return result;
    }

    private SlotsSymbol drawSymbol() {
        double roll = randomDouble() * totalWeight();
        double cumulative = 0;
        for (SlotsSymbol symbol : symbols) {
            cumulative += symbol.weight();
            if (roll < cumulative) {
                return symbol;
            }
        }
        // Floating-point drift only; the last symbol absorbs it.
        return symbols.get(symbols.size() - 1);
    }

    /**
     * What a set of reel stops is worth.
     *
     * @param symbol     the symbol the win is built on, or the most common symbol on a losing spin
     * @param matches    how many reels show {@code symbol}
     * @param multiplier gross multiplier, stake included; {@code 0} on a loss
     * @param label      short human-readable summary for messages and history
     */
    public record Outcome(SlotsSymbol symbol, int matches, double multiplier, String label) {

        public boolean isWin() {
            return multiplier > 0;
        }
    }

    /**
     * Scores a finished spin. A full match pays {@code three-match}; one reel short of a full match
     * pays {@code two-match} when the symbol defines one. Anything else pays nothing.
     */
    public Outcome evaluate(SlotsSymbol[] result) {
        if (result == null || result.length == 0) {
            return new Outcome(null, 0, 0, "No spin");
        }

        SlotsSymbol best = result[0];
        int bestCount = 0;
        for (SlotsSymbol candidate : result) {
            int count = 0;
            for (SlotsSymbol other : result) {
                if (other != null && candidate != null && other.key().equals(candidate.key())) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = candidate;
            }
        }

        int total = result.length;
        double multiplier = 0;
        if (bestCount >= total) {
            multiplier = fullMatchMultiplier(best);
        } else if (bestCount == total - 1) {
            multiplier = partialMatchMultiplier(best);
        }

        String name = best == null ? "?" : best.displayName();
        String label = multiplier > 0
                ? bestCount + "x " + name
                : "No match (" + bestCount + "x " + name + ")";
        return new Outcome(best, bestCount, multiplier, label);
    }

    public Money grossReturn(Money bet, double multiplier) {
        return bet.multiply(multiplier);
    }

    @Override
    public void startSession(Player player, Money bet) {
        openMachine(player, bet);
    }

    /**
     * Opens the cabinet with the reels idle and the requested stake loaded.
     *
     * <p>No money moves here. The machine is a place to sit rather than a round, so each press of
     * SPIN escrows its own wager through {@link #beginSpin} and settles it before the player is
     * handed the controls back. That is what lets the stake be changed between spins without a
     * refund, and what makes walking away from an idle machine free.
     */
    public void openMachine(Player player, Money bet) {
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
        if (plugin.sessions().has(player.getUniqueId())) {
            plugin.messages().send(player, "already-in-game");
            return;
        }
        plugin.guis().open(new SlotsGui(plugin, player, this, clampBet(bet)));
    }

    /** Clamps a requested stake into this machine's configured window. */
    Money clampBet(Money bet) {
        Money min = minBet();
        Money max = maxBet();
        Money value = bet == null ? min : bet;
        if (value.lessThan(min)) {
            value = min;
        }
        if (value.greaterThan(max)) {
            value = max;
        }
        return value;
    }

    /**
     * Escrows one spin for an already-open machine.
     *
     * <p>The returned session is registered and holding the wager but has not drawn anything yet;
     * the caller binds it and then calls {@link SlotsSession#spin()}. A {@code null} return means
     * the wager could not be taken and {@link #beginSession} has already told the player why.
     */
    SlotsSession beginSpin(Player player, Money bet, SlotsGui machine) {
        SlotsSession session = beginSession(player, bet,
                wager -> new SlotsSession(plugin, player, this, wager));
        if (session != null) {
            session.attach(machine);
        }
        return session;
    }
}
