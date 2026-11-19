package com.dravorax.casinodx.game.mines;

import com.dravorax.casinodx.animation.AbstractAnimation;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Short "flip" on a safe tile.
 *
 * <p>Purely cosmetic: {@link MinesSession#reveal(int)} has already recorded the reveal and bumped
 * the multiplier before this animation is registered. Its only job is to hold input locked for a
 * few ticks and then hand control back to the session.
 */
final class MinesTileAnimation extends AbstractAnimation {

    private static final int FLIP_TICKS = 6;
    /** Solid blocks, like every other board tile — a pane here would read as the frame. */
    private static final Material[] FLIP_FRAMES = {
            Material.WHITE_CONCRETE,
            Material.LIME_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.LIME_CONCRETE
    };

    private final MinesSession session;
    private final MinesGui gui;
    private final int index;
    private final float basePitch;

    MinesTileAnimation(Player player, MinesSession session, MinesGui gui, int index) {
        super(player.getUniqueId(), FLIP_TICKS);
        this.session = session;
        this.gui = gui;
        this.index = index;
        // Pitch climbs with the multiplier so the board audibly gets more tense.
        this.basePitch = (float) Math.min(2.0, 0.9 + session.safeRevealed() * 0.08);
    }

    @Override
    protected void onStart() {
        Player player = org.bukkit.Bukkit.getPlayer(playerId());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, basePitch);
        }
    }

    @Override
    public void tick(int elapsedTicks) {
        if (gui == null) {
            return;
        }
        Inventory inv = gui.inventory();
        int slot = gui.slotForIndex(index);
        if (inv == null || slot < 0) {
            return;
        }
        Material frame = FLIP_FRAMES[(elapsedTicks - 1) % FLIP_FRAMES.length];
        ColorPalette palette = ColorPalette.get();
        inv.setItem(slot, ItemBuilder.of(frame)
                .name(Text.color(palette.success(), "&l" + Text.smallCaps("Revealing")))
                .hideAllFlags()
                .build());
    }

    @Override
    protected void onFinish() {
        session.onSafeRevealFinished();
    }

    @Override
    protected void onCancel() {
        // Money is handled by GameSession#forceEnd; nothing to undo here.
    }
}
