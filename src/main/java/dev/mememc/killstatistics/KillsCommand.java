package dev.mememc.killstatistics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KillsCommand implements CommandExecutor, TabCompleter {

    private static final int LEADERBOARD_SIZE = 10;

    private final KillStatisticsService statisticsService;
    private final MessageConfig messageConfig;

    public KillsCommand(KillStatisticsService statisticsService, MessageConfig messageConfig) {
        this.statisticsService = statisticsService;
        this.messageConfig = messageConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("team")) {
            if (args.length > 1) {
                sendTeamMemberLeaderboard(sender, args[1]);
            } else {
                sendTeamLeaderboard(sender);
            }
            return true;
        }
        sendPlayerLeaderboard(sender);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1 && "team".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            suggestions.add("team");
            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("team")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            for (String teamName : getAllTeamNames()) {
                if (teamName.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    suggestions.add(teamName);
                }
            }
        }

        return suggestions;
    }

    private void sendPlayerLeaderboard(CommandSender sender) {
        String title = messageConfig.getString("leaderboard.players.title", "&8&m--------------------------------");
        List<String> lines = messageConfig.getStringList(
            "leaderboard.players.lines",
            List.of("&6#%rank% &f%player% &7- &c%kills% kills")
        );
        String emptyLine = messageConfig.getString("leaderboard.players.empty-line", "&6#%rank% &7- &8(none)");
        String footer = messageConfig.getString("leaderboard.players.footer", "&8&m--------------------------------");

        sender.sendMessage(title);
        List<Map.Entry<UUID, Integer>> top = statisticsService.getTopKillEntries(LEADERBOARD_SIZE);
        for (int i = 1; i <= LEADERBOARD_SIZE; i++) {
            if (i <= top.size()) {
                Map.Entry<UUID, Integer> entry = top.get(i - 1);
                OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
                String playerName = offline.getName() == null ? "Unknown" : offline.getName();
                String template = lines.get((i - 1) % lines.size());
                sender.sendMessage(messageConfig.format(template, Map.of(
                    "%rank%", Integer.toString(i),
                    "%player%", playerName,
                    "%kills%", Integer.toString(entry.getValue())
                )));
            } else {
                sender.sendMessage(messageConfig.format(emptyLine, Map.of("%rank%", Integer.toString(i))));
            }
        }
        sender.sendMessage(footer);
    }

    private void sendTeamLeaderboard(CommandSender sender) {
        BetterTeamsSnapshot snapshot = loadBetterTeamsSnapshot(sender);
        if (snapshot == null) {
            return;
        }

        String title = messageConfig.getString("leaderboard.teams.title", "&8&m-------- &6&lTeam Kills &8&m--------");
        List<String> lines = messageConfig.getStringList(
            "leaderboard.teams.lines",
            List.of("&6#%rank% &f%team% &7- &c%kills% kills")
        );
        String emptyLine = messageConfig.getString("leaderboard.teams.empty-line", "&6#%rank% &7- &8(none)");
        String footer = messageConfig.getString("leaderboard.teams.footer", "&8&m--------------------------------");

        List<Map.Entry<String, Integer>> sortedTeams = new ArrayList<>(snapshot.teamKills().entrySet());
        sortedTeams.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        sender.sendMessage(title);
        for (int i = 1; i <= LEADERBOARD_SIZE; i++) {
            if (i <= sortedTeams.size()) {
                Map.Entry<String, Integer> entry = sortedTeams.get(i - 1);
                String template = lines.get((i - 1) % lines.size());
                sender.sendMessage(messageConfig.format(template, Map.of(
                    "%rank%", Integer.toString(i),
                    "%team%", entry.getKey(),
                    "%kills%", Integer.toString(entry.getValue())
                )));
            } else {
                sender.sendMessage(messageConfig.format(emptyLine, Map.of("%rank%", Integer.toString(i))));
            }
        }
        sender.sendMessage(footer);
    }

    private void sendTeamMemberLeaderboard(CommandSender sender, String teamName) {
        BetterTeamsSnapshot snapshot = loadBetterTeamsSnapshot(sender);
        if (snapshot == null) {
            return;
        }

        List<UUID> members = snapshot.teamMembersByName().entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(teamName))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
        if (members == null) {
            sender.sendMessage(messageConfig.getString("leaderboard.team-members.not-found", "&cTeam not found."));
            return;
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>();
        Map<UUID, Integer> kills = statisticsService.getKillMapSnapshot();
        for (UUID member : members) {
            sorted.add(Map.entry(member, kills.getOrDefault(member, 0)));
        }
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        String titleTemplate = messageConfig.getString("leaderboard.team-members.title", "&8&l[&6&lKillStats&8&l] &7Team: &f%team%");
        List<String> lines = messageConfig.getStringList(
            "leaderboard.team-members.lines",
            List.of("&6#%rank% &f%player% &7- &c%kills% kills")
        );
        String emptyLine = messageConfig.getString("leaderboard.team-members.empty-line", "&6#%rank% &7- &8(none)");
        String footer = messageConfig.getString("leaderboard.team-members.footer", "&8&m------------------------------------------");

        sender.sendMessage(messageConfig.format(titleTemplate, Map.of("%team%", teamName)));
        for (int i = 1; i <= Math.max(LEADERBOARD_SIZE, sorted.size()); i++) {
            if (i <= sorted.size()) {
                Map.Entry<UUID, Integer> entry = sorted.get(i - 1);
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                String playerName = player.getName() == null ? "Unknown" : player.getName();
                String template = lines.get((i - 1) % lines.size());
                sender.sendMessage(messageConfig.format(template, Map.of(
                    "%rank%", Integer.toString(i),
                    "%player%", playerName,
                    "%kills%", Integer.toString(entry.getValue())
                )));
            } else if (i <= LEADERBOARD_SIZE) {
                sender.sendMessage(messageConfig.format(emptyLine, Map.of("%rank%", Integer.toString(i))));
            }
        }
        if (sender.isOp()) {
            sender.sendMessage(footer);
        }
    }

    private List<String> getAllTeamNames() {
        BetterTeamsSnapshot snapshot = loadBetterTeamsSnapshot(null);
        if (snapshot == null) {
            return List.of();
        }
        return new ArrayList<>(snapshot.teamKills().keySet());
    }

    private BetterTeamsSnapshot loadBetterTeamsSnapshot(@Nullable CommandSender sender) {
        if (Bukkit.getPluginManager().getPlugin("BetterTeams") == null) {
            if (sender != null) {
                sender.sendMessage(messageConfig.getString("leaderboard.teams.no-plugin", "&cBetterTeams is not installed."));
            }
            return null;
        }

        Map<UUID, Integer> kills = statisticsService.getKillMapSnapshot();
        Map<String, Integer> teamKills = new HashMap<>();
        Map<String, List<UUID>> membersByTeam = new HashMap<>();

        try {
            Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");
            Method getTeamManager = teamClass.getMethod("getTeamManager");
            Object teamManager = getTeamManager.invoke(null);

            Method getLoadedTeamListClone = teamManager.getClass().getMethod("getLoadedTeamListClone");
            Object loaded = getLoadedTeamListClone.invoke(teamManager);
            if (!(loaded instanceof Map<?, ?> loadedMap)) {
                if (sender != null) {
                    sender.sendMessage(messageConfig.getString("leaderboard.teams.error", "&cFailed to read BetterTeams data."));
                }
                return null;
            }

            for (Object team : loadedMap.values()) {
                Method getName = team.getClass().getMethod("getName");
                String teamName = String.valueOf(getName.invoke(team));

                Method getMembers = team.getClass().getMethod("getMembers");
                Object memberSetComponent = getMembers.invoke(team);
                Method getMemberSet = memberSetComponent.getClass().getMethod("get");
                Object membersObj = getMemberSet.invoke(memberSetComponent);
                if (!(membersObj instanceof Collection<?> members)) {
                    continue;
                }

                int total = 0;
                List<UUID> memberList = new ArrayList<>();
                for (Object member : members) {
                    Method getPlayerUUID = member.getClass().getMethod("getPlayerUUID");
                    Object memberUuidObj = getPlayerUUID.invoke(member);
                    if (memberUuidObj instanceof UUID memberUuid) {
                        memberList.add(memberUuid);
                        total += kills.getOrDefault(memberUuid, 0);
                    }
                }
                membersByTeam.put(teamName, memberList);
                teamKills.put(teamName, total);
            }
        } catch (ReflectiveOperationException ex) {
            if (sender != null) {
                sender.sendMessage(messageConfig.getString("leaderboard.teams.error", "&cFailed to read BetterTeams data."));
            }
            return null;
        }

        return new BetterTeamsSnapshot(teamKills, membersByTeam);
    }

    private record BetterTeamsSnapshot(
        Map<String, Integer> teamKills,
        Map<String, List<UUID>> teamMembersByName
    ) {
    }
}
