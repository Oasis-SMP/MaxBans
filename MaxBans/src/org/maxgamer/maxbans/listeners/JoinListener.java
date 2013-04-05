package org.maxgamer.maxbans.listeners;

import java.util.HashSet;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.maxgamer.maxbans.banmanager.*;
import org.maxgamer.maxbans.commands.DupeIPCommand;
import org.maxgamer.maxbans.sync.Packet;
import org.maxgamer.maxbans.util.Formatter;
import org.maxgamer.maxbans.util.IPAddress;
import org.maxgamer.maxbans.util.RangeBan;
import org.maxgamer.maxbans.util.Util;

public class JoinListener extends ListenerSkeleton{
	@EventHandler(priority = EventPriority.NORMAL)
	public void onJoinDupeip(PlayerLoginEvent e){
		if(plugin.getConfig().getBoolean("auto-dupeip") == false){
			return;
		}
		
		HashSet<String> dupes = plugin.getBanManager().getUsers(e.getAddress().getHostAddress());
		if(dupes == null){
			return;
		}
		dupes.remove(e.getPlayer().getName().toLowerCase());
		if(dupes.isEmpty()){
			return;
		}
		
		StringBuilder sb = new StringBuilder();
		for(String dupe : dupes){
			sb.append(DupeIPCommand.getChatColor(dupe).toString()+ dupe + ", ");
		}
		
		sb.replace(sb.length() - 2, sb.length(), "");
		for(Player p : Bukkit.getOnlinePlayers()){
			if(p.hasPermission("maxbans.notify")){
				p.sendMessage(DupeIPCommand.getScanningString(e.getPlayer().getName().toLowerCase(), e.getAddress().getHostAddress()));
				p.sendMessage(sb.toString());
			}
		}
		
	}
	
