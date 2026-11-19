package com.dravorax.casinodx.gui.menu;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.animation.BorderRevealAnimation;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.gui.HeadTextures;
import com.dravorax.casinodx.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The casino lobby.
 *
 * <p>Seven game icons sit in two centred rows with a seventh below them, which keeps the block
 * balanced whether the server runs three games or all seven. Everything that is not an icon is
 * either the border or a nameless dark pane, so there is never an inert item that looks clickable
 * and never a bare air hole in the middle of the menu.
 */
public final class MainMenuGui extends GuiView {

    private static final int SIZE = 54;

    private static final int SLOT_WELCOME = 4;
    private static final int SLOT_BALANCE = 48;
    private static final int SLOT_STATS = 49;
    private static final int SLOT_HISTORY = 50;
    private static final int SLOT_HELP = 51;
    private static final int SLOT_CLOSE = 53;

    private static final int[] GAME_SLOTS = {11, 13, 15, 29, 31, 33, 40};

    private static final int[] INTERIOR_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    public MainMenuGui(CasinoDX plugin, Player player) {
        super(plugin, player, "main-menu");
    }

    @Override
    public String title() {
        String configured = plugin.configs().gui().getString("main-menu.title", "CasinoDX");
        ColorPalette c = ColorPalette.get();
        return Text.color(c.primary(), "&l✦ " + Text.smallCaps(configured) + " ✦");
    }

    @Override
    public int size() {
        // The layout below is built around a 54-slot grid, so the size is not configurable.
        return SIZE;
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());
        for (int slot : INTERIOR_SLOTS) {
            set(inv, slot, GuiComponents.blankPane());
        }
        if (!animateBorder()) {
            accentCorners(inv, palette);
        }

        int slotIndex = 0;
        for (GameType type : plugin.games().enabledTypes()) {
            if (slotIndex >= GAME_SLOTS.length) {
                break;
            }
            if (!player.hasPermission(type.permission())) {
                continue;
            }
            int slot = GAME_SLOTS[slotIndex++];
            set(inv, slot, gameIcon(type));
            setAction(slot, "game:" + type.name());
        }

        set(inv, SLOT_WELCOME, GuiComponents.infoPanel("Welcome", List.of(
                "Pick a game below to play.",
                "Your balance is shown at the bottom.",
                "",
                "Tax: " + plugin.tax().infoLine(),
                "",
                "New here? Click How To Play."
        )));

        var balance = plugin.economy().getBalance(player);
        set(inv, SLOT_BALANCE, GuiComponents.balancePanel(balance, plugin.economy().format(balance)));
        set(inv, SLOT_STATS, GuiComponents.statsButton());
        setAction(SLOT_STATS, "stats");
        set(inv, SLOT_HISTORY, GuiComponents.historyButton());
        setAction(SLOT_HISTORY, "history");
        set(inv, SLOT_HELP, GuiComponents.helpButton(null));
        setAction(SLOT_HELP, "help");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
    }

    private boolean animateBorder() {
        return plugin.configs().gui().getBoolean("main-menu.animation-border-reveal", true);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!plugin.antiExploit().tryClick(player.getUniqueId())) {
            return;
        }
        String action = action(event.getRawSlot());
        if (action == null) {
            return;
        }
        playClickSound();

        switch (action) {
            case "close" -> player.closeInventory();
            case "stats" -> plugin.guis().open(new StatsGui(plugin, player));
            case "history" -> plugin.guis().open(new HistoryGui(plugin, player));
            case "help" -> plugin.guis().open(new HelpGui(plugin, player,
                    () -> plugin.guis().open(new MainMenuGui(plugin, player))));
            default -> {
                if (action.startsWith("game:")) {
                    try {
                        plugin.games().openLobby(player, GameType.valueOf(action.substring("game:".length())));
                    } catch (IllegalArgumentException ignored) {
                        // Stale action binding.
                    }
                }
            }
        }
    }

    @Override
    public void open() {
        super.open();
        if (animateBorder()) {
            plugin.animations().register(new BorderRevealAnimation(player, this, size(), SLOT_WELCOME));
        }
    }

    private ItemStack gameIcon(GameType type) {
        ColorPalette c = ColorPalette.get();
        HeadTextures heads = plugin.heads();
        return heads.createHead(type.headKey(),
                Text.color(c.accent(), "&l" + Text.smallCaps(type.displayName())),
                "",
                Text.color(c.neutral(), type.description()),
                "",
                Text.color(c.primary(), "▶ Click to play"));
    }

    private void accentCorners(Inventory inv, ColorPalette palette) {
        ItemStack accent = GuiComponents.decorativePane(palette.borderAccent());
        ItemStack secondary = GuiComponents.decorativePane(palette.borderSecondary());
        // Runs before the icons and the bottom row are placed, so those always win the slot.
        for (int slot : new int[]{0, 8, 45, 53}) {
            set(inv, slot, accent);
        }
        for (int slot : new int[]{1, 7, 9, 17, 36, 44, 46, 52}) {
            set(inv, slot, secondary);
        }
    }
}
