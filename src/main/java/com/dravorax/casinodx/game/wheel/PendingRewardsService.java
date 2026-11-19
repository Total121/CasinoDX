package com.dravorax.casinodx.game.wheel;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists wheel item/command rewards that could not be delivered because the player disconnected
 * mid-spin, then grants them on the next join.
 *
 * <p>Encoding is deliberately simple and line-oriented so it stays readable in the database:
 * <pre>
 *   ITEM|DIAMOND:3
 *   CMD|give {player} emerald 1
 * </pre>
 */
public final class PendingRewardsService implements Listener {

    public static final String META_KEY = "wheel.pending_rewards";

    private final CasinoDX plugin;

    public PendingRewardsService(CasinoDX plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Queues non-cash parts of a reward for later delivery. No-op when there is nothing to queue. */
    public void enqueue(UUID playerId, WheelReward reward) {
        if (playerId == null || reward == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String item : reward.items()) {
            if (item != null && !item.isBlank()) {
                lines.add("ITEM|" + item.trim());
            }
        }
        if (plugin.games().get(com.dravorax.casinodx.game.GameType.WHEEL) instanceof WheelGame wheel
                && wheel.commandsEnabled()) {
            for (String command : reward.commands()) {
                if (command != null && !command.isBlank()) {
                    lines.add("CMD|" + command.trim());
                }
            }
        }
        if (lines.isEmpty()) {
            return;
        }

        plugin.storage().getMeta(playerId, META_KEY, existing -> {
            StringBuilder payload = new StringBuilder(existing.orElse(""));
            if (!payload.isEmpty() && payload.charAt(payload.length() - 1) != '\n') {
                payload.append('\n');
            }
            for (String line : lines) {
                payload.append(line).append('\n');
            }
            plugin.storage().setMeta(playerId, META_KEY, payload.toString());
            plugin.getLogger().info("[CasinoDX] Queued " + lines.size()
                    + " pending wheel reward line(s) for " + playerId + ".");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        plugin.storage().getMeta(id, META_KEY, optional -> {
            if (optional.isEmpty() || optional.get().isBlank()) {
                return;
            }
            String payload = optional.get();
            // Clear first so a crash mid-delivery cannot duplicate forever; re-queue failures below.
            plugin.storage().setMeta(id, META_KEY, "");
            plugin.scheduler().runForEntity(player, () -> deliver(player, payload));
        });
    }

    private void deliver(Player player, String payload) {
        if (player == null || !player.isOnline() || payload == null || payload.isBlank()) {
            return;
        }
        List<String> failed = new ArrayList<>();
        int granted = 0;
        for (String raw : payload.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                if (line.startsWith("ITEM|")) {
                    ItemStack stack = parseItem(line.substring(5));
                    if (stack == null) {
                        failed.add(line);
                        continue;
                    }
                    player.getInventory().addItem(stack).values()
                            .forEach(leftover -> player.getWorld()
                                    .dropItemNaturally(player.getLocation(), leftover));
                    granted++;
                } else if (line.startsWith("CMD|")) {
                    WheelGame wheel = plugin.games().get(com.dravorax.casinodx.game.GameType.WHEEL)
                            instanceof WheelGame w ? w : null;
                    if (wheel == null || !wheel.commandsEnabled()) {
                        failed.add(line);
                        continue;
                    }
                    String command = line.substring(4)
                            .replace("{player}", player.getName())
                            .replace("%player%", player.getName());
                    if (command.startsWith("/")) {
                        command = command.substring(1);
                    }
                    String toRun = command;
                    plugin.scheduler().runGlobal(() -> {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun);
                        } catch (Throwable t) {
                            plugin.getLogger().log(Level.SEVERE,
                                    "[CasinoDX] Pending wheel command failed: " + toRun, t);
                        }
                    });
                    granted++;
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING,
                        "[CasinoDX] Failed to deliver pending reward line: " + line, t);
                failed.add(line);
            }
        }

        if (!failed.isEmpty()) {
            StringBuilder requeue = new StringBuilder();
            for (String line : failed) {
                requeue.append(line).append('\n');
            }
            plugin.storage().setMeta(player.getUniqueId(), META_KEY, requeue.toString());
        }

        if (granted > 0) {
            plugin.messages().send(player, "wheel-pending-delivered", "count", String.valueOf(granted));
            plugin.sounds().play(player, SoundService.REWARD);
        }
    }

    private static ItemStack parseItem(String spec) {
        if (spec == null || spec.isBlank()) {
            return null;
        }
        String[] parts = spec.trim().split(":");
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = 1;
        if (parts.length > 1) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        return new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize() * 9, amount)));
    }
}
