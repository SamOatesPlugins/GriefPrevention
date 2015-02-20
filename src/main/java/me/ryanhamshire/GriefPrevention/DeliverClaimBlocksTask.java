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

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.JailsAPI;
import java.util.Collection;
import net.milkbowl.vault.economy.Economy;

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

    public DeliverClaimBlocksTask(Player player) {
        this.player = player;
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
    
    @Override
    public void run() {
        //if no player specified, this task will create a player-specific task for each online player, scheduled one tick apart
        if (this.player == null && this.blocksPerHour > 0) {
            final Server server = GriefPrevention.instance.getServer();
            final Collection<Player> players = (Collection<Player>) server.getOnlinePlayers();

            int i = 0;
            final BukkitScheduler scheduler = server.getScheduler();
            for (Player onlinePlayer : players) {
                DeliverClaimBlocksTask task = new DeliverClaimBlocksTask(onlinePlayer);
                scheduler.scheduleSyncDelayedTask(GriefPrevention.instance, task, 20L * (i++));
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
                        && (lastLocation == null || (lastLocation.getWorld() == location.getWorld() && lastLocation.distanceSquared(location) >= 9))
                        && !location.getBlock().isLiquid()) {

                    //add blocks
                    int newTotal = currentTotal + this.blocksPerHour;

                    //respect limits
                    if (newTotal > this.maxAccruedBlocks) {
                        newTotal = this.maxAccruedBlocks;
                    }
                    
                    // If jailed loose blocks mwhahahaha
                    final JailManager jail = JailsAPI.getJailManager();
                    if (jail != null) {
                        if (jail.isPlayerJailed(player.getUniqueId())) {
                            newTotal = currentTotal - (int)(this.blocksPerHour / 2);
                            if (newTotal < 0) {
                                newTotal = 0;
                            }
                        }
                    }

                    // Give the blocks
                    this.playerData.setAccruedClaimBlocks(newTotal);

                    // Give some money
                    if (this.economy != null) {
                        this.economy.depositPlayer(player, this.blocksPerHour * 0.01);
                    }
                }
            } catch (Exception e) {
                GriefPrevention.AddLogEntry("Problem delivering claim blocks to player " + player.getName() + ":");
                e.printStackTrace();
            }

            //remember current location for next time
            playerData.lastAfkCheckLocation = player.getLocation();
        }
    }
}
