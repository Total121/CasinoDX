package com.dravorax.casinodx.game.mines;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Mines — reveal safe tiles for a rising multiplier, cash out before hitting a mine.
 *
 * <h2>Multiplier mathematics</h2>
 * Mine positions are drawn once when the round starts, so every tile the player has not yet touched
 * is equally likely to be a mine. With {@code N} tiles, {@code M} mines and {@code S} safe reveals,
 * the probability of getting that far is
 * <pre>
 *   P(S) = C(N-M, S) / C(N, S)
 * </pre>
 * A zero-edge (fair) payout is therefore {@code 1 / P(S)}, and the house takes its cut by scaling
 * that down:
 * <pre>
 *   multiplier(S) = (1 - houseEdge) * C(N, S) / C(N-M, S)
 * </pre>
 * This is exact rather than an approximation, which means the expected return of cashing out after
 * any number of reveals is identically {@code 1 - houseEdge}. There is no "quota" or forced-loss
 * logic anywhere: the only lever is the published house edge.
 *
 * <p>Server operators may override individual steps with a {@code multipliers} section in
 * {@code modules/mines/payouts.yml}; anything not listed falls back to the formula above.
 */
public final class MinesGame extends CasinoGame {

    /** A 54-slot inventory can display at most a 5×5 board alongside its side panel. */
    public static final int MAX_GRID_SIZE = 5;
    public static final int MIN_GRID_SIZE = 3;

    private int gridSize;
    private int mineCount;
    private Map<Integer, Double> multiplierOverrides = Map.of();

    public MinesGame(CasinoDX plugin) {
        super(plugin, GameType.MINES);
        reload();
    }

    @Override
    public void reload() {
        int configured = config().getInt("grid-size", 5);
        this.gridSize = Math.max(MIN_GRID_SIZE, Math.min(MAX_GRID_SIZE, configured));
        if (configured != gridSize) {
            plugin.getLogger().warning("[CasinoDX] mines grid-size " + configured
                    + " does not fit a 54-slot menu; using " + gridSize + ".");
        }

        int tiles = gridSize * gridSize;
        int configuredMines = config().getInt("mine-count", 3);
        // At least one mine, and at least one safe tile, otherwise the round is unplayable.
        this.mineCount = Math.max(1, Math.min(tiles - 1, configuredMines));

        Map<Integer, Double> overrides = new HashMap<>();
        ConfigurationSection section = payouts().getConfigurationSection("multipliers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    int reveals = Integer.parseInt(key);
                    double value = section.getDouble(key, -1);
                    if (reveals > 0 && value > 0) {
                        overrides.put(reveals, value);
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("[CasinoDX] Ignoring non-numeric mines multiplier key '" + key + "'.");
                }
            }
        }
        this.multiplierOverrides = Map.copyOf(overrides);
    }

    public int gridSize() {
        return gridSize;
    }

    public int tileCount() {
        return gridSize * gridSize;
    }

    public int mineCount() {
        return mineCount;
    }

    public int safeTileCount() {
        return tileCount() - mineCount;
    }

    /**
     * Gross multiplier after {@code safeReveals} successful picks.
     *
     * @return {@code 1.0} for zero reveals (cashing out immediately just returns the stake)
     */
    public double multiplierFor(int safeReveals) {
        if (safeReveals <= 0) {
            return 1.0;
        }
        int safeTiles = safeTileCount();
        int clamped = Math.min(safeReveals, safeTiles);

        Double override = multiplierOverrides.get(clamped);
        if (override != null) {
            return override;
        }

        return MinesMath.multiplier(tileCount(), mineCount, clamped, houseEdge());
    }

    /** Potential gross return if the player cashed out at {@code safeReveals}. */
    public Money potentialReturn(Money bet, int safeReveals) {
        return bet.multiply(multiplierFor(safeReveals));
    }

    @Override
    public void startSession(Player player, Money bet) {
        MinesSession session = beginSession(player, bet,
                wager -> new MinesSession(plugin, player, this, wager));
        if (session == null) {
            return;
        }
        session.openBoard();
    }
}
