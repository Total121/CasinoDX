package com.dravorax.casinodx.game.wheel;

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
import java.util.Locale;

/**
 * Scrolls the wheel strip past a fixed pointer, decelerating into the section that was already
 * drawn by {@link WheelGame#drawSection()}.
 *
 * <h2>Why this cannot change the outcome</h2>
 * The step schedule is built first, which fixes the number of advances. The <em>starting</em>
 * position is then derived backwards from the winning index:
 * {@code start = winningIndex - pointerOffset - steps (mod n)}. After exactly {@code steps}
 * advances the pointer is guaranteed to sit on the winning section, so lag, tick drift or an early
 * cancel can only affect what the player sees, never what they are paid.
 */
final class WheelSpinAnimation extends AbstractAnimation {

    /** Slots making up the visible strip, left to right. */
    static final int[] STRIP_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    /** Index within {@link #STRIP_SLOTS} that sits under the pointer. */
    static final int POINTER_OFFSET = 3;
    /** Lane of panes directly beneath the strip, used to flash the winner. */
    static final int[] HIGHLIGHT_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    private static final int HOLD_TICKS = 16;

    private final WheelSession session;
    private final WheelGui gui;
    private final List<WheelReward> sections;
    private final List<Integer> stepTicks;
    private final int startPosition;

    private int stepsDone;

    static WheelSpinAnimation create(Player player, WheelSession session, WheelGui gui, int spinTicks) {
        return new WheelSpinAnimation(player, session, gui, buildSchedule(spinTicks));
    }

    private WheelSpinAnimation(Player player, WheelSession session, WheelGui gui, List<Integer> schedule) {
        super(player.getUniqueId(), schedule.get(schedule.size() - 1) + HOLD_TICKS);
        this.session = session;
        this.gui = gui;
        this.sections = session.game().sections();
        this.stepTicks = schedule;

        int n = Math.max(1, sections.size());
        int target = Math.floorMod(session.winningIndex() - POINTER_OFFSET, n);
        this.startPosition = Math.floorMod(target - schedule.size(), n);
    }

    /**
     * Builds the tick offsets at which the strip advances by one section. Delays grow cubically so
     * the wheel blurs at first and then visibly clicks section by section at the end.
     */
    private static List<Integer> buildSchedule(int spinTicks) {
        int duration = Math.max(40, spinTicks);
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

    private int positionAfter(int steps) {
        return Math.floorMod(startPosition + steps, Math.max(1, sections.size()));
    }

    @Override
    public void tick(int elapsedTicks) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null || sections.isEmpty()) {
            return;
        }

        boolean stepped = false;
        while (stepsDone < stepTicks.size() && elapsedTicks >= stepTicks.get(stepsDone)) {
            stepsDone++;
            stepped = true;
        }
        boolean settled = stepsDone >= stepTicks.size();

        int position = positionAfter(stepsDone);
        session.displayIndex(Math.floorMod(position + POINTER_OFFSET, sections.size()));

        drawStrip(inv, position, settled, elapsedTicks);
        drawHighlight(inv, settled, elapsedTicks);

        if (stepped && !settled) {
            playTick();
        }
    }

    private void drawStrip(Inventory inv, int position, boolean settled, int elapsedTicks) {
        ColorPalette palette = ColorPalette.get();
        int n = sections.size();

        for (int i = 0; i < STRIP_SLOTS.length; i++) {
            WheelReward reward = sections.get(Math.floorMod(position + i, n));
            boolean pointed = i == POINTER_OFFSET;

            String colour = pointed ? palette.accent() : palette.neutral();
            if (pointed && settled) {
                // Flash the landed section between success and accent so it reads as "locked in".
                colour = (elapsedTicks / 3) % 2 == 0 ? palette.success() : palette.accent();
            }

            ItemBuilder builder = ItemBuilder.of(reward.icon())
                    .name(Text.color(colour, (pointed ? "&l▶ " : "") + Text.smallCaps(reward.displayName())))
                    .lore("", Text.color(palette.neutral(), settled && pointed
                            ? "Landed!" : String.format(Locale.US, "%.2f%% chance",
                                    session.game().chanceOf(reward) * 100.0)))
                    .hideAllFlags();
            if (pointed) {
                builder.glow();
            }
            inv.setItem(STRIP_SLOTS[i], builder.build());
        }
    }

    private void drawHighlight(Inventory inv, boolean settled, int elapsedTicks) {
        Material moving = (elapsedTicks / 2) % 2 == 0
                ? Material.YELLOW_STAINED_GLASS_PANE
                : Material.ORANGE_STAINED_GLASS_PANE;
        Material landedPane = (elapsedTicks / 3) % 2 == 0
                ? Material.LIME_STAINED_GLASS_PANE
                : Material.GREEN_STAINED_GLASS_PANE;

        for (int i = 0; i < HIGHLIGHT_SLOTS.length; i++) {
            Material pane;
            if (settled) {
                pane = i == POINTER_OFFSET ? landedPane : Material.GRAY_STAINED_GLASS_PANE;
            } else {
                pane = i == POINTER_OFFSET ? moving : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            }
            inv.setItem(HIGHLIGHT_SLOTS[i], ItemBuilder.of(pane).name(" ").hideAllFlags().build());
        }
    }

    private void playTick() {
        Player player = Bukkit.getPlayer(playerId());
        if (player == null) {
            return;
        }
        int remaining = stepTicks.size() - stepsDone;
        // Pitch falls as the wheel slows, which is the audible cue that it is about to stop.
        float pitch = (float) Math.max(0.6, Math.min(2.0, 0.7 + remaining * 0.04));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, pitch);
    }

    @Override
    protected void onFinish() {
        session.finishSpin();
    }

    @Override
    protected void onCancel() {
        // The section is already drawn; GameSession#forceEnd settles the decided result.
    }
}