    @EventHandler(priority = EventPriority.LOW)
	public void onJoinLockdown(PlayerLoginEvent event) {
		if(event.getResult() != Result.ALLOWED) return;
    	Player player = event.getPlayer();
    	if(plugin.getBanManager().isLockdown()){
	        if(!player.hasPermission("maxbans.lockdown.bypass")){
	    		event.setKickMessage("Server is in lockdown mode. Try again shortly. Reason: \n" + plugin.getBanManager().getLockdownReason());
	    		event.setResult(Result.KICK_OTHER);
	    		return;
	    	}
	        else{ //Delay this, because it's fucken more important than essentials
	        	final String name = player.getName();
	        	Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, new Runnable(){
					public void run() {
						Player p = Bukkit.getPlayerExact(name);
						if(p != null){
							p.sendMessage(ChatColor.RED + "Bypassing lockdown (" + plugin.getBanManager().getLockdownReason() + ")!");
						}
					}
	        		
	        	}, 40);
	        }
        }
    }
    
    @EventHandler (priority = EventPriority.LOWEST)
    public void onJoinHandler(PlayerLoginEvent event) {
        final Player player = event.getPlayer();
        final String address = event.getAddress().getHostAddress();
        
        if(plugin.filter_names){
	        String invalidChars = Util.getInvalidChars(player.getName());
	        if(!invalidChars.isEmpty()){
	        	event.setKickMessage("Kicked by MaxBans.\nYour name contains invalid characters:\n'" + invalidChars + "'");
	        	event.setResult(Result.KICK_OTHER);
	        	return;
	        }
	        else if(player.getName().isEmpty()){
	        	event.setKickMessage("Kicked by MaxBans.\nYour name is invalid!");
	        	event.setResult(Result.KICK_OTHER);
	        	return;
	        }
        }
        
        //Log that the player connected from that IP address.
        if(plugin.getBanManager().logIP(player.getName(), address)){
        	if(plugin.getSyncer() != null){
	    		Packet ipUpdate = new Packet().setCommand("setip").put("name", player.getName());
	    		ipUpdate.put("ip", address);
	    		plugin.getSyncer().broadcast(ipUpdate);
	    	}
        }
        
        //Log the players actual case-sensitive name.
        if(plugin.getBanManager().logActual(player.getName(), player.getName())){
        	if(plugin.getSyncer() != null){
	    		Packet nameUpdate = new Packet().setCommand("setname").put("name", player.getName());
	    		plugin.getSyncer().broadcast(nameUpdate);
	    	}
        }
        
        //Ban
        Ban ban = plugin.getBanManager().getBan(player.getName());
        
        //IP Ban
        IPBan ipban = null;
        boolean whitelisted = plugin.getBanManager().isWhitelisted(player.getName());
        if(!whitelisted){ //Only fetch the IP ban if the user is not whitelisted.
        	 ipban = plugin.getBanManager().getIPBan(address); 
        }
        
        //If they haven't been banned or IP banned, they can join.
        if(ipban == null && ban == null){
        	if(!whitelisted){
        		//Check for a rangeban
	        	IPAddress ip = new IPAddress(address);
	        	RangeBan rb = plugin.getBanManager().getRanger().getBan(ip);
	        	if(rb != null){
	        		String reason = Formatter.regular + "Your IP Address (" + Formatter.secondary + rb.toString() + Formatter.regular + ") is RangeBanned.\n";
	        		if(rb instanceof Temporary){
	        			reason += "The ban expires in " + Formatter.time + Util.getTimeUntil(((Temporary) rb).getExpires()) + Formatter.regular + ".\n"; 
	        		}
	        		reason += Formatter.regular + "Reason: " + Formatter.reason + rb.getReason() + "\n";
	        		reason += Formatter.regular + "By: " + Formatter.banner + rb.getBanner();
	        		
	        		 //Append the appeal message, if necessary.
	                String appeal = plugin.getBanManager().getAppealMessage();
	                if(appeal != null && appeal.isEmpty() == false){
	                	reason += "\n" + Formatter.regular + appeal;
	                }
	                event.disallow(Result.KICK_OTHER, reason);
	                
	                if(plugin.getConfig().getBoolean("notify", true)){
	                	String msg = Formatter.secondary + player.getName() + Formatter.primary + " (" + ChatColor.RED + address + Formatter.primary + ")" + " tried to join, but is " + (rb instanceof Temporary ? "temp " : "") + "RangeBanned.";
	        	        for(Player p : Bukkit.getOnlinePlayers()){
	        	        	if(p.hasPermission("maxbans.notify")){
	        	        		p.sendMessage(msg);
	        	        	}
	        	        }
	                }
	                
	                return;
	        	}
	        	
	        	//DNS Blacklist handling, only if NOT whitelisted
	            if(plugin.getBanManager().getDNSBL() != null){
	            	plugin.getBanManager().getDNSBL().handle(event);
	            	if(event.getResult() != Result.ALLOWED) return; //DNSBL doesn't want them joining.
	            }
        	}
        	
        	return;
        }
        
        String reason;
        String banner;
        long expires = 0;
        
        if (ipban != null){ 
            if (ipban instanceof TempIPBan) {
            	TempIPBan tempipban = (TempIPBan) ipban;
            	expires = tempipban.getExpires(); //wish there was a better way to do this
            }
            reason = ipban.getReason();
            banner = ipban.getBanner();
            
        } else{ //We dont need to check ban isn't null here. We already did.
            if (ban instanceof TempBan) {
            	TempBan tempban = (TempBan) ban;
            	expires = tempban.getExpires();
            }
            reason = ban.getReason();
            banner = ban.getBanner();
        }
        
        StringBuilder km = new StringBuilder(25); //kickmessage
        km.append(Formatter.message + "You're "+(ipban == null ? "" : "IP ")+"banned!" + Formatter.regular + "\n Reason: '");
        km.append(Formatter.reason + reason);
        km.append(Formatter.regular + "'\n By ");
        km.append(Formatter.banner + banner + Formatter.regular + ". ");
        if (expires > 0) {
        	km.append("Expires in " + Formatter.time + Util.getTimeUntil(expires));
        }
        //Append the appeal message, if necessary.
        String appeal = plugin.getBanManager().getAppealMessage();
        if(appeal != null && appeal.isEmpty() == false){
        	km.append("\n" + Formatter.regular + appeal);
        }
        
        event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
        event.setKickMessage(km.toString());
        
        if(plugin.getConfig().getBoolean("notify", true)){
        	String msg = (ban == null ? Formatter.secondary : ChatColor.RED) + player.getName() + Formatter.primary + " (" + (ipban == null ? Formatter.secondary : ChatColor.RED) + address + Formatter.primary + ") tried to join, but is "+ (expires > 0 ? "temp banned" : "banned") +"!"; 
	        for(Player p : Bukkit.getOnlinePlayers()){
	        	if(p.hasPermission("maxbans.notify")){
	        		p.sendMessage(msg);
	        	}
	        }
        }
    }
}
