package com.dravorax.casinodx.game.blackjack;

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
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Blackjack table.
 *
 * <p>The menu reads top to bottom like a real table: the dealer's row, a status divider, the
 * player's first hand, the second hand after a split, and the action bar. Column 8 is a fixed
 * read-out column (stake, balance, dealer total, hand totals) so those numbers never move while
 * cards are being dealt. Cards are paper with suit-coloured names; the hole card is an empty map.
 *
 * <p>Both card rows are labelled at their left edge — the dealer plate above, the player's own head
 * beside their hand — so it is never ambiguous whose cards are whose. Card positions that have no
 * card yet are grey concrete, visibly a slot on the table rather than part of the frame.
 */
public final class BlackjackGui extends GuiView {

    private static final int SIZE = 54;
    private static final int MAX_CARDS = 7;

    private static final int SLOT_DEALER_LABEL = 4;
    private static final int SLOT_PLAYER_LABEL = 27;
    private static final int SLOT_STAKE = 8;
    private static final int DEALER_CARDS_START = 10;
    private static final int SLOT_DEALER_TOTAL = 17;
    private static final int STATUS_START = 19;
    private static final int SLOT_BALANCE = 26;
    private static final int HAND1_CARDS_START = 28;
    private static final int SLOT_HAND1_TOTAL = 35;
    private static final int HAND2_CARDS_START = 37;
    private static final int SLOT_HAND2_TOTAL = 44;

    private static final int SLOT_HIT = 46;
    private static final int SLOT_STAND = 47;
    private static final int SLOT_DOUBLE = 48;
    private static final int SLOT_SPLIT = 49;
    private static final int SLOT_INFO = 50;
    private static final int SLOT_LEAVE = 51;
    private static final int SLOT_CLOSE = 53;

    private final BlackjackSession session;

    /** Stake the next hand will use. Only adjustable once this one has been settled. */
    private Money nextBet;

    BlackjackGui(CasinoDX plugin, Player player, BlackjackSession session) {
        super(plugin, player, "blackjack");
        this.session = session;
        this.nextBet = session.bet();
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.primary(), "&l" + Text.smallCaps("Blackjack") + " &8| "
                + Text.smallCaps(session.game().dealerHitsSoft17() ? "Hits Soft 17" : "Stands All 17"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        drawFrame(inv, palette);
        drawDealer(inv, palette);
        drawStatusStrip(inv, palette);
        drawPlayerHands(inv, palette);
        drawReadouts(inv, palette);
        drawActionBar(inv, palette);
    }

    private void drawFrame(Inventory inv, ColorPalette palette) {
        ItemStack primary = GuiComponents.decorativePane(palette.borderPrimary());
        for (int row = 0; row < SIZE / 9; row++) {
            set(inv, row * 9, primary);
        }
        for (int slot = 0; slot < 9; slot++) {
            set(inv, slot, primary);
        }
        set(inv, 45, primary);
        set(inv, 52, primary);

        set(inv, SLOT_DEALER_LABEL, ItemBuilder.of(Material.PLAYER_HEAD)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Dealer")))
                .lore(
                        "",
                        Text.color(palette.neutral(), session.game().dealerHitsSoft17()
                                ? "Hits soft 17." : "Stands on all 17s."),
                        Text.color(palette.neutral(), "Blackjack pays ")
                                + Text.color(palette.accent(),
                                        Text.formatMultiplier(session.game().blackjackMultiplier()))
                )
                .hideAllFlags()
                .build());

        set(inv, SLOT_PLAYER_LABEL, playerLabel(palette));
    }

    /**
     * The player's own head beside their card row, mirroring the dealer plate at the top of the
     * table. Falls back to a plain head if the server hands back meta that is not a skull.
     */
    private ItemStack playerLabel(ColorPalette palette) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            head.setItemMeta(skull);
        }
        List<Hand> hands = session.playerHands();
        return ItemBuilder.of(head)
                .name(Text.color(palette.success(), "&l" + Text.smallCaps("Your Cards")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Player: &f" + player.getName()),
                        Text.color(palette.neutral(), hands.size() > 1
                                ? "Two hands after the split."
                                : "Beat the dealer without passing 21."),
                        "",
                        Text.color(palette.neutral(), "At risk: &f"
                                + plugin.economy().format(session.totalEscrow()))
                )
                .hideAllFlags()
                .build();
    }

