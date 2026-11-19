package com.dravorax.casinodx.util;

import com.dravorax.casinodx.CasinoDX;
import org.bukkit.Bukkit;

/**
 * True-color ANSI startup banner printed once during plugin enable.
 */
public final class ConsoleBanner {

    private ConsoleBanner() {}

    public static void print(CasinoDX plugin) {
        if (plugin == null) {
            return;
        }

        final String reset = "\u001B[0m";
        final String bold = "\u001B[1m";
        final String dim = "\u001B[2m";
        final String gold = rgb(255, 215, 0);
        final String amber = rgb(255, 180, 50);
        final String purple = rgb(170, 90, 255);
        final String magenta = rgb(220, 80, 200);
        final String frame = rgb(120, 90, 180);
        final String gray = rgb(170, 175, 180);
        final String darkGray = rgb(110, 115, 120);
        final String green = rgb(120, 230, 120);
        final String red = rgb(255, 110, 110);
        final String white = rgb(245, 245, 245);

        final int width = 62;
        var out = System.out;

        String version = plugin.getDescription().getVersion();
        boolean folia = plugin.scheduler().isFolia();
        String platform = folia ? "Folia" : "Paper";
        int gameCount = plugin.games() == null ? 0 : plugin.games().enabledTypes().size();
        boolean economyUp = plugin.economy() != null && plugin.economy().isAvailable();
        String economyName = plugin.economy() == null ? "none" : plugin.economy().providerName();
        String database = plugin.configs().main().getString("storage.type", "sqlite");
        if (database == null || database.isBlank()) {
            database = "sqlite";
        }

        out.println(frame + "╭" + repeat("─", width - 2) + "╮" + reset);
        printCentered(out, frame, width, " ");
        printCentered(out, frame, width, bold + gold + " ██████╗ █████╗ ███████╗██╗███╗   ██╗ ██████╗ " + reset);
        printCentered(out, frame, width, bold + amber + "██╔════╝██╔══██╗██╔════╝██║████╗  ██║██╔═══██╗" + reset);
        printCentered(out, frame, width, bold + amber + "██║     ███████║███████╗██║██╔██╗ ██║██║   ██║" + reset);
        printCentered(out, frame, width, bold + purple + "██║     ██╔══██║╚════██║██║██║╚██╗██║██║   ██║" + reset);
        printCentered(out, frame, width, bold + magenta + "╚██████╗██║  ██║███████║██║██║ ╚████║╚██████╔╝" + reset);
        printCentered(out, frame, width, bold + magenta + " ╚═════╝╚═╝  ╚═╝╚══════╝╚═╝╚═╝  ╚═══╝ ╚═════╝ " + reset);
        printCentered(out, frame, width, " ");
        printCentered(out, frame, width,
                bold + white + "C A S I N O   D X" + reset + dim + gray + "  v" + version + "  by DravoraX" + reset);
        printCentered(out, frame, width, dim + darkGray + "Premium Minecraft Casino System" + reset);
        printCentered(out, frame, width, " ");
        out.println(frame + "├" + repeat("─", width - 2) + "┤" + reset);

        printRow(out, frame, width, gray + " 🎰 Platform ", white + platform + darkGray + " · " + Bukkit.getVersion(), reset);
        printRow(out, frame, width, gray + " 🎲 Games    ",
                white + gameCount + darkGray + " enabled", reset);
        printRow(out, frame, width, gray + " 💰 Economy  ",
                (economyUp ? green : red) + economyName + reset, reset);
        printRow(out, frame, width, gray + " 🗄  Database ",
                white + database.toUpperCase(), reset);
        printRow(out, frame, width, gray + " ✓  Status   ", green + bold + "Ready", reset);

        out.println(frame + "╰" + repeat("─", width - 2) + "╯" + reset);
    }

    private static String rgb(int r, int g, int b) {
        return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
    }

    private static String repeat(String value, int count) {
        StringBuilder sb = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private static void printCentered(java.io.PrintStream out, String frame, int width, String content) {
        int visible = visibleLength(content);
        int inner = width - 2;
        int leftPad = Math.max(0, (inner - visible) / 2);
        int rightPad = Math.max(0, inner - visible - leftPad);
        out.println(frame + "│" + repeat(" ", leftPad) + content + "\u001B[0m" + repeat(" ", rightPad) + frame + "│" + "\u001B[0m");
    }

    private static void printRow(java.io.PrintStream out, String frame, int width, String label, String value, String reset) {
        String content = label + value;
        int inner = width - 2;
        int visible = visibleLength(content);
        int padRight = Math.max(0, inner - visible);
        out.println(frame + "│" + content + reset + repeat(" ", padRight) + frame + "│" + reset);
    }

    private static int visibleLength(String value) {
        int length = 0;
        boolean inEscape = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inEscape) {
                if (c == 'm') {
                    inEscape = false;
                }
                continue;
            }
            if (c == '\u001B') {
                inEscape = true;
                continue;
            }
            length++;
        }
        return length;
    }
}
