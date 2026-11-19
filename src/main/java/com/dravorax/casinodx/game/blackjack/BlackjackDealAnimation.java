package com.dravorax.casinodx.game.blackjack;

import com.dravorax.casinodx.animation.AbstractAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Opening deal: player, dealer, player, dealer-face-down, one card per interval.
 *
 * <p>All four cards were drawn in {@link BlackjackSession}'s constructor. This animation only
 * controls how many of them the GUI is allowed to render, which is why cancelling it cannot change
 * a single card.
 */
final class BlackjackDealAnimation extends AbstractAnimation {

    /** (visible player cards, visible dealer cards) after each step of the deal. */
    private static final int[][] REVEAL_STEPS = {
            {1, 0},
            {1, 1},
            {2, 1},
            {2, 1}
    };

    private final BlackjackSession session;
    private final BlackjackGui gui;
    private final int interval;

    private int stepsDone;

    BlackjackDealAnimation(Player player, BlackjackSession session, BlackjackGui gui, int interval) {
        super(player.getUniqueId(), Math.max(1, interval) * (REVEAL_STEPS.length + 1));
        this.session = session;
        this.gui = gui;
        this.interval = Math.max(1, interval);
    }

    @Override
    public void tick(int elapsedTicks) {
        if (stepsDone >= REVEAL_STEPS.length) {
            markFinished();
            return;
        }
        if (elapsedTicks < (stepsDone + 1) * interval) {
            return;
        }

        int[] step = REVEAL_STEPS[stepsDone++];
        session.revealNextCard(step[0], step[1]);

        Player player = Bukkit.getPlayer(playerId());
        if (player != null) {
            // Pitch rises across the deal so the four cards read as a sequence.
            float pitch = 1.0f + stepsDone * 0.08f;
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, pitch);
        }
        if (gui != null) {
            gui.refresh();
        }
    }

    @Override
    protected void onFinish() {
        session.onDealFinished();
    }

    @Override
    protected void onCancel() {
        // The round is still undecided during the deal; GameSession#forceEnd refunds.
    }
}
