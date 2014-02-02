/*
 * Copyright 2013 Ben Gibbs. All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 
 ****************************************************************************
 * This software is a plugin for the Bukkit Minecraft Server				*
 * It provides a way, independent of economy to keep balances separate    	*
 * between worlds.															*
 ****************************************************************************
 
 */

package com.wasteofplastic.multiworldmoney;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.onarandombox.MultiverseCore.MultiverseCore;

public class MultiWorldMoney extends JavaPlugin implements Listener {
    public static Economy econ = null;
    private MultiverseCore core = null;
    File configFile;
    File playerFile;
    //File groupsFile;
    FileConfiguration config;
    FileConfiguration players;
    //FileConfiguration groups;
    World worldDefault;
    private static HashMap<String,String> worldgroups = new HashMap<String,String>();
    //World worldDefault = getServer().getWorlds().get(0);
    //String defaultWorld = worldDefault.getName();
    String defaultWorld = "world";
    
   
    @Override
    public void onDisable() {
        // Save all our yamls
        saveYamls();
    }
    
    @Override
	public void onEnable() {
    	//getLogger().info("onEnable has been invoked!");
	    configFile = new File(getDataFolder(), "config.yml");
	    playerFile = new File(getDataFolder() + "/userdata", "temp"); // not a real file
	    //groupsFile = new File(getDataFolder(), "groups.yml");
	    
	    // Hook into the Vault economy system
		setupEconomy();
		
		// Hook into Multiverse (if it exists)
		setupMVCore();
		
		// Check if this is the first time this plug in has been run
		PluginManager pm = getServer().getPluginManager();		
		pm.registerEvents(this, this);
	    try {
	        firstRun();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
        // then we just use loadYamls(); method
        config = new YamlConfiguration();
        players = new YamlConfiguration();
        //groups = new YamlConfiguration();
        loadYamls();
        
        // Find out the default world and insert into the config
        if (config.getString("defaultWorld") == null) {
        	// Nothing in the config
        	config.set("defaultWorld",defaultWorld);
        	saveYamls();
        } else {
        	// Grab the new default world from config.yml
        	defaultWorld = config.getString("defaultWorld");
        }
        
    	// Send stats
    	try {
    	    MetricsLite metrics = new MetricsLite(this);
    	    metrics.start();
    	} catch (IOException e) {
    	    // Failed to submit the stats :-(
    	}
    	

        
	}
	
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }
    
