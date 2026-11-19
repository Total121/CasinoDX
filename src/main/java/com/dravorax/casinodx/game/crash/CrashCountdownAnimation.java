package com.dravorax.casinodx.game.crash;

import com.dravorax.casinodx.animation.AbstractAnimation;
import com.dravorax.casinodx.gui.ColorPalette;
import com.dravorax.casinodx.util.ItemBuilder;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * The "3… 2… 1…" before the rocket moves.
 *
 * <p>This exists so the player is looking at the cockpit — stake, potential payout, recent crash
 * points — before the multiplier starts climbing, rather than arriving mid-flight and having to
 * catch up. The countdown owns the multiplier slot and the rocket trail for its duration and hands
 * both back to {@link CrashFlightAnimation} through {@link CrashSession#beginFlight()}.
 *
 * <p>Cashing out is impossible while this runs: {@link CrashSession#inFlight()} requires the flight
 * phase, and a disconnect during the countdown refunds the wager because the player never had the
 * chance to collect. The crash point itself was already drawn when the session was constructed;
 * the countdown neither reads nor influences it.
 */
final class CrashCountdownAnimation extends AbstractAnimation {

    private final CrashSession session;
    private final CrashGui gui;
    private final int totalTicks;

    private int lastRenderedSecond = -1;

    CrashCountdownAnimation(Player player, CrashSession session, CrashGui gui, int countdownTicks) {
        super(player.getUniqueId(), Math.max(1, countdownTicks));
        this.session = session;
        this.gui = gui;
        this.totalTicks = Math.max(1, countdownTicks);
    }

    @Override
    public void tick(int elapsedTicks) {
        // Whole seconds still to go, counting down: 3, 2, 1, then launch.
        int remaining = (int) Math.ceil((totalTicks - elapsedTicks) / 20.0);
        if (remaining < 1) {
            remaining = 1;
        }
        session.updateCountdown(remaining);

        if (remaining != lastRenderedSecond) {
            lastRenderedSecond = remaining;
            playBeep(remaining);
        }

        Inventory inv = gui == null ? null : gui.inventory();
        if (inv != null) {
            renderCountdown(inv, remaining, elapsedTicks);
        }
    }

    private void renderCountdown(Inventory inv, int remaining, int elapsedTicks) {
        ColorPalette palette = ColorPalette.get();

        inv.setItem(CrashGui.SLOT_MULTIPLIER, ItemBuilder.of(countdownMaterial(remaining))
                .name(Text.color(palette.warning(), "&l" + remaining))
                .lore(
                        "",
                        Text.color(palette.neutral(), "Launching in " + remaining
                                + (remaining == 1 ? " second" : " seconds") + "."),
                        Text.color(palette.neutral(), "Cash out opens when the rocket lifts."),
                        "",
                        Text.color(palette.neutral(), "Leaving now returns your stake.")
                )
                .hideAllFlags()
                .glow()
                .build());

        // The trail pulses towards the launch so the row does not sit dead for three seconds.
        Material lit = (elapsedTicks / 4) % 2 == 0
                ? Material.ORANGE_STAINED_GLASS_PANE
                : Material.YELLOW_STAINED_GLASS_PANE;
        for (int i = 0; i < CrashGui.TRAIL_SLOTS.length; i++) {
            boolean on = i == 0 || (elapsedTicks / 3) % CrashGui.TRAIL_SLOTS.length == i;
            inv.setItem(CrashGui.TRAIL_SLOTS[i], ItemBuilder.of(
                            on ? lit : Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                    .name(" ")
                    .hideAllFlags()
                    .build());
        }
    }

    private static Material countdownMaterial(int remaining) {
        return switch (remaining) {
            case 1 -> Material.LIME_CONCRETE;
            case 2 -> Material.YELLOW_CONCRETE;
            default -> Material.ORANGE_CONCRETE;
        };
    }

    private void playBeep(int remaining) {
        Player player = Bukkit.getPlayer(playerId());
        if (player == null) {
            return;
        }
        // Pitch rises as the launch approaches.
        float pitch = (float) Math.max(0.8, Math.min(2.0, 1.8 - remaining * 0.2));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, pitch);
    }

    @Override
    protected void onFinish() {
        session.beginFlight();
    }

    @Override
    protected void onCancel() {
        // Nothing was decided during the countdown, so GameSession#forceEnd refunds the wager.
    }
}
