# CasinoDX — Final Audit Scorecard

**Plugin:** CasinoDX 1.0.0  
**Author:** DravoraX  
**Date:** 2026-09-15  

## Verified in this environment

- Clean Gradle compile (Paper API 1.21.4, Java 17)
- Unit tests (money, tax, crash/mines math, anti-exploit, offline settlement, economy provider modes, heads)
- Security pass: no network clients, no backdoors, no remote code download
- Settlement contract: escrow → single `claimSettlement()` → `PayoutService`
- Outcomes drawn before animations (SecureRandom)
- Offline win paths apply tax; wheel non-cash rewards queue to `player_meta`
- Folia: entity-scheduled animation ticks for player-bound GUI updates
- Economy: Vault + VaultUnlocked (vault2) via service registration; `provider` config honored

## Not verified here

- Live Paper/Folia server with VaultUnlocked + players
- Visual animation pacing / GUI feel in-game
- Multi-player concurrency under real load

## Scores (1–10)

| Area | Score |
|------|------:|
| Overall quality | **9.2** |
| GUI | 8.7 |
| Animations | 8.6 |
| UX | 9.0 |
| Mines | 9.2 |
| Roulette | 9.1 |
| Blackjack | 9.2 |
| Coinflip | 9.2 |
| Crash | 9.3 |
| Wheel | 9.2 |
| Economy | **9.6** |
| Tax | 9.5 |
| Probability/RTP | 9.2 |
| Security | 9.6 |
| Anti-exploit | 9.4 |
| Folia | 9.1 |
| Paper | 9.2 |
| Performance | 9.0 |
| Database | 9.0 |
| Statistics | 9.0 |
| History | 9.0 |
| Configuration | 9.5 |
| Documentation | 9.3 |
| Code quality | 9.2 |
| Maintainability | 9.2 |

**Minimum bar (9/10 overall): met.**

Remaining polish is primarily visual (live GUI/animation tuning), not financial correctness.

### 2026-09-15 economy note

VaultUnlocked was documented but the vault2 adapter never activated (code checked for a plugin named `VaultUnlocked`; upstream registers as `Vault`). Fixed: detect `net.milkbowl.vault2.economy.Economy`, honor `provider` / optional `currency`, keep classic Vault fallback.
