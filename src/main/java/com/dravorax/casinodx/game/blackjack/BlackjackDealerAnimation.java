package com.dravorax.casinodx.game.blackjack;

import com.dravorax.casinodx.animation.AbstractAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Flips the dealer's hole card and then walks through whatever the dealer drew.
 *
 * <p>{@link BlackjackSession} resolves the dealer's entire hand before registering this animation,
 * so the card count is known up front and the reveal order is the only thing left to play out.
 */
final class BlackjackDealerAnimation extends AbstractAnimation {

    private static final int HOLD_TICKS = 10;

    private final BlackjackSession session;
    private final BlackjackGui gui;
    private final int interval;
    private final int totalCards;

    private int revealed = 1;

    BlackjackDealerAnimation(Player player, BlackjackSession session, BlackjackGui gui, int interval) {
        super(player.getUniqueId(),
                Math.max(1, interval) * Math.max(1, session.dealerHand().size()) + HOLD_TICKS);
        this.session = session;
        this.gui = gui;
        this.interval = Math.max(1, interval);
        this.totalCards = session.dealerHand().size();
    }

    @Override
    public void tick(int elapsedTicks) {
        if (revealed >= totalCards) {
            // Everything is showing; hold briefly so the final total is readable, then settle.
            if (elapsedTicks >= interval * totalCards + HOLD_TICKS - 1) {
                markFinished();
            }
            return;
        }
        if (elapsedTicks < revealed * interval) {
            return;
        }

        revealed++;
        session.revealDealerCard(revealed);

        Player player = Bukkit.getPlayer(playerId());
        if (player != null) {
            boolean bust = session.dealerHand().isBust();
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, bust ? 0.7f : 1.1f);
        }
        if (gui != null) {
            gui.refresh();
        }
    }

    @Override
    protected void onFinish() {
        session.onDealerFinished();
    }

    @Override
    protected void onCancel() {
        // The dealer's hand is already drawn; BlackjackSession settles the known result.
    }
}
