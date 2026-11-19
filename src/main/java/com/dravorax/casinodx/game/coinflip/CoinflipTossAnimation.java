package com.dravorax.casinodx.game.coinflip;

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
 * Spins the coin through alternating faces with a decelerating cadence and lands on the side that
 * was already drawn.
 *
 * <p>Parity is what makes this exact: the schedule is built first, and the starting face is chosen
 * so that an even number of remaining flips leaves the coin showing {@code landsOn}. The animation
 * cannot desynchronise from the result because it never decides anything.
 */
final class CoinflipTossAnimation extends AbstractAnimation {

    private static final int HOLD_TICKS = 12;
    private static final int COIN_SLOT = 31;
    /** Ring around the coin. Skips Heads (29), Tails (33), and the Toss primary (40). */
    private static final int[] SPARKLE_SLOTS = {21, 22, 23, 30, 32, 39, 41};

    private final CoinflipSession session;
    private final CoinflipGui gui;
    private final CoinflipGame.Side landsOn;
    private final List<Integer> stepTicks;
    private final CoinflipGame.Side startFace;

    private int stepsDone;

    CoinflipTossAnimation(Player player, CoinflipSession session, CoinflipGui gui,
                          CoinflipGame.Side landsOn, int flipTicks) {
        this(player, session, gui, landsOn, buildSchedule(flipTicks));
    }

    private CoinflipTossAnimation(Player player, CoinflipSession session, CoinflipGui gui,
                                  CoinflipGame.Side landsOn, List<Integer> schedule) {
        super(player.getUniqueId(), schedule.get(schedule.size() - 1) + HOLD_TICKS);
        this.session = session;
        this.gui = gui;
        this.landsOn = landsOn;
        this.stepTicks = schedule;
        // Each step flips the face, so an odd step count means starting on the opposite side.
        this.startFace = (schedule.size() % 2 == 0) ? landsOn : landsOn.other();
    }

    private static List<Integer> buildSchedule(int flipTicks) {
        int duration = Math.max(20, flipTicks);
        List<Integer> schedule = new ArrayList<>();
        int cumulative = 0;
        while (cumulative < duration) {
            double progress = cumulative / (double) duration;
            // Quadratic slowdown: rapid blur at the start, distinct flips at the end.
            int delay = 1 + (int) Math.round(5.0 * progress * progress);
            cumulative += delay;
            schedule.add(cumulative);
        }
        if (schedule.isEmpty()) {
            schedule.add(1);
        }
        return schedule;
    }

    private CoinflipGame.Side faceAfter(int steps) {
        return (steps % 2 == 0) ? startFace : startFace.other();
    }

    @Override
    public void tick(int elapsedTicks) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null) {
            return;
        }

        boolean stepped = false;
        while (stepsDone < stepTicks.size() && elapsedTicks >= stepTicks.get(stepsDone)) {
            stepsDone++;
            stepped = true;
        }

        boolean settled = stepsDone >= stepTicks.size();
        CoinflipGame.Side face = faceAfter(stepsDone);
        renderCoin(inv, face, settled, elapsedTicks);

        if (stepped && !settled) {
            Player player = Bukkit.getPlayer(playerId());
            if (player != null) {
                int remaining = stepTicks.size() - stepsDone;
                float pitch = (float) Math.max(0.7, Math.min(2.0, 1.9 - remaining * 0.06));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.4f, pitch);
            }
        }
        if (settled) {
            renderSparkle(inv, elapsedTicks);
        }
    }

    private void renderCoin(Inventory inv, CoinflipGame.Side face, boolean settled, int elapsedTicks) {
        ColorPalette palette = ColorPalette.get();
        boolean heads = face == CoinflipGame.Side.HEADS;
        Material material = heads ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
        if (settled && (elapsedTicks / 2) % 2 == 0) {
            material = heads ? Material.RAW_GOLD_BLOCK : Material.RAW_IRON_BLOCK;
        }

        ItemBuilder builder = ItemBuilder.of(material)
                .name(Text.color(heads ? palette.accent() : palette.neutral(),
                        "&l" + Text.smallCaps(face.displayName())))
                .lore("", Text.color(palette.neutral(), settled ? "The coin has landed." : "Spinning…"))
                .hideAllFlags();
        if (settled) {
            builder.glow();
        }
        inv.setItem(COIN_SLOT, builder.build());
    }

    private void renderSparkle(Inventory inv, int elapsedTicks) {
        ColorPalette palette = ColorPalette.get();
        boolean won = session.choice() == landsOn;
        Material pane = won
                ? (elapsedTicks % 4 < 2 ? Material.LIME_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE)
                : (elapsedTicks % 4 < 2 ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);
        for (int slot : SPARKLE_SLOTS) {
            inv.setItem(slot, ItemBuilder.of(pane)
                    .name(Text.color(won ? palette.success() : palette.danger(), " "))
                    .hideAllFlags()
                    .build());
        }
    }

    @Override
    protected void onFinish() {
        session.onTossFinished();
    }

    @Override
    protected void onCancel() {
        // The toss is already drawn; GameSession#forceEnd settles it.
    }
}
