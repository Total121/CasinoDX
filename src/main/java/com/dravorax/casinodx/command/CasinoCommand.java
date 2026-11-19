package com.dravorax.casinodx.command;

import com.dravorax.casinodx.CasinoDX;
import com.dravorax.casinodx.game.CasinoGame;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.gui.menu.HistoryGui;
import com.dravorax.casinodx.gui.menu.MainMenuGui;
import com.dravorax.casinodx.gui.menu.StatsGui;
import com.dravorax.casinodx.history.HistoryEntry;
import com.dravorax.casinodx.history.PagedHistory;
import com.dravorax.casinodx.stats.PlayerStats;
import com.dravorax.casinodx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /casino} and its subcommands.
 *
 * <p>Console can use the informational and administrative subcommands; anything that opens a menu
 * requires a player. Game shortcuts ({@code /casino mines}) route through
 * {@link CasinoGame#openLobby(Player)} so they honour the same enable and permission checks as the
 * main menu.
 */
public final class CasinoCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final List<String> SUBCOMMANDS =
            List.of("help", "stats", "history", "games", "reload");

    private final CasinoDX plugin;

    public CasinoCommand(CasinoDX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return openMainMenu(sender);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        // Game shortcut: /casino <game>
        GameType shortcut = GameType.fromModule(sub);
        if (shortcut != null) {
            return openGame(sender, shortcut);
        }

        return switch (sub) {
            case "help", "?" -> sendHelp(sender, label);
            case "stats" -> handleStats(sender, args);
            case "history" -> handleHistory(sender, args);
            case "games" -> handleGames(sender);
            case "reload" -> handleReload(sender);
            default -> {
                plugin.messages().send(sender, "unknown-command");
                yield true;
            }
        };
    }

    private boolean openMainMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("casinodx.use")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        plugin.guis().open(new MainMenuGui(plugin, player));
        return true;
    }

    private boolean openGame(CommandSender sender, GameType type) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("casinodx.use")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        CasinoGame game = plugin.games().get(type);
        if (game == null) {
            plugin.messages().send(player, "game-disabled");
            return true;
        }
        // openLobby performs its own enabled and permission checks and messages the player.
        game.openLobby(player);
        return true;
    }

    private boolean sendHelp(CommandSender sender, String label) {
        plugin.messages().sendLiteral(sender, "&6&lCasinoDX &7» &fCommands");
        line(sender, "/" + label, "Open the casino menu");
        line(sender, "/" + label + " <game>", "Jump straight into a game");
        line(sender, "/" + label + " stats [player]", "View casino statistics");
        line(sender, "/" + label + " history [page]", "View your recent rounds");
        line(sender, "/" + label + " games", "List the available games");
        if (sender.hasPermission("casinodx.admin.reload")) {
            line(sender, "/" + label + " reload", "Reload configuration");
        }
        return true;
    }

    private void line(CommandSender sender, String usage, String description) {
        sender.sendMessage(Text.color("&e" + usage + " &8- &7" + description));
    }

    private boolean handleStats(CommandSender sender, String[] args) {
        // No target: players get the menu, console gets text.
        if (args.length < 2) {
            if (sender instanceof Player player) {
                if (!player.hasPermission("casinodx.use")) {
                    plugin.messages().send(player, "no-permission");
                    return true;
                }
                plugin.guis().open(new StatsGui(plugin, player));
                return true;
            }
            plugin.messages().send(sender, "players-only");
            return true;
        }

        if (!sender.hasPermission("casinodx.admin.stats")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        UUID target = resolvePlayer(sender, args[1]);
        if (target == null) {
            return true;
        }
        printStats(sender, args[1], plugin.statistics().getStats(target));
        return true;
    }

    private void printStats(CommandSender sender, String name, PlayerStats stats) {
        plugin.messages().send(sender, "stats-header", "player", name);
        sender.sendMessage(Text.color("&7Rounds played: &f" + stats.gamesPlayed()
                + " &7(&a" + stats.wins() + "W &c" + stats.losses() + "L &e" + stats.pushes() + "P&7)"));
        sender.sendMessage(Text.color(String.format(Locale.US, "&7Win rate: &f%.1f%%", stats.winRate())));
        sender.sendMessage(Text.color("&7Wagered: &f" + plugin.economy().format(stats.totalWagered())));
        sender.sendMessage(Text.color("&7Net profit: &f" + plugin.economy().format(stats.netProfit())));
        sender.sendMessage(Text.color("&7Largest win: &a" + plugin.economy().format(stats.largestWin())));
        sender.sendMessage(Text.color("&7Tax paid: &f" + plugin.economy().format(stats.taxPaid())));

        GameType favourite = stats.favoriteGame();
        if (favourite != null) {
            sender.sendMessage(Text.color("&7Favourite game: &f" + favourite.displayName()));
        }
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("casinodx.use")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.guis().open(new HistoryGui(plugin, player));
            return true;
        }

        // Pages are 1-based on the command line and 0-based internally.
        int page = 1;
        try {
            page = Math.max(1, Integer.parseInt(args[1].trim()));
        } catch (NumberFormatException ignored) {
            // Fall through to page 1.
        }
        printHistory(player, page);
        return true;
    }

    private void printHistory(Player player, int page) {
        PagedHistory history = plugin.history().getHistory(player.getUniqueId(), page - 1);
        if (history.entries().isEmpty()) {
            plugin.messages().send(player, "history-empty");
            return;
        }
        plugin.messages().send(player, "history-header", "page", page + "/" + history.totalPages());
        for (HistoryEntry entry : history.entries()) {
            String colour = switch (entry.result()) {
                case WIN -> "&a";
                case PUSH -> "&e";
                case LOSS -> "&c";
            };
            String amount = entry.result() == HistoryEntry.Result.LOSS
                    ? "-" + plugin.economy().format(entry.bet())
                    : "+" + plugin.economy().format(entry.net());
            player.sendMessage(Text.color("&8" + TIME_FMT.format(entry.timestamp())
                    + " &7" + entry.game().displayName() + " " + colour + amount
                    + " &8(" + Text.formatMultiplier(entry.multiplier()) + ")"));
        }
    }

    private boolean handleGames(CommandSender sender) {
        plugin.messages().sendLiteral(sender, "&6&lCasinoDX &7» &fGames");
        for (GameType type : GameType.values()) {
            CasinoGame game = plugin.games().get(type);
            boolean enabled = game != null && game.isEnabled();
            boolean allowed = game != null && sender.hasPermission(game.permission());
            String state = !enabled ? "&8disabled" : (allowed ? "&aavailable" : "&cno permission");
            String limits = enabled && game != null
                    ? " &8[" + plugin.economy().format(game.minBet())
                            + " - " + plugin.economy().format(game.maxBet()) + "]"
                    : "";
            sender.sendMessage(Text.color("&e" + type.displayName() + " &8- " + state + limits));
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("casinodx.admin.reload")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        // reloadSafe refuses to run while rounds are live so no wager can be stranded.
        if (plugin.sessions().hasActiveSessions()) {
            plugin.messages().send(sender, "reload-busy");
            return true;
        }
        boolean ok = plugin.reloadSafe();
        plugin.messages().send(sender, ok ? "reload-success" : "reload-failed");
        return true;
    }

    /** Resolves an online player first, then a known offline player. */
    private UUID resolvePlayer(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null) {
            return offline.getUniqueId();
        }
        plugin.messages().send(sender, "player-not-found", "player", name);
        return null;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if ("reload".equals(sub) && !sender.hasPermission("casinodx.admin.reload")) {
                    continue;
                }
                options.add(sub);
            }
            for (GameType type : GameType.values()) {
                CasinoGame game = plugin.games().get(type);
                if (game != null && game.isEnabled() && sender.hasPermission(game.permission())) {
                    options.add(type.moduleName());
                }
            }
            return filter(options, args[0]);
        }

        if (args.length == 2 && "stats".equalsIgnoreCase(args[0])
                && sender.hasPermission("casinodx.admin.stats")) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return filter(names, args[1]);
        }

        if (args.length == 2 && "history".equalsIgnoreCase(args[0])) {
            return filter(List.of("1", "2", "3"), args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
