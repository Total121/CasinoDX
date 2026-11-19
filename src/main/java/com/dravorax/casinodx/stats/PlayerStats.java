package com.dravorax.casinodx.stats;

import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mutable in-memory player statistics snapshot.
 */
public final class PlayerStats {

    public record GameStatLine(long played, long won, Money wagered, Money profit) {
        public GameStatLine {
            wagered = wagered == null ? Money.ZERO : wagered;
            profit = profit == null ? Money.ZERO : profit;
        }
    }

    private final UUID uuid;
    private long gamesPlayed;
    private long wins;
    private long losses;
    private long pushes;
    private Money totalWagered = Money.ZERO;
    private Money totalWinnings = Money.ZERO;
    private Money totalLossesAmount = Money.ZERO;
    private Money netProfit = Money.ZERO;
    private Money largestWin = Money.ZERO;
    private Money largestLoss = Money.ZERO;
    private double highestMultiplier;
    private Money taxPaid = Money.ZERO;
    private GameType favoriteGame;
    private final Map<GameType, GameStatLine> perGame = new EnumMap<>(GameType.class);
    private volatile boolean dirty;

    public PlayerStats(UUID uuid) {
        this.uuid = uuid;
        for (GameType type : GameType.values()) {
            perGame.put(type, new GameStatLine(0, 0, Money.ZERO, Money.ZERO));
        }
    }

    public UUID uuid() {
        return uuid;
    }

    public long gamesPlayed() {
        return gamesPlayed;
    }

    public long gamesWon() {
        return wins;
    }

    public long wins() {
        return wins;
    }

    public long losses() {
        return losses;
    }

    public long pushes() {
        return pushes;
    }

    public double winRate() {
        if (gamesPlayed == 0) {
            return 0;
        }
        return (wins * 100.0) / gamesPlayed;
    }

    public Money totalWagered() {
        return totalWagered;
    }

    public Money totalWon() {
        return totalWinnings;
    }

    public Money totalWinnings() {
        return totalWinnings;
    }

    public Money totalLost() {
        return totalLossesAmount;
    }

    public Money totalLossesAmount() {
        return totalLossesAmount;
    }

    public Money netProfit() {
        return netProfit;
    }

    public Money largestWin() {
        return largestWin;
    }

    public Money largestLoss() {
        return largestLoss;
    }

    public double highestMultiplier() {
        return highestMultiplier;
    }

    public Money taxPaid() {
        return taxPaid;
    }

    public GameType favoriteGame() {
        return favoriteGame;
    }

    public GameStatLine forGame(GameType game) {
        return perGame.getOrDefault(game, new GameStatLine(0, 0, Money.ZERO, Money.ZERO));
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public void recordRound(GameType game, Money bet, Money netReturn, Money tax, double multiplier, boolean win, boolean push) {
        gamesPlayed++;
        totalWagered = totalWagered.add(bet);
        taxPaid = taxPaid.add(tax);

        if (multiplier > highestMultiplier) {
            highestMultiplier = multiplier;
        }

        Money roundNet = netReturn.subtract(bet);
        netProfit = netProfit.add(roundNet);

        if (push) {
            pushes++;
        } else if (win) {
            wins++;
            Money profit = netReturn.subtract(bet);
            totalWinnings = totalWinnings.add(netReturn);
            if (profit.compareTo(largestWin) > 0) {
                largestWin = profit;
            }
        } else {
            losses++;
            totalLossesAmount = totalLossesAmount.add(bet);
            if (bet.compareTo(largestLoss) > 0) {
                largestLoss = bet;
            }
        }

        if (game != null) {
            GameStatLine line = perGame.getOrDefault(game, new GameStatLine(0, 0, Money.ZERO, Money.ZERO));
            perGame.put(game, new GameStatLine(
                    line.played() + 1,
                    line.won() + (win ? 1 : 0),
                    line.wagered().add(bet),
                    line.profit().add(roundNet)
            ));
            favoriteGame = perGame.entrySet().stream()
                    .max(Map.Entry.comparingByValue((a, b) -> Long.compare(a.played(), b.played())))
                    .map(Map.Entry::getKey)
                    .orElse(game);
        }

        dirty = true;
    }

    public void applyLoaded(
            long gamesPlayed,
            long wins,
            long losses,
            long pushes,
            Money totalWagered,
            Money totalWinnings,
            Money totalLossesAmount,
            Money netProfit,
            Money largestWin,
            Money largestLoss,
            double highestMultiplier,
            Money taxPaid,
            GameType favoriteGame
    ) {
        this.gamesPlayed = gamesPlayed;
        this.wins = wins;
        this.losses = losses;
        this.pushes = pushes;
        this.totalWagered = totalWagered == null ? Money.ZERO : totalWagered;
        this.totalWinnings = totalWinnings == null ? Money.ZERO : totalWinnings;
        this.totalLossesAmount = totalLossesAmount == null ? Money.ZERO : totalLossesAmount;
        this.netProfit = netProfit == null ? Money.ZERO : netProfit;
        this.largestWin = largestWin == null ? Money.ZERO : largestWin;
        this.largestLoss = largestLoss == null ? Money.ZERO : largestLoss;
        this.highestMultiplier = highestMultiplier;
        this.taxPaid = taxPaid == null ? Money.ZERO : taxPaid;
        this.favoriteGame = favoriteGame;
        this.dirty = false;
    }
}
