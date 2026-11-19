# Configuration Reference

CasinoDX uses a modular YAML layout. The main `config.yml` holds global switches; each feature has its own folder under `modules/`.

---

## config.yml (main)

| Key | Default | Description |
|-----|---------|-------------|
| `prefix` | MiniMessage gold prefix | Chat prefix for all messages. Overrides `messages.yml` when set. |
| `enabled` | `true` | Master plugin switch |
| `debug` | `false` | Verbose logging |
| `language` | `en` | Reserved for future locale bundles |
| `games.<name>.enabled` | `true` | Per-game master toggle |
| `storage.type` | `sqlite` | `sqlite` or `mysql` |
| `storage.mysql.*` | — | MySQL connection settings |
| `economy.symbol` | `$` | Currency symbol for display |
| `economy.format` | `#,##0.00` | Number format pattern |
| `betting.min` / `max` | `10` / `1000000` | Global bet limits |

A game must be enabled in **both** `config.yml` and its module `config.yml` to be playable.

---

## modules/economy/config.yml

| Key | Description |
|-----|-------------|
| `provider` | `auto` (prefer vault2 / VaultUnlocked, else classic Vault), `vaultunlocked`, or `vault` |
| `currency-symbol` | Display symbol (GUI / chat formatting only) |
| `currency` | Optional VaultUnlocked currency id; blank = provider default |
| `decimal-places` | Decimal places in formatted amounts |
| `minimum-bet` / `maximum-bet` | Economy-wide bet limits |

VaultUnlocked registers as Bukkit plugin `Vault`. CasinoDX detects vault2 via the `net.milkbowl.vault2.economy.Economy` service, not a plugin named `VaultUnlocked`.

---

## modules/tax/config.yml

| Key | Description |
|-----|-------------|
| `enabled` | Turn tax on/off |
| `percentage` | Rate in percent (e.g. `15.0`) |
| `apply-to` | `profit` or `gross` |
| `minimum-taxable` | Minimum taxable base before tax applies |
| `maximum-tax` | Cap per round (`0` = no cap) |
| `per-game.<game>.*` | Override percentage and apply-to per game |

See the tax module file for a worked $1,000 → $2,000 example at 15%.

---

## modules/messages/messages.yml

All player-facing strings. Supports `&` colour codes and `{placeholder}` tokens. Set a key to `""` to silence it.

If `prefix` is **not** set in main `config.yml`, the prefix here is used.

---

## modules/gui/

| File | Purpose |
|------|---------|
| `config.yml` | Shared GUI settings |
| `main-menu.yml` | Hub menu items and slots |
| `colors.yml` | Glass pane materials for states |
| `animations.yml` | Animated item frames |
| `sounds.yml` | GUI interaction sounds |

---

## Game modules

Each game folder contains:

- **config.yml** — enabled flag, bet limits, house-edge, game-specific options
- **payouts.yml** (or **rewards.yml** for wheel) — multiplier tables and prize weights

### Common game keys

| Key | Description |
|-----|-------------|
| `enabled` | Module-level enable |
| `minimum-bet` / `maximum-bet` | Override global limits (`0` max = use global) |
| `house-edge` | House edge in percent (meaning varies by game — see GAMES.md) |

---

## modules/sounds/config.yml

Maps logical events (`bet`, `win`, `loss`, etc.) to Bukkit sound names and volumes.

---

## modules/statistics/config.yml & modules/history/config.yml

Control retention, page sizes, and display options for `/casino stats` and `/casino history`.

---

## Reload behaviour

`/casino reload` re-reads all YAML, re-validates values, and hot-reloads services. Reload is **blocked** while any player has an active game session to prevent inconsistent state.

Validation errors are logged to console; the previous known-good config is kept when a file fails to parse.

---

## Validation rules (summary)

- Bet amounts and tax values must be non-negative finite numbers
- Percentages must be 0–100
- GUI slots must be 0–53
- Materials must be valid item materials
- Sounds must match Bukkit `Sound` enum names (or `minecraft:` prefixed variants)