    private void drawDealer(Inventory inv, ColorPalette palette) {
        Hand dealer = session.dealerHand();
        int visible = Math.max(0, Math.min(session.revealedDealerCards(), dealer.size()));

        for (int i = 0; i < MAX_CARDS; i++) {
            int slot = DEALER_CARDS_START + i;
            if (i < visible) {
                set(inv, slot, cardItem(dealer.card(i), palette));
            } else if (i == visible && i < dealer.size()) {
                // The face-down hole card, or a card still on its way.
                set(inv, slot, faceDownCard(palette));
            } else {
                set(inv, slot, emptyCardSlot(palette));
            }
        }
    }

    private void drawPlayerHands(Inventory inv, ColorPalette palette) {
        List<Hand> hands = session.playerHands();
        drawHandRow(inv, palette, HAND1_CARDS_START, hands.get(0), 0);

        if (hands.size() > 1) {
            drawHandRow(inv, palette, HAND2_CARDS_START, hands.get(1), 1);
        } else {
            // No split, so this is not a card row at all: plain filler rather than empty card
            // positions the player might be waiting to see filled.
            for (int i = 0; i < MAX_CARDS; i++) {
                set(inv, HAND2_CARDS_START + i, GuiComponents.blankPane());
            }
        }
    }

    private void drawHandRow(Inventory inv, ColorPalette palette, int start, Hand hand, int handIndex) {
        boolean active = session.playerCanAct() && session.activeHandIndex() == handIndex;
        // Only the opening deal drip-feeds cards; afterwards a hand is always fully visible.
        int visible = session.phase() == BlackjackSession.Phase.DEALING
                ? Math.min(hand.size(), Math.max(0, session.revealedPlayerCards()))
                : hand.size();

        for (int i = 0; i < MAX_CARDS; i++) {
            int slot = start + i;
            if (i < visible) {
                set(inv, slot, cardItem(hand.card(i), palette));
            } else if (active && i == visible) {
                set(inv, slot, ItemBuilder.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name(Text.color(palette.primary(), Text.smallCaps("Next Card")))
                        .hideAllFlags()
                        .build());
            } else {
                set(inv, slot, emptyCardSlot(palette));
            }
        }
    }

    private void drawStatusStrip(Inventory inv, ColorPalette palette) {
        String label;
        GuiComponents.Status status;
        switch (session.phase()) {
            case DEALING -> {
                label = "Dealing";
                status = GuiComponents.Status.RUNNING;
            }
            case PLAYER_TURN -> {
                label = session.isSplit()
                        ? "Your Turn · Hand " + (session.activeHandIndex() + 1)
                        : "Your Turn";
                status = GuiComponents.Status.NORMAL;
            }
            case DEALER_TURN -> {
                label = "Dealer Draws";
                status = GuiComponents.Status.RUNNING;
            }
            default -> {
                String summary = session.outcomeSummary();
                boolean won = summary.contains("win") || summary.contains("blackjack");
                label = "Round Over";
                status = won ? GuiComponents.Status.WIN : GuiComponents.Status.LOSS;
            }
        }

        ItemStack indicator = GuiComponents.statusIndicator(status, label);
        for (int i = 0; i < 7; i++) {
            set(inv, STATUS_START + i, i == 3 ? indicator
                    : GuiComponents.decorativePane(palette.borderSecondary()));
        }
    }

