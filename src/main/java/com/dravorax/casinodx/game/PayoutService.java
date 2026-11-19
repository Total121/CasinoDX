package com.dravorax.casinodx.game;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.history.HistoryEntry;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import com.dravorax.casinodx.tax.TaxResult;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The only place in CasinoDX that pays a player. Every game funnels its outcome through here.
 *
 * <p>Each method is idempotent per session: {@link GameSession#claimSettlement()} is a synchronized
 * compare-and-set, so the second and later calls for the same session return {@code false} and no
 * money moves. The wager itself was already withdrawn when the session started, which means a win
 * deposits the <em>gross return</em> (stake + profit) rather than just the profit, and a loss
 * deposits nothing at all.
 */
public final class PayoutService {

    private PayoutService() {}

    /**
     * General settlement for a round whose gross return can be anything from zero to a multiple of
     * the wager. Needed by blackjack, where a split can win one hand and lose the other and leave
     * the player with, say, half their total stake back.
     *
     * <p>Dispatches to {@link #settleWin}, {@link #settlePush} or {@link #settleLoss} for the three
     * clean cases, and handles the partial return itself: the money comes back untaxed (there is no
     * profit to tax) and the round is booked as the net loss it is.
     *
     * @return {@code true} when this call was the one that resolved the round
     */
    public static boolean settleReturn(CasinoDX plugin, Player player, GameSession game, Money bet,
                                       Money grossReturn, String resultLabel) {
        if (plugin == null || player == null || game == null) {
            return false;
        }
        Money safeBet = bet == null ? Money.ZERO : bet;
        Money safeGross = grossReturn == null ? Money.ZERO : grossReturn.max(Money.ZERO);
        double multiplier = safeBet.isPositive() ? safeGross.toDouble() / safeBet.toDouble() : 0.0;

        if (safeGross.greaterThan(safeBet)) {
            return settleWin(plugin, player, game, safeBet, safeGross, multiplier, resultLabel);
        }
        if (safeGross.equals(safeBet)) {
            return settlePush(plugin, player, game, safeBet, resultLabel);
        }
        if (!safeGross.isPositive()) {
            return settleLoss(plugin, player, game, safeBet, 0.0, resultLabel);
        }
        return settlePartial(plugin, player, game, safeBet, safeGross, multiplier, resultLabel);
    }

    /** Returns less than the wager: a net loss that still hands some money back. */
    private static boolean settlePartial(CasinoDX plugin, Player player, GameSession game, Money bet,
                                         Money grossReturn, double multiplier, String resultLabel) {
        if (!game.claimSettlement()) {
            return false;
        }
        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            plugin.economy().refund(player, bet);
            game.markResolved();
            return false;
        }
        try {
            if (!plugin.economy().deposit(player, grossReturn)) {
                plugin.economy().refund(player, bet);
                plugin.messages().send(player, "payout-failed");
                plugin.getLogger().warning("[CasinoDX] Partial payout failed for " + player.getName()
                        + " on " + game.type().displayName() + "; wager refunded.");
                return false;
            }

            plugin.statistics().recordRound(player.getUniqueId(), game.type(), bet, grossReturn,
                    Money.ZERO, multiplier, false, false);
            plugin.history().recordLoss(player, game.type(), bet, multiplier);

            plugin.messages().send(player, "loss",
                    "amount", plugin.economy().format(bet.subtract(grossReturn)),
                    "result", resultLabel == null ? "" : resultLabel,
                    "game", game.type().displayName());
            plugin.sounds().play(player, SoundService.LOSS);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Partial settlement error for " + player.getName()
                    + " on " + game.type().displayName() + ": " + t);
            plugin.economy().refund(player, bet);
            plugin.messages().send(player, "payout-failed");
            return false;
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
            game.markResolved();
        }
    }

    /**
     * Offline variant of {@link #settleReturn} for predetermined outcomes (e.g. blackjack dealer
     * turn interrupted by disconnect). Routes win/push/loss/partial with tax where applicable.
     */
    public static boolean settleReturnOffline(CasinoDX plugin, UUID playerId, GameSession game,
                                              Money bet, Money grossReturn) {
        if (plugin == null || playerId == null || game == null) {
            return false;
        }
        Money safeBet = bet == null ? Money.ZERO : bet;
        Money safeGross = grossReturn == null ? Money.ZERO : grossReturn.max(Money.ZERO);
        double multiplier = safeBet.isPositive() ? safeGross.toDouble() / safeBet.toDouble() : 0.0;

        if (safeGross.greaterThan(safeBet)) {
            return settleWinOffline(plugin, playerId, game, safeBet, safeGross, multiplier);
        }
        if (safeGross.equals(safeBet)) {
            return settlePushOffline(plugin, playerId, game, safeBet);
        }
        if (!safeGross.isPositive()) {
            return settleLossOffline(plugin, playerId, game, safeBet, 0.0);
        }
        return settlePartialOffline(plugin, playerId, game, safeBet, safeGross, multiplier);
    }

    private static boolean settlePushOffline(CasinoDX plugin, UUID playerId, GameSession game, Money bet) {
        if (!game.claimSettlement()) {
            return false;
        }
        if (!plugin.antiExploit().tryBeginTransaction(playerId)) {
            plugin.economy().refund(Bukkit.getOfflinePlayer(playerId), bet);
            game.markResolved();
            return false;
        }
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            if (bet.isPositive() && !plugin.economy().deposit(offline, bet)) {
                plugin.getLogger().warning("[CasinoDX] Offline push refund failed for " + playerId);
                return false;
            }
            plugin.statistics().recordRound(playerId, game.type(), bet, bet, Money.ZERO, 1.0, false, true);
            plugin.history().record(playerId, game.type(), bet, HistoryEntry.Result.PUSH, 1.0,
                    new TaxResult(bet, Money.ZERO, bet, bet, Money.ZERO));
            plugin.getLogger().info("[CasinoDX] Offline push refunded "
                    + plugin.economy().format(bet) + " to " + playerId + ".");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Offline push error for " + playerId + ": " + t);
            plugin.economy().refund(Bukkit.getOfflinePlayer(playerId), bet);
            return false;
        } finally {
            plugin.antiExploit().endTransaction(playerId);
            game.markResolved();
        }
    }

    private static boolean settleLossOffline(CasinoDX plugin, UUID playerId, GameSession game,
                                             Money bet, double multiplier) {
        if (!game.claimSettlement()) {
            return false;
        }
        try {
            plugin.statistics().recordRound(playerId, game.type(), bet, Money.ZERO, Money.ZERO,
                    multiplier, false, false);
            plugin.history().record(playerId, game.type(), bet, HistoryEntry.Result.LOSS, multiplier,
                    new TaxResult(Money.ZERO, Money.ZERO, Money.ZERO, bet, bet.negate()));
            plugin.getLogger().info("[CasinoDX] Offline loss recorded for " + playerId
                    + " on " + game.type().displayName() + ".");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Offline loss bookkeeping error for " + playerId + ": " + t);
            return false;
        } finally {
            game.markResolved();
        }
    }

    private static boolean settlePartialOffline(CasinoDX plugin, UUID playerId, GameSession game,
                                                Money bet, Money grossReturn, double multiplier) {
        if (!game.claimSettlement()) {
            return false;
        }
        if (!plugin.antiExploit().tryBeginTransaction(playerId)) {
            plugin.economy().refund(Bukkit.getOfflinePlayer(playerId), bet);
            game.markResolved();
            return false;
        }
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            if (!plugin.economy().deposit(offline, grossReturn)) {
                plugin.economy().refund(offline, bet);
                plugin.getLogger().warning("[CasinoDX] Offline partial payout failed for " + playerId);
                return false;
            }
            plugin.statistics().recordRound(playerId, game.type(), bet, grossReturn, Money.ZERO,
                    multiplier, false, false);
            plugin.history().record(playerId, game.type(), bet, HistoryEntry.Result.LOSS, multiplier,
                    new TaxResult(grossReturn, Money.ZERO, grossReturn, bet, grossReturn.subtract(bet)));
            plugin.getLogger().info("[CasinoDX] Offline partial return of "
                    + plugin.economy().format(grossReturn) + " paid to " + playerId + ".");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Offline partial error for " + playerId + ": " + t);
            plugin.economy().refund(Bukkit.getOfflinePlayer(playerId), bet);
            return false;
        } finally {
            plugin.antiExploit().endTransaction(playerId);
            game.markResolved();
        }
    }

    /**
     * Pays a predetermined win to an offline account (disconnect mid-animation). Applies tax the
     * same way as an online win so disconnecting cannot skip the house cut.
     */
    public static boolean settleWinOffline(CasinoDX plugin, UUID playerId, GameSession game, Money bet,
                                           Money grossReturn, double multiplier) {
        if (plugin == null || playerId == null || game == null) {
            return false;
        }
        Money safeBet = bet == null ? Money.ZERO : bet;
        Money safeGross = grossReturn == null ? Money.ZERO : grossReturn.max(Money.ZERO);

        if (!game.claimSettlement()) {
            return false;
        }

        if (!plugin.antiExploit().tryBeginTransaction(playerId)) {
            plugin.economy().refund(Bukkit.getOfflinePlayer(playerId), safeBet);
            game.markResolved();
            return false;
        }

        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            TaxResult tax = plugin.tax().computeOffline(offline, game.type(), safeBet, safeGross);
            Money net = tax.netPayout();

            boolean deposited = !net.isPositive() || plugin.economy().deposit(offline, net);
            if (!deposited) {
                plugin.economy().refund(offline, safeBet);
                plugin.getLogger().warning("[CasinoDX] Offline deposit failed for " + playerId
                        + " on " + game.type().displayName() + "; wager refunded.");
                return false;
            }

            plugin.statistics().recordRound(playerId, game.type(), safeBet, net,
                    tax.taxAmount(), multiplier, true, false);
            plugin.history().recordWinOffline(playerId, game.type(), safeBet, tax, multiplier);

            plugin.getLogger().info("[CasinoDX] Paid offline " + game.type().displayName()
                    + " win of " + plugin.economy().format(net)
                    + (tax.taxed() ? " (tax " + plugin.economy().format(tax.taxAmount()) + ")" : "")
                    + " to " + playerId + ".");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Offline payout error for " + playerId
                    + " on " + game.type().displayName() + ": " + t);
            plugin.economy().refund(Bukkit.getOfflinePlayer(playerId), safeBet);
            return false;
        } finally {
            plugin.antiExploit().endTransaction(playerId);
            game.markResolved();
        }
    }

    /**
     * Pays a winning round.
     *
     * @param bet          the escrowed wager, used for tax and statistics
     * @param grossReturn  total amount returned to the player, stake included
     * @param multiplier   gross multiple of the stake, for display and history
     * @param resultLabel  short human-readable outcome, e.g. {@code "Blackjack"} or {@code "2.84x"}
     * @return {@code true} when this call was the one that actually paid
     */
    public static boolean settleWin(CasinoDX plugin, Player player, GameSession game, Money bet,
                                    Money grossReturn, double multiplier, String resultLabel) {
        if (plugin == null || player == null || game == null) {
            return false;
        }
        Money safeBet = bet == null ? Money.ZERO : bet;
        Money safeGross = grossReturn == null ? Money.ZERO : grossReturn.max(Money.ZERO);

        if (!game.claimSettlement()) {
            return false;
        }

        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            // Another economy operation for this player is in flight. The settlement is already
            // claimed, so refund the stake rather than risk an interleaved double deposit.
            plugin.economy().refund(player, safeBet);
            game.markResolved();
            return false;
        }

        try {
            TaxResult tax = plugin.tax().compute(player, game.type(), safeBet, safeGross);
            Money net = tax.netPayout();

            boolean deposited = !net.isPositive() || plugin.economy().deposit(player, net);
            if (!deposited) {
                plugin.economy().refund(player, safeBet);
                plugin.messages().send(player, "payout-failed");
                plugin.getLogger().warning("[CasinoDX] Deposit failed for " + player.getName()
                        + " on " + game.type().displayName() + "; wager refunded.");
                return false;
            }

            plugin.statistics().recordRound(player.getUniqueId(), game.type(), safeBet, net,
                    tax.taxAmount(), multiplier, true, false);
            plugin.history().recordWin(player, game.type(), safeBet, tax, multiplier);

            plugin.messages().send(player, "win",
                    "amount", plugin.economy().format(net),
                    "multiplier", Text.formatMultiplier(multiplier),
                    "result", resultLabel == null ? "" : resultLabel,
                    "game", game.type().displayName());
            if (tax.taxed()) {
                plugin.messages().send(player, "tax-applied",
                        "amount", plugin.economy().format(tax.taxAmount()));
            }
            plugin.sounds().play(player, SoundService.WIN);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Payout error for " + player.getName()
                    + " on " + game.type().displayName() + ": " + t);
            plugin.economy().refund(player, safeBet);
            plugin.messages().send(player, "payout-failed");
            return false;
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
            game.markResolved();
        }
    }

    /**
     * Records a losing round. No money moves — the wager was withdrawn when the session started.
     *
     * @return {@code true} when this call was the one that closed the round
     */
    public static boolean settleLoss(CasinoDX plugin, Player player, GameSession game, Money bet,
                                     double multiplier, String resultLabel) {
        if (plugin == null || player == null || game == null) {
            return false;
        }
        Money safeBet = bet == null ? Money.ZERO : bet;

        if (!game.claimSettlement()) {
            return false;
        }

        try {
            plugin.statistics().recordRound(player.getUniqueId(), game.type(), safeBet, Money.ZERO,
                    Money.ZERO, multiplier, false, false);
            plugin.history().recordLoss(player, game.type(), safeBet, multiplier);

            // A zero wager has nothing to report; the free wheel spin relies on this.
            if (safeBet.isPositive()) {
                plugin.messages().send(player, "loss",
                        "amount", plugin.economy().format(safeBet),
                        "result", resultLabel == null ? "" : resultLabel,
                        "game", game.type().displayName());
                plugin.sounds().play(player, SoundService.LOSS);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Loss bookkeeping error for " + player.getName()
                    + " on " + game.type().displayName() + ": " + t);
            return false;
        } finally {
            game.markResolved();
        }
    }

    /**
     * Returns the wager untouched and untaxed (blackjack push, cancelled roulette spin, and so on).
     *
     * @return {@code true} when this call was the one that refunded
     */
    public static boolean settlePush(CasinoDX plugin, Player player, GameSession game, Money bet,
                                     String resultLabel) {
        if (plugin == null || player == null || game == null) {
            return false;
        }
        Money safeBet = bet == null ? Money.ZERO : bet;

        if (!game.claimSettlement()) {
            return false;
        }

        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            plugin.economy().refund(player, safeBet);
            game.markResolved();
            return false;
        }

        try {
            if (safeBet.isPositive() && !plugin.economy().deposit(player, safeBet)) {
                plugin.economy().refund(player, safeBet);
                plugin.messages().send(player, "payout-failed");
                plugin.getLogger().warning("[CasinoDX] Push refund failed for " + player.getName()
                        + " on " + game.type().displayName() + "; wager refunded.");
                return false;
            }

            plugin.statistics().recordRound(player.getUniqueId(), game.type(), safeBet, safeBet,
                    Money.ZERO, 1.0, false, true);
            plugin.history().recordPush(player, game.type(), safeBet);

            if (safeBet.isPositive()) {
                plugin.messages().send(player, "push",
                        "amount", plugin.economy().format(safeBet),
                        "result", resultLabel == null ? "" : resultLabel,
                        "game", game.type().displayName());
                plugin.sounds().play(player, SoundService.CASHOUT);
            }
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Push error for " + player.getName()
                    + " on " + game.type().displayName() + ": " + t);
            plugin.economy().refund(player, safeBet);
            plugin.messages().send(player, "payout-failed");
            return false;
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
            game.markResolved();
        }
    }
}
