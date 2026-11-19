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

import java.util.ArrayList;
import java.util.List;

/**
 * Flies the rocket up to the crash point that was drawn at launch.
 *
 * <p>The entire flight is planned in the constructor: {@link #buildFlightPlan} produces the exact
 * sequence of (tick, multiplier) pairs from 1.00× to the crash point, which means the animation's
 * total length is known up front and the multiplier the player sees is a pure function of elapsed
 * ticks. The animation reads {@code crashPoint}; it never writes it.
 *
 * <p>Intensity scales with the multiplier: steps get larger and land closer together, the trail
 * grows, and the tick sound climbs in pitch.
 */
final class CrashFlightAnimation extends AbstractAnimation {

    private static final int EXPLOSION_TICKS = 12;

    /** Rocket trail across the middle row of the crash menu. */
    private static final int[] TRAIL_SLOTS = CrashGui.TRAIL_SLOTS;

    private final CrashSession session;
    private final CrashGui gui;
    private final double crashPoint;
    private final int[] stepTicks;
    private final double[] stepMultipliers;
    private final int flightTicks;

    private int stepsDone;
    private boolean exploded;

    CrashFlightAnimation(Player player, CrashSession session, CrashGui gui,
                         double crashPoint, int ticksPerStep) {
        this(player, session, gui, crashPoint, buildFlightPlan(crashPoint, ticksPerStep));
    }

    private CrashFlightAnimation(Player player, CrashSession session, CrashGui gui,
                                 double crashPoint, FlightPlan plan) {
        super(player.getUniqueId(), plan.totalTicks() + EXPLOSION_TICKS);
        this.session = session;
        this.gui = gui;
        this.crashPoint = crashPoint;
        this.stepTicks = plan.ticks();
        this.stepMultipliers = plan.multipliers();
        this.flightTicks = plan.totalTicks();
    }

    private record FlightPlan(int[] ticks, double[] multipliers, int totalTicks) {}

    /**
     * Plans the climb. Step size grows with the multiplier so a 50× flight does not take minutes,
     * and the gap between steps shrinks so the ascent visibly accelerates.
     */
    private static FlightPlan buildFlightPlan(double crashPoint, int ticksPerStep) {
        List<Integer> ticks = new ArrayList<>();
        List<Double> multipliers = new ArrayList<>();

        double multiplier = 1.00;
        int cumulative = 0;
        // Bounded by construction: the smallest increment is 0.01 and crashPoint is clamped by
        // CrashGame#maxMultiplier, so this terminates well inside the guard below.
        int guard = 20_000;

        while (multiplier < crashPoint && guard-- > 0) {
            double increment = incrementFor(multiplier);
            multiplier = Math.min(crashPoint, Math.round((multiplier + increment) * 100.0) / 100.0);

            int interval = Math.max(1, ticksPerStep - (int) Math.floor(multiplier / 3.0));
            cumulative += interval;

            ticks.add(cumulative);
            multipliers.add(multiplier);
        }

        if (ticks.isEmpty()) {
            ticks.add(1);
            multipliers.add(crashPoint);
            cumulative = 1;
        }

        int[] tickArray = new int[ticks.size()];
        double[] multiplierArray = new double[multipliers.size()];
        for (int i = 0; i < ticks.size(); i++) {
            tickArray[i] = ticks.get(i);
            multiplierArray[i] = multipliers.get(i);
        }
        return new FlightPlan(tickArray, multiplierArray, cumulative);
    }

    private static double incrementFor(double multiplier) {
        if (multiplier < 2.0) {
            return 0.01;
        }
        if (multiplier < 5.0) {
            return 0.02;
        }
        if (multiplier < 10.0) {
            return 0.05;
        }
        if (multiplier < 25.0) {
            return 0.10;
        }
        if (multiplier < 50.0) {
            return 0.25;
        }
        return 0.50;
    }

    @Override
    public void tick(int elapsedTicks) {
        if (exploded) {
            renderExplosion(elapsedTicks);
            return;
        }

        boolean advanced = false;
        while (stepsDone < stepTicks.length && elapsedTicks >= stepTicks[stepsDone]) {
            stepsDone++;
            advanced = true;
        }

        if (advanced) {
            double multiplier = stepMultipliers[stepsDone - 1];
            session.updateMultiplier(multiplier);
            playTick(multiplier);
            renderTrail(multiplier);
        }

        if (stepsDone >= stepTicks.length || elapsedTicks >= flightTicks) {
            exploded = true;
            session.updateMultiplier(crashPoint);
            Player player = Bukkit.getPlayer(playerId());
            if (player != null) {
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            }
        }
    }

    private void playTick(double multiplier) {
        Player player = Bukkit.getPlayer(playerId());
        if (player == null) {
            return;
        }
        // Thin the sound out once the steps come every tick, or it becomes a solid tone.
        if (multiplier < 2.0 && stepsDone % 4 != 0) {
            return;
        }
        float pitch = (float) Math.max(0.8, Math.min(2.0, 0.8 + multiplier * 0.05));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.35f, pitch);
    }

    private void renderTrail(double multiplier) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null) {
            return;
        }
        ColorPalette palette = ColorPalette.get();

        // Trail length and colour escalate with the multiplier so the risk is legible at a glance.
        int lit = (int) Math.min(TRAIL_SLOTS.length, 1 + Math.floor(multiplier));
        Material flame = multiplier >= 10 ? Material.MAGMA_BLOCK
                : multiplier >= 5 ? Material.ORANGE_CONCRETE
                : Material.YELLOW_CONCRETE;

        for (int i = 0; i < TRAIL_SLOTS.length; i++) {
            if (i < lit - 1) {
                inv.setItem(TRAIL_SLOTS[i], ItemBuilder.of(flame)
                        .name(Text.color(palette.warning(), " "))
                        .hideAllFlags()
                        .build());
            } else if (i == lit - 1) {
                inv.setItem(TRAIL_SLOTS[i], ItemBuilder.of(Material.FIREWORK_ROCKET)
                        .name(Text.color(palette.accent(), "&l" + Text.formatMultiplier(multiplier)))
                        .lore("", Text.color(palette.neutral(), "Climbing…"))
                        .hideAllFlags()
                        .glow()
                        .build());
            } else {
                inv.setItem(TRAIL_SLOTS[i], ItemBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                        .name(" ")
                        .hideAllFlags()
                        .build());
            }
        }
    }

    private void renderExplosion(int elapsedTicks) {
        Inventory inv = gui == null ? null : gui.inventory();
        if (inv == null) {
            return;
        }
        ColorPalette palette = ColorPalette.get();
        boolean flash = (elapsedTicks % 2) == 0;
        for (int slot : TRAIL_SLOTS) {
            inv.setItem(slot, ItemBuilder.of(flash ? Material.RED_CONCRETE : Material.MAGMA_BLOCK)
                    .name(Text.color(palette.danger(), "&l" + Text.smallCaps("Crashed")))
                    .lore("", Text.color(palette.neutral(), "at "
                            + Text.formatMultiplier(crashPoint)))
                    .hideAllFlags()
                    .build());
        }
    }

    @Override
    protected void onFinish() {
        session.onCrash();
    }

    @Override
    protected void onCancel() {
        // Cancelled by a successful cash-out, a disconnect, or a reload. CrashSession has already
        // recorded which of those happened and settles accordingly.
    }
}
