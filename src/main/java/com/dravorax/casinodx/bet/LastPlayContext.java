package com.dravorax.casinodx.bet;

import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last round each player finished so the replay screen can offer "play again" with
 * the same stake — and, for games where the wager is chosen inside the round, the same selection.
 *
 * <p>This is presentation state only. Nothing here is consulted when money moves; a replayed round
 * goes through {@link com.dravorax.casinodx.game.CasinoGame#startSession} and is validated from
 * scratch like any other bet.
 */
public final class LastPlayContext {

    /**
     * @param selectionKey game-specific encoding of the wager the player picked inside the round
     *                     (roulette bet type, coinflip side), or {@code null} when the game has none
     */
    public record LastPlay(GameType gameType,
                           Money bet,
                           String selectionKey,
                           boolean won,
                           String resultLabel,
                           Money payout,
                           long recordedAt) {

        public LastPlay {
            bet = bet == null ? Money.ZERO : bet;
            payout = payout == null ? Money.ZERO : payout;
            resultLabel = resultLabel == null ? "" : resultLabel;
        }

        public boolean hasSelection() {
            return selectionKey != null && !selectionKey.isBlank();
        }

        public LastPlay withBet(Money newBet) {
            return new LastPlay(gameType, newBet, selectionKey, won, resultLabel, payout, recordedAt);
        }

        public LastPlay withoutSelection() {
            return new LastPlay(gameType, bet, null, won, resultLabel, payout, recordedAt);
        }
    }

    private final Map<UUID, LastPlay> lastPlays = new ConcurrentHashMap<>();

    public void record(UUID playerId, GameType type, Money bet, String selectionKey,
                       boolean won, String resultLabel, Money payout) {
        if (playerId == null || type == null) {
            return;
        }
        lastPlays.put(playerId, new LastPlay(type, bet, selectionKey, won, resultLabel, payout,
                System.currentTimeMillis()));
    }

    public LastPlay get(UUID playerId) {
        return playerId == null ? null : lastPlays.get(playerId);
    }

    public boolean has(UUID playerId) {
        return get(playerId) != null;
    }

    /** Updates the remembered stake after the player adjusts it on the replay screen. */
    public void updateBet(UUID playerId, Money bet) {
        if (playerId == null || bet == null) {
            return;
        }
        lastPlays.computeIfPresent(playerId, (id, play) -> play.withBet(bet));
    }

    /** Drops the remembered in-round selection so the next replay makes the player pick again. */
    public void clearSelection(UUID playerId) {
        if (playerId == null) {
            return;
        }
        lastPlays.computeIfPresent(playerId, (id, play) -> play.withoutSelection());
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            lastPlays.remove(playerId);
        }
    }

    public void clearAll() {
        lastPlays.clear();
    }
}
