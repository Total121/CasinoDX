package com.dravorax.casinodx.animation;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AnimationEngine {

    private final CasinoDX plugin;
    private final PlatformScheduler scheduler;
    private final Map<UUID, List<ActiveAnimation>> byPlayer = new ConcurrentHashMap<>();
    private final List<ActiveAnimation> global = new CopyOnWriteArrayList<>();
    private BukkitTask tickTask;

    public AnimationEngine(CasinoDX plugin, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        startEngine();
    }

    private void startEngine() {
        if (tickTask != null && !tickTask.isCancelled()) return;
        tickTask = scheduler.runTimer(() -> tick(), 1L, 1L);
    }

    public void register(Animation animation) {
        if (animation == null) return;
        ActiveAnimation active = new ActiveAnimation(animation);
        animation.start();
        if (animation.playerId() != null) {
            byPlayer.computeIfAbsent(animation.playerId(), id -> new CopyOnWriteArrayList<>()).add(active);
        } else {
            global.add(active);
        }
    }

    public void unregister(Animation animation) {
        if (animation == null) return;
        if (animation.playerId() != null) {
            List<ActiveAnimation> list = byPlayer.get(animation.playerId());
            if (list != null) {
                list.removeIf(a -> a.animation == animation);
                if (list.isEmpty()) {
                    byPlayer.remove(animation.playerId());
                }
            }
        } else {
            global.removeIf(a -> a.animation == animation);
        }
    }

    public void cancelForPlayer(UUID playerId) {
        if (playerId == null) return;
        List<ActiveAnimation> list = byPlayer.remove(playerId);
        if (list == null) return;
        for (ActiveAnimation active : list) {
            Animation animation = active.animation;
            if (!animation.isFinished() && !animation.isCancelled()) {
                animation.cancel();
            }
        }
    }

    public void cancelAll() {
        for (List<ActiveAnimation> list : byPlayer.values()) {
            for (ActiveAnimation active : list) {
                Animation animation = active.animation;
                if (!animation.isFinished() && !animation.isCancelled()) {
                    animation.cancel();
                }
            }
        }
        byPlayer.clear();
        for (ActiveAnimation active : global) {
            Animation animation = active.animation;
            if (!animation.isFinished() && !animation.isCancelled()) {
                animation.cancel();
            }
        }
        global.clear();
    }

    public void shutdown() {
        cancelAll();
        if (tickTask != null) {
            scheduler.cancel(tickTask);
            tickTask = null;
        }
    }

    private void tick() {
        tickList(global);
        for (UUID playerId : new ArrayList<>(byPlayer.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                cancelForPlayer(playerId);
                continue;
            }
            List<ActiveAnimation> list = byPlayer.get(playerId);
            if (list == null || list.isEmpty()) {
                continue;
            }
            // Inventory and sound effects must run on the player's entity region on Folia.
            if (scheduler.isFolia()) {
                scheduler.runForEntity(player, () -> tickList(list));
            } else {
                tickList(list);
            }
        }
    }

    private void tickList(List<ActiveAnimation> animations) {
        Iterator<ActiveAnimation> it = animations.iterator();
        while (it.hasNext()) {
            ActiveAnimation active = it.next();
            Animation animation = active.animation;
            if (animation.isFinished() || animation.isCancelled()) {
                unregister(animation);
                continue;
            }
            if (animation.isPaused()) continue;

            active.elapsedTicks++;
            animation.tick(active.elapsedTicks);

            if (active.elapsedTicks >= animation.maxDurationTicks()) {
                animation.finish();
                unregister(animation);
            }
        }
    }

    private static final class ActiveAnimation {
        private final Animation animation;
        private int elapsedTicks;

        private ActiveAnimation(Animation animation) {
            this.animation = animation;
        }
    }
}
