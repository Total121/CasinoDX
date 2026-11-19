package com.dravorax.casinodx.game.wheel;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.money.Money;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Wheel of Fortune — one free spin a day, or pay to spin again.
 *
 * <h2>Section selection</h2>
 * Sections come from {@code modules/wheel/rewards.yml} and each carries a relative {@code chance}
 * (or {@code weight}). {@link #drawSection()} normalises the weights and performs a single weighted
 * draw <em>before</em> the wheel animation is created, so the animation lands on a section that was
 * already chosen. The weights do not need to sum to 100.
 *
 * <p>The free-spin cooldown is persisted in the {@code player_meta} table, so it survives restarts
 * and cannot be reset by reconnecting.
 */
public final class WheelGame extends CasinoGame {

    /** Meta key used in {@code player_meta} for the last free spin timestamp. */
    public static final String META_LAST_FREE_SPIN = "wheel_last_free_spin";

    public static final String FREE_SPIN_PERMISSION = "casinodx.wheel.free";

    private List<WheelReward> sections = List.of();
    private long cooldownMillis;
    private Money paidSpinCost = Money.ZERO;
    private Money multiplierBase = Money.ZERO;
    private int spinTicks;
    private boolean broadcastRare;
    private boolean commandsEnabled;

    public WheelGame(CasinoDX plugin) {
        super(plugin, GameType.WHEEL);
        reload();
    }

    @Override
    public void reload() {
        this.cooldownMillis = TimeUnit.SECONDS.toMillis(
                Math.max(0, config().getInt("spin-cooldown-seconds", 86_400)));
        this.paidSpinCost = Money.of(Math.max(0, config().getDouble("paid-spin-cost", 1_000)));
        // Base used to turn a MULTIPLIER section into cash when the spin itself was free.
        this.multiplierBase = Money.of(Math.max(0, config().getDouble("multiplier-base", 500)));
        this.spinTicks = Math.max(40, Math.min(240, config().getInt("spin-duration-ticks", 110)));
        this.broadcastRare = config().getBoolean("broadcast-rare-rewards", true);
        this.commandsEnabled = config().getBoolean("allow-command-rewards", false);
        this.sections = loadSections();
    }

    private List<WheelReward> loadSections() {
        List<WheelReward> loaded = new ArrayList<>();
        var rewards = payouts();

        for (String key : rewards.getKeys(false)) {
            ConfigurationSection section = rewards.getConfigurationSection(key);
            if (section == null) {
                continue;
            }

            WheelReward.Type type = WheelReward.Type.fromString(section.getString("type"));
            double weight = section.getDouble("chance", section.getDouble("weight", -1));
            if (weight <= 0) {
                weight = 1.0;
            }

            List<String> commands = section.getStringList("commands");
            if (!commands.isEmpty() && !commandsEnabled) {
                plugin.getLogger().warning("[CasinoDX] wheel section '" + key
                        + "' defines commands but allow-command-rewards is false; they will be skipped.");
            }

            loaded.add(new WheelReward(
                    key,
                    section.getString("display-name", prettify(key)),
                    type,
                    parseMaterial(section.getString("material"), defaultIconFor(type)),
                    weight,
                    Money.of(Math.max(0, section.getDouble("money", 0))),
                    section.getDouble("multiplier", 1.0),
                    commands,
                    section.getStringList("items"),
                    section.getBoolean("broadcast", type == WheelReward.Type.MULTIPLIER
                            && section.getDouble("multiplier", 1.0) >= 10.0)
            ));
        }

        if (loaded.isEmpty()) {
            plugin.getLogger().warning("[CasinoDX] modules/wheel/rewards.yml has no sections; "
                    + "using a built-in fallback wheel.");
            loaded = fallbackSections();
        }
        return List.copyOf(loaded);
    }

    /** Keeps the wheel playable when the rewards file is empty or unreadable. */
    private List<WheelReward> fallbackSections() {
        return List.of(
                new WheelReward("common", "Small Prize", WheelReward.Type.MONEY,
                        Material.IRON_NUGGET, 70, Money.of(250), 1.0, List.of(), List.of(), false),
                new WheelReward("rare", "Rare Prize", WheelReward.Type.MONEY,
                        Material.GOLD_INGOT, 25, Money.of(2_500), 1.0, List.of(), List.of(), false),
                new WheelReward("jackpot", "Jackpot", WheelReward.Type.MONEY,
                        Material.NETHER_STAR, 5, Money.of(25_000), 1.0, List.of(), List.of(), true)
        );
    }

    private static String prettify(String key) {
        String spaced = key.replace('-', ' ').replace('_', ' ').trim();
        if (spaced.isEmpty()) {
            return key;
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim());
        return material != null && material.isItem() ? material : fallback;
    }

    private static Material defaultIconFor(WheelReward.Type type) {
        return switch (type) {
            case MONEY -> Material.GOLD_INGOT;
            case MULTIPLIER -> Material.EXPERIENCE_BOTTLE;
            case ITEMS -> Material.CHEST;
            case COMMAND -> Material.COMMAND_BLOCK;
            case NOTHING -> Material.GRAY_DYE;
        };
    }

    public List<WheelReward> sections() {
        return sections;
    }

    public long cooldownMillis() {
        return cooldownMillis;
    }

    public Money paidSpinCost() {
        return paidSpinCost;
    }

    public Money multiplierBase() {
        return multiplierBase;
    }

    public int spinTicks() {
        return spinTicks;
    }

    public boolean broadcastRare() {
        return broadcastRare;
    }

    public boolean commandsEnabled() {
        return commandsEnabled;
    }

    /** Total weight across all sections, used to display each section's real chance. */
    public double totalWeight() {
        double total = 0;
        for (WheelReward reward : sections) {
            total += reward.weight();
        }
        return total <= 0 ? 1 : total;
    }

    public double chanceOf(WheelReward reward) {
        return reward.weight() / totalWeight();
    }

    /**
     * Weighted draw over the configured sections. Called once per spin, before any animation.
     *
     * @return the winning section index
     */
    int drawSection() {
        double roll = randomDouble() * totalWeight();
        double cumulative = 0;
        for (int i = 0; i < sections.size(); i++) {
            cumulative += sections.get(i).weight();
            if (roll < cumulative) {
                return i;
            }
        }
        // Floating-point drift only; the last section absorbs it.
        return sections.size() - 1;
    }

    /** The wheel has its own entry screen rather than the shared bet-selection menu. */
    @Override
    public void openLobby(Player player) {
        openLobby(player, null);
    }

    /**
     * Opens the wheel, optionally spinning the moment it is on screen.
     *
     * <p>The auto-spin path is how "spin again" works after a result: a spun session has already
     * settled and retired itself, so the only way back to a live wheel is a fresh session. Rather
     * than making the player click through an intermediate screen, the freshly opened wheel is told
     * to press its own button. The request still goes through the same validation as a click — the
     * cooldown is re-read from the database on the way in, and a paid spin still has to be
     * afforded — so this is a shortcut through the menus, not through the rules.
     *
     * @param autoSpin {@code null} to just open the wheel, {@code TRUE} for a paid spin,
     *                 {@code FALSE} for the free daily spin
     */
    public void openLobby(Player player, Boolean autoSpin) {
        if (player == null) {
            return;
        }
        if (!isEnabled()) {
            plugin.messages().send(player, "game-disabled");
            return;
        }
        if (!player.hasPermission(permission())) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        // The cooldown lives in the database, so read it before opening the menu. A wheel session
        // carries no wager until the player chooses a paid spin, so it is opened with a zero bet.
        loadCooldown(player.getUniqueId(), remaining -> {
            WheelSession session = beginSession(player, Money.ZERO,
                    wager -> new WheelSession(plugin, player, this, wager));
            if (session == null) {
                return;
            }
            session.openWheel(remaining);
            if (autoSpin != null) {
                session.triggerSpin(autoSpin);
            }
        });
    }

    /**
     * Reads the player's remaining free-spin cooldown asynchronously and hands the result back on
     * the main thread.
     *
     * @param callback receives the remaining cooldown in milliseconds ({@code 0} when ready)
     */
    void loadCooldown(UUID playerId, java.util.function.LongConsumer callback) {
        if (cooldownMillis <= 0) {
            callback.accept(0L);
            return;
        }
        plugin.storage().getMeta(playerId, META_LAST_FREE_SPIN, value -> {
            long remaining = remainingCooldown(value);
            plugin.scheduler().runGlobal(() -> callback.accept(remaining));
        });
    }

    private long remainingCooldown(Optional<String> storedValue) {
        if (storedValue.isEmpty()) {
            return 0L;
        }
        try {
            long last = Long.parseLong(storedValue.get().trim());
            long elapsed = System.currentTimeMillis() - last;
            return Math.max(0L, cooldownMillis - elapsed);
        } catch (NumberFormatException ex) {
            // Corrupt value: treat the free spin as available rather than locking the player out.
            return 0L;
        }
    }

    void markFreeSpinUsed(UUID playerId) {
        plugin.storage().setMeta(playerId, META_LAST_FREE_SPIN,
                Long.toString(System.currentTimeMillis()));
    }

    /**
     * The wheel is not a bet-an-amount game, so the shared bet screen is bypassed entirely and any
     * requested amount is ignored in favour of the configured spin cost.
     */
    @Override
    public void startSession(Player player, Money bet) {
        openLobby(player);
    }
}
