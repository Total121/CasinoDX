package com.dravorax.casinodx.gui;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared CasinoDX button language. Every control has a distinct visual role so a new player can
 * tell primary actions from bet controls from navigation without reading lore.
 */
public final class GuiComponents {

    public enum Status {
        NORMAL,
        SELECTED,
        DISABLED,
        RUNNING,
        WIN,
        LOSS
    }

    private GuiComponents() {}

    private static ColorPalette palette() {
        return ColorPalette.get();
    }

    private static HeadTextures heads() {
        return CasinoDX.get().heads();
    }

    private static boolean showMin() {
        return CasinoDX.get().configs().gui().getBoolean("display.show-min-bet", true);
    }

    private static boolean showMax() {
        return CasinoDX.get().configs().gui().getBoolean("display.show-max-bet", true);
    }

    // --- Navigation -----------------------------------------------------------------

    public static ItemStack backButton(Runnable ignored) {
        ColorPalette c = palette();
        return heads().createHead("arrow-left",
                Text.color(c.neutral(), "&l« " + Text.smallCaps("Back")),
                Text.color(c.neutral(), "Go back one screen."));
    }

    public static ItemStack casinoButton() {
        ColorPalette c = palette();
        return heads().createHead("arrow-left",
                Text.color(c.neutral(), "&l« " + Text.smallCaps("Casino")),
                Text.color(c.neutral(), "Return to the game list."));
    }

    public static ItemStack closeButton() {
        ColorPalette c = palette();
        return heads().createHead("close",
                Text.color(c.danger(), "&l✕ " + Text.smallCaps("Close")),
                Text.color(c.neutral(), "Close this menu."));
    }

    // --- Money / bet ----------------------------------------------------------------

    public static ItemStack balancePanel(Money balance, String formatted) {
        ColorPalette c = palette();
        return heads().createHead("coin",
                Text.color(c.accent(), "&l" + Text.smallCaps("Your Balance")),
                "",
                Text.color(c.accent(), "&l" + formatted),
                Text.color(c.neutral(), "Money available to wager."));
    }

    /** Large, obvious current-bet display (gold block). */
    public static ItemStack currentBetPanel(Money bet, String formatted, Money min, Money max) {
        ColorPalette c = palette();
        CasinoDX plugin = CasinoDX.get();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(Text.color(c.accent(), "&l" + formatted));
        lore.add("");
        if (showMin()) {
            lore.add(Text.color(c.neutral(), "Minimum: &f" + plugin.economy().format(min)));
        }
        if (showMax()) {
            lore.add(Text.color(c.neutral(), "Maximum: &f" + plugin.economy().format(max)));
        }
        lore.add("");
        lore.add(Text.color(c.neutral(), "Use the gold + / − buttons to change."));
        return ItemBuilder.of(Material.GOLD_BLOCK)
                .name(Text.color(c.accent(), "&l" + Text.smallCaps("Current Bet")))
                .lore(lore)
                .hideAllFlags()
                .glow()
                .build();
    }

    /** @deprecated use {@link #currentBetPanel} */
    @Deprecated
    public static ItemStack betDisplay(Money bet, String formatted, Money min, Money max) {
        return currentBetPanel(bet, formatted, min, max);
    }

