package com.dravorax.casinodx.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and validates all CasinoDX YAML configuration files.
 * Keeps the last known-good copy when a reload fails.
 */
public final class ConfigManager {

    private static final String[] GAME_MODULES = {
            "mines", "roulette", "blackjack", "coinflip", "crash", "wheel", "slots"
    };

    private static final String[] SUPPORT_MODULES = {
            "gui", "economy", "tax", "statistics", "history", "messages", "sounds"
    };

    private final JavaPlugin plugin;
    private final Logger logger;

    private YamlConfiguration mainConfig = new YamlConfiguration();
    private YamlConfiguration guiComposite = new YamlConfiguration();
    private final Map<String, YamlConfiguration> moduleFiles = new LinkedHashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void loadAll() {
        ensureDefaultResources();
        loadMainConfig();
        loadAllModules();
        logValidationResults(validateAll());
    }

    public void reload() {
        loadAll();
    }

    public YamlConfiguration main() {
        return mainConfig;
    }

    /** Merged GUI configuration (config + main-menu + colors + animations + sounds). */
    public YamlConfiguration gui() {
        return guiComposite;
    }

    /** Raw {@code modules/gui/colors.yml}. */
    public YamlConfiguration colors() {
        return moduleFile("gui", "colors.yml");
    }

    /** Raw {@code modules/sounds/config.yml}. */
    public YamlConfiguration sounds() {
        return module("sounds");
    }

    /** Raw {@code modules/messages/messages.yml}. */
    public YamlConfiguration messages() {
        return moduleFile("messages", "messages.yml");
    }

    public YamlConfiguration module(String moduleName) {
        return moduleFile(moduleName, "config.yml");
    }

    /** Convenience accessor for a game module's payout/reward table. */
    public YamlConfiguration payouts(String moduleName) {
        return moduleFile(moduleName, "wheel".equalsIgnoreCase(moduleName) ? "rewards.yml" : "payouts.yml");
    }

    public YamlConfiguration moduleFile(String moduleName, String fileName) {
        String key = cacheKey(moduleName, fileName);
        YamlConfiguration config = moduleFiles.get(key);
        if (config != null) {
            return config;
        }
        return new YamlConfiguration();
    }

    public List<String> validateAll() {
        List<String> errors = new ArrayList<>();

        validateMain(errors);
        for (String game : GAME_MODULES) {
            validateGameModule(game, errors);
        }
        validateGuiModule(errors);
        validateEconomyModule(errors);
        validateTaxModule(errors);
        validateStatisticsModule(errors);
        validateHistoryModule(errors);
        validateMessagesModule(errors);
        validateSoundsModule(errors);

        return errors;
    }

    private void ensureDefaultResources() {
        saveDefaultIfMissing("config.yml");

        for (String game : GAME_MODULES) {
            saveDefaultIfMissing("modules/" + game + "/config.yml");
            if ("wheel".equals(game)) {
                saveDefaultIfMissing("modules/" + game + "/rewards.yml");
            } else {
                saveDefaultIfMissing("modules/" + game + "/payouts.yml");
            }
        }

        saveDefaultIfMissing("modules/gui/config.yml");
        saveDefaultIfMissing("modules/gui/main-menu.yml");
        saveDefaultIfMissing("modules/gui/colors.yml");
        saveDefaultIfMissing("modules/gui/animations.yml");
        saveDefaultIfMissing("modules/gui/sounds.yml");
        saveDefaultIfMissing("modules/gui/heads.yml");

        for (String module : new String[]{"economy", "tax", "statistics", "history", "sounds"}) {
            saveDefaultIfMissing("modules/" + module + "/config.yml");
        }
        saveDefaultIfMissing("modules/messages/messages.yml");
    }

