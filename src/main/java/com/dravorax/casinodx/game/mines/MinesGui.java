package com.dravorax.casinodx.game.mines;

import com.dravorax.casinodx.CasinoDX;
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

/**
 * The Mines board.
 *
 * <p>Layout of the 54-slot menu: the grid sits in the upper-left block, a vertical statistics
 * column runs down slot column 7, and the bottom row carries the cash-out and navigation buttons.
 * Columns 0 and 8 frame the menu and column 6 is a plain separator between the board and the
 * statistics, so the board reads as a framed panel rather than a loose scattering of items.
 *
 * <p>Every board tile is a solid block and every non-board slot is a glass pane. That split is
 * deliberate: it is what tells the player at a glance which squares are theirs to click, without
 * relying on colour alone.
 */
public final class MinesGui extends GuiView {

    private static final int SIZE = 54;
    private static final int SLOT_CASHOUT = 49;
    /** Carries the next-round button once the board has resolved. */
    private static final int SLOT_NEW_ROUND = 50;
    private static final int SLOT_BACK = 46;
    private static final int SLOT_CLOSE = 52;
    private static final int[] INFO_SLOTS = {7, 16, 25, 34, 43};
    private static final int[] FRAME_COLUMNS = {0, 8};
    /** Divides the board from the statistics column. */
    private static final int SEPARATOR_COLUMN = 6;

    private final MinesSession session;
    private final int[] tileSlots;

    /** Set once the round is over so the board renders as a read-only result screen. */
    private boolean finalView;
    private boolean finalWin;
    private Money finalPayout = Money.ZERO;

    MinesGui(CasinoDX plugin, Player player, MinesSession session) {
        super(plugin, player, "mines");
        this.session = session;
        this.tileSlots = computeTileSlots(session.gridSize());
    }

