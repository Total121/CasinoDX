package com.dravorax.casinodx.game.mines;

import com.dravorax.casinodx.animation.AbstractAnimation;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Loss sequence: flash the tile the player hit, then walk through the rest of the mines.
 *
 * <p>The loss is already recorded on the session before this runs, and the settlement itself happens
 * in {@link MinesSession#onExplosionFinished()} — or in
 * {@link MinesSession#settleDecidedOutcome(String)} if the animation is cancelled first. Either way
 * the bet is consumed exactly once.
 */
final class MinesExplosionAnimation extends AbstractAnimation {

    private static final int FLASH_TICKS = 8;
    private static final int TICKS_PER_MINE = 2;

    private final MinesSession session;
    private final MinesGui gui;
    private final int triggeredIndex;
    private final List<Integer> remainingMines = new ArrayList<>();

    private int revealedMines;

    MinesExplosionAnimation(Player player, MinesSession session, MinesGui gui, int triggeredIndex) {
        super(player.getUniqueId(), FLASH_TICKS + 1 + Math.max(0, session.game().mineCount() - 1) * TICKS_PER_MINE + 2);
        this.session = session;
        this.gui = gui;
        this.triggeredIndex = triggeredIndex;
        for (int i = 0; i < session.tileCount(); i++) {
            if (session.isMine(i) && i != triggeredIndex) {
                remainingMines.add(i);
            }
        }
    }

    @Override
    protected void onStart() {
        Player player = Bukkit.getPlayer(playerId());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        }
    }

    @Override
    public void tick(int elapsedTicks) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null) {
            return;
        }
        ColorPalette palette = ColorPalette.get();

        if (elapsedTicks <= FLASH_TICKS) {
            int slot = gui.slotForIndex(triggeredIndex);
            if (slot >= 0) {
                boolean on = (elapsedTicks % 2) == 1;
                inv.setItem(slot, ItemBuilder.of(on ? Material.RED_CONCRETE : Material.TNT)
                        .name(Text.color(palette.danger(), "&l" + Text.smallCaps("Boom")))
                        .hideAllFlags()
                        .build());
            }
            return;
        }

        // Cascade the remaining mines so the player can see how close they were.
        int sinceFlash = elapsedTicks - FLASH_TICKS;
        if (sinceFlash % TICKS_PER_MINE != 0) {
            return;
        }
        if (revealedMines >= remainingMines.size()) {
            markFinished();
            return;
        }

        int index = remainingMines.get(revealedMines++);
        int slot = gui.slotForIndex(index);
        if (slot >= 0) {
            inv.setItem(slot, ItemBuilder.of(Material.GUNPOWDER)
                    .name(Text.color(palette.danger(), Text.smallCaps("Hidden Mine")))
                    .lore("", Text.color(palette.neutral(), "You avoided this one."))
                    .hideAllFlags()
                    .build());
        }
        Player player = Bukkit.getPlayer(playerId());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f);
        }
    }

    @Override
    protected void onFinish() {
        session.onExplosionFinished();
    }

    @Override
    protected void onCancel() {
        // GameSession#forceEnd routes the already-decided loss through PayoutService.
    }
}
