package com.dravorax.casinodx.game.roulette;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One trip to the roulette table.
 *
 * <p>Unlike the other games, nothing is escrowed when the table opens: the session starts on a
 * {@link Money#ZERO} wager and the player spends as long as they like laying chips out. Every cell
 * they click toggles one chip of {@link #chipSize()} on or off, so the amount at risk is
 * {@code chip x chips on the table} and it is only withdrawn when {@link #spin()} is pressed. A
 * player who walks away before spinning has not paid anything.
 *
 * <p>{@link #spin()} takes the money, draws the pocket immediately, and only then starts the
 * animation, so the visual is a replay and interaction is locked for its duration. Each chip is
 * settled independently against that one pocket and the whole round resolves through a single
 * {@link PayoutService#settleReturn} call, which is what keeps the money contract
 * (one escrow, one settlement) intact across a dozen simultaneous bets.
 */
public final class RouletteSession extends GameSession {

    private final RouletteGame game;

    /** Chips on the felt, in the order they were placed. Never contains duplicates. */
    private final List<RouletteBet> bets = new ArrayList<>();

    private Money chipSize;

    /** The betting table itself, which is where the spin animation draws. */
    private RouletteGui table;

    /** Whichever roulette screen the player is looking at, so refreshes land on it. */
    private GuiView activeView;

    /** {@code -1} until the pocket has been drawn. */
    private int winningPocket = -1;
    private boolean spinning;

    RouletteSession(CasinoDX plugin, Player player, RouletteGame game, Money chipSize) {
        super(plugin, player, GameType.ROULETTE, Money.ZERO);
        this.game = game;
        this.chipSize = game.clampChip(chipSize);
    }

    public RouletteGame game() {
        return game;
    }

    // --- Chip size ------------------------------------------------------------------

    /** The unit stake. One of these is risked per chip on the table. */
    public Money chipSize() {
        return chipSize;
    }

    /** Sets the unit stake, clamped to the table limits. Free until the wheel is spun. */
    public void setChipSize(Money amount) {
        if (!acceptsInput() || amount == null) {
            return;
        }
        Money clamped = game.clampChip(amount);
        if (clamped.equals(chipSize)) {
            return;
        }
        this.chipSize = clamped;
        Player player = player();
        if (player != null) {
            plugin.sounds().play(player, SoundService.BET);
        }
        refreshView();
    }

    // --- Chips on the table ---------------------------------------------------------

    public List<RouletteBet> bets() {
        return Collections.unmodifiableList(bets);
    }

    public int betCount() {
        return bets.size();
    }

    public int maxBets() {
        return game.maxBetsPerRound();
    }

    public boolean hasBets() {
        return !bets.isEmpty();
    }

    public boolean hasBet(RouletteBet bet) {
        return bet != null && bets.contains(bet);
    }

    /**
     * Adds a chip to the clicked cell, or takes it back if one is already there.
     *
     * @return {@code true} when a chip was added
     */
    public boolean toggleBet(RouletteBet bet) {
        if (!acceptsInput() || spinning || bet == null) {
            return false;
        }
        Player player = player();
        if (bets.remove(bet)) {
            if (player != null) {
                plugin.sounds().play(player, SoundService.CLICK);
            }
            refreshView();
            return false;
        }
        if (bets.size() >= game.maxBetsPerRound()) {
            if (player != null) {
                plugin.messages().send(player, "roulette-board-full",
                        "count", String.valueOf(game.maxBetsPerRound()));
            }
            return false;
        }
        bets.add(bet);
        if (player != null) {
            plugin.sounds().play(player, SoundService.BET);
        }
        refreshView();
        return true;
    }

    /** Sweeps the felt. Free until the wheel is spun. */
    public void clearBets() {
        if (!acceptsInput() || spinning || bets.isEmpty()) {
            return;
        }
        bets.clear();
        Player player = player();
        if (player != null) {
            plugin.sounds().play(player, SoundService.CLICK);
        }
        refreshView();
    }

    /** Lays out chips carried over from a previous round. Silent — no sounds, no refresh. */
    void restoreBets(List<RouletteBet> restored) {
        if (restored == null) {
            return;
        }
        for (RouletteBet bet : restored) {
            if (bet == null || bets.contains(bet) || bets.size() >= game.maxBetsPerRound()) {
                continue;
            }
            bets.add(bet);
        }
    }

    // --- Money on the table ---------------------------------------------------------

    /** What pressing spin will cost: one chip per bet placed. */
    public Money totalWager() {
        return chipSize.multiply(bets.size());
    }

    /** Gross return this chip would pay if its cell wins, stake included. */
    public Money returnFor(RouletteBet bet) {
        if (bet == null) {
            return Money.ZERO;
        }
        return chipSize.multiply(game.multiplierFor(bet.kind()));
    }

    /** Total gross return the current board would pay if {@code pocket} came up. */
    public Money grossReturnFor(int pocket) {
        Money gross = Money.ZERO;
        for (RouletteBet bet : bets) {
            if (bet.wins(pocket)) {
                gross = gross.add(returnFor(bet));
            }
        }
        return gross;
    }

    /** The luckiest pocket for the current board — what "best case" means with spread bets. */
    public Money bestCaseReturn() {
        Money best = Money.ZERO;
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            best = best.max(grossReturnFor(pocket));
        }
        return best;
    }

    /** How many of the 37 pockets pay something back, so the player can see their coverage. */
    public int coveredPockets() {
        int covered = 0;
        for (int pocket = 0; pocket < RouletteWheel.POCKETS; pocket++) {
            if (grossReturnFor(pocket).isPositive()) {
                covered++;
            }
        }
        return covered;
    }

    // --- Round state ----------------------------------------------------------------

    public boolean spinning() {
        return spinning;
    }

    public int winningPocket() {
        return winningPocket;
    }

    public int winningBetCount() {
        if (winningPocket < 0) {
            return 0;
        }
        int won = 0;
        for (RouletteBet bet : bets) {
            if (bet.wins(winningPocket)) {
                won++;
            }
        }
        return won;
    }

    // --- Screens --------------------------------------------------------------------

    /** Opens (or reopens) the betting table and makes it the animation target. */
    public RouletteGui openTable() {
        Player player = player();
        if (player == null) {
            return table;
        }
        RouletteGui view = new RouletteGui(plugin, player, this);
        this.table = view;
        this.activeView = view;
        plugin.guis().open(view);
        return view;
    }

    /** Opens the 0-36 grid, which shares this session's chips. */
    public void openNumbers() {
        Player player = player();
        if (player == null) {
            return;
        }
        if (!acceptsInput()) {
            // The round moved on while the grid was away (a spin, or the round ended).
            openTable();
            return;
        }
        RouletteNumbersGui view = new RouletteNumbersGui(plugin, player, this);
        this.activeView = view;
        plugin.guis().open(view);
    }

    private void refreshView() {
        if (activeView != null) {
            activeView.refresh();
        }
    }

    /** The table has to be on screen for the spin animation to have somewhere to draw. */
    private RouletteGui ensureTable() {
        Player player = player();
        if (player == null) {
            return table;
        }
        if (table == null || plugin.guis().getView(player) != table) {
            return openTable();
        }
        return table;
    }

    // --- The spin -------------------------------------------------------------------

    /**
     * Takes the total wager, draws the winning pocket, and starts the visual spin. Rejected with an
     * empty board, an unaffordable total, or a spin already in flight.
     */
    public void spin() {
        if (!acceptsInput() || spinning) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }
        if (bets.isEmpty()) {
            plugin.messages().send(player, "roulette-no-bets");
            return;
        }

        Money total = totalWager();
        if (!total.isPositive() || !plugin.economy().has(player, total)) {
            plugin.messages().send(player, "insufficient-funds");
            return;
        }
        // The escrow this game never took up front is taken here, once, for every chip at once.
        if (!escrowWager(player, total)) {
            return;
        }

        // Outcome first, animation second — always in that order.
        this.winningPocket = game.drawPocket();
        this.spinning = true;
        state(State.ANIMATING);

        RouletteGui view = ensureTable();
        if (view != null) {
            view.refresh();
        }
        plugin.sounds().play(player, SoundService.ROULETTE_SPIN);
        plugin.animations().register(new RouletteSpinAnimation(player, this, view, winningPocket, game.spinTicks()));
    }

    /** Withdraws the whole board in one transaction and registers it as this session's escrow. */
    private boolean escrowWager(Player player, Money total) {
        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            plugin.messages().send(player, "already-in-game");
            return false;
        }
        try {
            if (!plugin.economy().withdraw(player, total)) {
                plugin.messages().send(player, "insufficient-funds");
                return false;
            }
            addEscrow(total);
            plugin.messages().send(player, "bet-placed", "amount", plugin.economy().format(total));
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("[CasinoDX] Roulette wager withdrawal failed for "
                    + player.getName() + ": " + t);
            plugin.messages().send(player, "payout-failed");
            return false;
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
        }
    }

    /** Called by {@link RouletteSpinAnimation} once the wheel has come to rest. */
    void onSpinFinished() {
        this.spinning = false;
        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left during the spin");
            return;
        }
        game.recordResult(winningPocket);

        Money staked = totalEscrow();
        Money gross = grossReturnFor(winningPocket);
        settleSpin(player);

        plugin.lastPlay().record(playerId(), type(), chipSize,
                RouletteGame.encodeSelection(bets), gross.greaterThan(staked), resultLabel(), gross);

        if (table != null) {
            table.showResult(winningPocket, gross);
        }
        plugin.sessions().end(playerId());
    }

    /**
     * Pays the board. Every chip is judged separately against the one pocket and the sums are
     * handed to {@link PayoutService#settleReturn}, which covers all three shapes a spread of bets
     * can take: more back than staked, exactly the stake, or less.
     */
    private void settleSpin(Player player) {
        PayoutService.settleReturn(plugin, player, this, totalEscrow(),
                grossReturnFor(winningPocket), resultLabel());
    }

    private String resultLabel() {
        if (winningPocket < 0) {
            return "No spin";
        }
        String pocket = winningPocket + " ("
                + RouletteWheel.colorOf(winningPocket).name().toLowerCase(Locale.ROOT) + ")";
        return bets.size() + (bets.size() == 1 ? " chip" : " chips") + " vs " + pocket
                + ", " + winningBetCount() + " won";
    }

    /**
     * The pocket is drawn the moment the player presses spin, so an interruption after that point
     * must honour the drawn result rather than refund. Before that point there is nothing to
     * honour and nothing to refund — the table was free to sit at.
     */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        Money staked = totalEscrow();
        if (winningPocket < 0 || bets.isEmpty() || !staked.isPositive()) {
            return false;
        }
        Money gross = grossReturnFor(winningPocket);
        Player player = player();
        if (player == null) {
            // Offline and every chip lost: consume the escrow. Anything owed still has to be paid,
            // so a partial or winning board falls through to a deposit against the offline account.
            if (!gross.isPositive()) {
                return discardEscrow();
            }
            return PayoutService.settleReturnOffline(plugin, playerId(), this, staked, gross);
        }
        settleSpin(player);
        return true;
    }

    @Override
    protected void onForceEnd(String reason) {
        spinning = false;
        table = null;
        activeView = null;
    }
}
