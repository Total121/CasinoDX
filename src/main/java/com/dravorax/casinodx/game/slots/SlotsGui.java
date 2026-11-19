package com.dravorax.casinodx.game.slots;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameType;
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
import java.util.Locale;

/**
 * The slot machine — a cabinet the player sits at, not a one-shot round.
 *
 * <p>This view outlives any individual spin. Each press of SPIN escrows its own wager through
 * {@link SlotsGame#beginSpin}, plays out, settles, and hands the machine straight back in its
 * READY state with the same stake loaded and the last result still on screen. There is no
 * play-again screen in the middle, and because nothing is escrowed between spins the stake can be
 * raised, lowered, or retyped at any point while the reels are at rest.
 *
 * <p>The 54-slot menu is laid out as a cabinet so the game reads top to bottom without a legend:
 * <pre>
 *   row 0  frame, the machine plate (slot 4) and the player's balance (slot 8)
 *   row 1  the marquee strip above the reels
 *   row 2  the reel windows themselves, separated by dark dividers
 *   row 3  paytable, the bet controls around the Current Bet plate, odds
 *   row 4  type-an-amount, the wide SPIN button, and the machine status
 *   row 5  back to the casino, the result banner, how-to-play, close
 * </pre>
 *
 * <p>Everything on screen is derived from whether a spin is in flight, so the same
 * {@link #build(Inventory)} renders the idle machine, the locked-out spin, and the settled result.
 * The reel windows in row 2 are handed to {@link SlotsSpinAnimation} while the reels are turning.
 *
 * <p>"How to play" is drawn <em>inside</em> this menu rather than opening the shared rules screen,
 * so that reading the rules never costs the player their seat at the machine.
 */
public final class SlotsGui extends GuiView {

    private static final int SIZE = 54;

    private static final int SLOT_PLATE = 4;
    private static final int SLOT_BALANCE = 8;

    /** Marquee strip directly above the reel windows. */
    private static final int[] MARQUEE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    /** Interior of the reel row; anything that is not a reel window becomes a divider. */
    private static final int REEL_ROW_START = 19;
    private static final int REEL_ROW_END = 25;

    /** Row 3 — reference panels flanking the bet controls. */
    private static final int SLOT_PAYTABLE = 28;
    private static final int SLOT_BET_MINUS = 30;
    private static final int SLOT_CURRENT_BET = 31;
    private static final int SLOT_BET_PLUS = 32;
    private static final int SLOT_ODDS = 34;

    /** Row 4 — the action bar. */
    private static final int SLOT_CUSTOM_BET = 37;
    private static final int[] SPIN_SLOTS = {38, 39, 40, 41, 42};
    private static final int SLOT_STATUS = 43;

    /** Row 5 — navigation and the round summary. */
    private static final int SLOT_BACK = 45;
    private static final int SLOT_RESULT = 47;
    private static final int SLOT_HELP = 49;
    private static final int SLOT_CLOSE = 53;

    /** Help overlay slots, reusing the reel and control rows. */
    private static final int SLOT_HELP_RULES = 20;
    private static final int SLOT_HELP_PAYTABLE = 22;
    private static final int SLOT_HELP_ODDS = 24;

    private static final int[] INTERIOR_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Interior slots the help overlay takes over, leaving the marquee and footer alone. */
    private static final int[] OVERLAY_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final SlotsGame game;
    private final int[] reelSlots;

    /** The stake the next spin will take. Freely adjustable while the reels are at rest. */
    private Money currentBet;

    /** The escrowed spin currently in flight, or {@code null} while the machine is READY. */
    private SlotsSession session;

    private boolean helpOpen;

    /** Snapshot of the last settled spin, kept so the machine still shows it while READY. */
    private SlotsSymbol[] lastSymbols;
    private SlotsGame.Outcome lastOutcome;
    private Money lastStake = Money.ZERO;
    private Money lastPayout = Money.ZERO;

    SlotsGui(CasinoDX plugin, Player player, SlotsGame game, Money bet) {
        super(plugin, player, "slots");
        this.game = game;
        this.currentBet = game.clampBet(bet);
        this.reelSlots = reelSlots(game.reels());
    }

