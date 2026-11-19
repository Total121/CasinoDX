package com.dravorax.casinodx.game.crash;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.gui.menu.MainMenuGui;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Crash cockpit.
 *
 * <p>Row 2 is the rocket trail, owned by {@link CrashFlightAnimation}. Row 1 shows the live
 * multiplier and potential payout, row 3 the recent crash points, and the bottom row is a single
 * oversized cash-out button — the only interaction the game has, and the one that has to be
 * reachable without hunting.
 *
 * <p>Once the round is over, row 3 becomes the launch bar instead: the same slots carry the stake
 * controls and an oversized LAUNCH button that starts the next flight from here, so a player who
 * wants another go never leaves the cockpit.
 */
public final class CrashGui extends GuiView {

    private static final int SIZE = 45;

    /** Owned by the countdown and flight animations as well as this view. */
    static final int SLOT_MULTIPLIER = 13;
    /** Rocket trail across the middle row. Shared with both crash animations. */
    static final int[] TRAIL_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private static final int SLOT_POTENTIAL = 11;
    private static final int SLOT_STAKE = 15;
    private static final int[] SLOT_CASHOUT = {30, 31, 32};
    private static final int[] RECENT_SLOTS = {28, 29, 33, 34};
    private static final int SLOT_BALANCE = 40;
    private static final int SLOT_LEAVE = 36;
    private static final int SLOT_INFO = 38;
    private static final int SLOT_CLOSE = 44;

    /** Launch bar, drawn over the recent-crash strip once the round has resolved. */
    private static final int SLOT_BET_MINUS = 28;
    private static final int SLOT_CURRENT_BET = 29;
    private static final int SLOT_BET_PLUS = 33;
    private static final int SLOT_RESULT_STATUS = 34;

    private final CrashSession session;

    /** Stake the next flight will use. Only adjustable once this one has resolved. */
    private Money nextBet;

    private boolean resultView;
    private boolean resultWin;
    private double resultMultiplier;
    private double resultCrashPoint;

    CrashGui(CasinoDX plugin, Player player, CrashSession session) {
        super(plugin, player, "crash");
        this.session = session;
        this.nextBet = session.bet();
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.danger(), "&l" + Text.smallCaps("Crash") + " &8| "
                + Text.smallCaps("Cash Out In Time"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());

        drawGauges(inv, palette);
        drawTrailPlaceholder(inv, palette);
        drawRecent(inv, palette);
        drawActionBar(inv, palette);

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
    }

