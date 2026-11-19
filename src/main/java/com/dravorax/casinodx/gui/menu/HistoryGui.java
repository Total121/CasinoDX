package com.dravorax.casinodx.gui.menu;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.gui.GuiComponents;
import com.dravorax.casinodx.gui.GuiView;
import com.dravorax.casinodx.history.HistoryEntry;
import com.dravorax.casinodx.history.PagedHistory;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class HistoryGui extends GuiView {

    private static final int SLOT_BACK = 45;
    private static final int SLOT_PREV = 48;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 50;
    private static final int SLOT_CLOSE = 53;
    private static final int[] ENTRY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private int page;

    public HistoryGui(CasinoDX plugin, Player player) {
        this(plugin, player, 0);
    }

    public HistoryGui(CasinoDX plugin, Player player, int page) {
        super(plugin, player, "history");
        this.page = Math.max(0, page);
    }

    @Override
    public String title() {
        ColorPalette c = ColorPalette.get();
        String configured = plugin.configs().gui().getString("history.title", "Game History");
        return Text.color(c.primary(), "&l" + Text.smallCaps(configured));
    }

    @Override
    public int size() {
        return plugin.configs().gui().getInt("history.size", 54);
    }

    @Override
    public void build(Inventory inv) {
        ColorPalette palette = ColorPalette.get();
        fillBorder(inv, palette.borderPrimary());

        PagedHistory history = plugin.history().getHistory(player.getUniqueId(), page);
        List<HistoryEntry> entries = history.entries();

        for (int i = 0; i < ENTRY_SLOTS.length; i++) {
            if (i >= entries.size()) break;
            HistoryEntry entry = entries.get(i);
            Material iconMat = entry.win() ? Material.LIME_DYE : Material.RED_DYE;

            set(inv, ENTRY_SLOTS[i], ItemBuilder.of(iconMat)
                    .name(Text.color(entry.win() ? palette.success() : palette.danger(),
                            "&l" + Text.smallCaps(entry.game().displayName())))
                    .lore(
                            "",
                            Text.color(palette.neutral(), TIME_FMT.format(entry.timestamp())),
                            Text.color(palette.neutral(), "Bet: &f" + plugin.economy().format(entry.bet())),
                            Text.color(palette.neutral(), "Payout: &f" + plugin.economy().format(entry.payout())),
                            Text.color(palette.neutral(), "Net: &f" + plugin.economy().format(entry.net())),
                            entry.detail().isBlank() ? "" : Text.color(palette.neutral(), entry.detail())
                    )
                    .hideAllFlags()
                    .build());
        }

        if (entries.isEmpty()) {
            set(inv, 22, GuiComponents.statusIndicator(GuiComponents.Status.DISABLED, "No History"));
        }

        set(inv, SLOT_PAGE, ItemBuilder.of(Material.PAPER)
                .name(Text.color(palette.accent(), "&l" + Text.smallCaps("Page") + " " + (history.page() + 1)))
                .lore(
                        Text.color(palette.neutral(), "Total entries: " + history.totalEntries()),
                        Text.color(palette.neutral(), "Pages: " + history.totalPages())
                )
                .hideAllFlags()
                .build());

        if (history.hasPrevious()) {
            set(inv, SLOT_PREV, GuiComponents.pageButton(false));
            setAction(SLOT_PREV, "prev");
        }
        if (history.hasNext()) {
            set(inv, SLOT_NEXT, GuiComponents.pageButton(true));
            setAction(SLOT_NEXT, "next");
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
        if (act == null) return;

        switch (act) {
            case "back" -> plugin.guis().open(new MainMenuGui(plugin, player));
            case "close" -> player.closeInventory();
            case "prev" -> {
                if (page > 0) {
                    page--;
                    refresh();
                }
            }
            case "next" -> {
                page++;
                refresh();
            }
        }
    }
}
