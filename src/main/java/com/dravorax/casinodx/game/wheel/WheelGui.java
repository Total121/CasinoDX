package com.dravorax.casinodx.game.wheel;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.gui.menu.HistoryGui;
import com.dravorax.casinodx.gui.menu.MainMenuGui;
import com.dravorax.casinodx.gui.menu.StatsGui;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.util.CooldownFormat;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wheel of Fortune screen.
 *
 * <p>The strip in row two scrolls horizontally beneath a fixed pointer in row one, which is how a
 * physical wheel reads when flattened into a chest menu. {@link WheelSpinAnimation} owns the strip
 * and highlight slots while a spin is in flight; everything else is drawn here.
 */
public final class WheelGui extends GuiView {

    private static final int SIZE = 54;

    private static final int SLOT_POINTER = 4;
    private static final int SLOT_PRIZES = 27;
    private static final int SLOT_STATUS = 31;
    private static final int SLOT_BALANCE = 35;
    private static final int SLOT_FREE_SPIN = 38;
    private static final int SLOT_COST = 40;
    private static final int SLOT_PAID_SPIN = 42;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_STATS = 48;
    private static final int SLOT_HISTORY = 50;
    private static final int SLOT_CLOSE = 53;

    private final WheelSession session;

    WheelGui(CasinoDX plugin, Player player, WheelSession session) {
        super(plugin, player, "wheel");
        this.session = session;
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.accent(), "&l" + Text.smallCaps("Wheel Of Fortune"))
                + Text.color(c.neutral(), " &8| " + Text.smallCaps("Spin To Win"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());

        drawPointer(inv, palette);
        drawStrip(inv, palette);
        drawHighlightLane(inv);
        drawPrizeList(inv);
        drawStatus(inv, palette);
        drawSpinButtons(inv, palette);
        drawNavigation(inv, palette);

        Money balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
    }

