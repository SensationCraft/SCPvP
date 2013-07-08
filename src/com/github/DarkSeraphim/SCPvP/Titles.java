package com.github.DarkSeraphim.SCPvP;

import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author DarkSeraphim
 */
public class Titles implements Listener, CommandExecutor
{
    
    private final static Titles instance = new Titles();
        
    private JavaPlugin plugin;
    
    File saveFile;
    
    YamlConfiguration yc;
    
    private final Map<String, String> lastKill = new HashMap<String, String>();
    
    private final String HELP = ChatColor.DARK_BLUE+"-- "+ChatColor.YELLOW+"SCPvPTitle"+ChatColor.DARK_BLUE+" --\n"+ChatColor.WHITE
                              + "/rank reset <playername>  - resets ones title\n"
                              + "/rank set <rank> <title>  - sets the title for that level\n"
                              + "/rank rem <rank>          - removes the rank for that level\n"
                              + "/rank list                - lists the ranks";
    
    private Titles()
    {
    }
    
    /*
     * Internals
     */
    protected void initialize(JavaPlugin plugin)
    {
        this.plugin = plugin;
        this.saveFile = new File(plugin.getDataFolder(), "titles.sav");
        if(!this.saveFile.exists())
        {
            try
            {
                if(!this.saveFile.getParentFile().mkdirs() || !this.saveFile.createNewFile())
                {
                    throw new IOException("Failed to create savefile");
                }
            }
            catch(IOException ex)
            {
                this.saveFile = null;
            }
        }
        
        if(canSave())
            this.yc = YamlConfiguration.loadConfiguration(saveFile);
        else
            this.yc = new YamlConfiguration();
        
        Bukkit.getPluginCommand("rank").setExecutor(this);
    }
    
    private boolean canSave()
    {
        return this.saveFile != null && this.saveFile.exists();
    }
    
    private int strToInt(String s)
    {
        try
        {
            return Integer.parseInt(s);
        }
        catch(NumberFormatException ex)
        {
            return Integer.MAX_VALUE;
        }
    }
    
    private void saveRanks()
    {
        if(canSave())
        {
            try
            {
                this.yc.save(saveFile);
            }
            catch(IOException ex)
            {
                getPlugin().getLogger().log(Level.WARNING, "Failed to save ranks");
                ex.printStackTrace();
            }
        }
    }
    
    
    /*
     * API
     */
    public JavaPlugin getPlugin()
    {
        return this.plugin;
    }
    
    public static boolean isAvailable()
    {
        return getInstance().getPlugin() != null;
    }
    
    public static Titles getInstance()
    {
        return Titles.instance;
    }
    
    public void resetKills(String name)
    {
        this.yc.set(name, null);
    }
    
    public void incrementKills(String name)
    {
        int newrank = this.yc.getInt(name, 0) + 1;
        this.yc.set(name, newrank);
        String title = getPlugin().getConfig().getString("ranks."+newrank, "");
        if(!title.isEmpty())
        {
            Bukkit.getPluginManager().callEvent(new RankEvent(name, title));
        }
        saveRanks();
    }
    
    public String getTitle(String name)
    {
        String rank = "";
        if(!isAvailable()) return rank;
        ConfigurationSection rankSection = getPlugin().getConfig().getConfigurationSection("ranks");
        if(rankSection == null) return rank;
        int kills = this.yc.getInt(name, 0);
        int rankKills;
        
        for(String key : rankSection.getKeys(false))
        {
            rankKills = strToInt(key);
            if(kills > rankKills)
            {
                rank = rankSection.getString(key, "");
            }
            else
            {
                return ChatColor.translateAlternateColorCodes('&', rank);
            }
        }
        return ChatColor.translateAlternateColorCodes('&', rank);
    }
    
    /*
     * Listeners
     */
    
