package com.dravorax.casinodx.game.wheel;

import com.dravorax.casinodx.money.Money;
import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * One section of the wheel, loaded from {@code modules/wheel/rewards.yml}.
 *
 * <p>{@code weight} is a relative chance, not a percentage: the drawing code normalises the whole
 * set, so operators can write 70/25/5 or 7/2.5/0.5 and get the same wheel. That avoids the classic
 * misconfiguration where chances do not add to 100 and the last entry silently absorbs the
 * remainder.
 */
public record WheelReward(
        String id,
        String displayName,
        Type type,
        Material icon,
        double weight,
        Money money,
        double multiplier,
        List<String> commands,
        List<String> items,
        boolean broadcast
) {

    public enum Type {
        /** A flat cash reward. */
        MONEY,
        /** A multiple of the spin cost (or of the configured base amount for free spins). */
        MULTIPLIER,
        /** Items given to the player. */
        ITEMS,
        /** Console commands run with {@code {player}} substituted. */
        COMMAND,
        /** A deliberate blank section. */
        NOTHING;

        public static Type fromString(String raw) {
            if (raw == null) {
                return MONEY;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "multiplier" -> MULTIPLIER;
                case "items", "item" -> ITEMS;
                case "command", "commands" -> COMMAND;
                case "nothing", "none", "blank" -> NOTHING;
                default -> MONEY;
            };
        }
    }

    public WheelReward {
        id = id == null ? "section" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        type = type == null ? Type.MONEY : type;
        icon = icon == null ? Material.PAPER : icon;
        weight = weight > 0 ? weight : 1.0;
        money = money == null ? Money.ZERO : money;
        commands = commands == null ? List.of() : List.copyOf(commands);
        items = items == null ? List.of() : List.copyOf(items);
    }

    public boolean isNothing() {
        return type == Type.NOTHING;
    }

    /** Single-line description used in the wheel GUI and the broadcast message. */
    public String summary() {
        return switch (type) {
            case MONEY -> displayName;
            case MULTIPLIER -> displayName + " (" + multiplier + "x)";
            case ITEMS -> displayName + " (" + items.size() + " item(s))";
            case COMMAND -> displayName;
            case NOTHING -> displayName;
        };
    }
}