    private void drawGauges(Inventory inv, ColorPalette palette) {
        double multiplier = resultView ? resultMultiplier : session.currentMultiplier();
        Money potential = session.bet().multiply(multiplier);

        String color = multiplier >= 10 ? palette.danger()
                : multiplier >= 5 ? palette.warning()
                : palette.success();

        if (!resultView && session.counting()) {
            // The countdown animation takes this slot over on its first tick; this is the frame the
            // player sees the instant the cockpit opens.
            int remaining = Math.max(1, session.countdownRemaining());
            set(inv, SLOT_MULTIPLIER, ItemBuilder.of(Material.ORANGE_CONCRETE)
                    .name(Text.color(palette.warning(), "&l" + remaining))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Launching in " + remaining
                                    + (remaining == 1 ? " second" : " seconds") + "."),
                            Text.color(palette.neutral(), "Cash out opens when the rocket lifts."),
                            "",
                            Text.color(palette.neutral(), "Leaving now returns your stake.")
                    )
                    .hideAllFlags()
                    .glow()
                    .build());
        } else {
            set(inv, SLOT_MULTIPLIER, ItemBuilder.of(resultView && !resultWin
                            ? Material.MAGMA_BLOCK : Material.FIREWORK_ROCKET)
                    .name(Text.color(color, "&l" + Text.formatMultiplier(multiplier)))
                    .lore(
                            "",
                            Text.color(palette.neutral(), resultView
                                    ? (resultWin ? "You cashed out here." : "This is where it blew up.")
                                    : "Current multiplier."),
                            resultView && resultCrashPoint > 0
                                    ? Text.color(palette.neutral(), "Crash point: ")
                                            + Text.color(palette.danger(),
                                                    Text.formatMultiplier(resultCrashPoint))
                                    : Text.color(palette.neutral(), "Rising every tick.")
                    )
                    .hideAllFlags()
                    .glow()
                    .build());
        }

        set(inv, SLOT_POTENTIAL, ItemBuilder.of(Material.DIAMOND)
                .name(Text.color(palette.success(), "&l" + Text.smallCaps("Potential Payout")))
                .lore(
                        "",
                        Text.color(palette.success(), plugin.economy().format(potential)),
                        Text.color(palette.neutral(), "at " + Text.formatMultiplier(multiplier))
                )
                .hideAllFlags()
                .build());

        set(inv, SLOT_STAKE, ItemBuilder.of(Material.GOLD_INGOT)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Your Stake")))
                .lore(
                        "",
                        Text.color(palette.neutral(), plugin.economy().format(session.bet())),
                        "",
                        Text.color(palette.neutral(), String.format("House edge: &f%.2f%%",
                                session.game().houseEdge() * 100))
                )
                .hideAllFlags()
                .build());
    }

    /** Initial trail state before the animation takes over the same slots. */
    private void drawTrailPlaceholder(Inventory inv, ColorPalette palette) {
        ItemStack idle = ItemBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                .name(" ")
                .hideAllFlags()
                .build();
        for (int slot = 19; slot <= 25; slot++) {
            set(inv, slot, idle);
        }
        if (resultView) {
            ItemStack marker = ItemBuilder.of(resultWin ? Material.EMERALD_BLOCK : Material.MAGMA_BLOCK)
                    .name(Text.color(resultWin ? palette.success() : palette.danger(),
                            "&l" + Text.smallCaps(resultWin ? "Cashed Out" : "Crashed")))
                    .hideAllFlags()
                    .build();
            for (int slot = 19; slot <= 25; slot++) {
                set(inv, slot, marker);
            }
        }
    }

    private void drawRecent(Inventory inv, ColorPalette palette) {
        List<Double> recent = session.game().recentCrashes();
        for (int i = 0; i < RECENT_SLOTS.length; i++) {
            if (i >= recent.size()) {
                set(inv, RECENT_SLOTS[i], GuiComponents.decorativePane(palette.borderSecondary()));
                continue;
            }
            double value = recent.get(i);
            Material material = value >= 10 ? Material.LIME_DYE
                    : value >= 2 ? Material.YELLOW_DYE
                    : Material.RED_DYE;
            set(inv, RECENT_SLOTS[i], ItemBuilder.of(material)
                    .name(Text.color(palette.neutral(), "&l" + Text.formatMultiplier(value)))
                    .lore("", Text.color(palette.neutral(), "Crash #" + (i + 1) + " ago"))
                    .hideAllFlags()
                    .build());
        }
    }

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        if (resultView) {
            drawLaunchBar(inv);

            List<String> lines = new ArrayList<>();
            lines.add(resultWin
                    ? "Collected at " + Text.formatMultiplier(resultMultiplier)
                    : "Blew up at " + Text.formatMultiplier(resultCrashPoint));
            lines.add(resultWin
                    ? "Returned: " + plugin.economy().format(session.bet().multiply(resultMultiplier))
                    : "Lost: " + plugin.economy().format(session.bet()));
            if (resultWin) {
                lines.add("It would have crashed at "
                        + Text.formatMultiplier(resultCrashPoint) + ".");
            }
            set(inv, SLOT_LEAVE, GuiComponents.casinoButton());
            setAction(SLOT_LEAVE, "back");
            set(inv, SLOT_INFO, GuiComponents.infoPanel("Result", lines));
            set(inv, SLOT_CLOSE, GuiComponents.closeButton());
            setAction(SLOT_CLOSE, "close");
            return;
        }

        if (session.counting()) {
            int remaining = Math.max(1, session.countdownRemaining());
            for (int slot : SLOT_CASHOUT) {
                set(inv, slot, GuiComponents.disabledButton("Cash Out",
                        "Opens in " + remaining + (remaining == 1 ? " second." : " seconds.")));
            }
            // Nothing has been decided yet, so backing out here really is a refund and the button
            // is allowed to say so.
            set(inv, SLOT_LEAVE, ItemBuilder.of(Material.BARRIER)
                    .name(Text.color(palette.warning(), "&l" + Text.smallCaps("Leave & Refund")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "The rocket has not launched."),
                            Text.color(palette.neutral(), "Returns your stake of &f"
                                    + plugin.economy().format(session.bet()))
                    )
                    .hideAllFlags()
                    .build());
            setAction(SLOT_LEAVE, "abort");
            set(inv, SLOT_INFO, GuiComponents.infoPanel("Get Ready", List.of(
                    "Launching in " + remaining + "...",
                    "",
                    "The crash point is already drawn.",
                    "Cash out below it to win.",
                    "",
                    "Leaving before launch refunds;",
                    "leaving mid-flight does not."
            )));
            set(inv, SLOT_CLOSE, GuiComponents.closeButton());
            setAction(SLOT_CLOSE, "close");
            return;
        }

        boolean live = session.inFlight();
        for (int slot : SLOT_CASHOUT) {
            if (live) {
                set(inv, slot, GuiComponents.cashOutButton(
                        plugin.economy().format(session.currentPayout())
                                + " @ " + Text.formatMultiplier(session.currentMultiplier())));
                setAction(slot, "cashout");
            } else {
                set(inv, slot, GuiComponents.disabledButton("Cash Out", "The rocket is gone."));
            }
        }

        set(inv, SLOT_LEAVE, GuiComponents.disabledButton("Leave",
                "Leaving mid-flight forfeits the stake."));
        set(inv, SLOT_INFO, GuiComponents.infoPanel("Crash", List.of(
                "The crash point is drawn at launch.",
                "Cash out below it to win.",
                "Leaving early counts as a crash."
        )));
        set(inv, SLOT_CLOSE, GuiComponents.disabledButton("Close",
                "Cash out or ride it to the crash."));
    }

    /**
     * The post-round launch bar. It takes over the recent-crash strip and the cash-out row, which
     * are both meaningless now, and turns them into the controls for the next flight.
     */
    private void drawLaunchBar(Inventory inv) {
        CrashGame game = session.game();
        Money min = game.minBet();
        Money max = game.maxBet();
        String stepLabel = plugin.economy().format(Money.of(step()));

        set(inv, SLOT_BET_MINUS, GuiComponents.betMinus(stepLabel));
        setAction(SLOT_BET_MINUS, "bet-minus");
        set(inv, SLOT_CURRENT_BET, GuiComponents.currentBetPanel(nextBet,
                plugin.economy().format(nextBet), min, max));
        set(inv, SLOT_BET_PLUS, GuiComponents.betPlus(stepLabel));
        setAction(SLOT_BET_PLUS, "bet-plus");
        set(inv, SLOT_RESULT_STATUS, GuiComponents.statusIndicator(
                resultWin ? GuiComponents.Status.WIN : GuiComponents.Status.LOSS,
                resultWin ? "Cashed Out" : "Crashed"));

        if (!plugin.economy().has(player, nextBet)) {
            for (int slot : SLOT_CASHOUT) {
                set(inv, slot, GuiComponents.disabledButton("Launch",
                        "You need " + plugin.economy().format(nextBet) + " to stake that."));
            }
            return;
        }
        ItemStack launch = GuiComponents.primaryAction("Launch",
                "Send up another rocket.",
                "Stake: " + plugin.economy().format(nextBet),
                "A fresh crash point is drawn at launch.");
        for (int slot : SLOT_CASHOUT) {
            set(inv, slot, launch);
            setAction(slot, "launch");
        }
    }

    private double step() {
        double configured = plugin.configs().gui().getDouble("bet-select.step", 10.0);
        return configured > 0 ? configured : 10.0;
    }

    private void adjustBet(double delta) {
        if (!resultView) {
            return;
        }
        CrashGame game = session.game();
        Money min = game.minBet();
        Money max = game.maxBet();
        Money adjusted = delta < 0
                ? nextBet.subtract(Money.of(-delta))
                : nextBet.add(Money.of(delta));
        if (adjusted.lessThan(min)) {
            adjusted = min;
        }
        if (adjusted.greaterThan(max)) {
            adjusted = max;
        }
        this.nextBet = adjusted;
        plugin.sounds().play(player, "bet");
        refresh();
    }

    /**
     * Starts the next flight without leaving the cockpit. The finished session has already retired
     * itself, but force-ending first keeps this honest if a settlement path ever leaves one
     * registered — {@link CrashGame#startSession} refuses to escrow while a session exists.
     */
    private void launchAgain() {
        if (!resultView) {
            return;
        }
        plugin.sessions().forceEnd(player.getUniqueId(), "Launching another rocket");
        session.game().startSession(player, nextBet);
    }

    /** Lightweight refresh of just the live gauges and cash-out button, called every flight step. */
    void updateFlight() {
        if (inventory() == null || resultView) {
            return;
        }
        ColorPalette palette = ColorPalette.get();
        drawGauges(inventory(), palette);
        drawActionBar(inventory(), palette);
    }

    /**
     * Refreshes the countdown read-out each second. The multiplier slot itself is written by
     * {@link CrashCountdownAnimation}, so only the surrounding panels are redrawn here.
     */
    void updateCountdown() {
        if (inventory() == null || resultView) {
            return;
        }
        drawActionBar(inventory(), ColorPalette.get());
    }

    /** Swaps the cockpit out of its pre-launch state once the rocket lifts. */
    void onFlightStarted() {
        if (inventory() == null || resultView) {
            return;
        }
        refresh();
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!plugin.antiExploit().tryClick(player.getUniqueId())) {
            return;
        }
        String act = action(event.getRawSlot());
        if (act == null) {
            return;
        }
        playClickSound();

        switch (act) {
            case "cashout" -> session.cashOut();
            case "abort" -> {
                // Only bound during the countdown, where GameSession refunds rather than settles.
                plugin.sessions().forceEnd(player.getUniqueId(), "Left before launch");
                plugin.guis().open(new MainMenuGui(plugin, player));
            }
            case "back" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "launch" -> launchAgain();
            case "bet-plus" -> adjustBet(step());
            case "bet-minus" -> adjustBet(-step());
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Closing mid-flight is a forfeit, not a refund — CrashSession settles it as a loss.
        // Closing during the countdown refunds, because nothing has been decided yet.
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), session.counting()
                    ? "Closed the cockpit before launch"
                    : "Closed the cockpit mid-flight");
        }
    }

    void showResult(boolean win, double multiplier, double crashPoint) {
        this.resultView = true;
        this.resultWin = win;
        this.resultMultiplier = multiplier;
        this.resultCrashPoint = crashPoint;
        refresh();
    }
}