    /**
     * Reel windows for a given reel count, centred in row 2. Three reels are spread out with a
     * divider between each so the windows read as three separate drums; four and five reels pack
     * in tighter because the row only has seven usable slots.
     */
    static int[] reelSlots(int reels) {
        return switch (reels) {
            case 4 -> new int[]{19, 21, 23, 25};
            case 5 -> new int[]{20, 21, 22, 23, 24};
            default -> new int[]{20, 22, 24};
        };
    }

    /** Reel windows for this machine, left to right. Shared with the spin animation. */
    int[] reelWindowSlots() {
        return reelSlots.clone();
    }

    /** True while a drawn spin is still being played back. Input other than nothing is locked. */
    private boolean spinning() {
        return session != null && !session.isFinished();
    }

    private boolean hasLastResult() {
        return lastOutcome != null;
    }

    private boolean lastWon() {
        return lastOutcome != null && lastOutcome.isWin();
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        if (spinning()) {
            return Text.color(c.warning(), "&l" + Text.smallCaps("Slots") + " &8| "
                    + Text.smallCaps("Spinning"));
        }
        if (hasLastResult()) {
            return Text.color(lastWon() ? c.success() : c.danger(), "&l" + Text.smallCaps("Slots")
                    + " &8| " + Text.smallCaps(lastWon() ? "Paid Out" : "No Match"));
        }
        return Text.color(c.accent(), "&l" + Text.smallCaps("Slots") + " &8| "
                + Text.smallCaps("Press Spin"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());
        for (int slot : INTERIOR_SLOTS) {
            set(inv, slot, GuiComponents.blankPane());
        }

        drawCabinet(inv, palette);
        // The spin animation owns the reel windows, which the overlay would sit on top of.
        if (helpOpen && spinning()) {
            helpOpen = false;
        }
        if (helpOpen) {
            drawHelpOverlay(inv, palette);
        } else {
            drawReels(inv, palette);
            drawBetRow(inv, palette);
            drawActionBar(inv, palette);
        }
        drawFooter(inv, palette);

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
    }

    private void drawCabinet(Inventory inv, ColorPalette palette) {
        ItemStack marquee = GuiComponents.decorativePane(palette.borderAccent());
        for (int slot : MARQUEE_SLOTS) {
            set(inv, slot, marquee);
        }

        set(inv, SLOT_PLATE, plugin.heads().createHead(GameType.SLOTS.headKey(),
                Text.color(palette.accent(), "&l" + Text.smallCaps("Slots")),
                "",
                Text.color(palette.neutral(), game.reels() + " independent weighted reels."),
                Text.color(palette.neutral(), "Match them all for the top prize."),
                "",
                Text.color(palette.neutral(), "Jackpot: ")
                        + Text.color(palette.accent(), Text.formatMultiplier(game.topMultiplier()))));
    }

    private void drawReels(Inventory inv, ColorPalette palette) {
        ItemStack divider = GuiComponents.decorativePane(palette.borderPrimary());
        for (int slot = REEL_ROW_START; slot <= REEL_ROW_END; slot++) {
            set(inv, slot, divider);
        }

        boolean showResult = !spinning() && hasLastResult() && lastSymbols != null;
        List<SlotsSymbol> strip = game.symbols();

        for (int reel = 0; reel < reelSlots.length; reel++) {
            int slot = reelSlots[reel];
            if (showResult && reel < lastSymbols.length) {
                boolean winning = lastWon() && lastOutcome.symbol() != null
                        && lastSymbols[reel].key().equals(lastOutcome.symbol().key());
                set(inv, slot, resultWindow(palette, lastSymbols[reel], reel, winning));
            } else {
                // Idle drums show a static slice of the strip so the machine looks like a machine
                // rather than three empty frames. Nothing here is the outcome.
                SlotsSymbol idle = strip.get(reel % strip.size());
                set(inv, slot, idleWindow(palette, idle, reel));
            }
        }
    }

    private ItemStack idleWindow(ColorPalette palette, SlotsSymbol symbol, int reel) {
        return ItemBuilder.of(symbol.material())
                .name(Text.color(palette.neutral(), "&l" + Text.smallCaps("Reel " + (reel + 1))))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Idle — press ")
                                + Text.color(palette.success(), Text.smallCaps("Spin")),
                        "",
                        Text.color(palette.neutral(), "Every reel is drawn independently"),
                        Text.color(palette.neutral(), "from the weights in the paytable.")
                )
                .hideAllFlags()
                .build();
    }