    private boolean setupMVCore() {
        // Multiverse plugin
        MultiverseCore mvCore;
        mvCore = (MultiverseCore) this.getServer().getPluginManager().getPlugin("Multiverse-Core");
        // Test if the Core was found
        if (mvCore == null) {
        	getLogger().info("Multiverse-Core not found.");
        	return false;
        } else {
        	this.core = mvCore;
        	return true;
        }
    }
    // This is the main event handler of this plugin. It adjusts balances when a player changes world
	@EventHandler(ignoreCancelled = true)
	public void onWorldLoad(PlayerChangedWorldEvent event) {
		// Find out who is moving world
		Player player = event.getPlayer();
		// Check to see if they are in a grouped world
		// TODO:
		// Pseudocode
		// If new world is in same group as old world, move the money from one world to the other
		// If player moves from a world outside a group into a group, then add up all the balances in that group, zero them out and give them to the player and set the new world balance to be the total
		// If a player moves within a group, then zero out from the old world and move to the new (to keep the net worth correct) - Done
		// If a player moves from a group to a world outside a group (or another group) then leave the balance in the last world.
		//getLogger().info("Old world name is: "+ event.getFrom().getName());
		//getLogger().info("New world name is: "+ player.getWorld().getName());
		// Initialize and retrieve the current balance of this player
		Double oldBalance = econ.getBalance(player.getName());
		Double newBalance = 0.0; // Always zero to start - in future change to a config value
		//player.sendMessage(ChatColor.GOLD + "You changed world!!");
		//player.sendMessage(String.format("Your old world balance was %s", econ.format(oldBalance)));
		// Create a new player file if one does not exist
		playerFile = new File(getDataFolder() + "/userdata", player.getName() + ".yml");
		if (playerFile.exists()) {
	    	// Get the YAML file for this player
	    	try {
	    		players.load(playerFile);
	    		// Save the old balance unless the player is moving within a group of worlds
	    		// If both these worlds are in the groups.yml file, they may be linked
	    		if (worldgroups.containsKey(event.getFrom().getName()) && worldgroups.containsKey(player.getWorld().getName())) {
	    			if (worldgroups.get(event.getFrom().getName()).equalsIgnoreCase(worldgroups.get(player.getWorld().getName()))) {
	    				//getLogger().info("Old world and new world are in the same group!");
	    				// Set the balance in the old world to zero
	    				players.set(event.getFrom().getName() + ".money", 0.0);
	    				// Player keeps their current balance plus any money that is in this new world   				
	    			} 
	    		} else {
	    			// These worlds are not in the same group
    				// Save the balance in the old world
    				players.set(event.getFrom().getName() + ".money", oldBalance);
    	    		// Zero out our current balance - Vault does not allow balances to be set to an arbitrary amount
    	    		econ.withdrawPlayer(player.getName(), oldBalance);
    			}
	    		// Player's balance at this point should be zero 0.0
	    		// Sort out the balance for the new world
	    		if (worldgroups.containsKey(player.getWorld().getName())) {
	    			// The new world in is a group
	    			//getLogger().info("The new world is in a group");
	    			// Step through each world, apply the balance and zero out balances if they are not in that world
	    			String groupName = worldgroups.get(player.getWorld().getName());
	    			//getLogger().info("Group name = " + groupName);
	    			// Get the name of each world in the group
	    			Set<String> keys = worldgroups.keySet();
	    			for (String key:keys) {
	    				//getLogger().info("World:" + key);
	    				if (worldgroups.get(key).equals(groupName)) {
	    					// The world is in the same group as this one
	    					//getLogger().info("The new world is in group "+groupName);
	    					newBalance = players.getDouble(key + ".money");
	    					//getLogger().info("Balance in world "+ key+ " = $"+newBalance);
	    					econ.depositPlayer(player.getName(), newBalance);
	    					// Zero out the old amount
	    					players.set(key + ".money", 0.0);
	    				}
	    			}
	    		} else {
	    			// This world is not in a group
		    		// Grab new balance from new world, if it exists, otherwise it is zero
		    		newBalance = players.getDouble((player.getWorld().getName() + ".money"));
		    		// Apply new balance to player;
		    		econ.depositPlayer(player.getName(), newBalance);
	    		// If the new world is in a group, then take the value of all the worlds together
	    		}
	    	} catch (Exception e) {
	            e.printStackTrace();
	        }
		} else {
			playerFile.getParentFile().mkdirs(); // just make the directory
			// First time to change a world
			// Find out if they are going to the default world. If so, they keep their balance.
			//getLogger().info("New world name is: "+ player.getWorld().getName());
			if (player.getWorld().getName() != defaultWorld) {
				//getLogger().info("Not going to the default world");
				// We want to keep money in the default world if this is the case
	    		// Save their balance in the default world
	    		players.set(defaultWorld + ".money", oldBalance);
	    		// Zero out our current balance
	    		econ.withdrawPlayer(player.getName(), oldBalance);
			} 
 	    }
		// Tell the user
		player.sendMessage(String.format(ChatColor.GOLD + "Your balance in this world is %s", econ.format(econ.getBalance(player.getName()))));
		// Write the balance to this world
		players.set(player.getWorld().getName() + ".money", econ.getBalance(player.getName()));
		// Save the player file just in case there is a server problem
   		try {
			players.save(playerFile);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private void firstRun() throws Exception {
	    if(!configFile.exists()){
	        configFile.getParentFile().mkdirs();
	        copy(getResource("config.yml"), configFile);
	    }
	    //if(!playerFile.exists()){
	    //    playerFile.getParentFile().mkdirs(); // just make the directory - maybe not needed
	    //}
	    //if(!groupsFile.exists()){
	    //    groupsFile.getParentFile().mkdirs();
	    //    copy(getResource("groups.yml"), groupsFile);
	    //}
	    
	    worldDefault = getServer().getWorlds().get(0);
	    defaultWorld = worldDefault.getName();
	}
	private void copy(InputStream in, File file) {
	    try {
	        OutputStream out = new FileOutputStream(file);
	        byte[] buf = new byte[1024];
	        int len;
	        while((len=in.read(buf))>0){
	            out.write(buf,0,len);
	        }
	        out.close();
	        in.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	}
	/*
     * in here, each of the FileConfigurations loaded the contents of yamls
     *  found at the /plugins/<pluginName>/*yml.
     * needed at onEnable() after using firstRun();
     * can be called anywhere if you need to reload the yamls.
     */
    public void loadYamls() {
        try {
            config.load(configFile); //loads the contents of the File to its FileConfiguration
            //groups.load(groupsFile);
            loadGroups();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void loadGroups() {
        YamlConfiguration groups = loadYamlFile("groups.yml");
        if(groups == null) {
            //MultiInv.log.info("No groups.yml found. Creating example file...");
            groups = new YamlConfiguration();
            
            ArrayList<String> exampleGroup = new ArrayList<String>();
            exampleGroup.add("world");
            exampleGroup.add("world_nether");
            exampleGroup.add("world_the_end");
            groups.set("exampleGroup", exampleGroup);
            saveYamlFile(groups, "groups.yml");
        }
        parseGroups(groups);
        
    }
    
    public static void parseGroups(Configuration config) {
        worldgroups.clear();
        Set<String> keys = config.getKeys(false);
        for(String group : keys) {
            List<String> worlds = config.getStringList(group);
            for(String world : worlds) {
                worldgroups.put(world, group);
            }
        }
    }
    
    public static YamlConfiguration loadYamlFile(String file) {
        File dataFolder = Bukkit.getServer().getPluginManager().getPlugin("MultiWorldMoney").getDataFolder();
        File yamlFile = new File(dataFolder, file);
        
        YamlConfiguration config = null;
        if(yamlFile.exists()) {
            try {
                config = new YamlConfiguration();
                config.load(yamlFile);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        return config;
    }
    public static void saveYamlFile(YamlConfiguration yamlFile, String fileLocation) {
        File dataFolder = Bukkit.getServer().getPluginManager().getPlugin("MultiWorldMoney").getDataFolder();
        File file = new File(dataFolder, fileLocation);
        
        try {
            yamlFile.save(file);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
   
    /*
     * save all FileConfigurations to its corresponding File
     * optional at onDisable()
     * can be called anywhere if you have *.set(path,value) on your methods
     */
    public void saveYamls() {
        try {
        	// Config and groups are not changed yet, but it doesn't matter to save them
            //config.save(configFile); //saves the FileConfiguration to its File
            //groups.save(groupsFile);
            players.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
    	if(cmd.getName().equalsIgnoreCase("balance")){ // If the player typed /balance then do the following...
    		// Find out who sent the command
    		String requestedPlayer = sender.getName();
    		// Find out where I am
    		String playerWorld = sender.getServer().getPlayer(requestedPlayer).getWorld().getName();
    		getLogger().info("Default player =" + requestedPlayer + " in " + playerWorld);
    		// Check if there is a player name associated with the command
    		/*
    		 * if (args[0] != "") {
    		
    			requestedPlayer = args[0];
    		}*/
    		//getLogger().info();
    		// Look up details on that player
    		playerFile = new File(getDataFolder() + "/userdata", requestedPlayer + ".yml");
    		if (playerFile.exists()) {
    			// The player exists
    			players = new YamlConfiguration();
    	    	// Get the YAML file for this player
    	    	try {
    	    		players.load(playerFile);
    	    	} catch (Exception e) {
    	            e.printStackTrace();
    	        }
    	    	// Step through file and print out balances for each world and total at the end
    	    	Double networth = 0.0;
    	    	Double worldBalance = 0.0;
    	    	Set<String> worldList = players.getKeys(false);
    	    	for (String s: worldList) {
    	    		//getLogger().info("World in file = "+s);
    	    		// Ignore the world I am in
    	    		if (s.equals(playerWorld)) {
    	    			worldBalance = econ.getBalance(requestedPlayer);
    	    			//getLogger().info("I am in this world and my balance is " + worldBalance);
    	    		} else {
    	    			worldBalance = players.getDouble(s+".money");
    	    		}
    	    		networth += worldBalance;
    	    		// Display balance in each world
    	    		// The line below can be used to grab all world names
    	    		// Collection<MultiverseWorld> wmList = core.getMVWorldManager().getMVWorlds();
    	    		String newName = core.getMVWorldManager().getMVWorld(s).getAlias();
    	    		if (newName != null) {
    	    			s = newName;
    	    		}
    	    		sender.sendMessage(String.format(s + " " + ChatColor.GOLD + econ.format(worldBalance)));
    	    	}
    	    	sender.sendMessage(String.format(ChatColor.GOLD + "Total across all worlds is " + econ.format(networth)));
    		}
    		return true;
    	} //If this has happened the function will return true. 
            // If this hasn't happened the a value of false will be returned.
    	return false; 
    }
}

