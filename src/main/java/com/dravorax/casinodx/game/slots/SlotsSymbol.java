package com.dravorax.casinodx.game.slots;

import org.bukkit.Material;

/**
 * One reel symbol, loaded from {@code modules/slots/payouts.yml}.
 *
 * <p>Every reel carries the same symbol set with the same weights, so a symbol's probability of
 * landing on any one reel is {@code weight / totalWeight} and the reels are independent.
 *
 * @param key         config key; used in log lines and result labels
 * @param displayName name shown on the reel window
 * @param material    icon material, chosen to be visually distinct from every other symbol
 * @param weight      relative frequency on each reel; higher means more common
 * @param threeMatch  gross multiplier (stake included) when <em>every</em> reel shows this symbol
 * @param twoMatch    gross multiplier when all but one reel show it; {@code 0} disables it
 */
public record SlotsSymbol(String key,
                          String displayName,
                          Material material,
                          double weight,
                          double threeMatch,
                          double twoMatch) {

    /** Whether this symbol also pays on a near-miss (two of three on the default layout). */
    public boolean paysPartial() {
        return twoMatch > 0;
    }
}
