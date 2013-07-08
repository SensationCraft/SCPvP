package com.github.DarkSeraphim.SCPvP;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.sensationcraft.login.event.SCLoginRegisterEvent;

/**
 *
 * @author DarkSeraphim
 */
public class SCPvP extends JavaPlugin implements Listener
{
    
    private final Map<String, Long> protection = new HashMap<String, Long>();
    private final Object lock = new Object();
    
    // 2 hours of protection
    private final long PROTECTION_TIME = 1000*3600*2;
    
    // Removed protection message
    private final String NO_LONGER_PROTECTED = ChatColor.AQUA+""+ChatColor.BOLD+"You are no longer immune to PvP combat.";
    
    // Now protected + terms of protection
    private final String NOW_PROTECTED = ChatColor.AQUA+""+ChatColor.BOLD+"You are protected from PvP combat for the next 2 hours. This effect will cancel if voluntarily engage in combat.";
    
    // Protected message for the attacker
    private final String PROTECTED = ChatColor.AQUA+""+ChatColor.BOLD+"This player is temporarily immune to PvP damage.";
    
    private File save;
    
    @Override
    public void onEnable()
    {
        saveDefaultConfig();
        Titles.getInstance().initialize(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        save = new File(getDataFolder(), "protection.dat");
        YamlConfiguration yc = YamlConfiguration.loadConfiguration(save);
        synchronized(this.lock)
        {
            for(String player : yc.getKeys(false))
            {
                this.protection.put(player, yc.getLong(player, 0L));
            }
        }
    }
    
    @Override
    public void onDisable()
    {
        YamlConfiguration yc = new YamlConfiguration();
        synchronized(this.lock)
        {
            for(Map.Entry<String, Long> protectionEntry : this.protection.entrySet())
            {
                if(protectionEntry.getValue() != null)
                {
                    yc.set(protectionEntry.getKey(), protectionEntry.getValue());
                }
            }
        }
        
        if(!save.exists())
        {
            try
            {
                if(!save.getParentFile().mkdirs() || !save.createNewFile())
                {
                    throw new IOException("Could not make a savefile");
                }
            }
            catch(IOException ex)
            {
                getLogger().log(Level.SEVERE, "Could not create savefile {0}", save.getAbsolutePath());
                return;
            }
        }
        
        try
        {
            yc.save(save);
        }
        catch(IOException ex)
        {
            getLogger().log(Level.SEVERE, "Could not save to savefile {0}", save.getAbsolutePath());
        }
    }
    
    
    
    @EventHandler (priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event)
    {
        if(event.getEntity() instanceof Player && event.getDamager() instanceof Player)
        {
            Player attacker = (Player) event.getDamager();
            Player attacked = (Player) event.getEntity();
            if(isProtected(attacked))
            {
                event.setDamage(0);
                event.setCancelled(true);
                attacker.sendMessage(PROTECTED);
                return;
            }
            
            if(isProtected(attacker))
            {
                removeProtection(attacker);
            }
        }
    }
    
    @EventHandler
    public void onRegister(SCLoginRegisterEvent event)
    {
        final String name = event.getName();
        synchronized(this.lock)
        {
            this.protection.put(name.toLowerCase(), System.currentTimeMillis() + PROTECTION_TIME);
        }
        final Player player = Bukkit.getPlayerExact(name);
        if(player != null)
        {
            new BukkitRunnable()
            {
                @Override
                public void run()
                {
                    player.sendMessage(NOW_PROTECTED);
                }
            }.runTask(this);
        }
    }
    
    public boolean isProtected(Player player)
    {
        final String name = player.getName().toLowerCase();
        Long l;
        synchronized(this.lock)
        {
            l = this.protection.get(name);
        }
        if(l != null && l.longValue() > System.currentTimeMillis())
        {
            return true;
        }
        else if(l != null)
        {
            synchronized(this.lock)
            {
                removeProtection(player);
            }
        }
        return false;
    }
    
    public void removeProtection(Player player)
    {
        synchronized(this.lock)
        {
            this.protection.remove(player.getName().toLowerCase());
        }
        player.sendMessage(NO_LONGER_PROTECTED);
    }

}