    /** Gold-family increase control (not the same head as navigation arrows). */
    public static ItemStack betPlus(String stepLabel) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.GOLD_INGOT)
                .name(Text.color(c.success(), "&l+ " + Text.smallCaps("Increase Bet")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Raise your bet by &f" + stepLabel + "&7."),
                        Text.color(c.primary(), "Click to increase.")
                )
                .hideAllFlags()
                .build();
    }

    public static ItemStack betMinus(String stepLabel) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.GOLD_NUGGET)
                .name(Text.color(c.danger(), "&l− " + Text.smallCaps("Decrease Bet")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Lower your bet by &f" + stepLabel + "&7."),
                        Text.color(c.primary(), "Click to decrease.")
                )
                .hideAllFlags()
                .build();
    }

    public static ItemStack incrementButton() {
        CasinoDX plugin = CasinoDX.get();
        double step = plugin.configs().gui().getDouble("bet-select.step", 10);
        return betPlus(plugin.economy().format(Money.of(step)));
    }

    public static ItemStack decrementButton() {
        CasinoDX plugin = CasinoDX.get();
        double step = plugin.configs().gui().getDouble("bet-select.step", 10);
        return betMinus(plugin.economy().format(Money.of(step)));
    }

    public static ItemStack customBetButton(String currentLabel) {
        ColorPalette c = palette();
        return heads().createHead("custom-bet",
                Text.color(c.accent(), "&l" + Text.smallCaps("Type Amount")),
                "",
                Text.color(c.neutral(), "Current: ") + Text.color(c.accent(), currentLabel),
                Text.color(c.neutral(), "Type any amount in chat"),
                Text.color(c.neutral(), "(or type &fcancel&7 to abort)."));
    }

    public static ItemStack presetBetButton(String formatted, boolean selected) {
        ColorPalette c = palette();
        Material mat = selected ? Material.GOLD_BLOCK : Material.RAW_GOLD;
        return ItemBuilder.of(mat)
                .name(Text.color(c.accent(), (selected ? "&l✓ " : "&l") + formatted))
                .lore(
                        "",
                        Text.color(c.neutral(), selected
                                ? "This is your current bet."
                                : "Click to set your bet to " + formatted + ".")
                )
                .hideAllFlags()
                .glow(selected)
                .build();
    }

    // --- Primary actions ------------------------------------------------------------

    /** Big lime "do the thing" button. */
    public static ItemStack primaryAction(String title, String... loreLines) {
        ColorPalette c = palette();
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (loreLines != null) {
            for (String line : loreLines) {
                if (line != null) {
                    lore.add(Text.color(c.neutral(), line));
                }
            }
        }
        if (lore.size() == 1) {
            lore.add(Text.color(c.primary(), "Click to continue."));
        }
        return ItemBuilder.of(Material.LIME_CONCRETE)
                .name(Text.color(c.success(), "&l▶ " + Text.smallCaps(title)))
                .lore(lore)
                .hideAllFlags()
                .glow()
                .build();
    }

    public static ItemStack confirmButton() {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.EMERALD_BLOCK)
                .name(Text.color(c.success(), "&l✓ " + Text.smallCaps("Confirm Bet")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Lock in this bet and start."),
                        Text.color(c.primary(), "Click to play.")
                )
                .hideAllFlags()
                .glow()
                .build();
    }

    public static ItemStack cashOutButton(String payoutLabel) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.ORANGE_CONCRETE)
                .name(Text.color(c.warning(), "&l$ " + Text.smallCaps("Cash Out")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Take your winnings now."),
                        Text.color(c.accent(), "Payout: &l" + payoutLabel),
                        Text.color(c.primary(), "Click before it crashes!")
                )
                .hideAllFlags()
                .glow()
                .build();
    }

    /** Blackjack-only one-click next round. */
    public static ItemStack playAgainButton(String stakeLabel) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.LIME_CONCRETE)
                .name(Text.color(c.success(), "&l▶ " + Text.smallCaps("Play Again")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Deal another hand."),
                        Text.color(c.neutral(), "Stake: ") + Text.color(c.accent(), stakeLabel),
                        Text.color(c.primary(), "Click to deal.")
                )
                .hideAllFlags()
                .glow()
                .build();
    }

    public static ItemStack newRoundButton(String stakeLabel) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.LIME_CONCRETE)
                .name(Text.color(c.success(), "&l▶ " + Text.smallCaps("New Round")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Start again with stake "),
                        Text.color(c.accent(), stakeLabel),
                        Text.color(c.primary(), "Click to continue.")
                )
                .hideAllFlags()
                .glow()
                .build();
    }

    // --- Info / status (non-clickable look) -----------------------------------------

    public static ItemStack infoPanel(String title, List<String> lines) {
        ColorPalette c = palette();
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (lines != null) {
            for (String line : lines) {
                lore.add(Text.color(c.neutral(), line));
            }
        }
        lore.add("");
        lore.add(Text.color(c.neutral(), "&oNot a button — info only."));
        return ItemBuilder.of(Material.BOOK)
                .name(Text.color(c.primary(), "&l" + Text.smallCaps(title)))
                .lore(lore)
                .hideAllFlags()
                .build();
    }

    public static ItemStack sectionLabel(String title, String subtitle) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.NAME_TAG)
                .name(Text.color(c.primary(), "&l" + Text.smallCaps(title)))
                .lore("", Text.color(c.neutral(), subtitle == null ? "" : subtitle))
                .hideAllFlags()
                .build();
    }

    public static ItemStack helpButton(String subject) {
        ColorPalette c = palette();
        return heads().createHead("help",
                Text.color(c.primary(), "&l? " + Text.smallCaps("How To Play")),
                "",
                Text.color(c.neutral(), subject == null || subject.isBlank()
                        ? "Short rules for this game."
                        : "Short rules for " + subject + "."));
    }

    public static ItemStack disabledButton(String label, String reason) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.GRAY_DYE)
                .name(Text.color(c.neutral(), "&l" + Text.smallCaps(label)))
                .lore(
                        "",
                        Text.color(c.danger(), "Unavailable"),
                        Text.color(c.neutral(), reason == null ? "" : reason)
                )
                .hideAllFlags()
                .build();
    }

    public static ItemStack statusIndicator(Status status, String label) {
        ColorPalette c = palette();
        Material material;
        String color;
        String statusText;
        switch (status) {
            case SELECTED -> {
                material = Material.LIME_CONCRETE;
                color = c.success();
                statusText = "Selected";
            }
            case DISABLED -> {
                material = Material.GRAY_CONCRETE;
                color = c.neutral();
                statusText = "Disabled";
            }
            case RUNNING -> {
                material = Material.YELLOW_CONCRETE;
                color = c.warning();
                statusText = "In progress";
            }
            case WIN -> {
                material = Material.EMERALD_BLOCK;
                color = c.success();
                statusText = "You won";
            }
            case LOSS -> {
                material = Material.REDSTONE_BLOCK;
                color = c.danger();
                statusText = "You lost";
            }
            default -> {
                material = Material.LIGHT_BLUE_CONCRETE;
                color = c.primary();
                statusText = "Ready";
            }
        }
        return ItemBuilder.of(material)
                .name(Text.color(color, "&l" + Text.smallCaps(label)))
                .lore("", Text.color(c.neutral(), statusText))
                .hideAllFlags()
                .build();
    }

    public static ItemStack resultBanner(boolean won, String resultLabel, String stakeLabel, String payoutLabel) {
        return resultPanel(won, resultLabel, stakeLabel, payoutLabel);
    }

    public static ItemStack resultPanel(boolean won, String resultLabel, String stakeLabel, String payoutLabel) {
        ColorPalette c = palette();
        String color = won ? c.success() : c.danger();
        return ItemBuilder.of(won ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK)
                .name(Text.color(color, "&l" + Text.smallCaps(won ? "You Won!" : "You Lost")))
                .lore(
                        "",
                        Text.color(c.neutral(), resultLabel == null || resultLabel.isBlank() ? "-" : resultLabel),
                        Text.color(c.neutral(), "Stake: &f" + stakeLabel),
                        Text.color(c.neutral(), won ? "Paid: " : "Lost: ") + Text.color(color, payoutLabel),
                        "",
                        Text.color(c.neutral(), "&oResult — not a button.")
                )
                .hideAllFlags()
                .glow()
                .build();
    }

    public static ItemStack statsButton() {
        ColorPalette c = palette();
        return heads().createHead("stats",
                Text.color(c.primary(), "&l" + Text.smallCaps("Statistics")),
                Text.color(c.neutral(), "View your casino stats."));
    }

    public static ItemStack historyButton() {
        ColorPalette c = palette();
        return heads().createHead("history",
                Text.color(c.primary(), "&l" + Text.smallCaps("History")),
                Text.color(c.neutral(), "Browse recent results."));
    }

    public static ItemStack pageButton(boolean next) {
        ColorPalette c = palette();
        String key = next ? "arrow-right" : "arrow-left";
        String label = next ? Text.smallCaps("Next") : Text.smallCaps("Previous");
        return heads().createHead(key,
                Text.color(c.accent(), "&l" + (next ? "» " : "« ") + label),
                Text.color(c.neutral(), next ? "Next page." : "Previous page."));
    }

    public static ItemStack changeSelectionButton(String currentSelection) {
        ColorPalette c = palette();
        return ItemBuilder.of(Material.COMPARATOR)
                .name(Text.color(c.warning(), "&l" + Text.smallCaps("Change Pick")))
                .lore(
                        "",
                        Text.color(c.neutral(), "Last: ") + Text.color(c.accent(),
                                currentSelection == null || currentSelection.isBlank() ? "none" : currentSelection)
                )
                .hideAllFlags()
                .build();
    }

    public static ItemStack decorativePane(Material material) {
        return ItemBuilder.of(material).name(" ").hideAllFlags().build();
    }

    public static ItemStack blankPane() {
        return decorativePane(Material.BLACK_STAINED_GLASS_PANE);
    }
}
