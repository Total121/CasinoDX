package com.dravorax.casinodx.gui.menu;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Spacious "how much do you want to bet?" screen.
 *
 * <p>Hierarchy top→bottom: balance → quick amounts → current bet ± → confirm → nav.
 * A new player should only need to click a gold amount (or Confirm) to play.
 */
public final class BetSelectGui extends GuiView {

    private static final int SIZE = 54;

    private static final int SLOT_TITLE = 4;
    private static final int SLOT_BALANCE = 8;

    private static final int PRESET_START = 19; // row 2, 7 slots: 19-25
    private static final int PRESET_SLOTS = 7;

    private static final int SLOT_MINUS = 30;
    private static final int SLOT_BET = 31;
    private static final int SLOT_PLUS = 32;
    private static final int SLOT_CUSTOM = 33;

    private static final int SLOT_CONFIRM_A = 39;
    private static final int SLOT_CONFIRM_B = 40;
    private static final int SLOT_CONFIRM_C = 41;

    private static final int SLOT_BACK = 45;
    private static final int SLOT_HELP = 49;
    private static final int SLOT_CLOSE = 53;

    private static final List<Double> FALLBACK_PRESETS = List.of(10.0, 50.0, 100.0, 500.0, 1000.0, 5000.0);

    private final GameType gameType;
    private Money currentBet;

    public BetSelectGui(CasinoDX plugin, Player player, GameType gameType) {
        this(plugin, player, gameType, null);
    }

    public BetSelectGui(CasinoDX plugin, Player player, GameType gameType, Money initialBet) {
        super(plugin, player, "bet-select-" + gameType.name());
        this.gameType = gameType;
        this.currentBet = initialBet == null ? minBet() : initialBet;
        holder.putMeta("gameType", gameType.name());
    }

    private CasinoGame game() {
        return plugin.games().get(gameType);
    }

    private Money minBet() {
        CasinoGame g = game();
        return g == null ? plugin.economy().minBet() : g.minBet();
    }

