package dev.mememc.killstatistics;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillStatisticsService {

    private final long windowMillis;
    private final int maxKillsPerPairPerWindow;
    private final SQLiteStorage storage;
    private final Map<UUID, Integer> totalKillsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> totalDeathsByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> currentStreakByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bestStreakByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> pairKillTimes = new ConcurrentHashMap<>();

    public KillStatisticsService(int windowMinutes, int maxKillsPerPairPerWindow, SQLiteStorage storage, SQLiteStorage.LoadedData loadedData) {
        int safeWindow = Math.max(1, windowMinutes);
        int safeMax = Math.max(1, maxKillsPerPairPerWindow);
        this.windowMillis = Duration.ofMinutes(safeWindow).toMillis();
        this.maxKillsPerPairPerWindow = safeMax;
        this.storage = storage;

        totalKillsByPlayer.putAll(loadedData.kills());
        totalDeathsByPlayer.putAll(loadedData.deaths());
        currentStreakByPlayer.putAll(loadedData.currentStreak());
        bestStreakByPlayer.putAll(loadedData.bestStreak());
        pairKillTimes.putAll(loadedData.pairTimes());
    }

    public synchronized boolean recordKill(UUID killerId, UUID victimId) {
        if (killerId == null || victimId == null) {
            return false;
        }

        String pairKey = killerId + ":" + victimId;
        long now = System.currentTimeMillis();

        Deque<Long> killsInWindow = pairKillTimes.computeIfAbsent(pairKey, key -> new ArrayDeque<>());
        pruneOld(killsInWindow, now);
        if (killsInWindow.size() >= maxKillsPerPairPerWindow) {
            return false;
        }

        killsInWindow.addLast(now);
        totalKillsByPlayer.merge(killerId, 1, Integer::sum);
        int streak = currentStreakByPlayer.merge(killerId, 1, Integer::sum);
        bestStreakByPlayer.merge(killerId, streak, Math::max);
        currentStreakByPlayer.put(victimId, 0);

        persistPlayer(killerId);
        persistPlayer(victimId);
        storage.replacePairKills(killerId, victimId, new ArrayDeque<>(killsInWindow));
        return true;
    }

    public synchronized void recordDeath(UUID victimId) {
        if (victimId == null) {
            return;
        }
        totalDeathsByPlayer.merge(victimId, 1, Integer::sum);
        currentStreakByPlayer.put(victimId, 0);
        persistPlayer(victimId);
    }

    public int getKills(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return totalKillsByPlayer.getOrDefault(playerId, 0);
    }

    public int getDeaths(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return totalDeathsByPlayer.getOrDefault(playerId, 0);
    }

    public int getCurrentStreak(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return currentStreakByPlayer.getOrDefault(playerId, 0);
    }

    public int getBestStreak(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        return bestStreakByPlayer.getOrDefault(playerId, 0);
    }

    public double getKd(UUID playerId) {
        int kills = getKills(playerId);
        int deaths = getDeaths(playerId);
        if (deaths <= 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    public synchronized void resetAll() {
        totalKillsByPlayer.clear();
        totalDeathsByPlayer.clear();
        currentStreakByPlayer.clear();
        bestStreakByPlayer.clear();
        pairKillTimes.clear();
        storage.clearAll();
    }

    public synchronized void resetPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        totalKillsByPlayer.remove(playerId);
        totalDeathsByPlayer.remove(playerId);
        currentStreakByPlayer.remove(playerId);
        bestStreakByPlayer.remove(playerId);
        pairKillTimes.keySet().removeIf(key -> key.startsWith(playerId.toString() + ":") || key.endsWith(":" + playerId));
        storage.deletePlayer(playerId);
    }

    public synchronized int resetPlayers(Iterable<UUID> playerIds) {
        int count = 0;
        for (UUID playerId : playerIds) {
            if (playerId == null) {
                continue;
            }
            totalKillsByPlayer.remove(playerId);
            totalDeathsByPlayer.remove(playerId);
            currentStreakByPlayer.remove(playerId);
            bestStreakByPlayer.remove(playerId);
            pairKillTimes.keySet().removeIf(key -> key.startsWith(playerId.toString() + ":") || key.endsWith(":" + playerId));
            storage.deletePlayer(playerId);
            count++;
        }
        return count;
    }

    public synchronized int addKills(UUID playerId, int amount) {
        if (playerId == null || amount == 0) {
            return getKills(playerId);
        }
        int updated = Math.max(0, getKills(playerId) + amount);
        totalKillsByPlayer.put(playerId, updated);
        persistPlayer(playerId);
        return updated;
    }

    public List<UUID> getTopKillers(int limit) {
        int safeLimit = Math.max(1, limit);
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(totalKillsByPlayer.entrySet());
        entries.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()));
        List<UUID> top = new ArrayList<>();
        for (int i = 0; i < Math.min(safeLimit, entries.size()); i++) {
            top.add(entries.get(i).getKey());
        }
        return top;
    }

    public List<Map.Entry<UUID, Integer>> getTopKillEntries(int limit) {
        int safeLimit = Math.max(1, limit);
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(totalKillsByPlayer.entrySet());
        entries.sort(Map.Entry.<UUID, Integer>comparingByValue(Comparator.reverseOrder()));
        if (entries.size() <= safeLimit) {
            return entries;
        }
        return new ArrayList<>(entries.subList(0, safeLimit));
    }

    public Map<UUID, Integer> getKillMapSnapshot() {
        return new ConcurrentHashMap<>(totalKillsByPlayer);
    }

    private void pruneOld(Deque<Long> timestamps, long now) {
        long threshold = now - windowMillis;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < threshold) {
            timestamps.removeFirst();
        }
    }

    private void persistPlayer(UUID playerId) {
        storage.upsertPlayerStats(
            playerId,
            getKills(playerId),
            getDeaths(playerId),
            getCurrentStreak(playerId),
            getBestStreak(playerId)
        );
    }
}