    @EventHandler
    public void onDeath(PlayerDeathEvent event)
    {
        Player player = event.getEntity();
        String name = player.getName();
        if(player.getLastDamageCause() instanceof EntityDamageByEntityEvent)
        {
            Entity damager = ((EntityDamageByEntityEvent)player.getLastDamageCause()).getDamager();
            if(damager instanceof Player)
            {
                Player other = ((Player)damager);
                if(other.getAddress().getAddress().getHostAddress().equals(player.getAddress().getAddress().getHostAddress())) return;
                String oname = other.getName();
                if(!name.equals(this.lastKill.get(oname)))
                {
                    incrementKills(oname);
                    this.lastKill.put(oname, name);
                }
            }
        }
        
    }

    /*
     * CommandExecutor
     */
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if(!sender.hasPermission("titles.edit"))
        {
            sender.sendMessage("Unknown command. Type \"help\" for help");
            return true;
        }
        
        if(cmd.getName().equals("rank"))
        {
            if(args.length == 0)
            {
                sender.sendMessage(HELP);
            }
            else
            {
                if(args[0].equalsIgnoreCase("debug"))
                {
                    sender.sendMessage(getTitle(sender.getName()));
                    return true;
                }
                boolean valid = args[0].equalsIgnoreCase("set") && args.length > 2 || args.length == 2 || args[0].equalsIgnoreCase("list");
                if(valid)
                {
                    if(args[0].equalsIgnoreCase("reset"))
                    {
                        if(!this.yc.contains(args[1]))
                        {
                            sender.sendMessage(ChatColor.DARK_RED+"Player not found");
                            return true;
                        }
                        else
                        {
                            this.resetKills(args[1]);
                            this.saveRanks();
                            sender.sendMessage(ChatColor.GREEN+String.format("Rank of %s reset", args[1]));
                            Player player = Bukkit.getPlayerExact(args[1]);
                            if(player != null)
                            {
                                player.sendMessage(ChatColor.GOLD+"Your rank has been reset.");
                            }
                        }
                    }
                    else if(args[0].equalsIgnoreCase("set"))
                    {
                        int rank = strToInt(args[1]);
                        if(rank == Integer.MAX_VALUE)
                        {
                            sender.sendMessage(ChatColor.RED+"Please give a number for the rank.");
                            return true;
                        }
                        
                        String title = Joiner.on(' ').join(Arrays.copyOfRange(args, 2, args.length));
                        
                        getPlugin().getConfig().set("ranks."+rank, title);
                        getPlugin().saveConfig();
                        sender.sendMessage(String.format("Title %s added for rank %d", title, rank));
                        return true;
                    }
                    else if(args[0].equalsIgnoreCase("rem"))
                    {
                        String path = String.format("ranks.%s", args[1]);
                        if(!getPlugin().getConfig().contains(path))
                        {
                            sender.sendMessage(ChatColor.DARK_RED+"Rank not found.");
                            return true;
                        }
                        getPlugin().getConfig().set(path, null);
                        getPlugin().saveConfig();
                        sender.sendMessage(String.format("Rank %s cleared", args[1]));
                        return true;
                    }
                    else if(args[0].equalsIgnoreCase("list"))
                    {
                        ConfigurationSection section  = getPlugin().getConfig().getConfigurationSection("ranks");
                        if(section == null)
                        {
                            section = getPlugin().getConfig().createSection("ranks");
                            getPlugin().saveConfig();
                        }
                        StringBuilder rankList = new StringBuilder(ChatColor.DARK_BLUE.toString())
                                                     .append("-- ").append(ChatColor.YELLOW).append("SCPvPTitles list")
                                                     .append(ChatColor.DARK_BLUE).append(" --");
                        for(String rank : section.getKeys(false))
                        {
                            rankList.append("\n- ").append(rank).append(": ").append(section.getString(rank, ""));
                        }
                        sender.sendMessage(rankList.toString());
                        return true;
                    }
                }
                else
                {
                    sender.sendMessage(HELP);
                }
            }
            return true;
        }
        
        return false;
    }
    
    @EventHandler()
    public void onRank(RankEvent event)
    {
        Bukkit.broadcastMessage(String.format("%s %s", event.getTitle(), event.getName()));
    }
}
