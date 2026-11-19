# Games Reference

CasinoDX ships six games. Each draws its outcome **server-side before any animation**, so visual timing cannot affect results.

---

## Mines

**Concept:** Grid of hidden tiles; some are mines. Reveal safe tiles to increase multiplier; cash out anytime or lose on a mine.

**Multiplier formula** (when not overridden in `payouts.yml`):

```
fair(k) = C(N, k) / C(N − M, k)
multiplier(k) = fair(k) × (1 − houseEdge/100)
```

Where `N` = total tiles, `M` = mines, `k` = safe reveals.

**Default RTP:** 99% with `house-edge: 1.0`

**Key config:** `grid-size` (3–5), `mine-count`, `house-edge`, optional `multipliers` overrides in `payouts.yml`

---

## Roulette

**Concept:** European single-zero wheel (0–36). Inside and outside bets with standard payouts.

**Edge:** Inherent 2.7% from 36:1 payout on 37 pockets. The `house-edge` config value is informational.

**Key config:** `spin-duration-ticks`, bet types in `payouts.yml`

---

## Blackjack

**Concept:** Standard blackjack vs dealer. Hit, stand, double where rules allow.

**Edge:** ~0.5% with default rules (dealer stands on soft 17, 3:2 blackjack).

**Key config:** `decks`, `dealer-hits-soft-17`, `blackjack-payout` in module and `payouts.yml`

---

## Coinflip

**Concept:** Pick heads or tails. Win pays a fixed multiplier.

**Formula:**

```
multiplier = 2.0 × (1 − houseEdge/100)
```

**Default:** `house-edge: 4.0` → 1.92× payout, 96% RTP on wins (50% win chance).

---

## Crash

**Concept:** Multiplier rises; cash out before crash or lose stake.

**Crash point distribution:**

```
crashPoint = floor((1 − e) / (1 − U) × 100) / 100
```

`U ~ Uniform[0,1)`, `e` = house edge fraction. Clamped to `min-multiplier` / `max-multiplier`.

**Fairness property:** Expected value of cashing out at any target `x` equals `1 − e`.

**Default RTP:** 99% with `house-edge: 1.0`

**Key config:** `min-multiplier`, `max-multiplier`, `ticks-per-step`, `house-edge`

---

## Wheel of Fortune

**Concept:** Weighted prize wheel. One free spin per cooldown; optional paid spins.

**Outcome:** Sampled once from normalized weights in `rewards.yml` before animation.

**Reward types:** Cash, multiplier (× stake or `multiplier-base`), items, commands (gated).

**Key config:** `spin-cooldown-seconds`, `paid-spin-cost`, `allow-command-rewards`, `rewards.yml` sections

**Security:** Command rewards are **off** by default. Only enable after auditing every command in `rewards.yml`.

---

## Disabling a game

1. Set `games.<name>.enabled: false` in main `config.yml`, **or**
2. Set `enabled: false` in the game's module `config.yml`

Both must allow the game for it to appear and accept bets.

---

## House edge vs RTP

| Term | Meaning |
|------|---------|
| House edge | Percent retained by the house (e.g. 1% edge → 99% RTP) |
| RTP | Return to player = 100% − house edge (for symmetric games) |

Roulette and Wheel RTP depend on configured odds/weights rather than a single `house-edge` key.
