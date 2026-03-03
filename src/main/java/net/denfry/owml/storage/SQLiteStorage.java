package net.denfry.owml.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.denfry.owml.OverWatchML;
import net.denfry.owml.utils.MessageManager;

/**
 * SQLite database storage implementation.
 * Provides fast, reliable data storage with better performance than YAML files.
 *
 * @author OverWatch Team
 * @version 1.0.0
 * @since 1.8.1
 */
public class SQLiteStorage implements DataStorage {

    private final OverWatchML plugin;
    private HikariDataSource dataSource;

    public SQLiteStorage(OverWatchML plugin) {
        this.plugin = plugin;
    }
    private final Map<String, PreparedStatement> statementCache = new ConcurrentHashMap<>();
    private long queryCount = 0;
    private long totalQueryTime = 0;

    @Override
    public void initialize() throws StorageException {
        try {
            File databaseFile = new File(plugin.getDataFolder(), "OverWatch.db");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);

            createTables();
            MessageManager.log("info", "SQLite storage initialized: {PATH}", "PATH", databaseFile.getAbsolutePath());

        } catch (Exception e) {
            throw new StorageException("Failed to initialize SQLite storage: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null) {
            // Close cached statements
            statementCache.values().forEach(stmt -> {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    MessageManager.log("warning", "Failed to close prepared statement: {ERROR}", "ERROR", e.getMessage());
                }
            });
            statementCache.clear();

            dataSource.close();
            MessageManager.log("info", "SQLite storage shut down");
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    @Override
    public void savePlayerStats(@NotNull UUID playerId, @NotNull Map<String, Object> stats) {
        String sql = "INSERT OR REPLACE INTO player_stats (player_uuid, stat_key, stat_value) VALUES (?, ?, ?)";
        executeBatch(sql, playerId, stats);
    }

    @Override
    @Nullable
    public Map<String, Object> loadPlayerStats(@NotNull UUID playerId) {
        String sql = "SELECT stat_key, stat_value FROM player_stats WHERE player_uuid = ?";
        Map<String, Object> stats = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            long startTime = System.nanoTime();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("stat_key"), rs.getObject("stat_value"));
                }
            }

            recordQueryTime(System.nanoTime() - startTime);

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to load player stats for {UUID}: {ERROR}",
                "UUID", playerId.toString(), "ERROR", e.getMessage());
        }

        return stats.isEmpty() ? null : stats;
    }

    @Override
    public void saveSuspiciousData(@NotNull UUID playerId, int suspicionLevel, long lastActivity) {
        String sql = """
            INSERT OR REPLACE INTO suspicious_players
            (player_uuid, suspicion_level, last_activity, total_alerts, first_detected)
            VALUES (?, ?, ?, COALESCE((SELECT total_alerts + 1 FROM suspicious_players WHERE player_uuid = ?), 1),
                   COALESCE((SELECT first_detected FROM suspicious_players WHERE player_uuid = ?), ?))
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = prepareStatement(sql)) {

            long currentTime = System.currentTimeMillis();
            stmt.setString(1, playerId.toString());
            stmt.setInt(2, suspicionLevel);
            stmt.setLong(3, lastActivity);
            stmt.setString(4, playerId.toString());
            stmt.setString(5, playerId.toString());
            stmt.setLong(6, currentTime);

            long startTime = System.nanoTime();
            stmt.executeUpdate();
            recordQueryTime(System.nanoTime() - startTime);

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to save suspicious data for {UUID}: {ERROR}",
                "UUID", playerId.toString(), "ERROR", e.getMessage());
        }
    }

    @Override
    @Nullable
    public SuspiciousData loadSuspiciousData(@NotNull UUID playerId) {
        String sql = "SELECT suspicion_level, last_activity, total_alerts, first_detected FROM suspicious_players WHERE player_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            long startTime = System.nanoTime();

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    recordQueryTime(System.nanoTime() - startTime);
                    return new SuspiciousData(
                        rs.getInt("suspicion_level"),
                        rs.getLong("last_activity"),
                        rs.getInt("total_alerts"),
                        rs.getLong("first_detected")
                    );
                }
            }

            recordQueryTime(System.nanoTime() - startTime);

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to load suspicious data for {UUID}: {ERROR}",
                "UUID", playerId.toString(), "ERROR", e.getMessage());
        }

        return null;
    }

    @Override
    @NotNull
    public Map<UUID, SuspiciousData> getAllSuspiciousPlayers() {
        String sql = "SELECT player_uuid, suspicion_level, last_activity, total_alerts, first_detected FROM suspicious_players";
        Map<UUID, SuspiciousData> suspiciousPlayers = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            long startTime = System.nanoTime();

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                SuspiciousData data = new SuspiciousData(
                    rs.getInt("suspicion_level"),
                    rs.getLong("last_activity"),
                    rs.getInt("total_alerts"),
                    rs.getLong("first_detected")
                );
                suspiciousPlayers.put(playerId, data);
            }

            recordQueryTime(System.nanoTime() - startTime);

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to load all suspicious players: {ERROR}", "ERROR", e.getMessage());
        }

        return suspiciousPlayers;
    }

    @Override
    public void savePunishmentData(@NotNull UUID playerId, @NotNull Map<String, Object> punishmentData) {
        String sql = "INSERT OR REPLACE INTO punishment_data (player_uuid, data_key, data_value) VALUES (?, ?, ?)";
        executeBatch(sql, playerId, punishmentData);
    }

    @Override
    @Nullable
    public Map<String, Object> loadPunishmentData(@NotNull UUID playerId) {
        String sql = "SELECT data_key, data_value FROM punishment_data WHERE player_uuid = ?";
        Map<String, Object> data = new HashMap<>();

        try (Connection conn = getConnection();
             PreparedStatement stmt = prepareStatement(sql)) {

            stmt.setString(1, playerId.toString());
            long startTime = System.nanoTime();

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    data.put(rs.getString("data_key"), rs.getObject("data_value"));
                }
            }

            recordQueryTime(System.nanoTime() - startTime);

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to load punishment data for {UUID}: {ERROR}",
                "UUID", playerId.toString(), "ERROR", e.getMessage());
        }

        return data.isEmpty() ? null : data;
    }

    @Override
    public void deletePlayerData(@NotNull UUID playerId) {
        String[] tables = {"player_stats", "suspicious_players", "punishment_data"};

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            for (String table : tables) {
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE player_uuid = ?")) {
                    stmt.setString(1, playerId.toString());
                    stmt.executeUpdate();
                }
            }

            conn.commit();
            MessageManager.log("info", "Deleted all data for player {UUID}", "UUID", playerId.toString());

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to delete player data for {UUID}: {ERROR}",
                "UUID", playerId.toString(), "ERROR", e.getMessage());
        }
    }

    @Override
    @NotNull
    public StorageStats getStats() {
        long totalPlayers = 0;
        long suspiciousPlayers = 0;
        long totalRecords = 0;
        long storageSize = 0;

        try (Connection conn = getConnection()) {
            // Count total players (unique UUIDs across all tables)
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT player_uuid) FROM (" +
                    "SELECT player_uuid FROM player_stats UNION " +
                    "SELECT player_uuid FROM suspicious_players UNION " +
                    "SELECT player_uuid FROM punishment_data)")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalPlayers = rs.getLong(1);
                    }
                }
            }

            // Count suspicious players
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM suspicious_players")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        suspiciousPlayers = rs.getLong(1);
                    }
                }
            }

            // Count total records
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT " +
                    "(SELECT COUNT(*) FROM player_stats) + " +
                    "(SELECT COUNT(*) FROM suspicious_players) + " +
                    "(SELECT COUNT(*) FROM punishment_data)")) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalRecords = rs.getLong(1);
                    }
                }
            }

            // Get database file size
            File dbFile = new File(plugin.getDataFolder(), "OverWatch.db");
            if (dbFile.exists()) {
                storageSize = dbFile.length();
            }

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to get storage stats: {ERROR}", "ERROR", e.getMessage());
        }

        double avgQueryTime = queryCount > 0 ? (double) totalQueryTime / queryCount / 1_000_000 : 0.0;

        return new StorageStats(totalPlayers, suspiciousPlayers, totalRecords, storageSize, avgQueryTime, "SQLite");
    }

    @Override
    public void saveAll() {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            
            // This is a placeholder for actual batch saving logic if needed
            // Currently, individual save methods are called, but they should be optimized.
            
            conn.commit();
        } catch (SQLException e) {
            MessageManager.log("error", "Failed to save all data: {ERROR}", "ERROR", e.getMessage());
        }
    }

    @Override
    public void performMaintenance() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Optimize database
            stmt.execute("VACUUM");
            stmt.execute("ANALYZE");

            // Clear statement cache to prevent memory leaks
            statementCache.values().forEach(cachedStmt -> {
                try {
                    cachedStmt.close();
                } catch (SQLException e) {
                    MessageManager.log("warning", "Failed to close cached statement: {ERROR}", "ERROR", e.getMessage());
                }
            });
            statementCache.clear();

            MessageManager.log("info", "SQLite maintenance completed - database optimized and cache cleared");

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to perform SQLite maintenance: {ERROR}", "ERROR", e.getMessage());
        }
    }

    /**
     * Create database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Player statistics table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_uuid TEXT NOT NULL,
                    stat_key TEXT NOT NULL,
                    stat_value TEXT,
                    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
                    PRIMARY KEY (player_uuid, stat_key)
                )
                """);

            // Suspicious players table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS suspicious_players (
                    player_uuid TEXT PRIMARY KEY,
                    suspicion_level INTEGER NOT NULL DEFAULT 0,
                    last_activity INTEGER NOT NULL DEFAULT 0,
                    total_alerts INTEGER NOT NULL DEFAULT 0,
                    first_detected INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
                    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
                )
                """);

            // Punishment data table
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS punishment_data (
                    player_uuid TEXT NOT NULL,
                    data_key TEXT NOT NULL,
                    data_value TEXT,
                    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
                    PRIMARY KEY (player_uuid, data_key)
                )
                """);

            // Create indexes for better performance
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_stats_uuid ON player_stats(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_suspicious_level ON suspicious_players(suspicion_level)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_punishment_uuid ON punishment_data(player_uuid)");

            MessageManager.log("info", "Database tables created/verified");
        }
    }

    /**
     * Get database connection from pool.
     */
    private Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * Get cached prepared statement or create new one.
     */
    private PreparedStatement prepareStatement(String sql) throws SQLException {
        PreparedStatement stmt = statementCache.get(sql);
        if (stmt == null || stmt.isClosed()) {
            stmt = getConnection().prepareStatement(sql);
            statementCache.put(sql, stmt);
        }
        return stmt;
    }

    /**
     * Execute batch insert/update operation.
     */
    private void executeBatch(String sql, UUID playerId, Map<String, Object> data) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            long startTime = System.nanoTime();

            for (Map.Entry<String, Object> entry : data.entrySet()) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue().toString());
                stmt.addBatch();
            }

            stmt.executeBatch();
            recordQueryTime(System.nanoTime() - startTime);

        } catch (SQLException e) {
            MessageManager.log("error", "Failed to execute batch operation for {UUID}: {ERROR}",
                "UUID", playerId.toString(), "ERROR", e.getMessage());
        }
    }

    /**
     * Record query execution time for performance monitoring.
     */
    private void recordQueryTime(long nanoTime) {
        queryCount++;
        totalQueryTime += nanoTime;
    }
}
