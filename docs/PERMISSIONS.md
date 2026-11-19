# Permissions Guide

CasinoDX uses granular permissions so you can run a VIP casino, staff-only games, or tax-free ranks.

---

## Permission tree

```
casinodx.use                    # Open menu, stats GUI, history
casinodx.play                   # Base play (implicit via per-game perms)
├── casinodx.play.mines
├── casinodx.play.roulette
├── casinodx.play.blackjack
├── casinodx.play.coinflip
├── casinodx.play.crash
└── casinodx.play.wheel
casinodx.stats
casinodx.history
casinodx.tax.exempt             # No winnings tax
casinodx.wheel.free             # No wheel cooldown
casinodx.bypass.limits          # Ignore min/max bets
casinodx.admin
├── casinodx.admin.reload
└── casinodx.admin.stats
```

---

## Default behaviour

Most play permissions default to **`true`** for all players. Admin permissions default to **`op`**.

---

## Example: LuckPerms groups

**Default player** — already has defaults; optionally explicit:

```yaml
casinodx.use: true
casinodx.play.*: true
casinodx.stats: true
casinodx.history: true
```

**VIP** — tax exempt + unlimited wheel:

```yaml
casinodx.tax.exempt: true
casinodx.wheel.free: true
```

**High roller** — bypass bet caps:

```yaml
casinodx.bypass.limits: true
```

**Casino moderator**:

```yaml
casinodx.admin.reload: true
casinodx.admin.stats: true
```

**Mines-only lobby** — disable other games:

```yaml
casinodx.play.mines: true
casinodx.play.roulette: false
casinodx.play.blackjack: false
casinodx.play.coinflip: false
casinodx.play.crash: false
casinodx.play.wheel: false
```

Also set `enabled: false` in config for games you want fully offline.

---

## Notes

- `casinodx.use` is required for `/casino` and the main menu
- Each game checks `casinodx.play.<game>` when opening its lobby
- Tax exemption is checked at payout time in `TaxService`
- Wheel free bypass applies only to the cooldown, not paid spin cost
