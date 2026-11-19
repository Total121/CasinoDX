package com.dravorax.casinodx.game.roulette;

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
 * Scrolls the wheel strip along the top row of the table and brings it to rest on the pocket that
 * was already drawn.
 *
 * <p>The winning pocket is supplied by the caller. This class works <em>backwards</em> from it:
 * knowing how many steps the slowdown curve will take, it picks a starting offset such that the
 * final step lands the winner in the centre window. Nothing here can change the result — if the
 * animation is cancelled the session settles the same pocket it would have settled anyway.
 */
final class RouletteSpinAnimation extends AbstractAnimation {

    /** Strip slots along row 0; index 4 is the centre marker. */
    private static final int[] STRIP_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int CENTER = 4;
    private static final int HOLD_TICKS = 10;

    private final RouletteSession session;
    private final RouletteGui gui;
    private final int winningPocket;

    /** Cumulative tick at which each scroll step happens; derived from the slowdown curve. */
    private final List<Integer> stepTicks;
    private final int startIndex;

    private int stepsDone;

    RouletteSpinAnimation(Player player, RouletteSession session, RouletteGui gui,
                          int winningPocket, int spinTicks) {
        this(player, session, gui, winningPocket, buildSchedule(spinTicks));
    }

    private RouletteSpinAnimation(Player player, RouletteSession session, RouletteGui gui,
                                  int winningPocket, List<Integer> schedule) {
        super(player.getUniqueId(), schedule.get(schedule.size() - 1) + HOLD_TICKS);
        this.session = session;
        this.gui = gui;
        this.winningPocket = winningPocket;
        this.stepTicks = schedule;

        // Work backwards from the drawn pocket: after `steps` scroll steps the centre window must
        // show the winner, so start that many pockets behind it.
        int targetIndex = RouletteWheel.indexInOrder(winningPocket);
        this.startIndex = ((targetIndex - schedule.size()) % RouletteWheel.POCKETS
                + RouletteWheel.POCKETS) % RouletteWheel.POCKETS;
    }

    /**
     * Tick offsets for each scroll step. The gap between steps grows with the cube of elapsed
     * progress, so the strip rips past at one pocket per tick early on and crawls at the end.
     */
    private static List<Integer> buildSchedule(int spinTicks) {
        int duration = Math.max(20, spinTicks);
        List<Integer> schedule = new ArrayList<>();
        int cumulative = 0;
        while (cumulative < duration) {
            double progress = cumulative / (double) duration;
            int delay = 1 + (int) Math.round(7.0 * progress * progress * progress);
            cumulative += delay;
            schedule.add(cumulative);
        }
        if (schedule.isEmpty()) {
            schedule.add(1);
        }
        return schedule;
    }

    @Override
    public void tick(int elapsedTicks) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null) {
            return;
        }

        while (stepsDone < stepTicks.size() && elapsedTicks >= stepTicks.get(stepsDone)) {
            stepsDone++;
            renderStrip(inv, startIndex + stepsDone, false);
            clickSound(stepsDone);
        }

        if (stepsDone >= stepTicks.size()) {
            // Resting on the winner: flash the centre so the result reads clearly.
            boolean highlight = ((elapsedTicks / 2) % 2) == 0;
            renderStrip(inv, startIndex + stepsDone, highlight);
        }
    }

    private void renderStrip(Inventory inv, int centerOffset, boolean highlightCenter) {
        ColorPalette palette = ColorPalette.get();
        for (int i = 0; i < STRIP_SLOTS.length; i++) {
            int order = centerOffset + (i - CENTER);
            int normalized = ((order % RouletteWheel.POCKETS) + RouletteWheel.POCKETS) % RouletteWheel.POCKETS;
            int pocket = RouletteWheel.ORDER[normalized];

            boolean center = i == CENTER;
            Material material = center && highlightCenter
                    ? Material.YELLOW_CONCRETE
                    : RouletteWheel.materialOf(pocket);

            ItemBuilder builder = ItemBuilder.of(material)
                    .name(Text.color(center ? palette.accent() : palette.neutral(),
                            (center ? "&l▼ " : "") + RouletteWheel.colorCode(pocket) + "&l" + pocket))
                    .hideAllFlags();
            if (center) {
                builder.glow();
            }
            inv.setItem(STRIP_SLOTS[i], builder.build());
        }
    }

    private void clickSound(int step) {
        Player player = Bukkit.getPlayer(playerId());
        if (player == null) {
            return;
        }
        // Every step early on would be deafening; thin them out while the strip is fast.
        int remaining = stepTicks.size() - step;
        if (remaining > 6 && step % 3 != 0) {
            return;
        }
        float pitch = (float) Math.max(0.6, Math.min(2.0, 1.8 - remaining * 0.08));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, pitch);
    }

    @Override
    protected void onFinish() {
        Player player = Bukkit.getPlayer(playerId());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f,
                    RouletteWheel.colorOf(winningPocket) == RouletteWheel.Color.GREEN ? 1.8f : 1.2f);
        }
        session.onSpinFinished();
    }

    @Override
    protected void onCancel() {
        // The pocket is already drawn; GameSession#forceEnd settles it.
    }
}
