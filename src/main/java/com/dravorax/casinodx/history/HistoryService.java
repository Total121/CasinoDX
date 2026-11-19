package com.dravorax.casinodx.history;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.storage.StorageService;
import com.dravorax.casinodx.tax.TaxResult;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records casino round history and serves paginated data for GUIs.
 */
public final class HistoryService implements Listener {

    private final CasinoDX plugin;
    private final StorageService storage;
    private final ConfigManager configs;
    private final ConcurrentHashMap<UUID, List<HistoryEntry>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, AtomicLong> totalCounts = new ConcurrentHashMap<>();
    private Object retentionTask;

    public HistoryService(CasinoDX plugin, StorageService storage, ConfigManager configs) {
        this.plugin = plugin;
        this.storage = storage;
        this.configs = configs;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startRetentionTask();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        preload(event.getPlayer().getUniqueId());
    }

    public int pageSize() {
        return Math.max(1, Math.min(45, configs.module("history").getInt("page-size", 28)));
    }

    /**
     * Synchronous read from the in-memory cache (populated async on join / record).
     */
    public PagedHistory getHistory(UUID uuid, int page) {
        int safePage = Math.max(0, page);
        int size = pageSize();
        List<HistoryEntry> all = cache.getOrDefault(uuid, List.of());
        long total = totalCounts.computeIfAbsent(uuid, ignored -> new AtomicLong(all.size())).get();
        if (total < all.size()) {
            total = all.size();
        }

        int from = safePage * size;
        if (from >= all.size()) {
            return new PagedHistory(List.of(), safePage, totalPages(total), (int) total);
        }
        int to = Math.min(from + size, all.size());
        return new PagedHistory(all.subList(from, to), safePage, totalPages(total), (int) total);
    }

    public void preload(UUID uuid) {
        storage.loadHistoryPage(uuid, 0, Math.max(pageSize() * 3, 50), entries -> {
            cache.put(uuid, new ArrayList<>(entries));
        });
        storage.countHistory(uuid, count -> totalCounts
                .computeIfAbsent(uuid, ignored -> new AtomicLong())
                .set(count));
    }

    public void recordWin(Player player, GameType game, Money bet, TaxResult taxResult, double multiplier) {
        if (player == null) {
            return;
        }
        record(player.getUniqueId(), game, bet, HistoryEntry.Result.WIN, multiplier, taxResult);
    }

    public void recordWinOffline(UUID uuid, GameType game, Money bet, TaxResult taxResult, double multiplier) {
        record(uuid, game, bet, HistoryEntry.Result.WIN, multiplier, taxResult);
    }

    public void recordLoss(Player player, GameType game, Money bet, double multiplier) {
        if (player == null) {
            return;
        }
        record(player.getUniqueId(), game, bet, HistoryEntry.Result.LOSS, multiplier,
                new TaxResult(Money.ZERO, Money.ZERO, Money.ZERO, bet, bet.negate()));
    }

    public void recordPush(Player player, GameType game, Money bet) {
        if (player == null) {
            return;
        }
        record(player.getUniqueId(), game, bet, HistoryEntry.Result.PUSH, 1.0,
                new TaxResult(bet, Money.ZERO, bet, bet, Money.ZERO));
    }

    public void record(Player player, GameType game, Money bet, HistoryEntry.Result result,
                       double multiplier, TaxResult taxResult) {
        if (player == null) {
            return;
        }
        record(player.getUniqueId(), game, bet, result, multiplier, taxResult);
    }

    public void record(UUID uuid, GameType game, Money bet, HistoryEntry.Result result,
                       double multiplier, TaxResult taxResult) {
        if (uuid == null || game == null || bet == null || taxResult == null) {
            return;
        }
        HistoryEntry entry = new HistoryEntry(
                -1L,
                game,
                bet,
                result,
                multiplier,
                taxResult.grossPayout(),
                taxResult.taxAmount(),
                taxResult.netPayout(),
                System.currentTimeMillis()
        );

        cache.compute(uuid, (id, existing) -> {
            List<HistoryEntry> list = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            list.add(0, entry);
            int maxCache = Math.max(pageSize() * 5, 100);
            if (list.size() > maxCache) {
                return new ArrayList<>(list.subList(0, maxCache));
            }
            return list;
        });
        totalCounts.computeIfAbsent(uuid, ignored -> new AtomicLong()).incrementAndGet();
        storage.insertHistory(uuid, entry);
    }

    public void purgeExpired() {
        int retentionDays = Math.max(1, configs.module("history").getInt("retention-days", 30));
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        storage.purgeHistoryOlderThan(cutoff);
    }

    public void shutdown() {
        if (retentionTask != null) {
            plugin.scheduler().cancelTask(retentionTask);
            retentionTask = null;
        }
        cache.clear();
        totalCounts.clear();
    }

    private void startRetentionTask() {
        retentionTask = plugin.scheduler().runAsyncRepeating(this::purgeExpired, 300L, 3600L);
    }

    private int totalPages(long totalEntries) {
        int size = pageSize();
        if (totalEntries <= 0) {
            return 1;
        }
        return (int) Math.ceil(totalEntries / (double) size);
    }
}
