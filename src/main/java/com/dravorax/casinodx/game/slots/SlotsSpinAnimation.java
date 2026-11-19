package com.dravorax.casinodx.game.slots;

import com.dravorax.casinodx.animation.AbstractAnimation;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;

/**
 * Spins the reel windows and lands them on the symbols that were already drawn.
 *
 * <h2>Why this cannot change the outcome</h2>
 * The animation is handed the finished {@code SlotsSymbol[]} and never draws anything. Each reel
 * has a fixed lock tick computed in the constructor, and the symbols shown <em>before</em> that
 * tick come from walking the configured strip with a deterministic offset — not from the RNG. Once
 * a reel's lock tick passes it renders its final symbol and nothing else. Lag, dropped ticks, or a
 * cancelled animation can therefore only affect what the player sees on the way there.
 *
 * <p>Reels lock left to right with a decelerating cadence: the cycle interval grows with the square
 * of the reel's own progress, so each drum blurs, slows, and clicks into place.
 */
final class SlotsSpinAnimation extends AbstractAnimation {

    /** Ticks the finished line is held before the round is settled. */
    private static final int HOLD_TICKS = 18;

    private final SlotsSession session;
    private final SlotsGui gui;
    private final SlotsSymbol[] result;
    private final List<SlotsSymbol> strip;
    private final int[] reelSlots;
    private final int[] lockTicks;
    private final boolean[] locked;

    private final int lastLockTick;

    SlotsSpinAnimation(Player player, SlotsSession session, SlotsGui gui,
                       SlotsSymbol[] result, int spinTicks) {
        this(player, session, gui, result, buildLockTicks(result.length, spinTicks));
    }

    private SlotsSpinAnimation(Player player, SlotsSession session, SlotsGui gui,
                               SlotsSymbol[] result, int[] lockTicks) {
        super(player.getUniqueId(), lockTicks[lockTicks.length - 1] + HOLD_TICKS);
        this.session = session;
        this.gui = gui;
        this.result = result;
        this.strip = session.game().symbols();
        this.reelSlots = gui == null ? SlotsGui.reelSlots(result.length) : gui.reelWindowSlots();
        this.lockTicks = lockTicks;
        this.locked = new boolean[result.length];
        this.lastLockTick = lockTicks[lockTicks.length - 1];
    }

    /**
     * Lock tick per reel. The first reel settles a little over halfway through the configured spin
     * length and the rest follow evenly, so the last reel is always the suspenseful one.
     */
    private static int[] buildLockTicks(int reels, int spinTicks) {
        int duration = Math.max(20, spinTicks);
        int[] ticks = new int[Math.max(1, reels)];
        for (int i = 0; i < ticks.length; i++) {
            double share = (i + 1) / (double) ticks.length;
            ticks[i] = Math.max(i + 1, (int) Math.round(duration * (0.55 + 0.45 * share)));
        }
        // Guarantee a strictly increasing schedule even at the smallest configured spin length.
        for (int i = 1; i < ticks.length; i++) {
            if (ticks[i] <= ticks[i - 1]) {
                ticks[i] = ticks[i - 1] + 1;
            }
        }
        return ticks;
    }

    /**
     * How many symbol steps reel {@code reel} has taken by {@code elapsedTicks}. Delays grow
     * quadratically with the reel's progress towards its own lock tick.
     */
    private int stepsFor(int reel, int elapsedTicks) {
        int lock = lockTicks[reel];
        int steps = 0;
        int cumulative = 0;
        while (cumulative < elapsedTicks && steps < 4096) {
            double progress = Math.min(1.0, cumulative / (double) lock);
            cumulative += 1 + (int) Math.round(4.0 * progress * progress);
            steps++;
        }
        return steps;
    }

    @Override
    public void tick(int elapsedTicks) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null || strip.isEmpty()) {
            return;
        }

        for (int reel = 0; reel < result.length && reel < reelSlots.length; reel++) {
            boolean settled = elapsedTicks >= lockTicks[reel];
            if (settled) {
                if (!locked[reel]) {
                    locked[reel] = true;
                    playLock(reel);
                }
                renderReel(inv, reel, result[reel], true, elapsedTicks);
                continue;
            }

            // Deterministic walk over the strip: offset by the reel index so the drums are not all
            // showing the same symbol at the same time.
            int steps = stepsFor(reel, elapsedTicks);
            SlotsSymbol shown = strip.get(Math.floorMod(reel * 2 + steps, strip.size()));
            renderReel(inv, reel, shown, false, elapsedTicks);
        }

        if (elapsedTicks < lastLockTick) {
            playSpinTick(elapsedTicks);
        } else {
            renderPayline(inv, elapsedTicks);
        }
    }

    /** Soft mechanical tick while any drum is still turning, thinned so it does not become a tone. */
    private void playSpinTick(int elapsedTicks) {
        if (elapsedTicks % 3 != 0) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId());
        if (player != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.6f);
        }
    }

    private void renderReel(Inventory inv, int reel, SlotsSymbol symbol, boolean settled, int elapsedTicks) {
        ColorPalette palette = ColorPalette.get();
        boolean winning = settled && isWinningSymbol(symbol);

        String colour = settled ? (winning ? palette.success() : palette.neutral()) : palette.warning();
        if (winning && (elapsedTicks / 3) % 2 == 0) {
            colour = palette.accent();
        }

        ItemBuilder builder = ItemBuilder.of(symbol.material())
                .name(Text.color(colour, "&l" + Text.smallCaps(symbol.displayName())))
                .lore("", Text.color(palette.neutral(), settled
                        ? "Reel " + (reel + 1) + " locked."
                        : "Reel " + (reel + 1) + " spinning…"))
                .hideAllFlags();
        if (winning) {
            builder.glow();
        }
        inv.setItem(reelSlots[reel], builder.build());
    }

    private boolean isWinningSymbol(SlotsSymbol symbol) {
        SlotsGame.Outcome outcome = session.outcome();
        return outcome != null && outcome.isWin() && outcome.symbol() != null
                && outcome.symbol().key().equals(symbol.key());
    }

    /** Flashes the dividers either side of the reels once the last drum has locked. */
    private void renderPayline(Inventory inv, int elapsedTicks) {
        SlotsGame.Outcome outcome = session.outcome();
        boolean won = outcome != null && outcome.isWin();
        Material pane = won
                ? (elapsedTicks % 4 < 2 ? Material.LIME_STAINED_GLASS_PANE : Material.YELLOW_STAINED_GLASS_PANE)
                : (elapsedTicks % 4 < 2 ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE);

        for (int slot = 19; slot <= 25; slot++) {
            if (isReelSlot(slot)) {
                continue;
            }
            inv.setItem(slot, ItemBuilder.of(pane).name(" ").hideAllFlags().build());
        }
    }

    private boolean isReelSlot(int slot) {
        for (int reelSlot : reelSlots) {
            if (reelSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private void playLock(int reel) {
        Player player = Bukkit.getPlayer(playerId());
        if (player == null) {
            return;
        }
        // Pitch climbs with each reel so the last lock is the loudest cue.
        float pitch = (float) Math.max(0.8, Math.min(2.0, 1.0 + reel * 0.25));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, pitch);
    }

    @Override
    protected void onFinish() {
        session.onSpinFinished();
    }

    @Override
    protected void onCancel() {
        // The reels were drawn before this animation existed; GameSession#forceEnd settles them.
    }
}
