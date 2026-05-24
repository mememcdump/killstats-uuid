package dev.mememc.killstatistics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KillsPlaceholderExpansion extends PlaceholderExpansion {

    private final KillStatisticsPlugin plugin;
    private final KillStatisticsService statisticsService;

    public KillsPlaceholderExpansion(KillStatisticsPlugin plugin, KillStatisticsService statisticsService) {
        this.plugin = plugin;
        this.statisticsService = statisticsService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "kills";
    }

    @Override
    public @NotNull String getAuthor() {
        return "mememc";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "0";
        }
        return resolvePlaceholder(player.getUniqueId(), params);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "0";
        }
        return resolvePlaceholder(offlinePlayer.getUniqueId(), params);
    }

    private String resolvePlaceholder(UUID requester, String params) {
        String normalized = normalizeParams(params);
        if (normalized.isEmpty()) {
            return Integer.toString(statisticsService.getKills(requester));
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        switch (lower) {
            case "self":
                return Integer.toString(statisticsService.getKills(requester));
            case "deaths":
                return Integer.toString(statisticsService.getDeaths(requester));
            case "kd":
                return String.format(Locale.US, "%.2f", statisticsService.getKd(requester));
            case "streak":
                return Integer.toString(statisticsService.getCurrentStreak(requester));
            case "beststreak":
                return Integer.toString(statisticsService.getBestStreak(requester));
            default:
                break;
        }

        if (lower.startsWith("top_")) {
            String rankPart = lower.substring("top_".length());
            try {
                int rank = Integer.parseInt(rankPart);
                return resolveTop(rank);
            } catch (NumberFormatException ignored) {
                return "none";
            }
        }

        if (lower.equals("team_total")) {
            Integer total = getRequestersTeamTotal(requester);
            return total == null ? "0" : Integer.toString(total);
        }

        if (lower.startsWith("team_top_")) {
            String rest = lower.substring("team_top_".length());
            if (rest.endsWith("_kills")) {
                String rankText = rest.substring(0, rest.length() - "_kills".length());
                try {
                    int rank = Integer.parseInt(rankText);
                    Map.Entry<String, Integer> entry = getTeamEntryAtRank(rank);
                    return entry == null ? "0" : Integer.toString(entry.getValue());
                } catch (NumberFormatException ignored) {
                    return "0";
                }
            }
            try {
                int rank = Integer.parseInt(rest);
                Map.Entry<String, Integer> entry = getTeamEntryAtRank(rank);
                return entry == null ? "none" : entry.getKey();
            } catch (NumberFormatException ignored) {
                return "none";
            }
        }

        if (lower.startsWith("team_")) {
            String teamName = normalized.substring("team_".length()).trim();
            if (!teamName.isEmpty()) {
                Integer total = getNamedTeamTotal(teamName);
                return total == null ? "0" : Integer.toString(total);
            }
        }

        Player onlineTarget = Bukkit.getPlayerExact(normalized);
        if (onlineTarget != null) {
            return Integer.toString(statisticsService.getKills(onlineTarget.getUniqueId()));
        }

        OfflinePlayer cachedTarget = Bukkit.getOfflinePlayerIfCached(normalized);
        if (cachedTarget != null) {
            return Integer.toString(statisticsService.getKills(cachedTarget.getUniqueId()));
        }

        return "0";
    }

    private String resolveTop(int rank) {
        if (rank <= 0) {
            return "none";
        }

        List<UUID> top = statisticsService.getTopKillers(rank);
        if (top.size() < rank) {
            return "none";
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(top.get(rank - 1));
        String name = player.getName();
        return name == null ? "unknown" : name;
    }

    private String normalizeParams(String params) {
        String trimmed = params == null ? "" : params.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length() > 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("_")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private Integer getRequestersTeamTotal(UUID requester) {
        try {
            BetterTeamsData data = collectBetterTeamsData();
            if (data == null) {
                return null;
            }

            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(requester);
            Object team = data.getTeamForPlayer(offlinePlayer);
            if (team == null) {
                return 0;
            }
            String teamName = data.getTeamName(team);
            return data.teamKills().getOrDefault(teamName, 0);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Integer getNamedTeamTotal(String teamName) {
        try {
            BetterTeamsData data = collectBetterTeamsData();
            if (data == null) {
                return null;
            }
            for (Map.Entry<String, Integer> entry : data.teamKills().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(teamName)) {
                    return entry.getValue();
                }
            }
            return 0;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private Map.Entry<String, Integer> getTeamEntryAtRank(int rank) {
        if (rank <= 0) {
            return null;
        }
        try {
            BetterTeamsData data = collectBetterTeamsData();
            if (data == null) {
                return null;
            }
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(data.teamKills().entrySet());
            sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
            return rank <= sorted.size() ? sorted.get(rank - 1) : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private BetterTeamsData collectBetterTeamsData() throws ReflectiveOperationException {
        if (Bukkit.getPluginManager().getPlugin("BetterTeams") == null) {
            return null;
        }

        Map<UUID, Integer> kills = statisticsService.getKillMapSnapshot();
        Map<String, Integer> teamKills = new HashMap<>();

        Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");
        Method getTeamManager = teamClass.getMethod("getTeamManager");
        Object teamManager = getTeamManager.invoke(null);

        Method getLoadedTeamListClone = teamManager.getClass().getMethod("getLoadedTeamListClone");
        Object loaded = getLoadedTeamListClone.invoke(teamManager);
        if (!(loaded instanceof Map<?, ?> loadedMap)) {
            return null;
        }

        for (Object team : loadedMap.values()) {
            Method getName = team.getClass().getMethod("getName");
            String teamName = String.valueOf(getName.invoke(team));

            Method getMembers = team.getClass().getMethod("getMembers");
            Object memberSetComponent = getMembers.invoke(team);
            Method getMemberSet = memberSetComponent.getClass().getMethod("get");
            Object membersObj = getMemberSet.invoke(memberSetComponent);
            if (!(membersObj instanceof Set<?> members)) {
                continue;
            }

            int total = 0;
            for (Object member : members) {
                Method getPlayerUUID = member.getClass().getMethod("getPlayerUUID");
                Object memberUuidObj = getPlayerUUID.invoke(member);
                if (memberUuidObj instanceof UUID memberUuid) {
                    total += kills.getOrDefault(memberUuid, 0);
                }
            }
            teamKills.put(teamName, total);
        }

        Method getTeamByPlayer = teamManager.getClass().getMethod("getTeam", OfflinePlayer.class);
        return new BetterTeamsData(teamManager, getTeamByPlayer, teamKills);
    }

    private record BetterTeamsData(
        Object teamManager,
        Method getTeamByPlayerMethod,
        Map<String, Integer> teamKills
    ) {
        Object getTeamForPlayer(OfflinePlayer player) throws ReflectiveOperationException {
            return getTeamByPlayerMethod.invoke(teamManager, player);
        }

        String getTeamName(Object team) throws ReflectiveOperationException {
            Method getName = team.getClass().getMethod("getName");
            return String.valueOf(getName.invoke(team));
        }
    }
}