    private void drawReadouts(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_STAKE, ItemBuilder.of(Material.GOLD_INGOT)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("At Risk")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Total: &f"
                                + plugin.economy().format(session.totalEscrow())),
                        Text.color(palette.neutral(), "Opening bet: &f"
                                + plugin.economy().format(session.bet()))
                )
                .hideAllFlags()
                .glow()
                .build());

        Hand dealer = session.dealerHand();
        boolean dealerHidden = session.revealedDealerCards() < dealer.size();
        int shownTotal = dealerHidden ? visibleTotal(dealer, session.revealedDealerCards()) : dealer.total();
        set(inv, SLOT_DEALER_TOTAL, ItemBuilder.of(dealer.isBust() && !dealerHidden
                        ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK)
                .name(Text.color(palette.primary(), "&l" + Text.smallCaps("Dealer Total")))
                .lore(
                        "",
                        Text.color(palette.neutral(), dealerHidden
                                ? "Showing: &f" + shownTotal + " &7(+ hidden card)"
                                : "Total: &f" + dealer.total()),
                        dealerHidden ? "" : Text.color(palette.neutral(),
                                dealer.isBust() ? "&cBust" : (dealer.isSoft() ? "Soft" : "Hard"))
                )
                .hideAllFlags()
                .build());

        List<Hand> hands = session.playerHands();
        set(inv, SLOT_HAND1_TOTAL, handTotalItem(palette, hands.get(0), 0));
        if (hands.size() > 1) {
            set(inv, SLOT_HAND2_TOTAL, handTotalItem(palette, hands.get(1), 1));
        } else {
            // The balance already has a home at slot 26; repeating it here only made the read-out
            // column look like it held two different numbers.
            set(inv, SLOT_HAND2_TOTAL, GuiComponents.blankPane());
        }

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
    }

    private ItemStack handTotalItem(ColorPalette palette, Hand hand, int index) {
        boolean active = session.playerCanAct() && session.activeHandIndex() == index;
        String state = hand.isBust() ? "&cBust"
                : hand.isBlackjack() ? "&aBlackjack"
                : hand.stood() ? "&7Stood"
                : active ? "&eActive" : "&7Waiting";

        Material material = hand.isBust() ? Material.REDSTONE_BLOCK
                : hand.isBlackjack() ? Material.EMERALD_BLOCK
                : Material.PAPER;

        ItemBuilder builder = ItemBuilder.of(material)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps(
                        session.isSplit() ? "Hand " + (index + 1) : "Your Hand")))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Total: &f" + hand.total()
                                + (hand.isSoft() ? " &7(soft)" : "")),
                        Text.color(palette.neutral(), "Wager: &f" + plugin.economy().format(hand.wager())
                                + (hand.doubled() ? " &7(doubled)" : "")),
                        Text.color(palette.neutral(), "State: ") + state
                )
                .hideAllFlags();
        if (active) {
            builder.glow();
        }
        return builder.build();
    }

    /** Total of just the cards currently face-up, for the dealer's partial read-out. */
    private int visibleTotal(Hand hand, int visibleCount) {
        int total = 0;
        int aces = 0;
        for (int i = 0; i < Math.min(visibleCount, hand.size()); i++) {
            Card card = hand.card(i);
            total += card.value();
            if (card.isAce()) {
                aces++;
            }
        }
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    private ItemStack cardItem(Card card, ColorPalette palette) {
        if (card == null) {
            return emptyCardSlot(palette);
        }
        return ItemBuilder.of(Material.PAPER)
                .name(card.suit().colorCode() + "&l" + card.rank().label() + " " + card.suit().symbol())
                .lore(
                        "",
                        Text.color(palette.neutral(), "Rank: &f" + card.rank().label()),
                        Text.color(palette.neutral(), "Suit: ") + card.suit().colorCode()
                                + card.suit().displayName() + " " + card.suit().symbol(),
                        Text.color(palette.neutral(), "Counts as: &f"
                                + (card.isAce() ? "1 or 11" : String.valueOf(card.value())))
                )
                .hideAllFlags()
                .build();
    }

    private ItemStack faceDownCard(ColorPalette palette) {
        return ItemBuilder.of(Material.MAP)
                .name(Text.color(palette.primary(), "&l" + Text.smallCaps("Face Down")))
                .lore("", Text.color(palette.neutral(), "The dealer's hole card."))
                .hideAllFlags()
                .build();
    }

    /** A card position with no card in it: a slot on the table, not part of the frame. */
    private ItemStack emptyCardSlot(ColorPalette palette) {
        return ItemBuilder.of(Material.GRAY_CONCRETE)
                .name(Text.color(palette.neutral(), Text.smallCaps("Empty")))
                .hideAllFlags()
                .build();
    }

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        Hand hand = session.activeHand();
        boolean canAct = session.playerCanAct() && hand != null;

        if (canAct) {
            set(inv, SLOT_HIT, actionButton(palette, Material.LIME_CONCRETE, "Hit",
                    "Take another card.",
                    "Bust above 21 and the hand is lost."));
            setAction(SLOT_HIT, "hit");

            set(inv, SLOT_STAND, actionButton(palette, Material.RED_CONCRETE, "Stand",
                    "Keep your total of " + hand.total() + ".",
                    session.isSplit() && session.activeHandIndex() == 0
                            ? "Moves on to your second hand."
                            : "Passes play to the dealer."));
            setAction(SLOT_STAND, "stand");

            boolean doubleAvailable = session.game().allowDoubleDown() && hand.canDouble();
            if (doubleAvailable) {
                set(inv, SLOT_DOUBLE, actionButton(palette, Material.GOLD_BLOCK, "Double Down",
                        "Doubles your wager to "
                                + plugin.economy().format(hand.wager().multiply(2.0)) + ".",
                        "Takes exactly one more card."));
                setAction(SLOT_DOUBLE, "double");
            } else {
                set(inv, SLOT_DOUBLE, GuiComponents.disabledButton("Double Down",
                        session.game().allowDoubleDown()
                                ? "Only on your first two cards."
                                : "Disabled on this server."));
            }

            boolean splitAvailable = session.game().allowSplit() && !session.isSplit() && hand.isSplittable();
            if (splitAvailable) {
                set(inv, SLOT_SPLIT, actionButton(palette, Material.SHEARS, "Split",
                        "Splits your pair into two hands.",
                        "Costs another " + plugin.economy().format(hand.wager()) + "."));
                setAction(SLOT_SPLIT, "split");
            } else {
                set(inv, SLOT_SPLIT, GuiComponents.disabledButton("Split",
                        !session.game().allowSplit() ? "Disabled on this server."
                                : session.isSplit() ? "One split per round."
                                : "Needs a matching pair."));
            }
        } else if (session.phase() == BlackjackSession.Phase.FINISHED) {
            // The hand actions are meaningless now, so the row becomes the next-hand control
            // instead of four greyed-out buttons: deal again on the left, stake controls beside it.
            String stakeLabel = plugin.economy().format(nextBet);
            if (plugin.economy().has(player, nextBet)) {
                set(inv, SLOT_HIT, GuiComponents.playAgainButton(stakeLabel));
                setAction(SLOT_HIT, "replay");
            } else {
                set(inv, SLOT_HIT, GuiComponents.disabledButton("Play Again",
                        "You need " + stakeLabel + " to stake that."));
            }

            String stepLabel = plugin.economy().format(Money.of(step()));
            set(inv, SLOT_STAND, GuiComponents.betMinus(stepLabel));
            setAction(SLOT_STAND, "bet-minus");
            set(inv, SLOT_DOUBLE, GuiComponents.currentBetPanel(nextBet, stakeLabel,
                    session.game().minBet(), session.game().maxBet()));
            set(inv, SLOT_SPLIT, GuiComponents.betPlus(stepLabel));
            setAction(SLOT_SPLIT, "bet-plus");
        } else {
            String reason = "Wait your turn.";
            set(inv, SLOT_HIT, GuiComponents.disabledButton("Hit", reason));
            set(inv, SLOT_STAND, GuiComponents.disabledButton("Stand", reason));
            set(inv, SLOT_DOUBLE, GuiComponents.disabledButton("Double Down", reason));
            set(inv, SLOT_SPLIT, GuiComponents.disabledButton("Split", reason));
        }

        List<String> info = new ArrayList<>();
        if (session.phase() == BlackjackSession.Phase.FINISHED) {
            info.add("Result: " + (session.outcomeSummary().isEmpty() ? "-" : session.outcomeSummary()));
            info.add("Dealer: " + session.dealerHand().total());
        } else {
            info.add("Dealer " + (session.game().dealerHitsSoft17() ? "hits" : "stands on") + " soft 17.");
            info.add("Blackjack pays " + Text.formatMultiplier(session.game().blackjackMultiplier()) + ".");
            info.add("Wins pay " + Text.formatMultiplier(session.game().winMultiplier()) + ".");
            info.add(session.game().deckCount() + "-deck shoe.");
        }
        set(inv, SLOT_INFO, GuiComponents.infoPanel("Table Rules", info));

        if (session.phase() == BlackjackSession.Phase.FINISHED) {
            set(inv, SLOT_LEAVE, GuiComponents.casinoButton());
            setAction(SLOT_LEAVE, "back");
            set(inv, SLOT_CLOSE, GuiComponents.closeButton());
            setAction(SLOT_CLOSE, "close");
        } else if (session.playerCanAct()) {
            set(inv, SLOT_LEAVE, ItemBuilder.of(Material.BARRIER)
                    .name(Text.color(palette.warning(), "&l" + Text.smallCaps("Leave & Refund")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Abandons the hand and returns"),
                            Text.color(palette.neutral(), "&f" + plugin.economy().format(session.totalEscrow())
                                    + " &7— everything you have staked.")
                    )
                    .hideAllFlags()
                    .build());
            setAction(SLOT_LEAVE, "forfeit");
            set(inv, SLOT_CLOSE, GuiComponents.closeButton());
            setAction(SLOT_CLOSE, "close");
        } else {
            set(inv, SLOT_LEAVE, GuiComponents.disabledButton("Leave", "Cards are still in motion."));
            set(inv, SLOT_CLOSE, GuiComponents.disabledButton("Close", "Cards are still in motion."));
        }
    }

    private ItemStack actionButton(ColorPalette palette, Material material, String label,
                                   String line1, String line2) {
        return ItemBuilder.of(material)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps(label)))
                .lore(
                        "",
                        Text.color(palette.neutral(), line1),
                        Text.color(palette.neutral(), line2)
                )
                .hideAllFlags()
                .build();
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
            case "hit" -> session.hit();
            case "stand" -> session.stand();
            case "double" -> session.doubleDown();
            case "split" -> session.split();
            case "forfeit" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Left the blackjack table");
                plugin.guis().open(new MainMenuGui(plugin, player));
            }
            case "back" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "replay" -> dealAgain();
            case "bet-plus" -> adjustNextBet(step());
            case "bet-minus" -> adjustNextBet(-step());
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    /**
     * Deals the next hand at the same stake in one click, without a play-again screen in between.
     * The finished session has already retired itself; force-ending first keeps this safe if it
     * ever has not, because {@link BlackjackGame#startSession} refuses to escrow while a session
     * exists.
     */
    private void dealAgain() {
        if (session.phase() != BlackjackSession.Phase.FINISHED) {
            return;
        }
        plugin.sessions().forceEnd(player.getUniqueId(), "Dealing another hand");
        session.game().startSession(player, nextBet);
    }

    private double step() {
        double configured = plugin.configs().gui().getDouble("bet-select.step", 10.0);
        return configured > 0 ? configured : 10.0;
    }

    private void adjustNextBet(double delta) {
        if (session.phase() != BlackjackSession.Phase.FINISHED) {
            return;
        }
        Money min = session.game().minBet();
        Money max = session.game().maxBet();
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

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the blackjack table");
        }
    }
}
