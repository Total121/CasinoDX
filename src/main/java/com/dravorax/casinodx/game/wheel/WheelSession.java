package com.dravorax.casinodx.game.wheel;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameSession;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.game.PayoutService;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.sound.SoundService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A single visit to the Wheel of Fortune.
 *
 * <p>The session is created with a zero wager when the menu opens; a paid spin escrows the spin
 * cost through {@link GameSession#addEscrow(Money)} only once the player has actually committed to
 * it. The winning section is drawn in {@link #spin(boolean)} before the animation is built, and
 * {@link #finishSpin()} pays out against that stored index — the animation cannot change it.
 */
public final class WheelSession extends GameSession {

    private final WheelGame game;

    private WheelGui gui;
    private WheelSpinAnimation animation;

    /** Index into {@link WheelGame#sections()}, decided server-side. {@code -1} until spun. */
    private int winningIndex = -1;
    /** Highlighted section while the wheel is turning; purely cosmetic. */
    private int displayIndex;
    private boolean paidSpin;
    private boolean rewardsGranted;
    private long cooldownRemaining;
    private String resultSummary = "";

    public WheelSession(CasinoDX plugin, Player player, WheelGame game, Money bet) {
        super(plugin, player, GameType.WHEEL, bet);
        this.game = game;
    }

    public WheelGame game() {
        return game;
    }

    public int displayIndex() {
        return displayIndex;
    }

    public void displayIndex(int index) {
        this.displayIndex = index;
    }

    public int winningIndex() {
        return winningIndex;
    }

    public boolean hasResult() {
        return winningIndex >= 0 && animation == null;
    }

    public boolean spinning() {
        return state() == State.ANIMATING;
    }

    public boolean paidSpin() {
        return paidSpin;
    }

    public long cooldownRemaining() {
        return cooldownRemaining;
    }

    public String resultSummary() {
        return resultSummary;
    }

    /** The section the wheel landed on, or {@code null} while the outcome is still hidden. */
    public WheelReward result() {
        if (winningIndex < 0 || winningIndex >= game.sections().size()) {
            return null;
        }
        return game.sections().get(winningIndex);
    }

    /** True when the player's free spin is available right now. */
    public boolean freeSpinReady() {
        Player player = player();
        if (player != null && player.hasPermission(WheelGame.FREE_SPIN_PERMISSION)) {
            // Staff and rank perks bypass the daily cooldown entirely.
            return true;
        }
        return cooldownRemaining <= 0;
    }

    public void openWheel(long cooldownRemaining) {
        this.cooldownRemaining = Math.max(0, cooldownRemaining);
        Player player = player();
        if (player == null) {
            forceEnd("Player offline");
            return;
        }
        this.gui = new WheelGui(plugin, player, this);
        plugin.guis().open(gui);
    }

    /**
     * Presses one of the wheel's own spin buttons on the player's behalf, used by the "spin again"
     * path. Routed through the view so the cooldown check and the paid-spin escrow are exactly the
     * ones a real click would take.
     */
    void triggerSpin(boolean paid) {
        if (gui != null) {
            gui.requestSpin(paid);
        }
    }

    /**
     * Draws the winning section and starts the reveal animation.
     *
     * @param paid {@code true} for a paid spin (cost already escrowed), {@code false} for the free
     *             daily spin
     */
    public void spin(boolean paid) {
        if (state() != State.ACTIVE || winningIndex >= 0) {
            return;
        }
        Player player = player();
        if (player == null) {
            forceEnd("Player offline");
            return;
        }
        if (game.sections().isEmpty()) {
            plugin.messages().send(player, "game-disabled");
            forceEnd("No wheel sections configured");
            return;
        }

        this.paidSpin = paid;

        // 1. Decide the outcome. Everything below is presentation.
        this.winningIndex = game.drawSection();
        this.displayIndex = winningIndex;

        if (!paid) {
            // Burn the free spin immediately so a disconnect mid-animation cannot recycle it.
            game.markFreeSpinUsed(playerId());
            this.cooldownRemaining = game.cooldownMillis();
        }

        // 2. Lock input and visualise the decision.
        state(State.ANIMATING);
        if (gui != null) {
            gui.refresh();
        }
        plugin.sounds().play(player, SoundService.WHEEL_SPIN);

        this.animation = WheelSpinAnimation.create(player, this, gui, game.spinTicks());
        plugin.animations().register(animation);
    }

    /** Called by the animation once the wheel has stopped on {@link #winningIndex}. */
    void finishSpin() {
        this.animation = null;
        if (isSettled() || isFinished()) {
            return;
        }
        WheelReward reward = result();
        if (reward == null) {
            forceEnd("Wheel section vanished");
            return;
        }

        this.displayIndex = winningIndex;
        this.resultSummary = reward.summary();

        Player player = player();
        if (player == null) {
            // Offline before the payout: settleDecidedOutcome honours the draw on next touch.
            forceEnd("Player disconnected during spin");
            return;
        }

        Money stake = totalEscrow();
        settleReward(player, reward);
        plugin.lastPlay().record(playerId(), type(), stake, null,
                !reward.isNothing(), reward.displayName(), cashValueOf(reward, stake));

        if (gui != null) {
            gui.refresh();
        }
        plugin.sessions().end(playerId());
    }

    /**
     * Grants the drawn section and resolves the escrow exactly once.
     *
     * <p>Cash and multiplier sections are paid through the shared payout service so tax, statistics
     * and history all behave like any other game. Item and command sections have no cash value, so
     * they settle as a loss of the (possibly zero) spin cost and are granted separately.
     */
    private void settleReward(Player player, WheelReward reward) {
        Money stake = totalEscrow();
        Money cash = cashValueOf(reward, stake);

        // Items and commands are granted before the settlement claim so that a payout failure
        // cannot silently swallow them, and they are guarded against running twice.
        grantNonCashRewards(player, reward);

        if (cash.isPositive()) {
            double multiplier = stake.isPositive() ? cash.toDouble() / stake.toDouble() : 1.0;
            PayoutService.settleWin(plugin, player, this, stake, cash, multiplier, reward.displayName());
        } else {
            PayoutService.settleLoss(plugin, player, this, stake, 0.0, reward.displayName());
            if (!stake.isPositive()) {
                plugin.messages().send(player, "wheel-no-reward", "reward", reward.displayName());
                plugin.sounds().play(player, SoundService.LOSS);
            }
        }

        announce(player, reward, cash);
    }

    /**
     * Cash value of a section. A {@link WheelReward.Type#MULTIPLIER} section multiplies the paid
     * spin cost; for a free spin there is nothing to multiply, so the configured
     * {@code multiplier-base} stands in.
     */
    private Money cashValueOf(WheelReward reward, Money stake) {
        return switch (reward.type()) {
            case MONEY -> reward.money();
            case MULTIPLIER -> {
                Money base = stake.isPositive() ? stake : game.multiplierBase();
                yield base.multiply(Math.max(0, reward.multiplier()));
            }
            case ITEMS, COMMAND, NOTHING -> Money.ZERO;
        };
    }

    private void grantNonCashRewards(Player player, WheelReward reward) {
        if (rewardsGranted) {
            return;
        }
        rewardsGranted = true;

        for (String spec : reward.items()) {
            ItemStack stack = parseItem(spec);
            if (stack == null) {
                plugin.getLogger().warning("[CasinoDX] wheel section '" + reward.id()
                        + "' has an unreadable item entry: " + spec);
                continue;
            }
            // Drop overflow at the player's feet rather than deleting it.
            player.getInventory().addItem(stack).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }

        if (reward.commands().isEmpty()) {
            return;
        }
        if (!game.commandsEnabled()) {
            plugin.getLogger().warning("[CasinoDX] Skipped " + reward.commands().size()
                    + " command reward(s) for wheel section '" + reward.id()
                    + "': allow-command-rewards is disabled.");
            return;
        }
        for (String command : reward.commands()) {
            String resolved = command
                    .replace("{player}", player.getName())
                    .replace("%player%", player.getName());
            if (resolved.startsWith("/")) {
                resolved = resolved.substring(1);
            }
            String toRun = resolved;
            plugin.scheduler().runGlobal(() -> {
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), toRun);
                } catch (Throwable t) {
                    plugin.getLogger().severe("[CasinoDX] Wheel reward command failed ('"
                            + toRun + "'): " + t.getMessage());
                }
            });
        }
    }

    /** Parses {@code MATERIAL:amount} into a stack. */
    private ItemStack parseItem(String spec) {
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

    private void announce(Player player, WheelReward reward, Money cash) {
        plugin.sounds().play(player, SoundService.REWARD);
        plugin.messages().send(player, "wheel-result",
                "reward", reward.displayName(),
                "amount", plugin.economy().format(cash));

        if (!reward.broadcast() || !game.broadcastRare()) {
            return;
        }
        plugin.messages().broadcast("wheel-broadcast",
                "player", player.getName(),
                "reward", reward.summary(),
                "amount", plugin.economy().format(cash));
    }

    /**
     * The wheel result is final the moment {@link #spin(boolean)} draws it, so an interruption after
     * that point must honour the draw instead of refunding.
     */
    @Override
    protected boolean settleDecidedOutcome(String reason) {
        WheelReward reward = result();
        if (reward == null) {
            return false;
        }

        Player player = player();
        if (player == null) {
            Money stake = totalEscrow();
            Money cash = cashValueOf(reward, stake);
            // Persist item/command rewards so reconnecting still receives them.
            if ((!reward.items().isEmpty() || !reward.commands().isEmpty())
                    && plugin.pendingRewards() != null) {
                plugin.pendingRewards().enqueue(playerId(), reward);
            }
            if (!cash.isPositive()) {
                return discardEscrow();
            }
            double multiplier = stake.isPositive() ? cash.toDouble() / stake.toDouble() : 0.0;
            return PayoutService.settleWinOffline(plugin, playerId(), this, stake, cash, multiplier);
        }

        settleReward(player, reward);
        return true;
    }

    @Override
    protected void onForceEnd(String reason) {
        if (animation != null) {
            animation.cancel();
            animation = null;
        }
        gui = null;
    }
}