    private void saveDefaultIfMissing(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (!target.getParentFile().exists() && !target.getParentFile().mkdirs()) {
            logger.warning("Failed to create directory: " + target.getParentFile().getAbsolutePath());
        }
        if (!target.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException ex) {
                logger.warning("Missing bundled default resource: " + resourcePath);
                try {
                    if (target.createNewFile()) {
                        YamlConfiguration empty = new YamlConfiguration();
                        empty.save(target);
                    }
                } catch (IOException io) {
                    logger.log(Level.WARNING, "Could not create empty config file: " + target.getName(), io);
                }
            }
        }
    }

    private void loadMainConfig() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        mainConfig = loadYamlFile("config.yml", file, mainConfig);
    }

    private void loadAllModules() {
        for (String game : GAME_MODULES) {
            loadModuleFile(game, "config.yml");
            if ("wheel".equals(game)) {
                loadModuleFile(game, "rewards.yml");
            } else {
                loadModuleFile(game, "payouts.yml");
            }
        }

        loadModuleFile("gui", "config.yml");
        loadModuleFile("gui", "main-menu.yml");
        loadModuleFile("gui", "colors.yml");
        loadModuleFile("gui", "animations.yml");
        loadModuleFile("gui", "sounds.yml");
        loadModuleFile("gui", "heads.yml");

        loadModuleFile("economy", "config.yml");
        loadModuleFile("tax", "config.yml");
        loadModuleFile("statistics", "config.yml");
        loadModuleFile("history", "config.yml");
        loadModuleFile("messages", "messages.yml");
        loadModuleFile("sounds", "config.yml");
        rebuildGuiComposite();
    }

    private void rebuildGuiComposite() {
        YamlConfiguration composite = cloneOrKeep(module("gui"));
        nestYaml(composite, "main-menu", moduleFile("gui", "main-menu.yml"));
        nestYaml(composite, "colors", moduleFile("gui", "colors.yml"));
        nestYaml(composite, "animations", moduleFile("gui", "animations.yml"));
        nestYaml(composite, "sounds", moduleFile("gui", "sounds.yml"));
        // heads.yml is already rooted at `heads:`, so it merges in at its own paths rather than
        // being nested again — otherwise lookups would have to go through `heads.heads.*`.
        mergeYaml(composite, moduleFile("gui", "heads.yml"));
        this.guiComposite = composite;
    }

    /** Copies every leaf value from {@code source} into {@code target} at its own path. */
    private static void mergeYaml(YamlConfiguration target, YamlConfiguration source) {
        if (source == null) {
            return;
        }
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            target.set(key, source.get(key));
        }
    }

    private static void nestYaml(YamlConfiguration target, String section, YamlConfiguration source) {
        if (source == null || source.getKeys(true).isEmpty()) {
            return;
        }
        target.createSection(section);
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            target.set(section + "." + key, source.get(key));
        }
    }

    private void loadModuleFile(String moduleName, String fileName) {
        String relative = "modules/" + moduleName + "/" + fileName;
        File file = new File(plugin.getDataFolder(), relative);
        String key = cacheKey(moduleName, fileName);
        YamlConfiguration previous = moduleFiles.getOrDefault(key, defaultFromResource(relative));
        YamlConfiguration loaded = loadYamlFile(relative, file, previous);
        moduleFiles.put(key, loaded);
    }

    private YamlConfiguration loadYamlFile(String relativePath, File file, YamlConfiguration fallback) {
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            mergeDefaults(relativePath, loaded);
            return loaded;
        } catch (IOException | InvalidConfigurationException ex) {
            logger.log(Level.SEVERE, "[CasinoDX] ERROR: " + relativePath + " Reason: failed to load — "
                    + ex.getMessage() + " (keeping previous valid configuration)");
            return cloneOrKeep(fallback);
        }
    }

    private YamlConfiguration defaultFromResource(String relativePath) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(relativePath)) {
            if (stream != null) {
                defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        return defaults;
    }

    private void mergeDefaults(String relativePath, YamlConfiguration target) {
        YamlConfiguration defaults = defaultFromResource(relativePath);
        if (defaults.getKeys(true).isEmpty()) {
            return;
        }
        target.setDefaults(defaults);
        target.options().copyDefaults(true);
    }

    private YamlConfiguration cloneOrKeep(YamlConfiguration source) {
        if (source == null) {
            return new YamlConfiguration();
        }
        YamlConfiguration clone = new YamlConfiguration();
        clone.addDefaults(source);
        clone.options().copyDefaults(true);
        for (String key : source.getKeys(true)) {
            clone.set(key, source.get(key));
        }
        return clone;
    }

    private static String cacheKey(String moduleName, String fileName) {
        return moduleName.toLowerCase(Locale.ROOT) + "/" + fileName.toLowerCase(Locale.ROOT);
    }

    private void validateMain(List<String> errors) {
        addError(errors, "config.yml", "debug",
                ConfigValidator.validateEnum(mainConfig, "debug", "true", "false"));
    }

    private void validateGameModule(String module, List<String> errors) {
        YamlConfiguration config = module(module);
        String base = "modules/" + module + "/config.yml";

        addError(errors, base, "minimum-bet,maximum-bet",
                ConfigValidator.validateMoneyRange(config, "minimum-bet", "maximum-bet"));
        addError(errors, base, "minimum-bet",
                ConfigValidator.validatePositiveMoney(config, "minimum-bet"));
        addError(errors, base, "maximum-bet",
                ConfigValidator.validatePositiveMoney(config, "maximum-bet"));
        addError(errors, base, "house-edge",
                ConfigValidator.validatePercentage(config, "house-edge"));
        addError(errors, base, "enabled",
                ConfigValidator.validateEnum(config, "enabled", "true", "false"));

        if ("mines".equals(module)) {
            if (config.contains("grid-size")) {
                int size = config.getInt("grid-size");
                if (size < 3 || size > 8) {
                    addError(errors, base, "grid-size", Optional.of(
                            "must be between 3 and 8 (got " + size + ")"));
                }
            }
            if (config.contains("mine-count") && config.contains("grid-size")) {
                int mines = config.getInt("mine-count");
                int cells = config.getInt("grid-size") * config.getInt("grid-size");
                if (mines < 1 || mines >= cells) {
                    addError(errors, base, "mine-count", Optional.of(
                            "must be between 1 and " + (cells - 1) + " (got " + mines + ")"));
                }
            }
        }

        if ("crash".equals(module)) {
            addError(errors, base, "max-multiplier",
                    ConfigValidator.validateMultiplier(config, "max-multiplier"));
            if (config.contains("min-multiplier") && config.contains("max-multiplier")) {
                double min = config.getDouble("min-multiplier");
                double max = config.getDouble("max-multiplier");
                if (min > max) {
                    addError(errors, base, "min-multiplier", Optional.of(
                            "minimum multiplier (" + min + ") cannot exceed maximum (" + max + ")"));
                }
            }
            if (config.contains("countdown-seconds")) {
                int countdown = config.getInt("countdown-seconds");
                if (countdown < 0 || countdown > 10) {
                    addError(errors, base, "countdown-seconds", Optional.of(
                            "must be between 0 and 10 seconds (got " + countdown + ")"));
                }
            }
        }

        if ("slots".equals(module)) {
            validateSlotsModule(config, base, errors);
        }

        if ("wheel".equals(module) && config.contains("spin-cooldown-seconds")) {
            int cooldown = config.getInt("spin-cooldown-seconds");
            if (cooldown < 0) {
                addError(errors, base, "spin-cooldown-seconds", Optional.of(
                        "must be zero or positive (got " + cooldown + ")"));
            }
        }

        String payoutFile = "wheel".equals(module) ? "rewards.yml" : "payouts.yml";
        YamlConfiguration payouts = moduleFile(module, payoutFile);
        String payoutPath = "modules/" + module + "/" + payoutFile;
        if (payouts.getKeys(false).isEmpty()) {
            addError(errors, payoutPath, "(root)", Optional.of("file is empty or missing payout entries"));
        }
        for (String key : payouts.getKeys(false)) {
            addError(errors, payoutPath, key + ".chance",
                    ConfigValidator.validatePercentage(payouts, key + ".chance"));
            addError(errors, payoutPath, key + ".multiplier",
                    ConfigValidator.validateMultiplier(payouts, key + ".multiplier"));
        }
    }

    /**
     * Slots is the only game whose payout file defines the reel strip itself, so the symbols need
     * checking for things the generic payout loop cannot see: a usable material, and a weight that
     * actually lets the symbol land.
     */
    private void validateSlotsModule(YamlConfiguration config, String base, List<String> errors) {
        if (config.contains("reels")) {
            int reels = config.getInt("reels");
            if (reels < 3 || reels > 5) {
                addError(errors, base, "reels", Optional.of(
                        "must be between 3 and 5 (got " + reels + ")"));
            }
        }
        if (config.contains("spin-ticks")) {
            int ticks = config.getInt("spin-ticks");
            if (ticks < 20 || ticks > 200) {
                addError(errors, base, "spin-ticks", Optional.of(
                        "must be between 20 and 200 ticks (got " + ticks + ")"));
            }
        }

        String payoutPath = "modules/slots/payouts.yml";
        var symbols = moduleFile("slots", "payouts.yml").getConfigurationSection("symbols");
        if (symbols == null || symbols.getKeys(false).size() < 2) {
            addError(errors, payoutPath, "symbols", Optional.of(
                    "needs at least two symbols; the built-in reel strip will be used instead"));
            return;
        }

        for (String key : symbols.getKeys(false)) {
            var entry = symbols.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            addError(errors, payoutPath, "symbols." + key + ".material",
                    ConfigValidator.validateMaterial(entry.getString("material")));
            if (entry.getDouble("weight", 0) <= 0) {
                addError(errors, payoutPath, "symbols." + key + ".weight", Optional.of(
                        "must be greater than zero or the symbol can never land"));
            }
            if (entry.getDouble("three-match", 0) < 0 || entry.getDouble("two-match", 0) < 0) {
                addError(errors, payoutPath, "symbols." + key, Optional.of(
                        "multipliers must be zero or positive"));
            }
        }
    }

    private void validateGuiModule(List<String> errors) {
        YamlConfiguration gui = module("gui");
        String base = "modules/gui/config.yml";

        addError(errors, base, "rows", validateGuiRows(gui.getInt("rows", 6)));
        addError(errors, base, "title", validateNonEmpty(gui.getString("title"), "title"));

        YamlConfiguration menu = moduleFile("gui", "main-menu.yml");
        for (String err : ConfigValidator.validateSectionSlots(menu.getConfigurationSection("items"))) {
            addError(errors, "modules/gui/main-menu.yml", "items", Optional.of(err));
        }
        var menuItems = menu.getConfigurationSection("items");
        for (String err : ConfigValidator.validateSectionMaterials(menuItems)) {
            addError(errors, "modules/gui/main-menu.yml", "items", Optional.of(err));
        }

        YamlConfiguration colors = moduleFile("gui", "colors.yml");
        if (colors.getKeys(false).isEmpty()) {
            addError(errors, "modules/gui/colors.yml", "(root)",
                    Optional.of("no color definitions found"));
        }

        YamlConfiguration animations = moduleFile("gui", "animations.yml");
        if (animations.contains("tick-interval")) {
            int interval = animations.getInt("tick-interval");
            if (interval < 1 || interval > 20) {
                addError(errors, "modules/gui/animations.yml", "tick-interval", Optional.of(
                        "must be between 1 and 20 ticks (got " + interval + ")"));
            }
        }

        YamlConfiguration guiSounds = moduleFile("gui", "sounds.yml");
        for (String key : guiSounds.getKeys(true)) {
            if (guiSounds.isString(key)) {
                addError(errors, "modules/gui/sounds.yml", key,
                        ConfigValidator.validateSound(guiSounds.getString(key)));
            }
        }

        YamlConfiguration headsFile = moduleFile("gui", "heads.yml");
        var headEntries = headsFile.getConfigurationSection("heads");
        if (headEntries == null || headEntries.getKeys(false).isEmpty()) {
            // Not fatal: HeadTextures falls back to its built-in catalog.
            addError(errors, "modules/gui/heads.yml", "heads",
                    Optional.of("no head textures defined; built-in defaults will be used"));
        } else {
            for (String key : headEntries.getKeys(false)) {
                String value = headEntries.getString(key);
                if (value == null || value.isBlank()) {
                    addError(errors, "modules/gui/heads.yml", "heads." + key,
                            Optional.of("value is empty"));
                }
            }
        }
    }

    private void validateEconomyModule(List<String> errors) {
        YamlConfiguration economy = module("economy");
        String base = "modules/economy/config.yml";
        addError(errors, base, "provider",
                ConfigValidator.validateEnum(economy, "provider", "vault", "vaultunlocked", "auto"));
        addError(errors, base, "currency-symbol",
                validateNonEmpty(economy.getString("currency-symbol"), "currency symbol"));
    }

    private void validateTaxModule(List<String> errors) {
        YamlConfiguration tax = module("tax");
        String base = "modules/tax/config.yml";

        addError(errors, base, "percentage", ConfigValidator.validatePercentage(tax, "percentage"));
        addError(errors, base, "apply-to",
                ConfigValidator.validateEnum(tax, "apply-to", "profit", "gross"));
        addError(errors, base, "minimum-taxable",
                ConfigValidator.validatePositiveMoney(tax, "minimum-taxable"));
        addError(errors, base, "maximum-tax",
                ConfigValidator.validatePositiveMoney(tax, "maximum-tax"));

        if (tax.contains("minimum-taxable") && tax.contains("maximum-tax")) {
            double min = tax.getDouble("minimum-taxable");
            double max = tax.getDouble("maximum-tax");
            if (max > 0 && min > max) {
                addError(errors, base, "minimum-taxable", Optional.of(
                        "minimum taxable (" + min + ") cannot exceed maximum tax (" + max + ")"));
            }
        }

        var overrides = tax.getConfigurationSection("per-game");
        if (overrides != null) {
            for (String game : overrides.getKeys(false)) {
                String prefix = "per-game." + game;
                addError(errors, base, prefix + ".percentage",
                        ConfigValidator.validatePercentage(tax, prefix + ".percentage"));
                addError(errors, base, prefix + ".apply-to",
                        ConfigValidator.validateEnum(tax, prefix + ".apply-to", "profit", "gross"));
            }
        }
    }

    private void validateStatisticsModule(List<String> errors) {
        YamlConfiguration stats = module("statistics");
        String base = "modules/statistics/config.yml";
        if (stats.contains("flush-interval-seconds")) {
            int interval = stats.getInt("flush-interval-seconds");
            if (interval < 5) {
                addError(errors, base, "flush-interval-seconds", Optional.of(
                        "must be at least 5 seconds (got " + interval + ")"));
            }
        }
    }

    private void validateHistoryModule(List<String> errors) {
        YamlConfiguration history = module("history");
        String base = "modules/history/config.yml";
        if (history.contains("retention-days")) {
            int days = history.getInt("retention-days");
            if (days < 1) {
                addError(errors, base, "retention-days", Optional.of(
                        "must be at least 1 day (got " + days + ")"));
            }
        }
        if (history.contains("page-size")) {
            int pageSize = history.getInt("page-size");
            if (pageSize < 1 || pageSize > 45) {
                addError(errors, base, "page-size", Optional.of(
                        "must be between 1 and 45 (got " + pageSize + ")"));
            }
        }
    }

    private void validateMessagesModule(List<String> errors) {
        YamlConfiguration messages = moduleFile("messages", "messages.yml");
        if (messages.getKeys(true).isEmpty()) {
            addError(errors, "modules/messages/messages.yml", "(root)",
                    Optional.of("no message entries found"));
        }
    }

    private void validateSoundsModule(List<String> errors) {
        YamlConfiguration sounds = module("sounds");
        String base = "modules/sounds/config.yml";
        for (String key : sounds.getKeys(true)) {
            if (sounds.isString(key)) {
                addError(errors, base, key, ConfigValidator.validateSound(sounds.getString(key)));
            }
        }
    }

    private Optional<String> validateGuiRows(int rows) {
        if (rows < 1 || rows > 6) {
            return Optional.of("rows must be between 1 and 6 (got " + rows + ")");
        }
        return Optional.empty();
    }

    private Optional<String> validateNonEmpty(String value, String label) {
        if (value == null || value.isBlank()) {
            return Optional.of(label + " cannot be empty");
        }
        return Optional.empty();
    }

    private void addError(List<String> errors, String file, String setting, Optional<String> reason) {
        if (reason.isPresent()) {
            String message = "[CasinoDX] ERROR: " + file + " Setting: " + setting + " Reason: " + reason.get();
            errors.add(message);
        }
    }

    private void logValidationResults(List<String> errors) {
        if (errors.isEmpty()) {
            logger.info("[CasinoDX] Configuration validation passed.");
            return;
        }
        for (String error : errors) {
            logger.severe(error);
        }
        logger.warning("[CasinoDX] Configuration loaded with " + errors.size() + " validation error(s).");
    }

    public Map<String, YamlConfiguration> snapshotModuleFiles() {
        return Collections.unmodifiableMap(moduleFiles);
    }

}
