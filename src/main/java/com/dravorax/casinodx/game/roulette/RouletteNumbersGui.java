package com.dravorax.casinodx.game.roulette;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
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
 * The pockets, 0 through 36, in their wheel colours.
 *
 * <p>The green zero sits alone on the top row and 1-36 fill four rows of nine below it. Every cell
 * is a toggle on the same board as {@link RouletteGui}: clicking a number lays a chip on it, clicking
 * it again lifts that chip. The chip controls and spin button are repeated here so a player betting a
 * spread of numbers never has to walk back to the outside bets to place or settle them.
 */
public final class RouletteNumbersGui extends GuiView {

    private static final int SIZE = 54;

    /** Row 0 — the zero pocket flanked by the two money read-outs. */
    private static final int SLOT_CHIP = 2;
    private static final int SLOT_ZERO = 4;
    private static final int SLOT_TOTAL = 6;

    /** Rows 1-4 — pockets 1-36, nine to a row. */
    private static final int NUMBER_START = 9;

    /** Row 5 — actions. */
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CHIP_MINUS = 46;
    private static final int SLOT_CHIP_PLUS = 47;
    private static final int SLOT_CLEAR = 48;
    private static final int SLOT_SPIN = 49;
    private static final int SLOT_MY_CHIPS = 50;
    private static final int SLOT_BALANCE = 51;
    private static final int SLOT_HELP = 52;
    private static final int SLOT_CLOSE = 53;

    private final RouletteSession session;

