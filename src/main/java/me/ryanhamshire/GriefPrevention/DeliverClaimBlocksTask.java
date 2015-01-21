/*
 GriefPrevention Server Plugin for Minecraft
 Copyright (C) 2012 Ryan Hamshire

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package me.ryanhamshire.GriefPrevention;

import java.util.Collection;
import java.util.logging.Level;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

//FEATURE: give players claim blocks for playing, as long as they're not away from their computer
//runs every 5 minutes in the main thread, grants blocks per hour / 12 to each online player who appears to be actively playing
class DeliverClaimBlocksTask implements Runnable {

    private final Player player;
    private final int blocksPerHour;
    private final int maxAccruedBlocks;
    private final Economy economy;
    private final DataStore dataStore;
    private final PlayerData playerData;

    public DeliverClaimBlocksTask() {
        this.player = null;
        this.blocksPerHour = GriefPrevention.instance.config_claims_blocksAccruedPerHour / 12;
        this.maxAccruedBlocks = GriefPrevention.instance.config_claims_maxAccruedBlocks;
        this.economy = GriefPrevention.economy;
        this.dataStore = GriefPrevention.instance.dataStore;
        
        if (player != null) {
            this.playerData = this.dataStore.getPlayerData(player.getUniqueId());
        } else {
            this.playerData = null;
        }
    }

    public DeliverClaimBlocksTask(Player player, int blocksPerHour, int maxAccuredBlocks, Economy economy, DataStore dataStore) {
        this.player = player;
        this.blocksPerHour = blocksPerHour;
        this.maxAccruedBlocks = maxAccuredBlocks;
        this.economy = economy;
        this.dataStore = dataStore;
        
        if (player != null) {
            this.playerData = this.dataStore.getPlayerData(player.getUniqueId());
        } else {
            this.playerData = null;
        }
    }
    
    @Override
    public void run() {
        //if no player specified, this task will create a player-specific task for each online player, scheduled one tick apart
        if (this.player == null && this.blocksPerHour > 0) {
            final Server server = GriefPrevention.instance.getServer();
            final Collection<Player> players = (Collection<Player>) server.getOnlinePlayers();

            Bukkit.getLogger().log(Level.INFO, "[GriefPrevention] Performing 'Dellver Claim Blocks'.");
            
            int i = 0;
            final BukkitScheduler scheduler = server.getScheduler();
            for (Player onlinePlayer : players) {
                DeliverClaimBlocksTask task = new DeliverClaimBlocksTask(onlinePlayer, blocksPerHour, maxAccruedBlocks, economy, dataStore);
                scheduler.scheduleSyncDelayedTask(GriefPrevention.instance, task, 20L * 5 * (i++));
            }
        } //otherwise, deliver claim blocks to the specified player
        else {
            //if player is over accrued limit, accrued limit was probably reduced in config file AFTER he accrued
            //in that case, leave his blocks where they are
            int currentTotal = this.playerData.getAccruedClaimBlocks();
            if (currentTotal >= this.maxAccruedBlocks) {
                return;
            }

            Location lastLocation = this.playerData.lastAfkCheckLocation;
            try {
                //if he's not in a vehicle and has moved at least three blocks since the last check
                //and he's not being pushed around by fluids
                final Location location = player.getLocation();                
                if (!player.isInsideVehicle()
                        && (lastLocation == null || lastLocation.distanceSquared(location) >= 9)
                        && !location.getBlock().isLiquid()) {

                    //add blocks
                    int newTotal = currentTotal + this.blocksPerHour;

                    //respect limits
                    if (newTotal > this.maxAccruedBlocks) {
                        newTotal = this.maxAccruedBlocks;
                    }

                    // Give the blocks
                    this.playerData.setAccruedClaimBlocks(newTotal);

                    // Give some money
                    this.economy.depositPlayer(player, this.blocksPerHour * 0.01);
                    
                    Bukkit.getLogger().log(Level.INFO, "[GriefPrevention] Given ''{0}'' {1} blocks .", new Object[]{player.getName(), this.blocksPerHour});
                }
            } catch (IllegalArgumentException e) //can't measure distance when to/from are different worlds
            {

            } catch (Exception e) {
                GriefPrevention.AddLogEntry("Problem delivering claim blocks to player " + player.getName() + ":");
                e.printStackTrace();
            }

            //remember current location for next time
            playerData.lastAfkCheckLocation = player.getLocation();
        }
    }
}
