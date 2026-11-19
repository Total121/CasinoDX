# Folia Support

CasinoDX fully supports **Folia** (region-threaded Paper fork) in addition to standard Paper.

---

## What works

- All six games and GUIs
- Economy transactions via Vault / VaultUnlocked
- SQLite and MySQL storage (async I/O off region threads)
- Animations scheduled through `PlatformScheduler`, which detects Folia at runtime
- `/casino reload`, stats, and history

The plugin declares `folia-supported: true` in `plugin.yml`.

---

## How scheduling works

CasinoDX uses a small abstraction (`PlatformScheduler`) that:

1. Detects whether the server is Folia or Paper
2. On **Folia**, schedules tasks on the correct region thread for each player
3. On **Paper**, uses the standard Bukkit scheduler

You do not need to configure anything for Folia — install the jar and run.

---

## Recommendations

| Topic | Guidance |
|-------|----------|
| Database | Prefer MySQL for multi-server networks; SQLite is fine on single Folia instances |
| Reload | Avoid `/casino reload` during peak play; wait until no active sessions |
| Economy | Ensure your economy plugin is Folia-compatible |

---

## Troubleshooting on Folia

- **Tasks not running:** Check console for scheduler errors; ensure you use a Folia build of Paper API 1.21.4+
- **Player stuck in session:** Disconnect and reconnect; session cleanup runs on quit
- **Cross-region issues:** CasinoDX binds sessions to the opening player; do not share GUIs across players on different regions simultaneously

For general issues, enable `debug: true` in `config.yml` and inspect console output.
