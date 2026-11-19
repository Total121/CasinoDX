package com.dravorax.casinodx.game.coinflip;

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
import java.util.Locale;

/**
 * Coinflip table on a spacious 54-slot board: coin centred, Heads / Tails flanking it, wide TOSS
 * primary below. After the toss lands, New Round keeps the last stake on the same GUI.
 */
public final class CoinflipGui extends GuiView {

    private static final int SIZE = 54;
    private static final int SLOT_COIN = 31;
    private static final int SLOT_HEADS = 29;
    private static final int SLOT_TAILS = 33;
    private static final int SLOT_INFO = 4;
    private static final int SLOT_BALANCE = 49;
    private static final int SLOT_TOSS = 40;
    private static final int SLOT_BACK = 45;
    /** Carries the next-round button once the coin has landed. */
    private static final int SLOT_NEW_ROUND = 47;
    private static final int SLOT_CLOSE = 53;

    private final CoinflipSession session;

    private boolean resultView;
    private CoinflipGame.Side resultSide;
    private boolean resultWin;

    CoinflipGui(CasinoDX plugin, Player player, CoinflipSession session) {
        super(plugin, player, "coinflip");
        this.session = session;
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.accent(), "&l" + Text.smallCaps("Coinflip") + " &8| "
                + Text.smallCaps("Heads Or Tails"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());
        fillBlankInterior(inv);

        drawCoin(inv, palette);
        drawChoice(inv, palette, SLOT_HEADS, CoinflipGame.Side.HEADS, "heads");
        drawChoice(inv, palette, SLOT_TAILS, CoinflipGame.Side.TAILS, "tails");
        drawInfo(inv);
        drawActionBar(inv, palette);

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
    }

