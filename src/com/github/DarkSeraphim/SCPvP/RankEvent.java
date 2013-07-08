package com.github.DarkSeraphim.SCPvP;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 *
 * @author DarkSeraphim
 */
public class RankEvent extends Event
{

    private static final HandlerList handlers = new HandlerList();

    private final String player;
    
    private final String title;
    
    protected RankEvent(String player, String title)
    {
        this.player = player;
        this.title = title;
    }
    
    public String getName()
    {
        return this.player;
    }
    
    public String getTitle()
    {
        return this.title;
    }
    
    public HandlerList getHandlers()
    {
        return RankEvent.handlers;
    }

    public static HandlerList getHandlerList()
    {
        return RankEvent.handlers;
    }
}
