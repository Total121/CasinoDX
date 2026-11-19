package com.dravorax.casinodx.bet;

import com.dravorax.casinodx.CasinoDX;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Captures the next chat line from a player who is entering a bet amount.
 *
 * <p>Chat arrives off the main thread, so the line is only inspected here; the actual handling is
 * bounced back onto the server thread through {@link BetInputService#handleInput(Player, String)}.
 *
 * <p>Paper replaced {@link AsyncPlayerChatEvent} with
 * {@link io.papermc.paper.event.player.AsyncChatEvent}. {@link #register(CasinoDX)} picks whichever
 * of the two the running server actually has, and registers exactly one of them so a captured line
 * can never be both swallowed and broadcast.
 */
public final class ChatBetListener implements Listener {

    private final CasinoDX plugin;

    public ChatBetListener(CasinoDX plugin) {
        this.plugin = plugin;
    }

    /** Registers the modern Paper chat listener when available, else the legacy Bukkit one. */
    public static void register(CasinoDX plugin) {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Bukkit.getPluginManager().registerEvents(new PaperChatBetListener(plugin), plugin);
            return;
        } catch (Throwable ignored) {
            // Server predates Paper's component chat events.
        }
        Bukkit.getPluginManager().registerEvents(new ChatBetListener(plugin), plugin);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.betInput().isWaiting(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        plugin.scheduler().runGlobal(() -> plugin.betInput().handleInput(player, message));
    }
}

/** Paper-native counterpart of {@link ChatBetListener}; only loaded when the event class exists. */
final class PaperChatBetListener implements Listener {

    private final CasinoDX plugin;

    PaperChatBetListener(CasinoDX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.betInput().isWaiting(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.scheduler().runGlobal(() -> plugin.betInput().handleInput(player, message));
    }
}
