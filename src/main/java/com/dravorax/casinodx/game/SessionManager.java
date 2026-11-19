package com.dravorax.casinodx.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the single active {@link GameSession} per player.
 *
 * <p>One session per player is a deliberate constraint: it makes "the player has exactly one
 * escrowed wager" an invariant, which is what lets shutdown and disconnect handling resolve money
 * without reconciliation.
 */
public final class SessionManager {

    private final Map<UUID, GameSession> sessions = new ConcurrentHashMap<>();

    /**
     * Registers a session.
     *
     * @return {@code false} when the player already has an active session, in which case the
     *         caller must not escrow a wager
     */
    public boolean start(GameSession session) {
        if (session == null) {
            return false;
        }
        return sessions.putIfAbsent(session.playerId(), session) == null;
    }

    public GameSession get(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    public boolean has(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    /**
     * Removes a session that resolved its own money (normal completion).
     * Unresolved wagers are refunded defensively.
     */
    public void end(UUID playerId) {
        if (playerId == null) {
            return;
        }
        GameSession session = sessions.remove(playerId);
        if (session != null && !session.isSettled()) {
            session.forceEnd("Session closed");
        }
        cleanup(playerId);
    }

    /** Ends a session, refunding or settling the escrowed wager as appropriate. */
    public void forceEnd(UUID playerId, String reason) {
        if (playerId == null) {
            return;
        }
        GameSession session = sessions.remove(playerId);
        if (session != null) {
            session.forceEnd(reason);
        }
        cleanup(playerId);
    }

    public void forceEndAll(String reason) {
        List<UUID> ids = new ArrayList<>(sessions.keySet());
        for (UUID id : ids) {
            forceEnd(id, reason);
        }
        sessions.clear();
    }

    public boolean hasActiveSessions() {
        return !sessions.isEmpty();
    }

    public int activeCount() {
        return sessions.size();
    }

    public Collection<GameSession> active() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    private void cleanup(UUID playerId) {
        com.dravorax.casinodx.CasinoDX plugin = com.dravorax.casinodx.CasinoDX.get();
        if (plugin == null) {
            return;
        }
        try {
            plugin.animations().cancelForPlayer(playerId);
        } catch (Throwable ignored) {
            // Shutting down.
        }
        try {
            plugin.antiExploit().cleanup(playerId);
        } catch (Throwable ignored) {
            // Shutting down.
        }
    }
}