    RouletteNumbersGui(CasinoDX plugin, Player player, RouletteSession session) {
        super(plugin, player, "roulette-numbers");
        this.session = session;
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.primary(), "&l" + Text.smallCaps("Roulette") + " &8| "
                + Text.smallCaps("Numbers 0-36") + " &7(35:1)");
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());
        for (int slot = 0; slot < 9; slot++) {
            set(inv, slot, GuiComponents.decorativePane(palette.borderSecondary()));
        }

        drawPocket(inv, palette, SLOT_ZERO, RouletteBet.simple(RouletteBet.Kind.GREEN), 0);
        for (int number = 1; number <= 36; number++) {
            drawPocket(inv, palette, NUMBER_START + (number - 1), RouletteBet.straight(number), number);
        }

        drawPanels(inv, palette);
        drawActionBar(inv, palette);
    }

    private void drawPocket(Inventory inv, ColorPalette palette, int slot, RouletteBet bet, int number) {
        boolean placed = session.hasBet(bet);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color(palette.neutral(), "Colour: &f"
                + RouletteWheel.colorOf(number).name().toLowerCase(Locale.ROOT)));
        lore.add(Text.color(palette.neutral(), "Odds: ") + Text.color(palette.accent(), "35:1"));
        lore.add(Text.color(palette.neutral(), "Chip: &f" + plugin.economy().format(session.chipSize())));
        lore.add(Text.color(palette.neutral(), "Pays ")
                + Text.color(palette.success(), plugin.economy().format(session.returnFor(bet))));
        lore.add("");
        if (!canBet()) {
            lore.add(Text.color(palette.neutral(), "Locked while the wheel turns."));
        } else if (placed) {
            lore.add(Text.color(palette.success(), "✔ Chip placed"));
            lore.add(Text.color(palette.neutral(), "Click again to remove it."));
        } else {
            lore.add(Text.color(palette.primary(), "▶ Click to add a chip"));
        }

        ItemBuilder builder = ItemBuilder.of(RouletteWheel.materialOf(number))
                .name(RouletteWheel.colorCode(number) + "&l" + (placed ? "✔ " : "") + number)
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

    private void drawPanels(Inventory inv, ColorPalette palette) {
        Money chip = session.chipSize();
        String chipLabel = plugin.economy().format(chip);

        set(inv, SLOT_CHIP, ItemBuilder.of(GuiComponents.currentBetPanel(chip, chipLabel,
                        session.game().minBet(), session.game().maxBet()))
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Your Chip")
                        + " &8| &f&l" + chipLabel))
                .build());

        set(inv, SLOT_TOTAL, totalPanel(palette));

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
        set(inv, SLOT_MY_CHIPS, GuiComponents.infoPanel("Your Chips", myChipsLines()));
    }

    private ItemStack totalPanel(ColorPalette palette) {
        Money total = session.totalWager();
        int count = session.betCount();
        return ItemBuilder.of(Material.DIAMOND_BLOCK)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Total Bet") + " &8| ")
                        + Text.color(total.isPositive() ? palette.success() : palette.neutral(),
                        "&l" + plugin.economy().format(total)))
                .lore(
                        "",
                        Text.color(palette.neutral(), plugin.economy().format(session.chipSize())
                                + " x " + count + (count == 1 ? " chip" : " chips")),
                        Text.color(palette.neutral(), "Chips left: &f"
                                + (session.maxBets() - count) + " &7of " + session.maxBets()),
                        "",
                        Text.color(palette.neutral(), "Best case: ")
                                + Text.color(palette.success(), plugin.economy().format(session.bestCaseReturn())),
                        "",
                        Text.color(palette.warning(), "Taken from your balance"),
                        Text.color(palette.warning(), "only when you spin.")
                )
                .hideAllFlags()
                .glow(total.isPositive())
                .build();
    }

    private List<String> myChipsLines() {
        List<String> lines = new ArrayList<>();
        if (!session.hasBets()) {
            lines.add("No chips on the table yet.");
            lines.add("");
            lines.add("Click numbers to add/remove bets.");
            return lines;
        }
        lines.add(session.betCount() + " of " + session.maxBets() + " chips placed:");
        lines.add("");
        for (RouletteBet bet : session.bets()) {
            lines.add("• " + bet.shortLabel() + " — " + plugin.economy().format(session.returnFor(bet)));
        }
        return lines;
    }

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_BACK, GuiComponents.backButton(null));
        setAction(SLOT_BACK, "back");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
        set(inv, SLOT_HELP, GuiComponents.helpButton("Roulette"));
        setAction(SLOT_HELP, "help");

        String stepLabel = plugin.economy().format(session.game().chipStep());
        if (canBet()) {
            set(inv, SLOT_CHIP_MINUS, GuiComponents.betMinus(stepLabel));
            setAction(SLOT_CHIP_MINUS, "chip-minus");
            set(inv, SLOT_CHIP_PLUS, GuiComponents.betPlus(stepLabel));
            setAction(SLOT_CHIP_PLUS, "chip-plus");
        } else {
            set(inv, SLOT_CHIP_MINUS, GuiComponents.disabledButton("Decrease Chip", "The round is over."));
            set(inv, SLOT_CHIP_PLUS, GuiComponents.disabledButton("Increase Chip", "The round is over."));
        }

        if (session.hasBets() && canBet()) {
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
                    "Returns you to the table."));
            setAction(SLOT_SPIN, "spin");
        }
    }

    private boolean canBet() {
        return session.acceptsInput() && !session.spinning();
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

        if (act.startsWith("bet:")) {
            session.toggleBet(RouletteGame.decodeSelection(act.substring("bet:".length())));
            return;
        }

        switch (act) {
            case "back" -> session.openTable();
            case "clear" -> session.clearBets();
            case "chip-minus" -> session.setChipSize(session.chipSize().subtract(session.game().chipStep()));
            case "chip-plus" -> session.setChipSize(session.chipSize().add(session.game().chipStep()));
            // The wheel animates on the table, so the spin starts by going back to it.
            case "spin" -> {
                session.openTable();
                session.spin();
            }
            case "help" -> plugin.guis().open(new com.dravorax.casinodx.gui.menu.HelpGui(
                    plugin, player, GameType.ROULETTE, session::openNumbers));
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Nothing is escrowed before a spin, so leaving the grid simply ends the round.
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the number grid");
        }
    }
}