    private ItemStack resultWindow(ColorPalette palette, SlotsSymbol symbol, int reel, boolean winning) {
        double chance = game.probabilityOf(symbol) * 100.0;
        ItemBuilder builder = ItemBuilder.of(symbol.material())
                .name(Text.color(winning ? palette.success() : palette.neutral(),
                        (winning ? "&l✔ " : "&l") + Text.smallCaps(symbol.displayName())))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Reel " + (reel + 1) + " stopped here."),
                        Text.color(palette.neutral(), String.format(Locale.US,
                                "Chance per reel: &f%.2f%%", chance)),
                        "",
                        Text.color(winning ? palette.success() : palette.neutral(),
                                winning ? "✔ Part of the winning line" : "Not part of a win")
                )
                .hideAllFlags();
        if (winning) {
            builder.glow();
        }
        return builder.build();
    }

    /**
     * Reference panels and the bet controls. The controls are only bound while the reels are at
     * rest, which is the whole reason the machine takes no escrow between spins.
     */
    private void drawBetRow(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_PAYTABLE, paytablePanel());
        set(inv, SLOT_ODDS, oddsPanel());

        Money min = game.minBet();
        Money max = game.maxBet();
        Money stake = spinning() ? session.bet() : currentBet;

        set(inv, SLOT_CURRENT_BET, GuiComponents.currentBetPanel(stake,
                plugin.economy().format(stake), min, max));

        if (spinning()) {
            set(inv, SLOT_BET_MINUS, GuiComponents.disabledButton("Decrease Bet",
                    "Wait for the reels to stop."));
            set(inv, SLOT_BET_PLUS, GuiComponents.disabledButton("Increase Bet",
                    "Wait for the reels to stop."));
            return;
        }

        String stepLabel = plugin.economy().format(Money.of(step()));
        set(inv, SLOT_BET_MINUS, GuiComponents.betMinus(stepLabel));
        setAction(SLOT_BET_MINUS, "bet-minus");
        set(inv, SLOT_BET_PLUS, GuiComponents.betPlus(stepLabel));
        setAction(SLOT_BET_PLUS, "bet-plus");
    }

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        if (spinning()) {
            for (int slot : SPIN_SLOTS) {
                set(inv, slot, GuiComponents.statusIndicator(GuiComponents.Status.RUNNING, "Spinning"));
            }
            set(inv, SLOT_CUSTOM_BET, GuiComponents.disabledButton("Type Amount",
                    "Wait for the reels to stop."));
            set(inv, SLOT_STATUS, GuiComponents.statusIndicator(
                    GuiComponents.Status.RUNNING, "Spinning"));
            return;
        }

        set(inv, SLOT_CUSTOM_BET, GuiComponents.customBetButton(plugin.economy().format(currentBet)));
        setAction(SLOT_CUSTOM_BET, "custom-bet");

        if (!plugin.economy().has(player, currentBet)) {
            for (int slot : SPIN_SLOTS) {
                set(inv, slot, GuiComponents.disabledButton("Spin",
                        "You need " + plugin.economy().format(currentBet) + " to stake that."));
            }
        } else {
            ItemStack spin = GuiComponents.primaryAction("Spin",
                    "Stake: " + plugin.economy().format(currentBet),
                    "Jackpot: " + plugin.economy().format(jackpotReturn()),
                    hasLastResult()
                            ? "The reels are loaded for another go."
                            : "One spin decides the round.");
            for (int slot : SPIN_SLOTS) {
                set(inv, slot, spin);
                setAction(slot, "spin");
            }
        }

        set(inv, SLOT_STATUS, hasLastResult()
                ? GuiComponents.statusIndicator(
                        lastWon() ? GuiComponents.Status.WIN : GuiComponents.Status.LOSS,
                        lastWon() ? "You Won" : "You Lost")
                : GuiComponents.statusIndicator(GuiComponents.Status.NORMAL, "Ready"));
    }

    /** Best case for the loaded stake, used to advertise the jackpot before the spin. */
    private Money jackpotReturn() {
        return currentBet.multiply(game.topMultiplier());
    }

    /**
     * The round summary: what the last spin paid once there is one, and what this one could pay
     * until then.
     */
    private ItemStack resultPanel(ColorPalette palette) {
        if (!spinning() && hasLastResult()) {
            return GuiComponents.resultBanner(lastWon(), lastOutcome.label(),
                    plugin.economy().format(lastStake),
                    plugin.economy().format(lastWon() ? lastPayout : lastStake));
        }

        Money stake = spinning() ? session.bet() : currentBet;
        SlotsSymbol top = game.topSymbol();
        return ItemBuilder.of(Material.DIAMOND)
                .name(Text.color(palette.success(), "&l" + Text.smallCaps("Potential Payout")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Jackpot: ")
                                + Text.color(palette.success(),
                                        plugin.economy().format(stake.multiply(game.topMultiplier()))),
                        Text.color(palette.neutral(), "Pays ")
                                + Text.color(palette.accent(),
                                        Text.formatMultiplier(game.topMultiplier()))
                                + Text.color(palette.neutral(), " for " + game.reels()
                                        + "x " + (top == null ? "?" : top.displayName())),
                        "",
                        Text.color(palette.neutral(), "Smaller matches pay less; the"),
                        Text.color(palette.neutral(), "paytable lists them all.")
                )
                .hideAllFlags()
                .build();
    }

    private ItemStack paytablePanel() {
        List<String> lines = new ArrayList<>();
        lines.add(game.reels() + " matching symbols pays:");
        for (SlotsSymbol symbol : game.symbols()) {
            double multiplier = game.fullMatchMultiplier(symbol);
            if (multiplier <= 0) {
                continue;
            }
            lines.add(String.format(Locale.US, "%s — %s (%.2f%% a reel)",
                    symbol.displayName(), Text.formatMultiplier(multiplier),
                    game.probabilityOf(symbol) * 100.0));
        }

        List<String> partial = new ArrayList<>();
        for (SlotsSymbol symbol : game.symbols()) {
            double multiplier = game.partialMatchMultiplier(symbol);
            if (multiplier > 0) {
                partial.add(symbol.displayName() + " — " + Text.formatMultiplier(multiplier));
            }
        }
        if (!partial.isEmpty()) {
            lines.add("");
            lines.add((game.reels() - 1) + " matching also pays:");
            lines.addAll(partial);
        }
        return GuiComponents.infoPanel("Paytable", lines);
    }

    private ItemStack oddsPanel() {
        return GuiComponents.infoPanel("Odds", List.of(
                String.format(Locale.US, "Return to player: %.2f%%", game.theoreticalRtp() * 100.0),
                String.format(Locale.US, "House edge: %.2f%%", game.effectiveHouseEdge() * 100.0),
                String.format(Locale.US, "Any win: %.2f%% of spins", game.hitChance() * 100.0),
                "",
                "Every reel stop is drawn before",
                "the reels move, from a",
                "cryptographic random source.",
                "Nothing you do during the spin",
                "can change what was decided."
        ));
    }

    /** In-place rules screen. Leaves the marquee and the footer intact. */
    private void drawHelpOverlay(Inventory inv, ColorPalette palette) {
        for (int slot : OVERLAY_SLOTS) {
            set(inv, slot, GuiComponents.blankPane());
        }

        List<String> rules = new ArrayList<>();
        rules.add("");
        for (String line : rulesText()) {
            rules.add(Text.color(palette.neutral(), line));
        }
        set(inv, SLOT_HELP_RULES, ItemBuilder.of(Material.WRITTEN_BOOK)
                .name(Text.color(palette.primary(), "&l" + Text.smallCaps("How To Play")))
                .lore(rules)
                .hideAllFlags()
                .build());
        set(inv, SLOT_HELP_PAYTABLE, paytablePanel());
        set(inv, SLOT_HELP_ODDS, oddsPanel());

        ItemStack back = GuiComponents.primaryAction("Back To The Machine",
                "Your stake is still loaded.");
        for (int slot : SPIN_SLOTS) {
            set(inv, slot, back);
            setAction(slot, "help-close");
        }
    }

    /** Server-authored rules from {@code modules/slots/config.yml}, with a built-in fallback. */
    private List<String> rulesText() {
        List<String> configured = plugin.configs().module(GameType.SLOTS.moduleName()).getStringList("help");
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return List.of(
                "Press SPIN once per round.",
                "Each reel stops on its own symbol.",
                "",
                "All reels matching pays the big",
                "multiplier; one short pays less,",
                "and only for symbols that say so.",
                "",
                "Rarer symbols pay more because",
                "they sit on the reels less often.",
                "",
                "The machine stays open — change",
                "your stake and spin again."
        );
    }

    private void drawFooter(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_RESULT, resultPanel(palette));

        if (spinning()) {
            set(inv, SLOT_BACK, GuiComponents.disabledButton("Casino", "Wait for the reels to stop."));
            set(inv, SLOT_HELP, GuiComponents.disabledButton("How To Play", "Wait for the reels to stop."));
            set(inv, SLOT_CLOSE, GuiComponents.disabledButton("Close", "Wait for the reels to stop."));
            return;
        }

        // Nothing is escrowed between spins, so leaving costs the player nothing.
        set(inv, SLOT_BACK, GuiComponents.casinoButton());
        setAction(SLOT_BACK, "back");
        set(inv, SLOT_HELP, GuiComponents.helpButton("Slots"));
        setAction(SLOT_HELP, helpOpen ? "help-close" : "help-open");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
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
            case "spin" -> startSpin();
            case "bet-plus" -> adjustBet(step());
            case "bet-minus" -> adjustBet(-step());
            case "custom-bet" -> promptCustomBet();
            case "help-open" -> {
                helpOpen = true;
                refresh();
            }
            case "help-close" -> {
                helpOpen = false;
                refresh();
            }
            case "back" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    /**
     * Escrows and starts one spin. The wager is taken here and nowhere else, so a failed
     * withdrawal leaves the machine exactly as it was with the player's balance untouched.
     */
    private void startSpin() {
        if (session != null) {
            return;
        }
        helpOpen = false;

        SlotsSession spin = game.beginSpin(player, currentBet, this);
        if (spin == null) {
            // beginSession has already told the player why; keep the machine on screen.
            refresh();
            return;
        }
        // Bound before the spin so the refresh it triggers renders the locked-out state.
        this.session = spin;
        spin.spin();

        if (spin.phase() == SlotsSession.Phase.READY) {
            // The spin never got off the ground; do not leave a registered escrow behind.
            this.session = null;
            plugin.sessions().forceEnd(player.getUniqueId(), "Spin did not start");
        }
        refresh();
    }

    /**
     * Called by {@link SlotsSession} once the round's money has been resolved. The machine keeps
     * the finished line and the result banner on screen and goes straight back to READY, which is
     * what makes the next spin a single click.
     */
    void onSpinSettled(SlotsSession spun) {
        if (spun != null) {
            this.lastSymbols = spun.symbols();
            this.lastOutcome = spun.outcome();
            this.lastStake = spun.bet();
            this.lastPayout = spun.payout();
        }
        this.session = null;
        refresh();
    }

    private double step() {
        double configured = plugin.configs().gui().getDouble("bet-select.step", 10.0);
        return configured > 0 ? configured : 10.0;
    }

    private void adjustBet(double delta) {
        if (spinning()) {
            return;
        }
        Money adjusted = delta < 0
                ? currentBet.subtract(Money.of(-delta))
                : currentBet.add(Money.of(delta));
        this.currentBet = game.clampBet(adjusted);
        plugin.sounds().play(player, "bet");
        refresh();
    }

    /**
     * Hands the player over to chat entry. The machine has to close to free their chat, so the
     * accepted amount reopens it — which is free, because no wager is held between spins.
     */
    private void promptCustomBet() {
        if (spinning()) {
            return;
        }
        player.closeInventory();
        plugin.betInput().startPrompt(player, GameType.SLOTS, currentBet,
                amount -> game.openMachine(player, amount));
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // A spin already in flight has been drawn, so GameSession settles it rather than refunding.
        // Between spins there is no escrow to resolve, which is what makes leaving the machine free.
        SlotsSession live = session;
        this.session = null;
        if (live != null && !live.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the slot machine");
        }
    }
}
