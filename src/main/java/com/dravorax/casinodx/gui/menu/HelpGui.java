package com.dravorax.casinodx.gui.menu;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Rules and odds for a single game, or an index of every game the player can reach.
 *
 * <p>Rule text is server-editable: each game module may declare a {@code help:} list in its
 * {@code config.yml}. When it does not, the built-in text below is used, so the screen is never
 * blank and never has to be configured to be useful.
 *
 * <p>The caller supplies where "back" goes, because this screen is opened from the main menu, the
 * bet screen and the replay screen alike and should return to whichever of those it came from.
 */
public final class HelpGui extends GuiView {

    private static final int SIZE = 54;

    private static final int SLOT_SUBJECT = 4;
    private static final int SLOT_RULES = 22;
    private static final int SLOT_LIMITS = 20;
    private static final int SLOT_ODDS = 24;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 53;

    private static final int[] INDEX_SLOTS = {11, 13, 15, 29, 31, 33, 40};

    /** Every non-border slot, filled first so the layout never leaves an air hole. */
    private static final int[] INTERIOR_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** {@code null} renders the index of every game rather than one game's rules. */
    private final GameType gameType;
    private final Runnable back;

    public HelpGui(CasinoDX plugin, Player player, GameType gameType, Runnable back) {
        super(plugin, player, "help-" + (gameType == null ? "all" : gameType.name()));
        this.gameType = gameType;
        this.back = back;
    }

    public HelpGui(CasinoDX plugin, Player player, Runnable back) {
        this(plugin, player, null, back);
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        String subject = gameType == null ? "Casino" : gameType.displayName();
        return Text.color(c.primary(), "&l" + Text.smallCaps("How To Play") + " &8| "
                + Text.smallCaps(subject));
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

        if (gameType == null) {
            buildIndex(inv, palette);
        } else {
            buildGameHelp(inv, palette);
        }

        set(inv, SLOT_BACK, GuiComponents.backButton(null));
        setAction(SLOT_BACK, "back");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
    }

    private void buildIndex(Inventory inv, ColorPalette palette) {
        set(inv, SLOT_SUBJECT, GuiComponents.infoPanel("Casino Guide", List.of(
                "Pick a game to read its rules,",
                "limits, odds and payouts.",
                "",
                "Every outcome is drawn before",
                "its animation starts, so what you",
                "watch is a replay of a decision",
                "the server already committed to."
        )));

        int index = 0;
        for (GameType type : plugin.games().enabledTypes()) {
            if (index >= INDEX_SLOTS.length) {
                break;
            }
            if (!player.hasPermission(type.permission())) {
                continue;
            }
            int slot = INDEX_SLOTS[index++];
            set(inv, slot, plugin.heads().createHead(type.headKey(),
                    Text.color(palette.accent(), "&l" + Text.smallCaps(type.displayName())),
                    "",
                    Text.color(palette.neutral(), type.description()),
                    "",
                    Text.color(palette.primary(), "▶ Click to read the rules")));
            setAction(slot, "game:" + type.name());
        }
    }

    private void buildGameHelp(Inventory inv, ColorPalette palette) {
        CasinoGame game = plugin.games().get(gameType);

        set(inv, SLOT_SUBJECT, plugin.heads().createHead(gameType.headKey(),
                Text.color(palette.accent(), "&l" + Text.smallCaps(gameType.displayName())),
                "",
                Text.color(palette.neutral(), gameType.description())));

        set(inv, SLOT_RULES, ItemBuilder.of(Material.WRITTEN_BOOK)
                .name(Text.color(palette.primary(), "&l" + Text.smallCaps("Rules")))
                .lore(loreOf(palette, rulesFor(gameType)))
                .hideAllFlags()
                .build());

        List<String> limits = new ArrayList<>();
        if (game == null) {
            limits.add("This game is not installed.");
        } else {
            limits.add("Minimum bet: " + plugin.economy().format(game.minBet()));
            limits.add("Maximum bet: " + plugin.economy().format(game.maxBet()));
            limits.add(String.format("House edge: %.2f%%", game.houseEdge() * 100.0));
            limits.add("");
            limits.add(plugin.tax().infoLine(gameType));
        }
        set(inv, SLOT_LIMITS, GuiComponents.infoPanel("Limits", limits));

        set(inv, SLOT_ODDS, GuiComponents.infoPanel("Fair Play", List.of(
                "Outcomes come from a cryptographic",
                "random source and are drawn before",
                "any animation begins.",
                "",
                "Nothing you do during an animation",
                "can change what was already decided.",
                "",
                "Payout tables live in",
                "modules/" + gameType.moduleName() + "/."
        )));
    }

