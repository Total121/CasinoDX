# CasinoDX

**Premium Minecraft Casino System** — six fully GUI-driven casino games for Paper and Folia servers, with Vault economy integration, modular configuration, statistics, history, and optional winnings tax.

**Author:** DravoraX

---

## What is CasinoDX?

CasinoDX is a Paper/Folia plugin that adds a complete in-game casino to your Minecraft server. Players open chest-style GUIs, place bets through your economy plugin, and play Mines, Roulette, Blackjack, Coinflip, Crash, or Wheel of Fortune. Every outcome is decided server-side before any animation runs, so lag, click timing, or packet order cannot change results.

The plugin is designed for server owners who want a polished, configurable casino without custom code. All game rules, payouts, messages, sounds, and menus are YAML-driven.

---

## Features

| Game | Description |
|------|-------------|
| **Mines** | Reveal safe tiles on a grid; each reveal raises the multiplier. Cash out or hit a mine. |
| **Roulette** | European single-zero wheel with inside/outside bets. |
| **Blackjack** | Standard rules with configurable decks, dealer stand, and 3:2 blackjack payout. |
| **Coinflip** | 50/50 heads or tails with configurable house-edge multiplier. |
| **Crash** | Rocket climbs; cash out before it explodes. Provably-fair crash distribution. |
| **Wheel of Fortune** | Weighted prize wheel with free-spin cooldown and optional paid spins. |

Additional features:

- Vault and VaultUnlocked economy support
- SQLite or MySQL storage for stats, history, and wheel cooldowns
- Configurable winnings tax (profit or gross basis; default 15% on profit)
- Offline settlement with tax; queued wheel item/command rewards on reconnect
- Anti-exploit guards (click cooldown, transaction locks)
- Folia-compatible scheduling
- Modular YAML configuration with validation on reload
- Fully customizable GUIs, colours, sounds, and messages

---

## Requirements

| Requirement | Version |
|-------------|---------|
| Server | **Paper** or **Folia** **1.21.4+** |
| Java | **17+** |
| Economy | **Vault** or **VaultUnlocked** + an economy provider (e.g. EssentialsX, CMI) |

---

## Installation

1. Download `CasinoDX-1.0.0.jar` (or build from source — see below).
2. Place the jar in your server's `plugins/` folder.
3. Install **Vault** (or **VaultUnlocked**) and an economy plugin.
4. Start or restart the server.
5. Edit `plugins/CasinoDX/config.yml` and module files as needed.
6. Run `/casino reload` (requires `casinodx.admin.reload`) or restart to apply changes.

On first launch, CasinoDX creates its data folder and copies default module configs.

---

## Vault setup

