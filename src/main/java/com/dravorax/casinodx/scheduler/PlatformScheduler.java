package com.dravorax.casinodx.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia-aware scheduler abstraction.
 * Falls back to the Bukkit scheduler on Paper / Spigot / Purpur.
 * Uses reflection to avoid a hard dependency on Folia classes.
 */
public final class PlatformScheduler {

    private final Plugin plugin;
    private final boolean folia;

    private Object globalRegionScheduler;
    private Object regionScheduler;
    private Object asyncScheduler;
    private Method globalRun;
    private Method globalRunDelayed;
    private Method globalRunRepeating;
    private Method regionExecute;
    private Method regionRunDelayed;
    private Method asyncRunNow;
    private Method asyncRunRepeating;
    private Method entitySchedulerGetter;
    private Method entitySchedulerRun;
    private Method entitySchedulerRunDelayed;
    private Method entitySchedulerRunRepeating;

    public PlatformScheduler(Plugin plugin) {
        this.plugin = plugin;
        boolean detected;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detected = true;
        } catch (ClassNotFoundException e) {
            detected = false;
        }

        boolean usable = false;
        if (detected) {
            try {
                this.globalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
                this.regionScheduler = Bukkit.class.getMethod("getRegionScheduler").invoke(null);
                this.asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);

                this.globalRun = globalRegionScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                this.globalRunDelayed = globalRegionScheduler.getClass().getMethod(
                        "runDelayed", Plugin.class, Consumer.class, long.class);
                this.globalRunRepeating = globalRegionScheduler.getClass().getMethod(
                        "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

                this.regionExecute = regionScheduler.getClass().getMethod(
                        "execute", Plugin.class, World.class, int.class, int.class, Runnable.class);
                this.regionRunDelayed = regionScheduler.getClass().getMethod(
                        "runDelayed", Plugin.class, World.class, int.class, int.class, Consumer.class, long.class);

                this.asyncRunNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
                this.asyncRunRepeating = asyncScheduler.getClass().getMethod(
                        "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class);

                this.entitySchedulerGetter = Entity.class.getMethod("getScheduler");
                Class<?> entitySchedulerClass =
                        Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                this.entitySchedulerRun = entitySchedulerClass.getMethod(
                        "run", Plugin.class, Consumer.class, Runnable.class);
                try {
                    this.entitySchedulerRunDelayed = entitySchedulerClass.getMethod(
                            "runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                } catch (NoSuchMethodException ignored) {
                    this.entitySchedulerRunDelayed = null;
                }
                try {
                    this.entitySchedulerRunRepeating = entitySchedulerClass.getMethod(
                            "runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
                } catch (NoSuchMethodException ignored) {
                    this.entitySchedulerRunRepeating = null;
                }
                usable = true;
            } catch (Throwable t) {
                plugin.getLogger().warning(
                        "Folia detected but scheduler init failed — using Bukkit scheduler: " + t.getMessage());
            }
        }
        this.folia = usable;
    }

    public boolean isFolia() {
        return folia;
    }

    public void runGlobal(Runnable task) {
        if (folia && globalRun != null) {
            try {
                globalRun.invoke(globalRegionScheduler, plugin, (Consumer<Object>) o -> task.run());
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia globalRun failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runGlobalLater(Runnable task, long delayTicks) {
        if (folia && globalRunDelayed != null) {
            try {
                globalRunDelayed.invoke(
                        globalRegionScheduler, plugin, (Consumer<Object>) o -> task.run(), Math.max(1L, delayTicks));
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia globalRunDelayed failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /**
     * Runs a repeating task on the global region scheduler (tick-based).
     *
     * @return a cancellable handle ({@link BukkitTask} or Folia scheduled task)
     */
    public Object runGlobalRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        long initial = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (folia && globalRunRepeating != null) {
            try {
                return globalRunRepeating.invoke(
                        globalRegionScheduler, plugin, (Consumer<Object>) o -> task.run(), initial, period);
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia globalRunRepeating failed: " + t.getMessage());
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, initial, period);
    }

    public void runAsync(Runnable task) {
        if (folia && asyncRunNow != null) {
            try {
                asyncRunNow.invoke(asyncScheduler, plugin, (Consumer<Object>) o -> task.run());
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia asyncRunNow failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public Object runAsyncRepeating(Runnable task, long initialDelaySec, long intervalSec) {
        if (folia && asyncRunRepeating != null) {
            try {
                return asyncRunRepeating.invoke(asyncScheduler, plugin,
                        (Consumer<Object>) o -> task.run(),
                        Math.max(1L, initialDelaySec), Math.max(1L, intervalSec), TimeUnit.SECONDS);
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia asyncRunRepeating failed: " + t.getMessage());
            }
        }
        long delayTicks = Math.max(1L, initialDelaySec * 20L);
        long periodTicks = Math.max(1L, intervalSec * 20L);
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    public void cancelTask(Object handle) {
        if (handle == null) {
            return;
        }
        try {
            if (handle instanceof BukkitTask bt) {
                bt.cancel();
                return;
            }
            handle.getClass().getMethod("cancel").invoke(handle);
        } catch (Throwable ignored) {
        }
    }

    public void runForRegion(Block block, Runnable task) {
        if (block == null) {
            runGlobal(task);
            return;
        }
        runForRegion(block.getWorld(), block.getX() >> 4, block.getZ() >> 4, task);
    }

    public void runForRegion(World world, int chunkX, int chunkZ, Runnable task) {
        if (folia && regionExecute != null && world != null) {
            try {
                regionExecute.invoke(regionScheduler, plugin, world, chunkX, chunkZ, task);
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia regionExecute failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runForRegionLater(Block block, Runnable task, long delayTicks) {
        if (block == null) {
            runGlobalLater(task, delayTicks);
            return;
        }
        World world = block.getWorld();
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        if (folia && regionRunDelayed != null && world != null) {
            try {
                regionRunDelayed.invoke(regionScheduler, plugin, world, chunkX, chunkZ,
                        (Consumer<Object>) o -> task.run(), Math.max(1L, delayTicks));
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia regionRunDelayed failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public void runForEntity(Entity entity, Runnable task) {
        if (folia && entitySchedulerRun != null && entity != null && entity.isValid()) {
            try {
                Object entityScheduler = entitySchedulerGetter.invoke(entity);
                entitySchedulerRun.invoke(entityScheduler, plugin, (Consumer<Object>) o -> task.run(), (Runnable) () -> {});
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia entityRun failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runForEntityLater(Entity entity, Runnable task, long delayTicks) {
        if (folia && entitySchedulerRunDelayed != null && entity != null && entity.isValid()) {
            try {
                Object entityScheduler = entitySchedulerGetter.invoke(entity);
                entitySchedulerRunDelayed.invoke(entityScheduler, plugin,
                        (Consumer<Object>) o -> task.run(), (Runnable) () -> {}, Math.max(1L, delayTicks));
                return;
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia entityRunDelayed failed: " + t.getMessage());
            }
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /**
     * Runs a repeating task tied to an entity's scheduler (tick-based).
     *
     * @return a cancellable handle ({@link BukkitTask} or Folia scheduled task)
     */
    public Object runForEntityRepeating(Entity entity, Runnable task, long initialDelayTicks, long periodTicks) {
        long initial = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (folia && entitySchedulerRunRepeating != null && entity != null && entity.isValid()) {
            try {
                Object entityScheduler = entitySchedulerGetter.invoke(entity);
                return entitySchedulerRunRepeating.invoke(entityScheduler, plugin,
                        (Consumer<Object>) o -> task.run(), (Runnable) () -> {}, initial, period);
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia entityRunRepeating failed: " + t.getMessage());
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, initial, period);
    }

    /**
     * Tick-based repeating task returning a {@link BukkitTask} compatible handle (including on Folia).
     */
    public BukkitTask runTimer(Runnable task, long delayTicks, long periodTicks) {
        long initial = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (folia && globalRunRepeating != null) {
            try {
                Object handle = globalRunRepeating.invoke(
                        globalRegionScheduler, plugin, (Consumer<Object>) o -> task.run(), initial, period);
                return new FoliaTaskAdapter(plugin, this, handle);
            } catch (Throwable t) {
                plugin.getLogger().fine("Folia globalRunRepeating failed: " + t.getMessage());
            }
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, task, initial, period);
    }

    public void cancel(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private static final class FoliaTaskAdapter implements BukkitTask {

        private final Plugin owner;
        private final PlatformScheduler scheduler;
        private final Object foliaHandle;
        private volatile boolean cancelled;

        private FoliaTaskAdapter(Plugin owner, PlatformScheduler scheduler, Object foliaHandle) {
            this.owner = owner;
            this.scheduler = scheduler;
            this.foliaHandle = foliaHandle;
        }

        @Override
        public int getTaskId() {
            return -1;
        }

        @Override
        public Plugin getOwner() {
            return owner;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        public boolean isAsynchronous() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            scheduler.cancelTask(foliaHandle);
        }

        public void run() {
            throw new UnsupportedOperationException("Folia scheduled tasks cannot be run manually");
        }
    }
}
