package dev.mememc.killstatistics;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SQLiteStorage {

    private final Connection connection;

    public SQLiteStorage(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        initializeSchema();
    }

    public void close() throws SQLException {
        connection.close();
    }

    public LoadedData loadAll() throws SQLException {
        Map<UUID, Integer> kills = new HashMap<>();
        Map<UUID, Integer> deaths = new HashMap<>();
        Map<UUID, Integer> streak = new HashMap<>();
        Map<UUID, Integer> bestStreak = new HashMap<>();
        Map<String, Deque<Long>> pairTimes = new HashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT player_uuid, kills, deaths, current_streak, best_streak FROM player_stats")) {
            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                kills.put(playerId, rs.getInt("kills"));
                deaths.put(playerId, rs.getInt("deaths"));
                streak.put(playerId, rs.getInt("current_streak"));
                bestStreak.put(playerId, rs.getInt("best_streak"));
            }
        }

        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT killer_uuid, victim_uuid, timestamp_ms FROM pair_kill_times ORDER BY timestamp_ms ASC")) {
            while (rs.next()) {
                String key = rs.getString("killer_uuid") + ":" + rs.getString("victim_uuid");
                pairTimes.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(rs.getLong("timestamp_ms"));
            }
        }

        return new LoadedData(kills, deaths, streak, bestStreak, pairTimes);
    }

    public void upsertPlayerStats(UUID playerId, int kills, int deaths, int currentStreak, int bestStreak) {
        String sql = "INSERT INTO player_stats(player_uuid, kills, deaths, current_streak, best_streak) VALUES(?, ?, ?, ?, ?) "
            + "ON CONFLICT(player_uuid) DO UPDATE SET kills = excluded.kills, deaths = excluded.deaths, "
            + "current_streak = excluded.current_streak, best_streak = excluded.best_streak";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, kills);
            ps.setInt(3, deaths);
            ps.setInt(4, currentStreak);
            ps.setInt(5, bestStreak);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void replacePairKills(UUID killerId, UUID victimId, Deque<Long> timestamps) {
        String delete = "DELETE FROM pair_kill_times WHERE killer_uuid = ? AND victim_uuid = ?";
        String insert = "INSERT INTO pair_kill_times(killer_uuid, victim_uuid, timestamp_ms) VALUES(?, ?, ?)";
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement deletePs = connection.prepareStatement(delete)) {
                deletePs.setString(1, killerId.toString());
                deletePs.setString(2, victimId.toString());
                deletePs.executeUpdate();
            }

            if (!timestamps.isEmpty()) {
                try (PreparedStatement insertPs = connection.prepareStatement(insert)) {
                    for (Long ts : timestamps) {
                        insertPs.setString(1, killerId.toString());
                        insertPs.setString(2, victimId.toString());
                        insertPs.setLong(3, ts);
                        insertPs.addBatch();
                    }
                    insertPs.executeBatch();
                }
            }
            connection.commit();
        } catch (SQLException ignored) {
            try {
                connection.rollback();
            } catch (SQLException ignoredRollback) {
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public void deletePlayer(UUID playerId) {
        String deleteStats = "DELETE FROM player_stats WHERE player_uuid = ?";
        String deletePairs = "DELETE FROM pair_kill_times WHERE killer_uuid = ? OR victim_uuid = ?";
        try (PreparedStatement statsPs = connection.prepareStatement(deleteStats);
             PreparedStatement pairPs = connection.prepareStatement(deletePairs)) {
            statsPs.setString(1, playerId.toString());
            statsPs.executeUpdate();

            pairPs.setString(1, playerId.toString());
            pairPs.setString(2, playerId.toString());
            pairPs.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void clearAll() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM player_stats");
            statement.executeUpdate("DELETE FROM pair_kill_times");
        } catch (SQLException ignored) {
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_stats (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "kills INTEGER NOT NULL DEFAULT 0," +
                    "deaths INTEGER NOT NULL DEFAULT 0," +
                    "current_streak INTEGER NOT NULL DEFAULT 0," +
                    "best_streak INTEGER NOT NULL DEFAULT 0" +
                    ")"
            );
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS pair_kill_times (" +
                    "killer_uuid TEXT NOT NULL," +
                    "victim_uuid TEXT NOT NULL," +
                    "timestamp_ms INTEGER NOT NULL" +
                    ")"
            );
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pair_kills ON pair_kill_times(killer_uuid, victim_uuid)");
        }
    }

    public record LoadedData(
        Map<UUID, Integer> kills,
        Map<UUID, Integer> deaths,
        Map<UUID, Integer> currentStreak,
        Map<UUID, Integer> bestStreak,
        Map<String, Deque<Long>> pairTimes
    ) {
    }
}
