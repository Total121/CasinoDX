package com.dravorax.casinodx.stats;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.storage.StorageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks and persists per-player casino statistics with an in-memory cache.
 */
public final class StatisticsService implements Listener {

    private final CasinoDX plugin;
    private final StorageService storage;
    private final ConfigManager configs;
    private final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private Object flushTask;

    public StatisticsService(CasinoDX plugin, StorageService storage, ConfigManager configs) {
        this.plugin = plugin;
        this.storage = storage;
        this.configs = configs;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startFlushTask();
    }

    public PlayerStats getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerStats::new);
    }

    public Optional<PlayerStats> getCached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public PlayerStats getStats(UUID uuid) {
        return getOrCreate(uuid);
    }

    public void recordRound(
            UUID uuid,
            GameType game,
            Money bet,
            Money netReturn,
            Money tax,
            double multiplier,
            boolean win,
            boolean push
    ) {
        PlayerStats stats = getOrCreate(uuid);
        stats.recordRound(game, bet, netReturn, tax, multiplier, win, push);
    }

    public void flush(UUID uuid) {
        PlayerStats stats = cache.get(uuid);
        if (stats == null || !stats.isDirty()) {
            return;
        }
        storage.saveStats(stats);
        stats.markClean();
    }

    public void flushAllDirty() {
        for (PlayerStats stats : cache.values()) {
            if (stats.isDirty()) {
                storage.saveStats(stats);
                stats.markClean();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        storage.loadStats(uuid, loaded -> {
            loaded.ifPresent(stats -> cache.put(uuid, stats));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        flush(uuid);
        cache.remove(uuid);
    }

    public void shutdown() {
        if (flushTask != null) {
            plugin.scheduler().cancelTask(flushTask);
            flushTask = null;
        }
        flushAllDirty();
        cache.clear();
    }

    private void startFlushTask() {
        long interval = Math.max(5L, configs.module("statistics").getLong("flush-interval-seconds", 60L));
        flushTask = plugin.scheduler().runAsyncRepeating(this::flushAllDirty, interval, interval);
    }

    public void preload(Player player) {
        if (player == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        if (cache.containsKey(uuid)) {
            return;
        }
        storage.loadStats(uuid, loaded -> loaded.ifPresent(stats -> cache.put(uuid, stats)));
    }
}
