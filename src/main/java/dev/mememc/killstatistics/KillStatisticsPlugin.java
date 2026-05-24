package dev.mememc.killstatistics;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class KillStatisticsPlugin extends JavaPlugin {

    private KillStatisticsService statisticsService;
    private SQLiteStorage storage;
    private MessageConfig messageConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int maxKills = getConfig().getInt("max-kills-per-target-window", 2);
        int windowMinutes = getConfig().getInt("target-window-minutes", 30);

        try {
            File dbFile = resolveDatabaseFile();
            this.storage = new SQLiteStorage(dbFile);
            SQLiteStorage.LoadedData loaded = storage.loadAll();
            this.statisticsService = new KillStatisticsService(windowMinutes, maxKills, storage, loaded);
            getLogger().info("Using SQLite database: " + dbFile.getAbsolutePath());
        } catch (SQLException e) {
            getLogger().severe("Failed to initialize SQLite storage: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(new KillListener(statisticsService), this);

        this.messageConfig = new MessageConfig(this);
        this.messageConfig.load();

        PluginCommand killStatsCommand = getCommand("killstats");
        if (killStatsCommand != null) {
            KillStatsCommand executor = new KillStatsCommand(statisticsService);
            killStatsCommand.setExecutor(executor);
            killStatsCommand.setTabCompleter(executor);
        } else {
            getLogger().warning("Command killstats not found in plugin.yml.");
        }

        PluginCommand killsCommand = getCommand("kills");
        if (killsCommand != null) {
            KillsCommand killsExecutor = new KillsCommand(statisticsService, messageConfig);
            killsCommand.setExecutor(killsExecutor);
            killsCommand.setTabCompleter(killsExecutor);
        } else {
            getLogger().warning("Command kills not found in plugin.yml.");
        }

        PluginCommand creditsCommand = getCommand("credits");
        if (creditsCommand != null) {
            creditsCommand.setExecutor(new CreditsCommand());
        } else {
            getLogger().warning("Command credits not found in plugin.yml.");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KillsPlaceholderExpansion(this, statisticsService).register();
            getLogger().info("Registered placeholders: %kills%, %kills_<player>%, %kills_deaths%, %kills_kd%, %kills_streak%, %kills_beststreak%");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders are disabled.");
        }
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            try {
                storage.close();
            } catch (SQLException e) {
                getLogger().warning("Failed to close SQLite connection cleanly: " + e.getMessage());
            }
        }
    }

    public KillStatisticsService getStatisticsService() {
        return statisticsService;
    }

    private File resolveDatabaseFile() {
        String configuredPath = getConfig().getString("database.file", "stats.db");
        File dbFile = new File(getDataFolder(), configuredPath);
        File legacyDb = new File(getDataFolder().getParentFile(), "KillStatistics.db");

        if (!dbFile.exists() && legacyDb.exists()) {
            try {
                File parent = dbFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                Files.copy(legacyDb.toPath(), dbFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Migrated legacy database from " + legacyDb.getAbsolutePath() + " to " + dbFile.getAbsolutePath());
            } catch (IOException e) {
                getLogger().warning("Could not migrate legacy database: " + e.getMessage());
            }
        }

        return dbFile;
    }
}
