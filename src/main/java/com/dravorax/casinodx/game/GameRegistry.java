package com.dravorax.casinodx.game;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registry of the installed casino games, keyed by {@link GameType}. */
public final class GameRegistry {

    private final Map<GameType, CasinoGame> games = new EnumMap<>(GameType.class);

    public void register(CasinoGame game) {
        if (game != null) {
            games.put(game.type(), game);
        }
    }

    public CasinoGame get(GameType type) {
        return type == null ? null : games.get(type);
    }

    public Collection<CasinoGame> all() {
        return Collections.unmodifiableCollection(games.values());
    }

    /** Games that are currently enabled, in declaration order. */
    public List<CasinoGame> enabledGames() {
        List<CasinoGame> enabled = new ArrayList<>();
        for (GameType type : GameType.values()) {
            CasinoGame game = games.get(type);
            if (game != null && game.isEnabled()) {
                enabled.add(game);
            }
        }
        return enabled;
    }

    public List<GameType> enabledTypes() {
        List<GameType> enabled = new ArrayList<>();
        for (CasinoGame game : enabledGames()) {
            enabled.add(game.type());
        }
        return enabled;
    }

    public boolean isEnabled(GameType type) {
        CasinoGame game = get(type);
        return game != null && game.isEnabled();
    }

    /** Opens a game's entry screen, letting the game itself enforce permission and enablement. */
    public void openLobby(Player player, GameType type) {
        if (player == null || type == null) {
            return;
        }
        CasinoGame game = games.get(type);
        if (game == null) {
            return;
        }
        game.openLobby(player);
    }

    public void reloadAll() {
        for (CasinoGame game : games.values()) {
            game.reload();
        }
    }
}
