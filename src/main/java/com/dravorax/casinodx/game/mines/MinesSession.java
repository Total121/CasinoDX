package com.dravorax.casinodx.game.mines;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import com.dravorax.casinodx.util.Text;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * State for one Mines round.
 *
 * <p>The mine layout is drawn in the constructor — before the board is ever shown — and the
 * {@code mines} array is never written again. Reveals therefore cannot be steered by click timing,
 * and there is no code path that could relocate a mine to force a loss.
 */
public final class MinesSession extends GameSession {

    private static final SecureRandom LAYOUT_RANDOM = new SecureRandom();

    private final MinesGame game;
    private final int gridSize;
    private final boolean[] mine;
    private final boolean[] revealed;

    private int safeRevealed;
    private boolean exploded;
    private int triggeredMineIndex = -1;
    private MinesGui gui;

    MinesSession(CasinoDX plugin, Player player, MinesGame game, Money bet) {
        super(plugin, player, GameType.MINES, bet);
        this.game = game;
        this.gridSize = game.gridSize();

        int tiles = game.tileCount();
        this.mine = new boolean[tiles];
        this.revealed = new boolean[tiles];

        // Uniform random layout: shuffle the tile indices and mine the first `mineCount` of them.
        List<Integer> indices = new ArrayList<>(tiles);
        for (int i = 0; i < tiles; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, LAYOUT_RANDOM);
        for (int i = 0; i < game.mineCount(); i++) {
            mine[indices.get(i)] = true;
        }
    }

    public MinesGame game() {
        return game;
    }

    public int gridSize() {
        return gridSize;
    }

    public int tileCount() {
        return mine.length;
    }

    public int safeRevealed() {
        return safeRevealed;
    }

    public boolean exploded() {
        return exploded;
    }

    public boolean isRevealed(int index) {
        return index >= 0 && index < revealed.length && revealed[index];
    }

    public boolean isMine(int index) {
        return index >= 0 && index < mine.length && mine[index];
    }

    public int triggeredMineIndex() {
        return triggeredMineIndex;
    }

    public double currentMultiplier() {
        return game.multiplierFor(safeRevealed);
    }

    public Money currentPayout() {
        return bet().multiply(currentMultiplier());
    }

    public double nextMultiplier() {
        return game.multiplierFor(safeRevealed + 1);
    }

    /** True once at least one safe tile is revealed, which is when cashing out becomes meaningful. */
    public boolean canCashOut() {
        return acceptsInput() && safeRevealed > 0;
    }

    /** True when every safe tile has been found — the board is cleared and pays automatically. */
    public boolean boardCleared() {
        return safeRevealed >= game.safeTileCount();
    }

    void attachGui(MinesGui gui) {
        this.gui = gui;
    }

    public void openBoard() {
        MinesGui view = new MinesGui(plugin, player(), this);
        attachGui(view);
        plugin.guis().open(view);
    }

    /**
     * Handles a tile click. Rejected unless the session is accepting input, so a click that lands
     * while the reveal animation is still playing is discarded rather than queued.
     */
    public void reveal(int index) {
        if (!acceptsInput() || index < 0 || index >= mine.length || revealed[index]) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        revealed[index] = true;
        state(State.ANIMATING);

        if (mine[index]) {
            exploded = true;
            triggeredMineIndex = index;
            plugin.animations().register(new MinesExplosionAnimation(player, this, gui, index));
            return;
        }

        safeRevealed++;
        plugin.sounds().play(player, SoundService.CLICK);
        plugin.animations().register(new MinesTileAnimation(player, this, gui, index));
    }

    /** Called by {@link MinesTileAnimation} once the flip animation has finished. */
    void onSafeRevealFinished() {
        if (isFinished()) {
            return;
        }
        if (boardCleared()) {
            cashOut(true);
            return;
        }
        state(State.ACTIVE);
        if (gui != null) {
            gui.refresh();
        }
    }

    /** Called by {@link MinesExplosionAnimation} once every mine has been shown. */
    void onExplosionFinished() {
        Player player = player();
        if (player == null) {
            plugin.sessions().end(playerId());
            return;
        }
        String label = "Hit a mine after " + safeRevealed + " safe tile(s)";
        PayoutService.settleLoss(plugin, player, this, bet(), 0.0, label);
        plugin.lastPlay().record(playerId(), type(), bet(), null, false, label, Money.ZERO);
        if (gui != null) {
            gui.showFinalBoard(false, Money.ZERO);
        }
        plugin.sessions().end(playerId());
    }

    /**
     * Cashes out at the current multiplier. The multiplier is derived purely from
     * {@link #safeRevealed}, which only ever increments inside {@link #reveal(int)}.
     */
    public void cashOut(boolean boardCleared) {
        if (isSettled() || exploded) {
            return;
        }
        if (!boardCleared && !canCashOut()) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        state(State.SETTLING);
        double multiplier = currentMultiplier();
        Money gross = bet().multiply(multiplier);
        String label = boardCleared
                ? "Cleared the board (" + Text.formatMultiplier(multiplier) + ")"
                : "Cashed out at " + Text.formatMultiplier(multiplier);

        boolean paid = PayoutService.settleWin(plugin, player, this, bet(), gross, multiplier, label);
        if (paid) {
            plugin.sounds().play(player, SoundService.CASHOUT);
        }
        plugin.lastPlay().record(playerId(), type(), bet(), null, true, label, gross);
        if (gui != null) {
            gui.showFinalBoard(true, gross);
        }
        plugin.sessions().end(playerId());
    }

    /**
     * Once a mine has been clicked the round is already lost, so an interruption must not hand the
     * wager back. Every other state (including mid-flip on a safe tile) is still open and falls
     * through to the default refund.
     */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        if (!exploded) {
            return false;
        }
        Player player = player();
        if (player == null) {
            return discardEscrow();
        }
        return PayoutService.settleLoss(plugin, player, this, bet(), 0.0, "Hit a mine");
    }

    @Override
    protected void onForceEnd(String reason) {
        gui = null;
    }
}
