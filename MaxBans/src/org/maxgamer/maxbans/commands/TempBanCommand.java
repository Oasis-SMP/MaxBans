package org.maxgamer.maxbans.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.maxgamer.maxbans.MaxBans;
import org.maxgamer.maxbans.banmanager.Ban;
import org.maxgamer.maxbans.banmanager.TempBan;

public class TempBanCommand implements CommandExecutor{
    private MaxBans plugin;
    public TempBanCommand(MaxBans plugin){
        this.plugin = plugin;
    }
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if(!sender.hasPermission("maxbans.tempban")){
			sender.sendMessage(plugin.color_secondary + "You don't have permission to do that");
			return true;
		}
		String usage = plugin.color_secondary + "Usage: /tempban <player> <time> <time form> [-s] <reason>";
		
		if(args.length < 3){
			sender.sendMessage(usage);
			return true;
		}
		else{
			String name = args[0];
			
			name = plugin.getBanManager().match(name);
			if(name == null){
				name = args[0]; //Use exact name then.
			}
			boolean silent = plugin.getBanManager().isSilent(args);
			
			long expires = plugin.getBanManager().getTime(args);
			if(expires <= 0){
				sender.sendMessage(usage);
				return true;
			}
			expires += System.currentTimeMillis();
			
			Ban ban = plugin.getBanManager().getBan(name);
			
			if(ban != null){
				if(ban instanceof TempBan){
					//They're already tempbanned!
					
					TempBan tBan = (TempBan) ban;
					if(tBan.getExpires() > expires){
						//Their old ban lasts longer than this one!
						sender.sendMessage(plugin.color_secondary + "That player has a tempban which will last longer than the one you supplied!");
						return true;
					}
					else{
						//Increasing a previous ban, remove the old one first.
						plugin.getBanManager().unban(name);
					}
				}
				else{
					//Already perma banned
					sender.sendMessage(plugin.color_secondary + "That player is already banned.");
					return true;
				}
			}
			
			Player player = Bukkit.getPlayerExact(name);
			
			String reason = plugin.getBanManager().buildReason(args);
			
			String banner = "Console";
			
			if(sender instanceof Player){
				banner = ((Player) sender).getName();
			}
			
			plugin.getBanManager().tempban(name, reason, banner, expires);
			
			if(player != null && player.isOnline()){
				player.kickPlayer("You have been Temporarily Banned for: \n"+reason);
			}
			
			if(!silent){
				plugin.getBanManager().announce(plugin.color_secondary + name + plugin.color_primary + " has been temp banned ("+plugin.getBanManager().getTimeUntil(expires)+") by " + plugin.color_secondary + banner + plugin.color_primary + ". Reason: " + plugin.color_secondary + reason + ".");
			}
			else{
				sender.sendMessage(plugin.color_secondary + name + plugin.color_primary + " has been silently temp banned ("+plugin.getBanManager().getTimeUntil(expires)+") by " + plugin.color_secondary + banner + plugin.color_primary + ". Reason: " + plugin.color_secondary + reason + ".");
			}
			
			return true;
		}
	}
}
