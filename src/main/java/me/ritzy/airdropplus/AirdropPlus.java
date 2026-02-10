
package me.ritzy.airdropplus;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public class AirdropPlus extends JavaPlugin implements Listener {

    private ZoneId zoneId;
    private Location activeDrop;
    private BossBar bossBar;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        zoneId = ZoneId.of(getConfig().getString("timezone", "GMT+8"));
        Bukkit.getPluginManager().registerEvents(this, this);
        startScheduler();
    }

    private void startScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                LocalTime now = LocalTime.now(zoneId).withSecond(0).withNano(0);
                ConfigurationSection sec = getConfig().getConfigurationSection("airdrops");
                if (sec == null) return;

                for (String name : sec.getKeys(false)) {
                    for (String time : sec.getStringList(name + ".daily-times")) {
                        if (now.equals(LocalTime.parse(time))) {
                            startCountdown(name);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1200L);
    }

    private void startCountdown(String name) {
        int seconds = getConfig().getInt("countdown.seconds", 60);
        bossBar = Bukkit.createBossBar("Airdrop Incoming!", BarColor.RED, BarStyle.SEGMENTED_10);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);

        new BukkitRunnable() {
            int time = seconds;

            @Override
            public void run() {
                if (time <= 0) {
                    bossBar.removeAll();
                    spawnAirdrop(name);
                    cancel();
                    return;
                }
                bossBar.setProgress((double) time / seconds);
                bossBar.setTitle("Airdrop in " + time + "s");
                time--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void spawnAirdrop(String name) {
        String worldName = getConfig().getString("airdrops." + name + ".world");
        int radius = getConfig().getInt("airdrops." + name + ".random-radius");

        World world = Bukkit.getWorld(worldName);
        Random r = new Random();
        int x = r.nextInt(radius * 2) - radius;
        int z = r.nextInt(radius * 2) - radius;
        int y = world.getHighestBlockYAt(x, z);

        activeDrop = new Location(world, x, y, z);
        world.getBlockAt(activeDrop).setType(Material.CHEST);

        world.strikeLightningEffect(activeDrop);
        world.spawn(activeDrop, Firework.class);

        Bukkit.broadcastMessage("§6Airdrop spawned at §e" + x + ", " + y + ", " + z);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (activeDrop == null) return;
        if (e.getBlock().getLocation().distance(activeDrop) <= getConfig().getInt("protection.radius")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!getConfig().getBoolean("protection.disable-pvp")) return;
        if (activeDrop == null) return;
        if (e.getEntity().getLocation().distance(activeDrop) <= getConfig().getInt("protection.radius")) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            startCountdown(args[1]);
            return true;
        }
        return true;
    }
}
