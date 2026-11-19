package com.dravorax.casinodx;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Cleans up casino state when a player disconnects so animations, sessions, and exploit guards
 * cannot leak across reconnects.
 */
public final class PlayerConnectionListener implements Listener {

    private static final String DISCONNECT_REASON = "Player disconnected";

    private final CasinoDX plugin;

    public PlayerConnectionListener(CasinoDX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        try {
            plugin.animations().cancelForPlayer(playerId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to cancel animations for " + player.getName(), t);
        }

        try {
            plugin.sessions().forceEnd(playerId, DISCONNECT_REASON);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to end casino session for " + player.getName(), t);
        }

        try {
            plugin.antiExploit().cleanup(playerId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to clear anti-exploit state for " + player.getName(), t);
        }

        try {
            plugin.economy().cleanup(playerId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Failed to clear economy locks for " + player.getName(), t);
        }

        try {
            // A pending chat bet prompt must not survive the disconnect, or the player's first
            // message after reconnecting would be swallowed as a bet amount.
            plugin.betInput().cancel(playerId);
            plugin.lastPlay().clear(playerId);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Failed to clear bet input state for " + player.getName(), t);
        }

        try {
            if (plugin.guis() != null) {
                plugin.guis().close(player);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Failed to close casino GUI for " + player.getName(), t);
        }
    }
}
