package dev.mememc.killstatistics;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class KillListener implements Listener {

    private final KillStatisticsService statisticsService;

    public KillListener(KillStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        statisticsService.recordDeath(victim.getUniqueId());

        Player killer = victim.getKiller();
        if (killer == null) {
            return;
        }
        statisticsService.recordKill(killer.getUniqueId(), victim.getUniqueId());
    }
}
