package com.dravorax.casinodx.game.roulette;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.gui.menu.HelpGui;
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
 * The roulette betting table.
 *
 * <p>Laid out like the felt it stands in for, top to bottom:
 * <ul>
 *   <li>row 0 — the wheel strip: recent pockets while idle, scrolling during
 *       {@link RouletteSpinAnimation}, the winner while the result is up,</li>
 *   <li>row 1 — zero and the even-money bets,</li>
 *   <li>row 2 — dozens, columns, and the door to the 0-36 grid,</li>
 *   <li>row 3 — the chip denominations,</li>
 *   <li>row 4 — chip controls, the total on the table, and the balance,</li>
 *   <li>row 5 — spin and navigation.</li>
 * </ul>
 *
 * <p>Every cell is a toggle: clicking it lays one chip of the current size down, clicking it again
 * picks that chip back up. Nothing is charged until {@link RouletteSession#spin()}, which is why the
 * "leave" control here is an ordinary back button rather than a refund.
 *
 * <p>A 54-slot inventory cannot hold all 37 pockets, all 12 outside bets <em>and</em> the chip
 * controls, so the pockets live one click away in {@link RouletteNumbersGui}, which shares this
 * session's chips and its own copy of the chip controls.
 */
public final class RouletteGui extends GuiView {

    private static final int SIZE = 54;

    /** Row 1 — zero and the even-money bets. */
    private static final int SLOT_ZERO = 10;
    private static final int SLOT_RED = 11;
    private static final int SLOT_BLACK = 12;
    private static final int SLOT_ODD = 13;
    private static final int SLOT_EVEN = 14;
    private static final int SLOT_LOW = 15;
    private static final int SLOT_HIGH = 16;

    /** Row 2 — dozens, columns, and the numbers grid. */
    private static final int SLOT_DOZEN_1 = 19;
    private static final int SLOT_DOZEN_2 = 20;
    private static final int SLOT_DOZEN_3 = 21;
    private static final int SLOT_NUMBERS = 22;
    private static final int SLOT_COLUMN_1 = 23;
    private static final int SLOT_COLUMN_2 = 24;
    private static final int SLOT_COLUMN_3 = 25;

    /** Row 3 — chip denominations, left to right. */
    private static final int PRESET_ROW_START = 28;

    /** Row 4 — chip controls and money read-outs. */
    private static final int SLOT_CHIP_MINUS = 37;
    private static final int SLOT_CHIP = 38;
    private static final int SLOT_CHIP_PLUS = 39;
    private static final int SLOT_CHIP_CUSTOM = 40;
    private static final int SLOT_TOTAL = 41;
    private static final int SLOT_MY_CHIPS = 42;
    private static final int SLOT_BALANCE = 43;

    /** Row 5 — actions. */
    private static final int SLOT_CASINO = 45;
    private static final int SLOT_CLEAR = 47;
    private static final int SLOT_SPIN = 49;
    private static final int SLOT_HELP = 51;
    private static final int SLOT_CLOSE = 53;

    private final RouletteSession session;

    private boolean resultView;
    private int resultPocket = -1;
    private Money resultReturn = Money.ZERO;

    RouletteGui(CasinoDX plugin, Player player, RouletteSession session) {
        super(plugin, player, "roulette");
        this.session = session;
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.primary(), "&l" + Text.smallCaps("Roulette") + " &8| "
                + Text.smallCaps("European Table"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());
        fillInterior(inv);
        drawWheelStrip(inv, palette);
        drawEvenMoneyBets(inv, palette);
        drawGroupBets(inv, palette);
        drawChipStrip(inv, palette);
        drawMoneyPanels(inv, palette);
        drawActionBar(inv, palette);
    }

    private void fillInterior(Inventory inv) {
        int rows = SIZE / 9;
        for (int slot = 0; slot < SIZE; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                continue;
            }
            set(inv, slot, GuiComponents.blankPane());
        }
    }

    // --- Row 0: the wheel -----------------------------------------------------------

    /**
     * Idle state of row 0: the last eight pockets, newest closest to the centre. Once a spin
     * starts, {@link RouletteSpinAnimation} overwrites these same slots every tick.
     */
    private void drawWheelStrip(Inventory inv, ColorPalette palette) {
        List<Integer> recent = session.game().recentResults();
        int highlight = resultView ? resultPocket : -1;

        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                set(inv, i, centerMarker(palette, highlight));
                continue;
            }
            int historyIndex = i < 4 ? 3 - i : i - 5;
            if (historyIndex < recent.size()) {
                int pocket = recent.get(historyIndex);
                set(inv, i, ItemBuilder.of(RouletteWheel.materialOf(pocket))
                        .name(RouletteWheel.colorCode(pocket) + "&l" + pocket)
                        .lore("", Text.color(palette.neutral(), "Spin #" + (historyIndex + 1) + " ago"))
                        .hideAllFlags()
                        .build());
            } else {
                set(inv, i, GuiComponents.decorativePane(palette.borderSecondary()));
            }
        }
    }

    private ItemStack centerMarker(ColorPalette palette, int highlight) {
        if (highlight >= 0) {
            boolean won = resultReturn.greaterThan(session.totalWager());
            return ItemBuilder.of(RouletteWheel.materialOf(highlight))
                    .name(RouletteWheel.colorCode(highlight) + "&l▼ " + highlight)
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Result: ")
                                    + RouletteWheel.colorCode(highlight) + highlight
                                    + " &7(" + RouletteWheel.colorOf(highlight).name().toLowerCase(Locale.ROOT) + ")",
                            Text.color(palette.neutral(), session.winningBetCount() + " of "
                                    + session.betCount() + " chips won."),
                            Text.color(won ? palette.success() : palette.danger(),
                                    "Returned " + plugin.economy().format(resultReturn))
                    )
                    .hideAllFlags()
                    .glow()
                    .build();
        }
        return ItemBuilder.of(Material.NETHER_STAR)
                .name(Text.color(palette.accent(), "&l▼ " + Text.smallCaps("Wheel")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "37 pockets: 0-36."),
                        Text.color(palette.neutral(), "The ball lands here.")
                )
                .hideAllFlags()
                .build();
    }

    // --- Rows 1-2: the felt ---------------------------------------------------------

    private void drawEvenMoneyBets(Inventory inv, ColorPalette palette) {
        betCell(inv, palette, SLOT_ZERO, RouletteBet.simple(RouletteBet.Kind.GREEN),
                "0 Green", "The single zero pocket.");
        betCell(inv, palette, SLOT_RED, RouletteBet.simple(RouletteBet.Kind.RED),
                "Red", "All 18 red pockets.");
        betCell(inv, palette, SLOT_BLACK, RouletteBet.simple(RouletteBet.Kind.BLACK),
                "Black", "All 18 black pockets.");
        betCell(inv, palette, SLOT_ODD, RouletteBet.simple(RouletteBet.Kind.ODD),
                "Odd", "1, 3, 5 … 35.");
        betCell(inv, palette, SLOT_EVEN, RouletteBet.simple(RouletteBet.Kind.EVEN),
                "Even", "2, 4, 6 … 36.");
        betCell(inv, palette, SLOT_LOW, RouletteBet.simple(RouletteBet.Kind.LOW),
                "Low 1-18", "Pockets 1 through 18.");
        betCell(inv, palette, SLOT_HIGH, RouletteBet.simple(RouletteBet.Kind.HIGH),
                "High 19-36", "Pockets 19 through 36.");
    }

    private void drawGroupBets(Inventory inv, ColorPalette palette) {
        betCell(inv, palette, SLOT_DOZEN_1, RouletteBet.dozen(1), "1st Dozen", "Pockets 1-12.");
        betCell(inv, palette, SLOT_DOZEN_2, RouletteBet.dozen(2), "2nd Dozen", "Pockets 13-24.");
        betCell(inv, palette, SLOT_DOZEN_3, RouletteBet.dozen(3), "3rd Dozen", "Pockets 25-36.");
        betCell(inv, palette, SLOT_COLUMN_1, RouletteBet.column(1), "1st Column", "1, 4, 7 … 34.");
        betCell(inv, palette, SLOT_COLUMN_2, RouletteBet.column(2), "2nd Column", "2, 5, 8 … 35.");
        betCell(inv, palette, SLOT_COLUMN_3, RouletteBet.column(3), "3rd Column", "3, 6, 9 … 36.");
        drawNumbersDoor(inv, palette);
    }

    /**
     * One betting cell. Placed chips glow and say how to take them back, so a full board reads as a
     * board rather than as a list of buttons.
     */
    private void betCell(Inventory inv, ColorPalette palette, int slot, RouletteBet bet,
                         String title, String explanation) {
        boolean placed = session.hasBet(bet);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color(palette.neutral(), explanation));
        lore.add(Text.color(palette.neutral(), "Odds: ")
                + Text.color(palette.accent(), oddsLabelFor(bet)));
        lore.add(Text.color(palette.neutral(), "Chip: &f"
                + plugin.economy().format(session.chipSize())));
        lore.add(Text.color(palette.neutral(), "Pays ")
                + Text.color(palette.success(), plugin.economy().format(session.returnFor(bet))));
        lore.add(Text.color(palette.neutral(),
                String.format(Locale.US, "Chance: &f%.1f%%", bet.winChance() * 100)));
        lore.add("");
        lore.addAll(cellStatusLines(palette, bet, placed));

        ItemBuilder builder = ItemBuilder.of(bet.material())
                .name(Text.color(placed ? palette.success() : bet.colorCode(),
                        (placed ? "&l✔ " : "&l") + Text.smallCaps(title)))
                .lore(lore)
                .hideAllFlags();
        if (placed) {
            builder.glow();
        }
        set(inv, slot, builder.build());

        if (canBet()) {
            setAction(slot, "bet:" + bet.encode());
        }
    }

    /** The one line that tells the player what clicking this cell will do right now. */
    private List<String> cellStatusLines(ColorPalette palette, RouletteBet bet, boolean placed) {
        List<String> lines = new ArrayList<>();
        if (resultView) {
            if (!placed) {
                lines.add(Text.color(palette.neutral(), "No chip here."));
            } else if (bet.wins(resultPocket)) {
                lines.add(Text.color(palette.success(), "✔ This chip won."));
            } else {
                lines.add(Text.color(palette.danger(), "✘ This chip lost."));
            }
            return lines;
        }
        if (!canBet()) {
            lines.add(Text.color(palette.neutral(), "Locked while the wheel turns."));
            return lines;
        }
        if (placed) {
            lines.add(Text.color(palette.success(), "✔ Chip placed"));
            lines.add(Text.color(palette.neutral(), "Click again to remove it."));
        } else {
            lines.add(Text.color(palette.primary(), "▶ Click to add a chip"));
        }
        return lines;
    }

    private void drawNumbersDoor(Inventory inv, ColorPalette palette) {
        int straightChips = 0;
        for (RouletteBet bet : session.bets()) {
            if (!bet.kind().isOutside()) {
                straightChips++;
            }
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color(palette.neutral(), "Open the 0-36 grid and click"));
        lore.add(Text.color(palette.neutral(), "numbers to add/remove bets."));
        lore.add(Text.color(palette.neutral(), "Odds: ") + Text.color(palette.accent(), "35:1"));
        lore.add("");
        lore.add(Text.color(palette.accent(), "Chips on numbers: &f" + straightChips));
        lore.add(canBet()
                ? Text.color(palette.primary(), "▶ Click to open the grid")
                : Text.color(palette.neutral(), "Locked while the wheel turns."));

        ItemBuilder builder = ItemBuilder.of(Material.TARGET)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Numbers 0-36") + " &8(35:1)"))
                .lore(lore)
                .hideAllFlags();
        if (straightChips > 0) {
            builder.glow();
        }
        set(inv, SLOT_NUMBERS, builder.build());
        if (canBet()) {
            setAction(SLOT_NUMBERS, "numbers");
        }
    }

    // --- Rows 3-4: chips and money --------------------------------------------------

    /** The denomination strip. Fills its whole row so the table never shows a hole. */
    private void drawChipStrip(Inventory inv, ColorPalette palette) {
        List<Money> presets = session.game().chipPresets();
        for (int i = 0; i < RouletteGame.CHIP_PRESET_SLOTS; i++) {
            int slot = PRESET_ROW_START + i;
            if (i >= presets.size()) {
                set(inv, slot, GuiComponents.decorativePane(Material.GRAY_STAINED_GLASS_PANE));
                continue;
            }
            Money amount = presets.get(i);
            boolean selected = amount.equals(session.chipSize());
            set(inv, slot, GuiComponents.presetBetButton(plugin.economy().format(amount), selected));
            if (canBet()) {
                setAction(slot, "chip:" + amount.format());
            }
        }
    }

    private void drawMoneyPanels(Inventory inv, ColorPalette palette) {
        Money chip = session.chipSize();
        Money min = session.game().minBet();
        Money max = session.game().maxBet();
        String chipLabel = plugin.economy().format(chip);
        String stepLabel = plugin.economy().format(session.game().chipStep());

        // currentBetPanel is the shared "this is what you are staking" block; on a roulette table
        // that amount is one chip, so it is relabelled rather than reinvented.
        set(inv, SLOT_CHIP, ItemBuilder.of(GuiComponents.currentBetPanel(chip, chipLabel, min, max))
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Your Chip")
                        + " &8| &f&l" + chipLabel))
                .build());

        if (canBet()) {
            set(inv, SLOT_CHIP_MINUS, GuiComponents.betMinus(stepLabel));
            setAction(SLOT_CHIP_MINUS, "chip-minus");
            set(inv, SLOT_CHIP_PLUS, GuiComponents.betPlus(stepLabel));
            setAction(SLOT_CHIP_PLUS, "chip-plus");
            set(inv, SLOT_CHIP_CUSTOM, GuiComponents.customBetButton(chipLabel));
            setAction(SLOT_CHIP_CUSTOM, "chip-custom");
        } else {
            set(inv, SLOT_CHIP_MINUS, GuiComponents.disabledButton("Decrease Chip", lockReason()));
            set(inv, SLOT_CHIP_PLUS, GuiComponents.disabledButton("Increase Chip", lockReason()));
            set(inv, SLOT_CHIP_CUSTOM, GuiComponents.disabledButton("Type Amount", lockReason()));
        }

        set(inv, SLOT_TOTAL, resultView ? resultPanel() : totalPanel(palette));
        set(inv, SLOT_MY_CHIPS, GuiComponents.infoPanel("Your Chips", myChipsLines()));

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));

        // Opening the help screen closes the table, which would settle a spin in flight.
        if (session.spinning()) {
            set(inv, SLOT_HELP, GuiComponents.disabledButton("How To Play", lockReason()));
        } else {
            set(inv, SLOT_HELP, GuiComponents.helpButton("Roulette"));
            setAction(SLOT_HELP, "help");
        }
    }

    /** Replaces the total panel once the wheel has stopped. */
    private ItemStack resultPanel() {
        Money staked = session.totalWager();
        return GuiComponents.resultPanel(resultReturn.greaterThan(staked),
                session.winningBetCount() + " of " + session.betCount() + " chips won on "
                        + resultPocket + " (" + RouletteWheel.colorOf(resultPocket).name().toLowerCase(Locale.ROOT) + ")",
                plugin.economy().format(staked),
                plugin.economy().format(resultReturn));
    }

    /** The headline number: chip size times chips placed, in the item name so it cannot be missed. */
    private ItemStack totalPanel(ColorPalette palette) {
        Money total = session.totalWager();
        int count = session.betCount();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color(palette.neutral(), plugin.economy().format(session.chipSize())
                + " x " + count + (count == 1 ? " chip" : " chips")));
        lore.add(Text.color(palette.neutral(), "Chips left: &f"
                + (session.maxBets() - count) + " &7of " + session.maxBets()));
        lore.add("");
        lore.add(Text.color(palette.neutral(), "Covers &f" + session.coveredPockets()
                + " &7of 37 pockets."));
        lore.add(Text.color(palette.neutral(), "Best case: ")
                + Text.color(palette.success(), plugin.economy().format(session.bestCaseReturn())));
        lore.add("");
        lore.add(Text.color(palette.warning(), "Taken from your balance"));
        lore.add(Text.color(palette.warning(), "only when you spin."));

        return ItemBuilder.of(Material.DIAMOND_BLOCK)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Total Bet") + " &8| ")
                        + Text.color(total.isPositive() ? palette.success() : palette.neutral(),
                        "&l" + plugin.economy().format(total)))
                .lore(lore)
                .hideAllFlags()
                .glow(total.isPositive())
                .build();
    }

    /** Every chip on the felt, so the player can audit the board without hovering each cell. */
    private List<String> myChipsLines() {
        List<String> lines = new ArrayList<>();
        if (!session.hasBets()) {
            lines.add("No chips on the table yet.");
            lines.add("");
            lines.add("Click numbers or outside bets");
            lines.add("to add/remove bets.");
            return lines;
        }
        lines.add(session.betCount() + " of " + session.maxBets() + " chips placed:");
        lines.add("");
        for (RouletteBet bet : session.bets()) {
            String line = "• " + bet.shortLabel() + " — " + plugin.economy().format(session.returnFor(bet));
            if (resultView) {
                line += bet.wins(resultPocket) ? " &a✔" : " &c✘";
            }
            lines.add(line);
        }
        return lines;
    }

    // --- Row 5: actions -------------------------------------------------------------

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");

        if (resultView) {
            set(inv, SLOT_SPIN, GuiComponents.newRoundButton(
                    plugin.economy().format(session.chipSize())));
            setAction(SLOT_SPIN, "again");
            set(inv, SLOT_CLEAR, ItemBuilder.of(Material.SPONGE)
                    .name(Text.color(palette.warning(), "&l" + Text.smallCaps("Clear & Play Again")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Start a fresh board with the"),
                            Text.color(palette.neutral(), "same chip of &f"
                                    + plugin.economy().format(session.chipSize()) + "&7.")
                    )
                    .hideAllFlags()
                    .build());
            setAction(SLOT_CLEAR, "again-clear");
            set(inv, SLOT_CASINO, GuiComponents.casinoButton());
            setAction(SLOT_CASINO, "casino");
            return;
        }

        if (session.spinning()) {
            set(inv, SLOT_SPIN, GuiComponents.statusIndicator(GuiComponents.Status.RUNNING, "Spinning"));
            set(inv, SLOT_CLEAR, GuiComponents.disabledButton("Clear Board", lockReason()));
            set(inv, SLOT_CASINO, GuiComponents.disabledButton("Casino", lockReason()));
            set(inv, SLOT_CLOSE, GuiComponents.disabledButton("Close", lockReason()));
            return;
        }

        if (!session.hasBets()) {
            set(inv, SLOT_SPIN, GuiComponents.disabledButton("Spin",
                    "Click numbers to add/remove bets."));
        } else if (!plugin.economy().has(player, session.totalWager())) {
            set(inv, SLOT_SPIN, GuiComponents.disabledButton("Spin",
                    "You need " + plugin.economy().format(session.totalWager()) + " to cover the board."));
        } else {
            set(inv, SLOT_SPIN, GuiComponents.primaryAction("Spin",
                    "Wager " + plugin.economy().format(session.totalWager())
                            + " on " + session.betCount() + (session.betCount() == 1 ? " chip." : " chips."),
                    "Best case " + plugin.economy().format(session.bestCaseReturn()) + ".",
                    "One spin settles every chip."));
            setAction(SLOT_SPIN, "spin");
        }

        if (session.hasBets()) {
            set(inv, SLOT_CLEAR, ItemBuilder.of(Material.SPONGE)
                    .name(Text.color(palette.warning(), "&l" + Text.smallCaps("Clear Board")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Takes all &f" + session.betCount()
                                    + " &7chips back off the felt."),
                            Text.color(palette.neutral(), "Free — nothing has been staked yet.")
                    )
                    .hideAllFlags()
                    .build());
            setAction(SLOT_CLEAR, "clear");
        } else {
            set(inv, SLOT_CLEAR, GuiComponents.disabledButton("Clear Board", "The felt is already empty."));
        }

        set(inv, SLOT_CASINO, GuiComponents.casinoButton());
        setAction(SLOT_CASINO, "leave");
    }

    private String lockReason() {
        return "Wait for the wheel to stop.";
    }

    private String oddsLabelFor(RouletteBet bet) {
        return formatOdds(session.game().multiplierFor(bet.kind()));
    }

    /** Converts a gross return multiplier into classic roulette odds (profit : stake). */
    private static String formatOdds(double grossMultiplier) {
        int profit = Math.max(0, (int) Math.round(grossMultiplier - 1.0));
        return profit + ":1";
    }

    private boolean canBet() {
        return !resultView && session.acceptsInput();
    }

    // --- Interaction ----------------------------------------------------------------

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

        if (act.startsWith("bet:")) {
            session.toggleBet(RouletteGame.decodeSelection(act.substring("bet:".length())));
            return;
        }
        if (act.startsWith("chip:")) {
            try {
                session.setChipSize(Money.of(act.substring("chip:".length())));
            } catch (IllegalArgumentException ignored) {
                // Stale action binding.
            }
            return;
        }

        switch (act) {
            case "numbers" -> session.openNumbers();
            case "spin" -> session.spin();
            case "clear" -> session.clearBets();
            case "chip-minus" -> session.setChipSize(session.chipSize().subtract(session.game().chipStep()));
            case "chip-plus" -> session.setChipSize(session.chipSize().add(session.game().chipStep()));
            case "chip-custom" -> promptCustomChip();
            case "again" -> playAgain(true);
            case "again-clear" -> playAgain(false);
            case "leave" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Left the roulette table");
                plugin.guis().open(new MainMenuGui(plugin, player));
            }
            case "casino" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "help" -> plugin.guis().open(new HelpGui(plugin, player, GameType.ROULETTE, this::open));
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    /** Same chip, fresh round. The finished session is already out of the way by this point. */
    private void playAgain(boolean keepBoard) {
        if (!resultView) {
            return;
        }
        List<RouletteBet> carryOver = keepBoard ? List.copyOf(session.bets()) : List.of();
        session.game().openTable(player, session.chipSize(), carryOver);
    }

    /**
     * Chat entry needs the inventory closed, and a closed table means the round is over — so the
     * round <em>is</em> ended here and the answer opens a new one with the same board. Nothing is
     * escrowed before a spin, so ending and restarting the session costs the player nothing, and an
     * ignored prompt leaves them with no dangling session.
     */
    private void promptCustomChip() {
        RouletteGame game = session.game();
        Money current = session.chipSize();
        List<RouletteBet> board = List.copyOf(session.bets());

        plugin.sessions().forceEnd(player.getUniqueId(), "Choosing a chip size");
        player.closeInventory();
        plugin.betInput().startPrompt(player, GameType.ROULETTE, current,
                amount -> game.openTable(player, amount, board));
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Nothing is escrowed until the wheel is spun, so walking away here simply ends the round.
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the roulette table");
        }
    }

    /** Switches the table into its read-only result state. */
    void showResult(int pocket, Money grossReturn) {
        this.resultView = true;
        this.resultPocket = pocket;
        this.resultReturn = grossReturn == null ? Money.ZERO : grossReturn;
        refresh();
    }
}