    private void drawCoin(Inventory inv, ColorPalette palette) {
        if (resultView && resultSide != null) {
            String headKey = resultSide == CoinflipGame.Side.HEADS ? "heads" : "tails";
            set(inv, SLOT_COIN, plugin.heads().createHead(headKey,
                    Text.color(resultWin ? palette.success() : palette.danger(),
                            "&l" + Text.smallCaps(resultSide.displayName())),
                    "",
                    Text.color(palette.neutral(), "You called: &f"
                            + (session.choice() == null ? "-" : session.choice().displayName())),
                    Text.color(palette.neutral(), "It landed: &f" + resultSide.displayName()),
                    "",
                    Text.color(resultWin ? palette.success() : palette.danger(),
                            resultWin ? "✔ Correct call" : "✕ Wrong call")));
            return;
        }

        ItemStack coin = ItemBuilder.of(Material.SUNFLOWER)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("The Coin")))
                .lore(
                        "",
                        Text.color(palette.neutral(), session.flipping()
                                ? "In the air…" : "A fair 50/50 toss."),
                        Text.color(palette.neutral(), "Payout: ")
                                + Text.color(palette.accent(),
                                        Text.formatMultiplier(session.game().payoutMultiplier()))
                )
                .hideAllFlags()
                .build();
        set(inv, SLOT_COIN, coin);
    }

    private void drawChoice(Inventory inv, ColorPalette palette, int slot,
                            CoinflipGame.Side side, String headKey) {
        boolean selected = session.choice() == side;
        boolean clickable = canChoose();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color(palette.neutral(), "Win chance: &f50.0%"));
        lore.add(Text.color(palette.neutral(), "Returns ")
                + Text.color(palette.success(), plugin.economy().format(session.potentialReturn())));
        lore.add("");
        if (selected) {
            lore.add(Text.color(palette.success(), "✔ Your call"));
        } else if (clickable) {
            lore.add(Text.color(palette.primary(), "▶ Click to call " + side.displayName().toLowerCase(Locale.ROOT)));
        } else {
            lore.add(Text.color(palette.neutral(), "Locked"));
        }

        ItemStack item = plugin.heads().createHead(headKey,
                Text.color(selected ? palette.success() : palette.accent(),
                        (selected ? "&l✔ " : "&l") + Text.smallCaps(side.displayName())),
                lore.toArray(new String[0]));
        if (selected) {
            item = ItemBuilder.of(item).glow().build();
        }
        set(inv, slot, item);

        if (clickable) {
            setAction(slot, "call:" + side.name());
        }
    }

    private void drawInfo(Inventory inv) {
        double edge = session.game().effectiveHouseEdge() * 100.0;
        List<String> lines = new ArrayList<>();
        lines.add("Stake: " + plugin.economy().format(session.bet()));
        lines.add("Payout: " + Text.formatMultiplier(session.game().payoutMultiplier()));
        lines.add("Win chance: 50.0% (fair coin)");
        lines.add(String.format(Locale.US, "House edge: %.2f%%", Math.max(0, edge)));
        lines.add("");
        lines.add("The coin is unbiased; the edge");
        lines.add("is in the payout, not the toss.");
        set(inv, SLOT_INFO, GuiComponents.infoPanel("Coinflip", lines));
    }

    private void drawActionBar(Inventory inv, ColorPalette palette) {
        if (resultView) {
            set(inv, SLOT_TOSS, GuiComponents.statusIndicator(
                    resultWin ? GuiComponents.Status.WIN : GuiComponents.Status.LOSS,
                    resultWin ? "You Won" : "You Lost"));
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

        if (session.flipping()) {
            set(inv, SLOT_TOSS, GuiComponents.statusIndicator(GuiComponents.Status.RUNNING, "Flipping"));
            set(inv, SLOT_BACK, GuiComponents.disabledButton("Back", "Wait for the coin to land."));
            set(inv, SLOT_CLOSE, GuiComponents.disabledButton("Close", "Wait for the coin to land."));
            return;
        }

        if (session.choice() != null) {
            set(inv, SLOT_TOSS, GuiComponents.primaryAction("Toss The Coin",
                    "Calling " + session.choice().displayName(),
                    "Stake " + plugin.economy().format(session.bet()),
                    "Click to toss."));
            setAction(SLOT_TOSS, "toss");
        } else {
            set(inv, SLOT_TOSS, GuiComponents.disabledButton("Toss The Coin", "Call heads or tails first."));
        }

        set(inv, SLOT_BACK, ItemBuilder.of(Material.BARRIER)
                .name(Text.color(palette.warning(), "&l" + Text.smallCaps("Leave & Refund")))
                .lore("", Text.color(palette.neutral(), "Returns your stake of &f"
                        + plugin.economy().format(session.bet())))
                .hideAllFlags()
                .build());
        setAction(SLOT_BACK, "forfeit");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
    }

    private boolean canChoose() {
        return !resultView && session.acceptsInput();
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

        if (act.startsWith("call:")) {
            try {
                session.choose(CoinflipGame.Side.valueOf(act.substring("call:".length())));
            } catch (IllegalArgumentException ignored) {
                // Stale action binding.
            }
            return;
        }

        switch (act) {
            case "toss" -> session.toss();
            case "forfeit" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Left the coinflip table");
                plugin.guis().open(new MainMenuGui(plugin, player));
            }
            case "back" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "new-round" -> newRound();
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    /**
     * Deals a fresh round at the same stake, keeping the call the player just made so that one
     * click really is one more toss. The settled session has already retired itself; force-ending
     * first keeps this safe if it ever has not.
     */
    private void newRound() {
        if (!resultView) {
            return;
        }
        plugin.sessions().forceEnd(player.getUniqueId(), "Starting another toss");
        session.game().startSession(player, session.bet(),
                CoinflipGame.encodeSelection(session.choice()));
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the coinflip table");
        }
    }

    void showResult(CoinflipGame.Side side, boolean win) {
        this.resultView = true;
        this.resultSide = side;
        this.resultWin = win;
        refresh();
    }
}
