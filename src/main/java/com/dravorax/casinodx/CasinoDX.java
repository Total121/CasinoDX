package com.dravorax.casinodx;

import com.dravorax.casinodx.animation.AnimationEngine;
import com.dravorax.casinodx.bet.BetInputService;
import com.dravorax.casinodx.bet.ChatBetListener;
import com.dravorax.casinodx.bet.LastPlayContext;
import com.dravorax.casinodx.command.CasinoCommand;
import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.economy.EconomyService;
import com.dravorax.casinodx.game.GameRegistry;
import com.dravorax.casinodx.game.SessionManager;
import com.dravorax.casinodx.game.blackjack.BlackjackGame;
import com.dravorax.casinodx.game.coinflip.CoinflipGame;
import com.dravorax.casinodx.game.crash.CrashGame;
import com.dravorax.casinodx.game.mines.MinesGame;
import com.dravorax.casinodx.game.roulette.RouletteGame;
import com.dravorax.casinodx.game.slots.SlotsGame;
import com.dravorax.casinodx.game.wheel.PendingRewardsService;
import com.dravorax.casinodx.game.wheel.WheelGame;
import com.dravorax.casinodx.gui.GuiListener;
import com.dravorax.casinodx.gui.GuiManager;
import com.dravorax.casinodx.gui.HeadTextures;
import com.dravorax.casinodx.history.HistoryService;
import com.dravorax.casinodx.scheduler.PlatformScheduler;
import com.dravorax.casinodx.security.AntiExploitGuard;
import com.dravorax.casinodx.sound.SoundService;
import com.dravorax.casinodx.stats.StatisticsService;
import com.dravorax.casinodx.storage.StorageService;
import com.dravorax.casinodx.tax.TaxService;
import com.dravorax.casinodx.util.ConsoleBanner;
import com.dravorax.casinodx.util.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * CasinoDX — Premium Minecraft Casino System by DravoraX.
 */
public final class CasinoDX extends JavaPlugin {

    private static CasinoDX instance;

    private PlatformScheduler scheduler;
    private ConfigManager configManager;
    private EconomyService economy;
    private TaxService taxService;
    private StorageService storage;
    private StatisticsService statistics;
    private HistoryService history;
    private MessageService messages;
    private SoundService sounds;
    private HeadTextures heads;
    private AnimationEngine animations;
    private GuiManager guiManager;
    private SessionManager sessions;
    private GameRegistry games;
    private AntiExploitGuard antiExploit;
    private PendingRewardsService pendingRewards;
    private BetInputService betInput;
    private LastPlayContext lastPlay;
    private boolean fullyEnabled;

    @Override
    public void onEnable() {
        instance = this;
        fullyEnabled = false;

        try {
            this.scheduler = new PlatformScheduler(this);
            this.configManager = new ConfigManager(this);
            this.configManager.loadAll();
            com.dravorax.casinodx.gui.ColorPalette.reload(configManager);

            if (!configManager.main().getBoolean("enabled", true)) {
                getLogger().warning("CasinoDX is disabled in config.yml (enabled: false).");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            this.messages = new MessageService(this, configManager);
            this.heads = new HeadTextures(configManager);
            this.sounds = new SoundService(this, configManager);
            this.economy = new EconomyService(this, configManager);
            this.taxService = new TaxService(configManager);
            this.antiExploit = new AntiExploitGuard();
            this.animations = new AnimationEngine(this, scheduler);
            this.sessions = new SessionManager();
            this.storage = new StorageService(this, configManager, scheduler);
            this.storage.init();
            this.statistics = new StatisticsService(this, storage, configManager);
            this.history = new HistoryService(this, storage, configManager);
            this.guiManager = new GuiManager(this);
            this.games = new GameRegistry();
            this.pendingRewards = new PendingRewardsService(this);
            this.lastPlay = new LastPlayContext();
            this.betInput = new BetInputService(this);

            registerGames();
            registerCommands();
            registerListeners();

            fullyEnabled = true;
            ConsoleBanner.print(this);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "CasinoDX failed to start. Disabling.", t);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void registerGames() {
        games.register(new MinesGame(this));
        games.register(new RouletteGame(this));
        games.register(new BlackjackGame(this));
        games.register(new CoinflipGame(this));
        games.register(new CrashGame(this));
        games.register(new WheelGame(this));
        games.register(new SlotsGame(this));
    }

    private void registerCommands() {
        CasinoCommand cmd = new CasinoCommand(this);
        var pluginCmd = getCommand("casino");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new GuiListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        ChatBetListener.register(this);
    }

    @Override
    public void onDisable() {
        fullyEnabled = false;
        try {
            if (sessions != null) {
                sessions.forceEndAll("Plugin disabled");
            }
            if (animations != null) {
                animations.shutdown();
            }
            if (guiManager != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (guiManager.isCasinoInventory(p.getOpenInventory().getTopInventory())) {
                        p.closeInventory();
                    }
                }
            }
            if (betInput != null) {
                betInput.clearAll();
            }
            if (lastPlay != null) {
                lastPlay.clearAll();
            }
            if (storage != null) {
                storage.shutdown();
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Error during CasinoDX shutdown", t);
        }
        instance = null;
    }

    public boolean reloadSafe() {
        if (!fullyEnabled) return false;
        try {
            if (sessions.hasActiveSessions()) {
                getLogger().warning("Reload aborted: active game sessions exist. Ask players to finish first.");
                return false;
            }
            animations.cancelAll();
            configManager.loadAll();
            com.dravorax.casinodx.gui.ColorPalette.reload(configManager);
            messages.reload();
            heads.reload();
            sounds.reload();
            taxService.reload();
            economy.reloadHints();
            games.reloadAll();
            getLogger().info("CasinoDX configuration reloaded successfully.");
            return true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Reload failed — previous valid config retained where possible.", t);
            return false;
        }
    }

    public static CasinoDX get() {
        return instance;
    }

    public boolean isFullyEnabled() { return fullyEnabled; }
    public PlatformScheduler scheduler() { return scheduler; }
    public ConfigManager configs() { return configManager; }
    public EconomyService economy() { return economy; }
    public TaxService tax() { return taxService; }
    public StorageService storage() { return storage; }
    public StatisticsService statistics() { return statistics; }
    public HistoryService history() { return history; }
    public MessageService messages() { return messages; }
    public SoundService sounds() { return sounds; }
    public HeadTextures heads() { return heads; }
    public AnimationEngine animations() { return animations; }
    public GuiManager guis() { return guiManager; }
    public SessionManager sessions() { return sessions; }
    public GameRegistry games() { return games; }
    public AntiExploitGuard antiExploit() { return antiExploit; }
    public PendingRewardsService pendingRewards() { return pendingRewards; }
    public BetInputService betInput() { return betInput; }
    public LastPlayContext lastPlay() { return lastPlay; }
}
