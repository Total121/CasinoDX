package com.dravorax.casinodx.gui.menu;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.stats.PlayerStats;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public final class StatsGui extends GuiView {

    private static final int SLOT_BACK = 45;
    private static final int SLOT_CLOSE = 53;
    private static final int[] STAT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public StatsGui(CasinoDX plugin, Player player) {
        super(plugin, player, "stats");
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        String configured = plugin.configs().gui().getString("stats.title", "Your Statistics");
        return Text.color(c.primary(), "&l" + Text.smallCaps(configured));
    }

    @Override
    public int size() {
        return plugin.configs().gui().getInt("stats.size", 54);
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());

        PlayerStats stats = plugin.statistics().getStats(player.getUniqueId());

        set(inv, 4, GuiComponents.infoPanel("Overview", List.of(
                "Games played: " + stats.gamesPlayed(),
                "Games won: " + stats.gamesWon(),
                "Win rate: " + String.format("%.1f%%", stats.winRate()),
                "Wagered: " + plugin.economy().format(stats.totalWagered()),
                "Won: " + plugin.economy().format(stats.totalWon()),
                "Lost: " + plugin.economy().format(stats.totalLost())
        )));

        int index = 0;
        for (GameType type : GameType.values()) {
            if (index >= STAT_SLOTS.length) break;
            PlayerStats.GameStatLine line = stats.forGame(type);
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(Text.color(palette.neutral(), "Played: &f" + line.played()));
            lore.add(Text.color(palette.neutral(), "Won: &f" + line.won()));
            lore.add(Text.color(palette.neutral(), "Wagered: &f" + plugin.economy().format(line.wagered())));
            lore.add(Text.color(palette.neutral(), "Profit: &f" + plugin.economy().format(line.profit())));

            set(inv, STAT_SLOTS[index++], ItemBuilder.of(type.iconMaterial())
                    .name(Text.color(palette.accent(), "&l" + Text.smallCaps(type.displayName())))
                    .lore(lore)
                    .hideAllFlags()
                    .build());
        }

        set(inv, SLOT_BACK, GuiComponents.backButton(null));
        setAction(SLOT_BACK, "back");
        set(inv, SLOT_CLOSE, GuiComponents.closeButton());
        setAction(SLOT_CLOSE, "close");
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        playClickSound();
        String act = action(event.getRawSlot());
        if ("back".equals(act)) {
            plugin.guis().open(new MainMenuGui(plugin, player));
        } else if ("close".equals(act)) {
            player.closeInventory();
        }
    }
}
