package com.github.DarkSeraphim.SCPvP;

import com.google.common.base.Joiner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
    
    private static Titles instance = new Titles();
        
    private JavaPlugin plugin;
    
    File saveFile;
    
    YamlConfiguration yc;
    
    List<String> rankKeys;
    
    private final Map<String, String> lastKill = new HashMap<String, String>();
    
    Comparator<String> comparator = new Comparator<String>()
    {

        @Override
        public int compare(String o1, String o2)
        {
            int i1 = Titles.strToInt(o1);
            int i2 = Titles.strToInt(o2);
            return i1 - i2;
        }
        
    };
    
    private final String HELP = ChatColor.DARK_BLUE+"-- "+ChatColor.YELLOW+"SCPvPTitle"+ChatColor.DARK_BLUE+" --\n"+ChatColor.WHITE
                              + "/rank reset <playername>  - resets ones title\n"
                              + "/rank set <rank> <title>  - sets the title for that level\n"
                              + "/rank rem <rank>          - removes the rank for that level\n"
                              + "/rank list                - lists the ranks";
    
    private final String RANKING = "\n"
                                  +ChatColor.YELLOW+""+ChatColor.BOLD+"You have "+ChatColor.RED+""+ChatColor.BOLD+""+ChatColor.UNDERLINE+"%d kills\n"
                                  +ChatColor.YELLOW+""+ChatColor.BOLD+"Next rank title: "+ChatColor.RED+""+ChatColor.BOLD+""+ChatColor.UNDERLINE+"%s\n"
                                  +"\n";
    private final String RANKUP = ChatColor.translateAlternateColorCodes('&', "\n&a&lYou have recieved a new title: %s\n");
    
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
                if(!this.saveFile.getParentFile().exists() && !this.saveFile.getParentFile().mkdirs())
                {
                    throw new IOException("Failed to create savefile @ "+this.saveFile.getAbsolutePath());
                }
                this.saveFile.createNewFile();
            }
            catch(IOException ex)
            {
                ex.printStackTrace();
                this.saveFile = null;
            }
        }
        
        if(canSave())
            this.yc = YamlConfiguration.loadConfiguration(saveFile);
        else
            this.yc = new YamlConfiguration();
        
        refreshRankList();
        
        Bukkit.getPluginCommand("rank").setExecutor(this);
        Bukkit.getPluginCommand("ranking").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    protected void disable()
    {
        Titles.instance = null;
    }
    
    private boolean canSave()
    {
        return this.saveFile != null && this.saveFile.exists();
    }
    
    private static int strToInt(String s)
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
    
    private void refreshRankList()
    {
        ConfigurationSection section = getPlugin().getConfig().getConfigurationSection("ranks");
        if(section == null)
        {
            section = getPlugin().getConfig().createSection("ranks");
            getPlugin().saveConfig();
        }
        this.rankKeys = new ArrayList<String>(section.getKeys(false));
        Collections.sort(rankKeys, comparator);    
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
        if(rankSection == null)
        {
            return rank;
        }
        int kills = this.yc.getInt(name, 0);
        int rankKills;
        for(String key : this.rankKeys)
        {
            rankKills = strToInt(key);
            if(kills >= rankKills)
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
    
    public String getNextTitle(String name)
    {
        String rank = "";
        if(!isAvailable()) return rank;
        ConfigurationSection rankSection = getPlugin().getConfig().getConfigurationSection("ranks");
        if(rankSection == null)
        {
            return rank;
        }
        int kills = this.yc.getInt(name, 0);
        int rankKills;
        String str;
        Iterator<String> it = this.rankKeys.iterator();
        while(it.hasNext())
        {
            str = it.next();
            rankKills = strToInt(str);
            if(kills < rankKills)
            {
                return ChatColor.translateAlternateColorCodes('&', rankSection.getString(str, ""));
            }
        }
        return "none";
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
            Player other = null;
            if(damager instanceof Player)
            {
                other = (Player) damager;
            }
            else if(damager instanceof Projectile)
            {
                if(((Projectile)damager).getShooter() instanceof Player)
                    other = (Player) ((Projectile)damager).getShooter();
            }
            
            if(other == null) return;
            if(other.getAddress().getAddress().getHostAddress().equals(player.getAddress().getAddress().getHostAddress())) return;
                String oname = other.getName();
            if(!name.equals(this.lastKill.get(oname)))
            {
                incrementKills(oname);
                this.lastKill.put(oname, name);
            }
        }
    }
    
    @EventHandler
    public void onRank(RankEvent event)
    {
        Player player = Bukkit.getPlayerExact(event.getName());
        if(player != null)
        {
            player.getWorld().playSound(player.getLocation(), Sound.WITHER_SPAWN, 2F, 2F);
            player.sendMessage(RANKUP);
        }
    }

    /*
     * CommandExecutor
     */
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if(cmd.getName().equals("ranking"))
        {
            int kills = this.yc.getInt(sender.getName(), 0);
            sender.sendMessage(String.format(RANKING, kills, getNextTitle(sender.getName())));
            return true;
        }
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
                if(args[0].equals("debug"))
                {
                    sender.sendMessage("kills: "+this.yc.getInt(sender.getName(), -1));
                    sender.sendMessage(getTitle(sender.getName()));
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
                        refreshRankList();
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
                        refreshRankList();
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
}
