package com.dravorax.casinodx.game.coinflip;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.entity.Player;

/** One coinflip: call a side, toss once, settle. */
public final class CoinflipSession extends GameSession {

    private final CoinflipGame game;

    private CoinflipGame.Side choice;
    private CoinflipGame.Side result;
    private boolean flipping;
    private CoinflipGui gui;

    CoinflipSession(CasinoDX plugin, Player player, CoinflipGame game, Money bet) {
        super(plugin, player, GameType.COINFLIP, bet);
        this.game = game;
    }

    public CoinflipGame game() {
        return game;
    }

    public CoinflipGame.Side choice() {
        return choice;
    }

    public CoinflipGame.Side result() {
        return result;
    }

    public boolean flipping() {
        return flipping;
    }

    public Money potentialReturn() {
        return game.grossReturn(bet());
    }

    public void openTable() {
        CoinflipGui view = new CoinflipGui(plugin, player(), this);
        this.gui = view;
        plugin.guis().open(view);
    }

    public void choose(CoinflipGame.Side side) {
        if (!acceptsInput() || side == null) {
            return;
        }
        this.choice = side;
        Player player = player();
        if (player != null) {
            plugin.sounds().play(player, SoundService.BET);
        }
        if (gui != null) {
            gui.refresh();
        }
    }

    /** Tosses the coin. The result is drawn here and the animation merely plays it back. */
    public void toss() {
        if (!acceptsInput() || choice == null || flipping) {
            return;
        }
        Player player = player();
        if (player == null) {
            return;
        }

        this.result = game.flip();
        this.flipping = true;
        state(State.ANIMATING);

        if (gui != null) {
            gui.refresh();
        }
        plugin.sounds().play(player, SoundService.COINFLIP);
        plugin.animations().register(new CoinflipTossAnimation(player, this, gui, result, game.flipTicks()));
    }

    /** Called by {@link CoinflipTossAnimation} once the coin has settled. */
    void onTossFinished() {
        this.flipping = false;
        Player player = player();
        if (player == null) {
            plugin.sessions().forceEnd(playerId(), "Left during the toss");
            return;
        }
        boolean won = settleToss(player);
        plugin.lastPlay().record(playerId(), type(), bet(),
                CoinflipGame.encodeSelection(choice), won,
                "Called " + (choice == null ? "nothing" : choice.displayName())
                        + ", landed " + (result == null ? "?" : result.displayName()),
                won ? bet().multiply(game.payoutMultiplier()) : Money.ZERO);
        if (gui != null) {
            gui.showResult(result, won);
        }
        plugin.sessions().end(playerId());
    }

    private boolean settleToss(Player player) {
        boolean won = choice != null && choice == result;
        String label = "Called " + (choice == null ? "nothing" : choice.displayName())
                + ", landed " + (result == null ? "?" : result.displayName());

        if (won) {
            double multiplier = game.payoutMultiplier();
            PayoutService.settleWin(plugin, player, this, bet(), bet().multiply(multiplier), multiplier, label);
        } else {
            PayoutService.settleLoss(plugin, player, this, bet(), 0.0, label);
        }
        return won;
    }

    /** The coin is drawn the instant the player tosses, so an interruption honours that draw. */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        if (result == null || choice == null) {
            return false;
        }
        Player player = player();
        if (player != null) {
            settleToss(player);
            return true;
        }
        if (choice != result) {
            return discardEscrow();
        }
        double multiplier = game.payoutMultiplier();
        return PayoutService.settleWinOffline(plugin, playerId(), this, bet(),
                bet().multiply(multiplier), multiplier);
    }

    @Override
    protected void onForceEnd(String reason) {
        flipping = false;
        gui = null;
    }
}