    private List<String> loreOf(ColorPalette palette, List<String> lines) {
        List<String> lore = new ArrayList<>(lines.size() + 1);
        lore.add("");
        for (String line : lines) {
            lore.add(Text.color(palette.neutral(), line));
        }
        return lore;
    }

    /** Server-authored rules from the game module, falling back to the built-in text. */
    private List<String> rulesFor(GameType type) {
        List<String> configured = plugin.configs().module(type.moduleName()).getStringList("help");
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        return defaultRules(type);
    }

    private static List<String> defaultRules(GameType type) {
        return switch (type) {
            case MINES -> List.of(
                    "Pick a stake, then reveal tiles.",
                    "Every safe tile raises your multiplier.",
                    "Hit a mine and the round is over.",
                    "Cash out any time to bank the current",
                    "multiplier — there is no obligation",
                    "to clear the board."
            );
            case ROULETTE -> List.of(
                    "European wheel: 37 pockets, 0 to 36.",
                    "Pick a chip size, then click as many",
                    "cells as you like — numbers, red/black,",
                    "odd/even, high/low, dozens, columns.",
                    "Clicking a cell again takes the chip back.",
                    "",
                    "Your total is chip size x chips placed,",
                    "and it is only taken when you spin.",
                    "One spin settles every chip you placed.",
                    "",
                    "Outside bets pay 2x, dozens and columns",
                    "3x, and a straight-up number 36x.",
                    "The single green zero is the house edge."
            );
            case BLACKJACK -> List.of(
                    "Beat the dealer without going over 21.",
                    "Hit to draw, stand to hold.",
                    "Double down doubles your wager for",
                    "exactly one more card.",
                    "Split a matching pair into two hands.",
                    "",
                    "A natural blackjack pays extra;",
                    "equal totals push and return the stake."
            );
            case COINFLIP -> List.of(
                    "Call heads or tails, then toss.",
                    "The coin is a true 50/50 draw —",
                    "it does not know what you called.",
                    "",
                    "The house edge lives entirely in the",
                    "payout multiplier, never in the coin."
            );
            case CRASH -> List.of(
                    "The multiplier climbs from 1.00x.",
                    "Cash out before the rocket crashes",
                    "and you keep stake x multiplier.",
                    "",
                    "The crash point is drawn at launch.",
                    "Leaving mid-flight counts as a crash,",
                    "so ride it out or cash out."
            );
            case WHEEL -> List.of(
                    "One free spin per cooldown period,",
                    "or pay to spin whenever you like.",
                    "",
                    "Paid and free spins use the same wheel",
                    "and the same odds. Every section's real",
                    "chance is listed on the prize table."
            );
            case SLOTS -> List.of(
                    "Spin the reels and match symbols",
                    "along a payline to win.",
                    "",
                    "Longer matches pay more. Every reel",
                    "stop is drawn before the reels move."
            );
        };
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

        if (act.startsWith("game:")) {
            try {
                GameType type = GameType.valueOf(act.substring("game:".length()));
                plugin.guis().open(new HelpGui(plugin, player, type,
                        () -> plugin.guis().open(new HelpGui(plugin, player, back))));
            } catch (IllegalArgumentException ignored) {
                // Stale action binding.
            }
            return;
        }

        switch (act) {
            case "back" -> {
                if (back != null) {
                    back.run();
                } else {
                    plugin.guis().open(new MainMenuGui(plugin, player));
                }
            }
            case "close" -> player.closeInventory();
            default -> { }
        }
    }
}