    /**
     * Maps board indices to inventory slots, centring the grid inside rows 0–4 / columns 1–5.
     */
    private static int[] computeTileSlots(int gridSize) {
        int rowOffset = (5 - gridSize) / 2;
        int colOffset = 1 + (5 - gridSize) / 2;
        int[] slots = new int[gridSize * gridSize];
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                slots[row * gridSize + col] = (row + rowOffset) * 9 + (col + colOffset);
            }
        }
        return slots;
    }

    /** Inventory slot → board index, or {@code -1} when the slot is not part of the board. */
    private int indexForSlot(int slot) {
        for (int i = 0; i < tileSlots.length; i++) {
            if (tileSlots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /** Inventory slot holding a given board index; used by the reveal animations. */
    int slotForIndex(int index) {
        return index < 0 || index >= tileSlots.length ? -1 : tileSlots[index];
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        if (finalView) {
            return finalWin
                    ? Text.color(c.success(), "&l" + Text.smallCaps("Mines") + " &8| " + Text.smallCaps("Cashed Out"))
                    : Text.color(c.danger(), "&l" + Text.smallCaps("Mines") + " &8| " + Text.smallCaps("Boom"));
        }
        return Text.color(c.primary(), "&l" + Text.smallCaps("Mines") + " &8| "
                + Text.smallCaps(session.game().mineCount() + " mines"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        drawFrame(inv, palette);
        drawBoard(inv, palette);
        drawInfoColumn(inv, palette);
        drawActionBar(inv, palette);
    }

    private void drawFrame(Inventory inv, ColorPalette palette) {
        ItemStack primary = GuiComponents.decorativePane(palette.borderPrimary());
        // Plain grey rather than the palette's secondary: the separator's only job is to be quiet
        // next to the board, and a second accent colour there reads as another row of tiles.
        ItemStack separator = GuiComponents.decorativePane(Material.GRAY_STAINED_GLASS_PANE);
        for (int row = 0; row < SIZE / 9; row++) {
            for (int column : FRAME_COLUMNS) {
                set(inv, row * 9 + column, primary);
            }
            set(inv, row * 9 + SEPARATOR_COLUMN, separator);
        }
        for (int slot = 45; slot < SIZE; slot++) {
            set(inv, slot, primary);
        }
    }

    private void drawBoard(Inventory inv, ColorPalette palette) {
        for (int index = 0; index < tileSlots.length; index++) {
            int slot = tileSlots[index];
            set(inv, slot, tileItem(index, palette));
            if (!finalView && session.acceptsInput() && !session.isRevealed(index)) {
                setAction(slot, "tile:" + index);
            }
        }
    }

    private ItemStack tileItem(int index, ColorPalette palette) {
        boolean revealed = session.isRevealed(index);
        boolean isMine = session.isMine(index);

        if (finalView && !revealed) {
            // End of round: show what the player never got to touch.
            return isMine
                    ? ItemBuilder.of(Material.GUNPOWDER)
                        .name(Text.color(palette.danger(), Text.smallCaps("Hidden Mine")))
                        .lore("", Text.color(palette.neutral(), "You avoided this one."))
                        .hideAllFlags()
                        .build()
                    : ItemBuilder.of(Material.LIME_CONCRETE)
                        .name(Text.color(palette.neutral(), Text.smallCaps("Safe Tile")))
                        .lore("", Text.color(palette.neutral(), "This one was clean."))
                        .hideAllFlags()
                        .build();
        }

        if (revealed && isMine) {
            boolean triggered = index == session.triggeredMineIndex();
            ItemBuilder builder = ItemBuilder.of(Material.TNT)
                    .name(Text.color(palette.danger(), "&l" + Text.smallCaps("Mine")))
                    .lore("", Text.color(palette.neutral(),
                            triggered ? "This is the one that got you." : "Revealed at the end of the round."))
                    .hideAllFlags();
            return triggered ? builder.glow().build() : builder.build();
        }

        if (revealed) {
            return ItemBuilder.of(Material.EMERALD)
                    .name(Text.color(palette.success(), "&l" + Text.smallCaps("Safe")))
                    .lore("", Text.color(palette.neutral(), "A gem, not a mine."))
                    .hideAllFlags()
                    .build();
        }

        // A solid block for a live tile, plain concrete for a locked one. Both are visibly unlike
        // the glass-pane frame, so an untouched square never reads as decoration.
        boolean clickable = !finalView && session.acceptsInput();
        return ItemBuilder.of(clickable ? Material.AMETHYST_BLOCK : Material.GRAY_CONCRETE)
                .name(Text.color(clickable ? palette.accent() : palette.neutral(), "&l?"))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Next tile pays ")
                                + Text.color(palette.accent(), Text.formatMultiplier(session.nextMultiplier())),
                        Text.color(palette.neutral(), clickable ? "Click to reveal." : "Locked.")
                )
                .hideAllFlags()
                .build();
    }

    private void drawInfoColumn(Inventory inv, ColorPalette palette) {
        int tilesLeft = session.game().safeTileCount() - session.safeRevealed();
        int unrevealed = session.tileCount() - countRevealed();
        double mineChance = unrevealed <= 0 ? 0 : (session.game().mineCount() * 100.0) / unrevealed;

        set(inv, INFO_SLOTS[0], ItemBuilder.of(Material.GOLD_INGOT)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Your Bet")))
                .lore("", Text.color(palette.neutral(), plugin.economy().format(session.bet())))
                .hideAllFlags()
                .glow()
                .build());

        set(inv, INFO_SLOTS[1], ItemBuilder.of(Material.EXPERIENCE_BOTTLE)
                .name(Text.color(palette.primary(), "&l" + Text.smallCaps("Multiplier")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Current: ")
                                + Text.color(palette.accent(), Text.formatMultiplier(session.currentMultiplier())),
                        Text.color(palette.neutral(), "Next tile: ")
                                + Text.color(palette.success(), Text.formatMultiplier(session.nextMultiplier()))
                )
                .hideAllFlags()
                .build());

        set(inv, INFO_SLOTS[2], ItemBuilder.of(Material.DIAMOND)
                .name(Text.color(palette.success(), "&l" + Text.smallCaps("Potential Payout")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Cash out now: ")
                                + Text.color(palette.success(), plugin.economy().format(session.currentPayout())),
                        Text.color(palette.neutral(), "One more tile: ")
                                + Text.color(palette.accent(),
                                        plugin.economy().format(session.bet().multiply(session.nextMultiplier())))
                )
                .hideAllFlags()
                .build());

        set(inv, INFO_SLOTS[3], ItemBuilder.of(Material.TNT)
                .name(Text.color(palette.danger(), "&l" + Text.smallCaps("Board")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Mines: &f" + session.game().mineCount()),
                        Text.color(palette.neutral(), "Safe found: &f" + session.safeRevealed()),
                        Text.color(palette.neutral(), "Safe left: &f" + Math.max(0, tilesLeft)),
                        Text.color(palette.neutral(), String.format("Mine risk: &f%.1f%%", mineChance))
                )
                .hideAllFlags()
                .build());

        Money balance = plugin.economy().getBalance(player);
        set(inv, INFO_SLOTS[4], GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
    }

    private int countRevealed() {
        int revealed = 0;
        for (int i = 0; i < session.tileCount(); i++) {
            if (session.isRevealed(i)) {
                revealed++;
            }
        }
        return revealed;
    }

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        if (finalView) {
            List<String> lines = new ArrayList<>();
            lines.add(finalWin ? "You cashed out." : "The board got you.");
            lines.add("Safe tiles found: " + session.safeRevealed());
            if (finalWin) {
                lines.add("Multiplier: " + Text.formatMultiplier(session.currentMultiplier()));
                lines.add("Returned: " + plugin.economy().format(finalPayout));
            } else {
                lines.add("Lost: " + plugin.economy().format(session.bet()));
            }
            set(inv, SLOT_CASHOUT, GuiComponents.statusIndicator(
                    finalWin ? GuiComponents.Status.WIN : GuiComponents.Status.LOSS,
                    finalWin ? "Cashed Out" : "Busted"));
            set(inv, 48, GuiComponents.infoPanel("Result", lines));
            String stakeLabel = plugin.economy().format(session.bet());
            if (plugin.economy().has(player, session.bet())) {
                set(inv, SLOT_NEW_ROUND, GuiComponents.newRoundButton(stakeLabel));
                setAction(SLOT_NEW_ROUND, "new-round");
            } else {
                set(inv, SLOT_NEW_ROUND, GuiComponents.disabledButton("New Round",
                        "You need " + stakeLabel + " to stake that."));
            }
            set(inv, SLOT_BACK, GuiComponents.casinoButton());
            setAction(SLOT_BACK, "back");
            set(inv, SLOT_CLOSE, GuiComponents.closeButton());
            setAction(SLOT_CLOSE, "close");
            return;
        }

        if (session.canCashOut()) {
            set(inv, SLOT_CASHOUT, GuiComponents.cashOutButton(
                    plugin.economy().format(session.currentPayout())
                            + " @ " + Text.formatMultiplier(session.currentMultiplier())));
            setAction(SLOT_CASHOUT, "cashout");
        } else {
            set(inv, SLOT_CASHOUT, GuiComponents.disabledButton("Cash Out",
                    session.acceptsInput() ? "Reveal at least one safe tile first."
                            : "Wait for the reveal to finish."));
        }

        set(inv, 48, GuiComponents.infoPanel("How To Play", List.of(
                "Click tiles to reveal gems.",
                "Each gem raises your multiplier.",
                "One mine ends the round.",
                "Cash out any time to keep your winnings."
        )));

        // Leaving mid-round refunds the wager, so the button says exactly that.
        set(inv, SLOT_BACK, ItemBuilder.of(Material.BARRIER)
                .name(Text.color(palette.warning(), "&l" + Text.smallCaps("Forfeit & Refund")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Abandons the round and returns"),
                        Text.color(palette.neutral(), "your bet of &f" + plugin.economy().format(session.bet()) + "&7.")
                )
                .hideAllFlags()
                .build());
        setAction(SLOT_BACK, "forfeit");
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

        if (act.startsWith("tile:")) {
            int index = indexForSlot(event.getRawSlot());
            if (index >= 0) {
                session.reveal(index);
            }
            return;
        }

        switch (act) {
            case "cashout" -> session.cashOut(false);
            case "forfeit" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Forfeited the round");
                plugin.guis().open(new com.dravorax.casinodx.gui.menu.MainMenuGui(plugin, player));
            }
            case "back" -> plugin.guis().open(new com.dravorax.casinodx.gui.menu.MainMenuGui(plugin, player));
            case "new-round" -> newRound();
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    /**
     * Lays out a fresh board at the same stake. The resolved session has already retired itself;
     * force-ending first keeps this safe if it ever has not, because
     * {@link MinesGame#startSession} refuses to escrow while a session exists.
     */
    private void newRound() {
        if (!finalView) {
            return;
        }
        plugin.sessions().forceEnd(player.getUniqueId(), "Starting another board");
        session.game().startSession(player, session.bet());
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        // Closing an unresolved board forfeits it; GameSession decides refund vs. settle.
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the board");
        }
    }

    /** Switches the board to its read-only result state. */
    void showFinalBoard(boolean win, Money payout) {
        this.finalView = true;
        this.finalWin = win;
        this.finalPayout = payout == null ? Money.ZERO : payout;
        refresh();
    }
}