    private Money maxBet() {
        CasinoGame g = game();
        return g == null ? plugin.economy().maxBet() : g.maxBet();
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        return Text.color(c.accent(), "&l" + Text.smallCaps(gameType.displayName())
                + " &8» &l" + Text.smallCaps("Choose Bet"));
    }

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderSecondary());
        fillBlankInterior(inv);

        Money balance = plugin.economy().getBalance(player);
        Money min = minBet();
        Money max = maxBet();
        if (currentBet.lessThan(min)) {
            currentBet = min;
        }
        if (currentBet.greaterThan(max)) {
            currentBet = max;
        }

        set(inv, SLOT_TITLE, GuiComponents.sectionLabel(gameType.displayName(),
                "Pick how much to wager, then press PLAY."));
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));

        drawPresets(inv);

        String stepLabel = plugin.economy().format(Money.of(step()));
        set(inv, SLOT_MINUS, GuiComponents.betMinus(stepLabel));
        setAction(SLOT_MINUS, "minus");
        set(inv, SLOT_BET, GuiComponents.currentBetPanel(currentBet, plugin.economy().format(currentBet), min, max));
        set(inv, SLOT_PLUS, GuiComponents.betPlus(stepLabel));
        setAction(SLOT_PLUS, "plus");
        set(inv, SLOT_CUSTOM, GuiComponents.customBetButton(plugin.economy().format(currentBet)));
        setAction(SLOT_CUSTOM, "custom");

        boolean canAfford = plugin.economy().has(player, currentBet);
        if (canAfford) {
            var play = GuiComponents.primaryAction("Play",
                    "Stake: " + plugin.economy().format(currentBet),
                    "Click to start " + gameType.displayName() + ".");
            set(inv, SLOT_CONFIRM_A, play);
            set(inv, SLOT_CONFIRM_B, play);
            set(inv, SLOT_CONFIRM_C, play);
            setAction(SLOT_CONFIRM_A, "confirm");
            setAction(SLOT_CONFIRM_B, "confirm");
            setAction(SLOT_CONFIRM_C, "confirm");
        } else {
            var disabled = GuiComponents.disabledButton("Play",
                    "Need " + plugin.economy().format(currentBet) + " — you don't have enough.");
            set(inv, SLOT_CONFIRM_A, disabled);
            set(inv, SLOT_CONFIRM_B, disabled);
            set(inv, SLOT_CONFIRM_C, disabled);
        }

        set(inv, SLOT_BACK, GuiComponents.casinoButton());
        setAction(SLOT_BACK, "back");
        set(inv, SLOT_HELP, GuiComponents.helpButton(gameType.displayName()));
        setAction(SLOT_HELP, "help");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
    }

    private void drawPresets(Inventory inv) {
        List<Double> presets = presets();
        int count = Math.min(presets.size(), PRESET_SLOTS);
        int offset = (PRESET_SLOTS - count) / 2;
        for (int i = 0; i < PRESET_SLOTS; i++) {
            int slot = PRESET_START + i;
            int idx = i - offset;
            if (idx < 0 || idx >= count) {
                set(inv, slot, GuiComponents.blankPane());
                continue;
            }
            Money amount = Money.of(presets.get(idx));
            boolean selected = currentBet.equals(amount);
            set(inv, slot, GuiComponents.presetBetButton(plugin.economy().format(amount), selected));
            setAction(slot, "preset:" + presets.get(idx));
        }
    }

    private List<Double> presets() {
        List<Double> configured = plugin.configs().gui().getDoubleList("bet-select.presets");
        if (configured == null || configured.isEmpty()) {
            return FALLBACK_PRESETS;
        }
        List<Double> positive = new ArrayList<>();
        for (double value : configured) {
            if (value > 0) {
                positive.add(value);
            }
        }
        return positive.isEmpty() ? FALLBACK_PRESETS : positive;
    }

    private double step() {
        double configured = plugin.configs().gui().getDouble("bet-select.step", 10.0);
        return configured > 0 ? configured : 10.0;
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

        Money min = minBet();
        Money max = maxBet();

        switch (act) {
            case "back" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "close" -> player.closeInventory();
            case "help" -> plugin.guis().open(new HelpGui(plugin, player, gameType, this::reopen));
            case "custom" -> {
                player.closeInventory();
                plugin.betInput().startPrompt(player, gameType, currentBet,
                        amount -> plugin.guis().open(new BetSelectGui(plugin, player, gameType, amount)));
            }
            case "minus" -> {
                plugin.sounds().play(player, "bet");
                currentBet = currentBet.subtract(Money.of(step()));
                if (currentBet.lessThan(min)) {
                    currentBet = min;
                }
                refresh();
            }
            case "plus" -> {
                plugin.sounds().play(player, "bet");
                currentBet = currentBet.add(Money.of(step()));
                if (currentBet.greaterThan(max)) {
                    currentBet = max;
                }
                refresh();
            }
            case "confirm" -> confirmBet();
            default -> {
                if (act.startsWith("preset:")) {
                    plugin.sounds().play(player, "bet");
                    try {
                        currentBet = Money.of(Double.parseDouble(act.substring("preset:".length())));
                        refresh();
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
    }

    private void reopen() {
        plugin.guis().open(new BetSelectGui(plugin, player, gameType, currentBet));
    }

    private void confirmBet() {
        Money min = minBet();
        Money max = maxBet();
        if (currentBet.lessThan(min)) {
            plugin.messages().send(player, "bet-too-low", "amount", plugin.economy().format(min));
            return;
        }
        if (currentBet.greaterThan(max)) {
            plugin.messages().send(player, "bet-too-high", "amount", plugin.economy().format(max));
            return;
        }
        CasinoGame g = game();
        if (g == null || !g.isEnabled()) {
            plugin.messages().send(player, "game-disabled");
            return;
        }
        g.startSession(player, currentBet);
    }
}