1. Install [Vault](https://www.spigotmc.org/resources/vault.34315/) on your server.
2. Install an economy plugin that registers with Vault (EssentialsX, CMI, etc.).
3. Start the server and confirm Vault loads before CasinoDX.
4. CasinoDX auto-detects Vault on enable. Check console for:
   ```
   [CasinoDX] Hooked into economy: <provider name>
   ```
5. If no provider is found, games refuse bets and players see `no-economy`.

Configure currency display in `modules/economy/config.yml` (`currency-symbol`, `minimum-bet`, `maximum-bet`).

---

## VaultUnlocked setup

[VaultUnlocked](https://modrinth.com/plugin/vaultunlocked) is a maintained fork of Vault. It still registers under the Bukkit plugin name **`Vault`**, and exposes both the classic Vault Economy API and the newer vault2 API (UUID + BigDecimal, optional multi-currency).

CasinoDX supports both:

1. Install **VaultUnlocked** (or classic Vault) and an economy plugin that hooks into it.
2. Leave `provider: auto` in `modules/economy/config.yml` (default). CasinoDX prefers the **vault2** service when one is registered, otherwise falls back to classic Vault Economy.
3. Optional: set `provider: vaultunlocked` to require vault2 only, or `provider: vault` to force the classic API.
4. Optional multi-currency: set `currency: <id>` to use a named VaultUnlocked currency (blank = provider default).
5. On enable you should see:
   ```
   [CasinoDX] Hooked into economy: VaultUnlocked (<provider>)
   ```
   or `Vault (<provider>)` when only the legacy API is available.

Classic Vault and VaultUnlocked cannot both be installed — they share the plugin name `Vault`.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/casino` | Open the main casino menu | `casinodx.use` |
| `/casino <game>` | Jump straight into a game (`mines`, `roulette`, etc.) | `casinodx.use` + game permission |
| `/casino stats` | View your statistics (GUI) | `casinodx.use` |
| `/casino stats <player>` | View another player's stats (text) | `casinodx.admin.stats` |
| `/casino history [page]` | View recent rounds | `casinodx.use` |
| `/casino games` | List enabled games | `casinodx.use` |
| `/casino reload` | Reload all configuration | `casinodx.admin.reload` |
| `/casino help` | Show command help | `casinodx.use` |

**Aliases:** `/casinodx`, `/cdx`

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `casinodx.use` | `true` | Open casino menu and use basic commands |
| `casinodx.play` | `true` | Base permission for playing games |
| `casinodx.play.mines` | `true` | Play Mines |
| `casinodx.play.roulette` | `true` | Play Roulette |
| `casinodx.play.blackjack` | `true` | Play Blackjack |
| `casinodx.play.coinflip` | `true` | Play Coinflip |
| `casinodx.play.crash` | `true` | Play Crash |
| `casinodx.play.wheel` | `true` | Play Wheel of Fortune |
| `casinodx.stats` | `true` | View personal statistics |
| `casinodx.history` | `true` | View personal history |
| `casinodx.tax.exempt` | `false` | Exempt from winnings tax |
| `casinodx.wheel.free` | `false` | Bypass wheel free-spin cooldown |
| `casinodx.bypass.limits` | `false` | Bypass bet min/max limits |
| `casinodx.admin` | `op` | Admin command group |
| `casinodx.admin.reload` | `op` | Reload configuration |
| `casinodx.admin.stats` | `op` | View other players' statistics |

See [docs/PERMISSIONS.md](docs/PERMISSIONS.md) for grouping examples.

---

## Configuration overview

```
plugins/CasinoDX/
├── config.yml                 # Master switch, prefix, games, storage, betting
├── data/
│   └── casinodx.db            # SQLite database (default)
└── modules/
    ├── blackjack/   config.yml, payouts.yml
    ├── coinflip/    config.yml, payouts.yml
    ├── crash/       config.yml, payouts.yml
    ├── economy/     config.yml
    ├── gui/         config.yml, main-menu.yml, colors.yml, animations.yml, sounds.yml
    ├── history/     config.yml
    ├── messages/    messages.yml
    ├── mines/       config.yml, payouts.yml
    ├── roulette/    config.yml, payouts.yml
    ├── sounds/      config.yml
    ├── statistics/  config.yml
    ├── tax/         config.yml
    └── wheel/       config.yml, rewards.yml
```

Detailed reference: [docs/CONFIGURATION.md](docs/CONFIGURATION.md)

---

## Beginner configuration guide

1. **Enable the plugin** — set `enabled: true` in `config.yml`.
2. **Set your chat prefix** — edit `prefix` in `config.yml` (overrides messages.yml).
3. **Adjust bet limits** — `betting.min` / `betting.max` in `config.yml`, or per-game in each module.
4. **Pick which games run** — toggle under `games:` in `config.yml` and `enabled:` in each game module.
5. **Configure tax** — edit `modules/tax/config.yml` (see Tax guide below).
6. **Customize messages** — edit `modules/messages/messages.yml`.
7. **Reload** — `/casino reload` when no players are mid-round.

---

## Per-game notes (probability / RTP)

| Game | Default edge | Notes |
|------|--------------|-------|
| Mines | 1% | Exact combinatorial multipliers; RTP = 100% − house-edge |
| Roulette | 2.7% | Built into single-zero wheel odds (36:1 on 37 pockets) |
| Blackjack | ~0.5% | Standard rules; edge from dealer stand and deck count |
| Coinflip | 4% | Win pays `2 × (1 − edge/100)` |
| Crash | 1% | Provably-fair; EV = 1 − edge regardless of cash-out target |
| Wheel | Configurable | RTP = weighted average of `rewards.yml` sections |

Full math and tuning: [docs/GAMES.md](docs/GAMES.md)

---

## Tax guide (15% example)

Default tax config charges **15% on profit**:

```yaml
enabled: true
percentage: 15.0
apply-to: profit
```

**Example:** Bet **$1,000**, cash out at **$2,000** gross.

- Profit = $2,000 − $1,000 = **$1,000**
- Tax = 15% × $1,000 = **$150**
- Net payout = $2,000 − $150 = **$1,850**

With `apply-to: gross`, tax would be 15% × $2,000 = $300 (net $1,700). Grant `casinodx.tax.exempt` to VIPs who should not be taxed.

---

## Database guide

**SQLite (default)** — zero setup. Data stored at `plugins/CasinoDX/data/casinodx.db`. Best for single servers.

**MySQL** — set in `config.yml`:

```yaml
storage:
  type: mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: casinodx
    username: user
    password: secret
    pool-size: 2
```

Use MySQL when sharing stats/history across a network. All database I/O runs off the main thread.

---

## GUI customization

Edit files under `modules/gui/`:

- **main-menu.yml** — casino hub layout and game icons
- **colors.yml** — glass pane colours for win/loss/neutral states
- **animations.yml** — frame timing for animated items
- **sounds.yml** — GUI click sounds (see also `modules/sounds/config.yml`)

Each game module may define its own slot layout in code-driven GUIs; bet amounts and info panels pull text from messages and economy format settings.

---

## Folia notes

CasinoDX declares `folia-supported: true` and uses a platform scheduler that detects Folia at runtime. No extra configuration is required — install on Folia the same way as Paper.

See [docs/FOLIA.md](docs/FOLIA.md) for threading details.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "No economy provider" | Install Vault/VaultUnlocked + economy plugin; restart |
| Bets always rejected | Check min/max bet, player balance, `casinodx.play.<game>` |
| Game missing from menu | Enable in `config.yml` **and** the game module's `enabled: true` |
| Reload fails | Ensure no players are in active sessions; check console for YAML errors |
| Tax seems wrong | Verify `apply-to` (profit vs gross) in `modules/tax/config.yml` |
| Wheel command rewards not running | Set `allow-command-rewards: true` in wheel config (security gate) |
| MySQL connection errors | Verify credentials, firewall, and that the database exists |

Enable `debug: true` in `config.yml` for verbose logging.

---

## Building from source

```bash
git clone <repository>
cd CasinoDX
./gradlew test jar    # Windows: gradlew.bat test jar
```

Output: `build/libs/CasinoDX-1.0.0.jar`

Requires JDK 17+. Unit tests use JUnit 5 and do not need a running Minecraft server.

---

## Security statement

CasinoDX is authored by **DravoraX** and distributed as open, auditable source.

- **No backdoors** — the plugin does not grant hidden operator access or execute undeclared commands.
- **No phone-home** — CasinoDX does not connect to external analytics, licensing, or telemetry servers.
- **Command rewards** on the Wheel are disabled by default (`allow-command-rewards: false`) because they run console commands you define in `rewards.yml`.
- **Anti-exploit** — per-player transaction locks and click cooldowns prevent double cash-outs and auto-clicker abuse.
- **Server-side outcomes** — all random results are drawn before animations start.

Review `rewards.yml` before enabling command rewards. Only install jars from sources you trust.

---

## Further reading

- [docs/CONFIGURATION.md](docs/CONFIGURATION.md)
- [docs/GAMES.md](docs/GAMES.md)
- [docs/FOLIA.md](docs/FOLIA.md)
- [docs/PERMISSIONS.md](docs/PERMISSIONS.md)

---

*CasinoDX — DravoraX*
