package com.dravorax.casinodx.sound;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.config.ConfigManager;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class SoundService {

    public static final String MENU_OPEN = "menu-open";
    public static final String CLICK = "click";
    public static final String BET = "bet";
    public static final String WIN = "win";
    public static final String LOSS = "loss";
    public static final String CASHOUT = "cashout";
    public static final String CARD_DEAL = "card-deal";
    public static final String ROULETTE_SPIN = "roulette-spin";
    public static final String COINFLIP = "coinflip";
    public static final String CRASH_TICK = "crash-tick";
    public static final String CRASH_EXPLODE = "crash-explode";
    public static final String WHEEL_SPIN = "wheel-spin";
    public static final String SLOTS_SPIN = "slots-spin";
    public static final String REWARD = "reward";

    private final CasinoDX plugin;
    private final ConfigManager configManager;
    private final Map<String, SoundDefinition> sounds = new HashMap<>();
    private boolean globallyEnabled = true;

    public SoundService(CasinoDX plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        sounds.clear();
        globallyEnabled = configManager.sounds().getBoolean("enabled", true);
        ConfigurationSection section = configManager.sounds().getConfigurationSection("sounds");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) continue;
            Sound sound = parseSound(entry.getString("sound", "UI_BUTTON_CLICK"));
            if (sound == null) {
                plugin.getLogger().log(Level.WARNING, "Invalid sound for key {0}", key);
                continue;
            }
            sounds.put(key.toLowerCase(Locale.ROOT), new SoundDefinition(
                    entry.getBoolean("enabled", true),
                    sound,
                    (float) entry.getDouble("volume", 1.0),
                    (float) entry.getDouble("pitch", 1.0)
            ));
        }
    }

    public void play(Player player, String soundKey) {
        if (player == null || soundKey == null || !globallyEnabled) return;
        SoundDefinition def = sounds.get(soundKey.toLowerCase(Locale.ROOT));
        if (def == null || !def.enabled()) return;
        player.playSound(player.getLocation(), def.sound(), def.volume(), def.pitch());
    }

    private Sound parseSound(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record SoundDefinition(boolean enabled, Sound sound, float volume, float pitch) {}
}