    private void drawPointer(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_POINTER, ItemBuilder.of(Material.REDSTONE_TORCH)
                .name(Text.color(palette.danger(), "&l▼ " + Text.smallCaps("Pointer") + " ▼"))
                .lore("", Text.color(palette.neutral(), "Whatever stops here is yours."))
                .hideAllFlags()
                .build());
    }

    /**
     * Draws the strip at rest, centred on the last known section so the wheel never appears blank.
     * While spinning, the animation overwrites these slots every tick.
     */
    private void drawStrip(Inventory inv, ColorPalette palette) {
        List<WheelReward> sections = session.game().sections();
        if (sections.isEmpty()) {
            return;
        }
        int centre = Math.floorMod(session.displayIndex(), sections.size());
        int position = Math.floorMod(centre - WheelSpinAnimation.POINTER_OFFSET, sections.size());

        for (int i = 0; i < WheelSpinAnimation.STRIP_SLOTS.length; i++) {
            WheelReward reward = sections.get(Math.floorMod(position + i, sections.size()));
            boolean pointed = i == WheelSpinAnimation.POINTER_OFFSET;
            boolean landed = pointed && session.hasResult();

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.addAll(describe(reward, palette));
            lore.add("");
            lore.add(Text.color(palette.neutral(), String.format(Locale.US, "Chance: &f%.2f%%",
                    session.game().chanceOf(reward) * 100.0)));
            if (landed) {
                lore.add("");
                lore.add(Text.color(palette.success(), "✔ You landed here"));
            }

            ItemBuilder builder = ItemBuilder.of(reward.icon())
                    .name(Text.color(pointed ? palette.accent() : palette.neutral(),
                            (pointed ? "&l▶ " : "") + Text.smallCaps(reward.displayName())))
                    .lore(lore)
                    .hideAllFlags();
            if (pointed) {
                builder.glow();
            }
            set(inv, WheelSpinAnimation.STRIP_SLOTS[i], builder.build());
        }
    }

    private void drawHighlightLane(Inventory inv) {
        for (int i = 0; i < WheelSpinAnimation.HIGHLIGHT_SLOTS.length; i++) {
            boolean pointed = i == WheelSpinAnimation.POINTER_OFFSET;
            Material pane = pointed
                    ? (session.hasResult() ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE)
                    : Material.GRAY_STAINED_GLASS_PANE;
            set(inv, WheelSpinAnimation.HIGHLIGHT_SLOTS[i],
                    ItemBuilder.of(pane).name(" ").hideAllFlags().build());
        }
    }

    /** A human-readable description of what a section actually pays. */
    private List<String> describe(WheelReward reward, ColorPalette palette) {
        List<String> lines = new ArrayList<>();
        switch (reward.type()) {
            case MONEY -> lines.add(Text.color(palette.success(),
                    "Pays " + plugin.economy().format(reward.money())));
            case MULTIPLIER -> {
                Money base = session.game().paidSpinCost().isPositive()
                        ? session.game().paidSpinCost()
                        : session.game().multiplierBase();
                lines.add(Text.color(palette.success(),
                        "Pays " + Text.formatMultiplier(reward.multiplier()) + " of the stake"));
                lines.add(Text.color(palette.neutral(), "≈ &f"
                        + plugin.economy().format(base.multiply(Math.max(0, reward.multiplier())))));
            }
            case ITEMS -> {
                lines.add(Text.color(palette.accent(), "Item reward:"));
                reward.items().forEach(item ->
                        lines.add(Text.color(palette.neutral(), " • &f" + prettyItem(item))));
            }
            case COMMAND -> lines.add(Text.color(palette.accent(), "Special reward"));
            case NOTHING -> lines.add(Text.color(palette.danger(), "Better luck next time"));
        }
        return lines;
    }

    private static String prettyItem(String spec) {
        if (spec == null || spec.isBlank()) {
            return "?";
        }
        String[] parts = spec.split(":");
        String name = parts[0].trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        String amount = parts.length > 1 ? parts[1].trim() : "1";
        return amount + "x " + name;
    }

    private void drawPrizeList(Inventory inv) {
        List<String> lines = new ArrayList<>();
        lines.add("Every section and its real odds:");
        lines.add("");
        for (WheelReward reward : session.game().sections()) {
            lines.add(String.format(Locale.US, "%-16s %5.2f%%",
                    trim(reward.displayName()), session.game().chanceOf(reward) * 100.0));
        }
        lines.add("");
        lines.add("Odds are drawn from the configured");
        lines.add("weights and always total 100%.");
        set(inv, SLOT_PRIZES, GuiComponents.infoPanel("Prize Table", lines));
    }

    private static String trim(String value) {
        return value.length() <= 16 ? value : value.substring(0, 15) + "…";
    }

    private void drawStatus(Inventory inv, ColorPalette palette) {
        if (session.spinning()) {
            set(inv, SLOT_STATUS, GuiComponents.statusIndicator(GuiComponents.Status.RUNNING, "Spinning"));
            return;
        }
        WheelReward result = session.hasResult() ? session.result() : null;
        if (result != null) {
            boolean blank = result.isNothing();
            set(inv, SLOT_STATUS, GuiComponents.statusIndicator(
                    blank ? GuiComponents.Status.LOSS : GuiComponents.Status.WIN,
                    blank ? "No Prize" : result.displayName()));
            return;
        }
        set(inv, SLOT_STATUS, GuiComponents.statusIndicator(GuiComponents.Status.NORMAL, "Ready To Spin"));
    }

    private void drawSpinButtons(Inventory inv, ColorPalette palette) {
        drawCostPanel(inv);

        if (session.spinning()) {
            set(inv, SLOT_FREE_SPIN, GuiComponents.disabledButton("Free Spin",
                    "The wheel is still turning."));
            set(inv, SLOT_PAID_SPIN, GuiComponents.disabledButton("Paid Spin",
                    "The wheel is still turning."));
            return;
        }
        if (session.hasResult()) {
            // The spun session has already settled, so both buttons restart the wheel instead of
            // acting on it. That is what replaces the old "play again" hop through the lobby.
            drawSpinAgainButtons(inv, palette);
            return;
        }

        boolean locked = !session.acceptsInput();

        // Free spin.
        if (locked) {
            set(inv, SLOT_FREE_SPIN, GuiComponents.disabledButton("Free Spin",
                    "The wheel is not ready."));
        } else if (session.freeSpinReady()) {
            set(inv, SLOT_FREE_SPIN, ItemBuilder.of(Material.EMERALD_BLOCK)
                    .name(Text.color(palette.success(), "&l" + Text.smallCaps("Free Daily Spin")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Costs nothing."),
                            Text.color(palette.neutral(), "Resets every &f"
                                    + CooldownFormat.format(session.game().cooldownMillis())),
                            "",
                            Text.color(palette.primary(), "▶ Click to spin")
                    )
                    .hideAllFlags()
                    .glow()
                    .build());
            setAction(SLOT_FREE_SPIN, "spin-free");
        } else {
            set(inv, SLOT_FREE_SPIN, GuiComponents.disabledButton("Free Daily Spin",
                    "Available in " + CooldownFormat.format(session.cooldownRemaining())));
        }

        // Paid spin.
        Money cost = session.game().paidSpinCost();
        boolean affordable = cost.isPositive() && plugin.economy().has(player, cost);
        if (locked) {
            set(inv, SLOT_PAID_SPIN, GuiComponents.disabledButton("Paid Spin",
                    "The wheel is not ready."));
        } else if (!cost.isPositive()) {
            set(inv, SLOT_PAID_SPIN, GuiComponents.disabledButton("Paid Spin",
                    "Paid spins are disabled on this server."));
        } else if (!affordable) {
            set(inv, SLOT_PAID_SPIN, GuiComponents.disabledButton("Paid Spin",
                    "You need " + plugin.economy().format(cost)));
        } else {
            set(inv, SLOT_PAID_SPIN, ItemBuilder.of(Material.GOLD_BLOCK)
                    .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Paid Spin")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Cost &f" + plugin.economy().format(cost)),
                            Text.color(palette.neutral(), "Charged when the wheel starts."),
                            "",
                            Text.color(palette.primary(), "▶ Click to spin")
                    )
                    .hideAllFlags()
                    .build());
            setAction(SLOT_PAID_SPIN, "spin-paid");
        }
    }

    private void drawCostPanel(Inventory inv) {
        Money cost = session.game().paidSpinCost();
        set(inv, SLOT_COST, GuiComponents.infoPanel("Paid Spin", List.of(
                "Cost: " + plugin.economy().format(cost),
                "",
                "Same wheel, same odds —",
                "paid spins ignore the cooldown.",
                "",
                plugin.tax().infoLine(session.type())
        )));
    }

    /**
     * The post-result spin buttons. They read and behave like the live ones, but each restarts the
     * wheel on a fresh session first, because the one that just paid out is finished. The free spin
     * still honours the cooldown it just consumed.
     */
    private void drawSpinAgainButtons(Inventory inv, ColorPalette palette) {
        if (session.freeSpinReady()) {
            set(inv, SLOT_FREE_SPIN, ItemBuilder.of(Material.EMERALD_BLOCK)
                    .name(Text.color(palette.success(), "&l" + Text.smallCaps("Free Daily Spin")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Costs nothing."),
                            Text.color(palette.neutral(), "Resets every &f"
                                    + CooldownFormat.format(session.game().cooldownMillis())),
                            "",
                            Text.color(palette.primary(), "▶ Click to spin again")
                    )
                    .hideAllFlags()
                    .glow()
                    .build());
            setAction(SLOT_FREE_SPIN, "again-free");
        } else {
            set(inv, SLOT_FREE_SPIN, GuiComponents.disabledButton("Free Daily Spin",
                    "Available in " + CooldownFormat.format(session.cooldownRemaining())));
        }

        Money cost = session.game().paidSpinCost();
        if (!cost.isPositive()) {
            set(inv, SLOT_PAID_SPIN, GuiComponents.disabledButton("Paid Spin",
                    "Paid spins are disabled on this server."));
        } else if (!plugin.economy().has(player, cost)) {
            set(inv, SLOT_PAID_SPIN, GuiComponents.disabledButton("Paid Spin",
                    "You need " + plugin.economy().format(cost)));
        } else {
            set(inv, SLOT_PAID_SPIN, ItemBuilder.of(Material.GOLD_BLOCK)
                    .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Paid Spin")))
                    .lore(
                            "",
                            Text.color(palette.neutral(), "Cost &f" + plugin.economy().format(cost)),
                            Text.color(palette.neutral(), "Charged when the wheel starts."),
                            "",
                            Text.color(palette.primary(), "▶ Click to spin again")
                    )
                    .hideAllFlags()
                    .build());
            setAction(SLOT_PAID_SPIN, "again-paid");
        }
    }

    private void drawNavigation(Inventory inv, ColorPalette palette) {
        if (session.spinning()) {
            set(inv, SLOT_BACK, GuiComponents.disabledButton("Back", "Wait for the wheel to stop."));
            set(inv, SLOT_CLOSE, GuiComponents.disabledButton("Close", "Wait for the wheel to stop."));
            set(inv, SLOT_STATS, GuiComponents.disabledButton("Statistics", "Wait for the wheel to stop."));
            set(inv, SLOT_HISTORY, GuiComponents.disabledButton("History", "Wait for the wheel to stop."));
            return;
        }

        set(inv, SLOT_BACK, GuiComponents.casinoButton());
        setAction(SLOT_BACK, "back");
        set(inv, SLOT_STATS, GuiComponents.statsButton());
        setAction(SLOT_STATS, "stats");
        set(inv, SLOT_HISTORY, GuiComponents.historyButton());
        setAction(SLOT_HISTORY, "history");
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
            case "spin-free" -> spinFree();
            case "spin-paid" -> spinPaid();
            case "back" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Left the wheel");
                plugin.guis().open(new MainMenuGui(plugin, player));
            }
            case "again-free" -> spinAgain(false);
            case "again-paid" -> spinAgain(true);
            case "stats" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Left the wheel");
                plugin.guis().open(new StatsGui(plugin, player));
            }
            case "history" -> {
                plugin.sessions().forceEnd(player.getUniqueId(), "Left the wheel");
                plugin.guis().open(new HistoryGui(plugin, player));
            }
            case "close" -> player.closeInventory();
            default -> { }
        }
    }

    /**
     * Presses one of the spin buttons on the player's behalf. Used by
     * {@link WheelSession#triggerSpin(boolean)} so the "spin again" path lands on the same
     * cooldown and escrow checks a real click would.
     */
    void requestSpin(boolean paid) {
        if (paid) {
            spinPaid();
        } else {
            spinFree();
        }
    }

    /**
     * Retires the settled session and opens a fresh wheel that spins straight away, so continuing
     * costs one click rather than a trip back through the lobby.
     */
    private void spinAgain(boolean paid) {
        plugin.sessions().forceEnd(player.getUniqueId(), "Spinning again");
        session.game().openLobby(player, paid);
    }

    private void spinFree() {
        if (!session.freeSpinReady()) {
            plugin.messages().send(player, "wheel-cooldown",
                    "time", CooldownFormat.format(session.cooldownRemaining()));
            refresh();
            return;
        }
        session.spin(false);
    }

    /**
     * Escrows the spin cost, then spins. The withdrawal happens before the section is drawn, and a
     * failed withdrawal leaves the session untouched.
     */
    private void spinPaid() {
        Money cost = session.game().paidSpinCost();
        if (!cost.isPositive()) {
            return;
        }
        if (!plugin.economy().has(player, cost)) {
            plugin.messages().send(player, "insufficient-funds");
            refresh();
            return;
        }
        if (!plugin.antiExploit().tryBeginTransaction(player.getUniqueId())) {
            return;
        }
        try {
            if (!plugin.economy().withdraw(player, cost)) {
                plugin.messages().send(player, "insufficient-funds");
                refresh();
                return;
            }
            session.addEscrow(cost);
        } finally {
            plugin.antiExploit().endTransaction(player.getUniqueId());
        }
        plugin.messages().send(player, "bet-placed", "amount", plugin.economy().format(cost));
        session.spin(true);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (!session.isFinished()) {
            plugin.sessions().forceEnd(player.getUniqueId(), "Closed the wheel");
        }
    }
}
