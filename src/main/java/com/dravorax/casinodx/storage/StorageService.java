package com.dravorax.casinodx.storage;

import com.dravorax.casinodx.config.ConfigManager;
import com.dravorax.casinodx.game.GameType;
import com.dravorax.casinodx.history.HistoryEntry;
import com.dravorax.casinodx.money.Money;
import com.dravorax.casinodx.scheduler.PlatformScheduler;
import com.dravorax.casinodx.stats.PlayerStats;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Async JDBC storage for player stats, history, and metadata.
 */
public final class StorageService {

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final PlatformScheduler scheduler;
    private final Logger logger;
    private final Object sqliteLock = new Object();

    private StorageMode mode = StorageMode.SQLITE;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private BlockingQueue<Connection> mysqlPool;
    private volatile boolean initialized;

    public StorageService(JavaPlugin plugin, ConfigManager configs, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.configs = configs;
        this.scheduler = scheduler;
        this.logger = plugin.getLogger();
    }

    public void init() {
        readStorageConfig();
        scheduler.runAsync(() -> {
            try {
                if (mode == StorageMode.MYSQL) {
                    initMysqlPool();
                }
                withConnection(connection -> initSchema(connection));
                initialized = true;
                logger.info("[CasinoDX] Storage initialized (" + mode.name().toLowerCase(Locale.ROOT) + ").");
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "[CasinoDX] Storage initialization failed.", ex);
            }
        });
    }

    public void shutdown() {
        initialized = false;
        if (mysqlPool == null) {
            return;
        }
        scheduler.runAsync(() -> {
            Connection connection;
            while ((connection = mysqlPool.poll()) != null) {
                closeQuietly(connection);
            }
            mysqlPool = null;
        });
    }

    public void runAsync(SqlConsumer task) {
        scheduler.runAsync(() -> {
            if (!initialized) {
                return;
            }
            try {
                withConnection(task::accept);
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "[CasinoDX] Async storage task failed.", ex);
            }
        });
    }

    public void loadStats(UUID uuid, Consumer<Optional<PlayerStats>> callback) {
        runAsync(connection -> callback.accept(loadStatsSync(connection, uuid)));
    }

    public Optional<PlayerStats> loadStatsSync(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT games_played,wins,losses,pushes,total_wagered,total_winnings,total_losses_amount,"
                        + "net_profit,largest_win,largest_loss,highest_multiplier,tax_paid,favorite_game "
                        + "FROM player_stats WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                PlayerStats stats = new PlayerStats(uuid);
                stats.applyLoaded(
                        rs.getLong("games_played"),
                        rs.getLong("wins"),
                        rs.getLong("losses"),
                        rs.getLong("pushes"),
                        Money.of(rs.getBigDecimal("total_wagered")),
                        Money.of(rs.getBigDecimal("total_winnings")),
                        Money.of(rs.getBigDecimal("total_losses_amount")),
                        Money.of(rs.getBigDecimal("net_profit")),
                        Money.of(rs.getBigDecimal("largest_win")),
                        Money.of(rs.getBigDecimal("largest_loss")),
                        rs.getDouble("highest_multiplier"),
                        Money.of(rs.getBigDecimal("tax_paid")),
                        parseGameType(rs.getString("favorite_game"))
                );
                return Optional.of(stats);
            }
        }
    }

    public void saveStats(PlayerStats stats) {
        if (stats == null) {
            return;
        }
        runAsync(connection -> saveStatsSync(connection, stats));
    }

    public void saveStatsSync(Connection connection, PlayerStats stats) throws SQLException {
        String sql = mode == StorageMode.MYSQL ? MYSQL_UPSERT_STATS : SQLITE_UPSERT_STATS;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, stats.uuid().toString());
            ps.setLong(2, stats.gamesPlayed());
            ps.setLong(3, stats.wins());
            ps.setLong(4, stats.losses());
            ps.setLong(5, stats.pushes());
            setMoney(ps, 6, stats.totalWagered());
            setMoney(ps, 7, stats.totalWinnings());
            setMoney(ps, 8, stats.totalLossesAmount());
            setMoney(ps, 9, stats.netProfit());
            setMoney(ps, 10, stats.largestWin());
            setMoney(ps, 11, stats.largestLoss());
            ps.setDouble(12, stats.highestMultiplier());
            setMoney(ps, 13, stats.taxPaid());
            ps.setString(14, stats.favoriteGame() == null ? null : stats.favoriteGame().name());
            ps.setLong(15, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void insertHistory(UUID uuid, HistoryEntry entry) {
        if (entry == null) {
            return;
        }
        runAsync(connection -> insertHistorySync(connection, uuid, entry));
    }

    public long insertHistorySync(Connection connection, UUID uuid, HistoryEntry entry) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_history (uuid,game_type,bet,result,multiplier,gross,tax,net,timestamp) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, entry.game() == null ? null : entry.game().name());
            setMoney(ps, 3, entry.bet());
            ps.setString(4, entry.result().name());
            ps.setDouble(5, entry.multiplier());
            setMoney(ps, 6, entry.gross());
            setMoney(ps, 7, entry.tax());
            setMoney(ps, 8, entry.net());
            ps.setLong(9, entry.timestampMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1;
    }

    public void loadHistoryPage(UUID uuid, int page, int pageSize, Consumer<List<HistoryEntry>> callback) {
        runAsync(connection -> callback.accept(loadHistoryPageSync(connection, uuid, page, pageSize)));
    }

    public List<HistoryEntry> loadHistoryPageSync(Connection connection, UUID uuid, int page, int pageSize) throws SQLException {
        int offset = Math.max(0, page) * Math.max(1, pageSize);
        List<HistoryEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id,game_type,bet,result,multiplier,gross,tax,net,timestamp "
                        + "FROM player_history WHERE uuid=? ORDER BY timestamp DESC, id DESC LIMIT ? OFFSET ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(1, pageSize));
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new HistoryEntry(
                            rs.getLong("id"),
                            parseGameType(rs.getString("game_type")),
                            Money.of(rs.getBigDecimal("bet")),
                            HistoryEntry.Result.fromString(rs.getString("result")),
                            rs.getDouble("multiplier"),
                            Money.of(rs.getBigDecimal("gross")),
                            Money.of(rs.getBigDecimal("tax")),
                            Money.of(rs.getBigDecimal("net")),
                            rs.getLong("timestamp")
                    )); // detail derived from multiplier/tax
                }
            }
        }
        return entries;
    }

    public void countHistory(UUID uuid, Consumer<Long> callback) {
        runAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM player_history WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    callback.accept(rs.next() ? rs.getLong(1) : 0L);
                }
            }
        });
    }

    public void purgeHistoryOlderThan(long cutoffMillis) {
        runAsync(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM player_history WHERE timestamp < ?")) {
                ps.setLong(1, cutoffMillis);
                int removed = ps.executeUpdate();
                if (removed > 0) {
                    logger.info("[CasinoDX] Purged " + removed + " expired history row(s).");
                }
            }
        });
    }

    public void getMeta(UUID uuid, String key, Consumer<Optional<String>> callback) {
        runAsync(connection -> callback.accept(getMetaSync(connection, uuid, key)));
    }

    public Optional<String> getMetaSync(Connection connection, UUID uuid, String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT meta_value FROM player_meta WHERE uuid=? AND meta_key=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString("meta_value"));
                }
            }
        }
        return Optional.empty();
    }

    public void setMeta(UUID uuid, String key, String value) {
        runAsync(connection -> setMetaSync(connection, uuid, key, value));
    }

    public void setMetaSync(Connection connection, UUID uuid, String key, String value) throws SQLException {
        String sql = mode == StorageMode.MYSQL ? MYSQL_UPSERT_META : SQLITE_UPSERT_META;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            ps.setString(3, value);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void readStorageConfig() {
        YamlConfiguration main = configs.main();
        String type = main.getString("storage.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if ("mysql".equals(type)) {
            mode = StorageMode.MYSQL;
            String host = main.getString("storage.mysql.host", "127.0.0.1");
            int port = main.getInt("storage.mysql.port", 3306);
            String database = main.getString("storage.mysql.database", "casinodx");
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            jdbcUser = main.getString("storage.mysql.username", "root");
            jdbcPassword = main.getString("storage.mysql.password", "");
        } else {
            mode = StorageMode.SQLITE;
            File dataDir = new File(plugin.getDataFolder(), "data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            jdbcUrl = "jdbc:sqlite:" + new File(dataDir, "casinodx.db").getAbsolutePath();
        }
    }

    private void initMysqlPool() throws SQLException {
        int poolSize = Math.max(1, Math.min(3, configs.main().getInt("storage.mysql.pool-size", 2)));
        BlockingQueue<Connection> pool = new ArrayBlockingQueue<>(poolSize);
        for (int i = 0; i < poolSize; i++) {
            pool.offer(openConnection());
        }
        this.mysqlPool = pool;
    }

    private Connection openConnection() throws SQLException {
        if (mode == StorageMode.MYSQL) {
            return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
        }
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
        return connection;
    }

    private void withConnection(SqlConsumer consumer) throws SQLException {
        if (mode == StorageMode.SQLITE) {
            synchronized (sqliteLock) {
                try (Connection connection = openConnection()) {
                    consumer.accept(connection);
                }
            }
            return;
        }

        Connection connection = mysqlPool.poll();
        if (connection == null) {
            connection = openConnection();
        }
        try {
            if (connection.isClosed()) {
                connection = openConnection();
            }
            consumer.accept(connection);
        } finally {
            if (!mysqlPool.offer(connection)) {
                closeQuietly(connection);
            }
        }
    }

    private void initSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (mode == StorageMode.MYSQL) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                            uuid VARCHAR(36) PRIMARY KEY,
                            games_played BIGINT NOT NULL DEFAULT 0,
                            wins BIGINT NOT NULL DEFAULT 0,
                            losses BIGINT NOT NULL DEFAULT 0,
                            pushes BIGINT NOT NULL DEFAULT 0,
                            total_wagered DECIMAL(20,4) NOT NULL DEFAULT 0,
                            total_winnings DECIMAL(20,4) NOT NULL DEFAULT 0,
                            total_losses_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
                            net_profit DECIMAL(20,4) NOT NULL DEFAULT 0,
                            largest_win DECIMAL(20,4) NOT NULL DEFAULT 0,
                            largest_loss DECIMAL(20,4) NOT NULL DEFAULT 0,
                            highest_multiplier DOUBLE NOT NULL DEFAULT 0,
                            tax_paid DECIMAL(20,4) NOT NULL DEFAULT 0,
                            favorite_game VARCHAR(32),
                            updated_at BIGINT NOT NULL DEFAULT 0
                        )""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_history (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            uuid VARCHAR(36) NOT NULL,
                            game_type VARCHAR(32),
                            bet DECIMAL(20,4) NOT NULL DEFAULT 0,
                            result VARCHAR(16) NOT NULL,
                            multiplier DOUBLE NOT NULL DEFAULT 0,
                            gross DECIMAL(20,4) NOT NULL DEFAULT 0,
                            tax DECIMAL(20,4) NOT NULL DEFAULT 0,
                            net DECIMAL(20,4) NOT NULL DEFAULT 0,
                            timestamp BIGINT NOT NULL,
                            INDEX idx_player_history_uuid_ts (uuid, timestamp)
                        )""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_meta (
                            uuid VARCHAR(36) NOT NULL,
                            meta_key VARCHAR(64) NOT NULL,
                            meta_value TEXT,
                            updated_at BIGINT NOT NULL DEFAULT 0,
                            PRIMARY KEY (uuid, meta_key)
                        )""");
            } else {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_stats (
                            uuid TEXT PRIMARY KEY,
                            games_played INTEGER NOT NULL DEFAULT 0,
                            wins INTEGER NOT NULL DEFAULT 0,
                            losses INTEGER NOT NULL DEFAULT 0,
                            pushes INTEGER NOT NULL DEFAULT 0,
                            total_wagered DECIMAL(20,4) NOT NULL DEFAULT 0,
                            total_winnings DECIMAL(20,4) NOT NULL DEFAULT 0,
                            total_losses_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
                            net_profit DECIMAL(20,4) NOT NULL DEFAULT 0,
                            largest_win DECIMAL(20,4) NOT NULL DEFAULT 0,
                            largest_loss DECIMAL(20,4) NOT NULL DEFAULT 0,
                            highest_multiplier DOUBLE NOT NULL DEFAULT 0,
                            tax_paid DECIMAL(20,4) NOT NULL DEFAULT 0,
                            favorite_game TEXT,
                            updated_at INTEGER NOT NULL DEFAULT 0
                        )""");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_history (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            uuid TEXT NOT NULL,
                            game_type TEXT,
                            bet DECIMAL(20,4) NOT NULL DEFAULT 0,
                            result TEXT NOT NULL,
                            multiplier REAL NOT NULL DEFAULT 0,
                            gross DECIMAL(20,4) NOT NULL DEFAULT 0,
                            tax DECIMAL(20,4) NOT NULL DEFAULT 0,
                            net DECIMAL(20,4) NOT NULL DEFAULT 0,
                            timestamp INTEGER NOT NULL
                        )""");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_player_history_uuid_ts ON player_history(uuid, timestamp DESC)");
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_meta (
                            uuid TEXT NOT NULL,
                            meta_key TEXT NOT NULL,
                            meta_value TEXT,
                            updated_at INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY (uuid, meta_key)
                        )""");
            }
        }
    }

    private static void setMoney(PreparedStatement ps, int index, Money money) throws SQLException {
        ps.setBigDecimal(index, money == null ? BigDecimal.ZERO : money.toBigDecimal());
    }

    private static GameType parseGameType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GameType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GameType.fromModule(raw);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection connection) throws SQLException;
    }

    private enum StorageMode {
        SQLITE,
        MYSQL
    }

    private static final String SQLITE_UPSERT_STATS = """
            INSERT INTO player_stats (uuid,games_played,wins,losses,pushes,total_wagered,total_winnings,
            total_losses_amount,net_profit,largest_win,largest_loss,highest_multiplier,tax_paid,favorite_game,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(uuid) DO UPDATE SET
            games_played=excluded.games_played,wins=excluded.wins,losses=excluded.losses,pushes=excluded.pushes,
            total_wagered=excluded.total_wagered,total_winnings=excluded.total_winnings,
            total_losses_amount=excluded.total_losses_amount,net_profit=excluded.net_profit,
            largest_win=excluded.largest_win,largest_loss=excluded.largest_loss,
            highest_multiplier=excluded.highest_multiplier,tax_paid=excluded.tax_paid,
            favorite_game=excluded.favorite_game,updated_at=excluded.updated_at""";

    private static final String MYSQL_UPSERT_STATS = """
            INSERT INTO player_stats (uuid,games_played,wins,losses,pushes,total_wagered,total_winnings,
            total_losses_amount,net_profit,largest_win,largest_loss,highest_multiplier,tax_paid,favorite_game,updated_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
            games_played=VALUES(games_played),wins=VALUES(wins),losses=VALUES(losses),pushes=VALUES(pushes),
            total_wagered=VALUES(total_wagered),total_winnings=VALUES(total_winnings),
            total_losses_amount=VALUES(total_losses_amount),net_profit=VALUES(net_profit),
            largest_win=VALUES(largest_win),largest_loss=VALUES(largest_loss),
            highest_multiplier=VALUES(highest_multiplier),tax_paid=VALUES(tax_paid),
            favorite_game=VALUES(favorite_game),updated_at=VALUES(updated_at)""";

    private static final String SQLITE_UPSERT_META = """
            INSERT INTO player_meta (uuid,meta_key,meta_value,updated_at) VALUES (?,?,?,?)
            ON CONFLICT(uuid, meta_key) DO UPDATE SET meta_value=excluded.meta_value, updated_at=excluded.updated_at""";

    private static final String MYSQL_UPSERT_META = """
            INSERT INTO player_meta (uuid,meta_key,meta_value,updated_at) VALUES (?,?,?,?)
            ON DUPLICATE KEY UPDATE meta_value=VALUES(meta_value), updated_at=VALUES(updated_at)""";
}
