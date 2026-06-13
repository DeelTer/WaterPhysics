/*     */ package me.RealisticWater;
/*     */ 
/*     */ import java.io.FileWriter;
/*     */ import java.io.IOException;
/*     */ import me.RealisticWater.events.SeaItemsEvent;
/*     */ import me.RealisticWater.events.WaterEvents;
/*     */ import me.RealisticWater.mechanics.WaterFlow;
/*     */ import me.RealisticWater.mechanics.WaterFlowSync;
/*     */ import org.bukkit.Bukkit;
/*     */ import org.bukkit.ChatColor;
/*     */ import org.bukkit.Material;
/*     */ import org.bukkit.command.Command;
/*     */ import org.bukkit.command.CommandSender;
/*     */ import org.bukkit.configuration.file.FileConfiguration;
/*     */ import org.bukkit.entity.Player;
/*     */ import org.bukkit.event.Listener;
/*     */ import org.bukkit.plugin.Plugin;
/*     */ import org.bukkit.plugin.java.JavaPlugin;
/*     */ import org.bukkit.scheduler.BukkitScheduler;
/*     */ import org.bukkit.scheduler.BukkitTask;
/*     */ 
/*     */ public class Main
/*     */   extends JavaPlugin
/*     */ {
/*     */   public static boolean status = true;
/*  26 */   String prefix = "&7[ &3Water &7]&f";
/*     */   
/*     */   BukkitTask waterFlowMechanic;
/*     */   BukkitTask visualUpdates;
/*     */   public static Plugin instance;
/*  31 */   WaterFlowSync sync = null;
/*  32 */   WaterFlow aSync = null;
/*     */ 
/*     */ 
/*     */   
/*     */   public void onEnable() {
/*  37 */     instance = (Plugin)this;
/*  38 */     sm("Checking configuration...");
/*  39 */     saveDefaultConfig();
/*  40 */     checkForSettings();
/*     */     
/*  42 */     Config.config = getConfig();
/*     */     
/*  44 */     boolean onstart = getConfig().getBoolean("Enable on start");
/*  45 */     status = onstart;
/*     */     
/*  47 */     sm("Loading processes:");
/*  48 */     sm("    Loading Water Events...");
/*  49 */     getServer().getPluginManager().registerEvents((Listener)new WaterEvents(), (Plugin)this);
/*  50 */     sm("    Loading Cheking for sea items Events...");
/*  51 */     getServer().getPluginManager().registerEvents((Listener)new SeaItemsEvent(), (Plugin)this);
/*     */     
/*  53 */     BukkitScheduler scheduler = Bukkit.getScheduler();
/*     */     
/*  55 */     sm("Launching mechanics:");
/*  56 */     sm("    Launching Normal Water Mechanincs...");
/*  57 */     this.sync = new WaterFlowSync();
/*  58 */     this.aSync = new WaterFlow();
/*  59 */     this.waterFlowMechanic = scheduler.runTaskTimerAsynchronously((Plugin)this, (Runnable)this.aSync, 100L, 1L);
/*  60 */     this.visualUpdates = scheduler.runTaskTimer((Plugin)this, (Runnable)this.sync, 10L, 10L);
/*     */     
/*  62 */     sm("Realistic Water was loaded!");
/*     */   }
/*     */   
/*     */   private void sm(String string) {
/*  66 */     getServer().getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', String.valueOf(this.prefix) + " " + string));
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   public void onDisable() {
/*  72 */     Bukkit.getScheduler().cancelTask(this.waterFlowMechanic.getTaskId());
/*  73 */     Bukkit.getScheduler().cancelTask(this.visualUpdates.getTaskId());
/*  74 */     sm("Realistic Water was unloaded.");
/*     */   }
/*     */ 
/*     */   
/*     */   public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
/*  79 */     if (command.getName().equalsIgnoreCase("water")) {
/*  80 */       if (sender.isOp()) {
/*  81 */         if (args.length > 0)
/*  82 */         { if (args[0].equalsIgnoreCase("reload")) {
/*  83 */             Bukkit.getScheduler().cancelTask(this.waterFlowMechanic.getTaskId());
/*  84 */             Bukkit.getScheduler().cancelTask(this.visualUpdates.getTaskId());
/*  85 */             reloadConfig();
/*  86 */             WaterList.clearAllblocks();
/*  87 */             Config.config = getConfig();
/*  88 */             this.waterFlowMechanic = Bukkit.getScheduler().runTaskTimerAsynchronously((Plugin)this, (Runnable)this.aSync, 100L, 1L);
/*  89 */             this.visualUpdates = Bukkit.getScheduler().runTaskTimer((Plugin)this, (Runnable)this.sync, 10L, 10L);
/*  90 */             sendMessage(sender, "&aPlugin reloaded");
/*  91 */           } else if (!args[0].equalsIgnoreCase("test")) {
/*  92 */             if (args[0].equalsIgnoreCase("disable")) {
/*  93 */               sendMessage(sender, "&aWater was &c&ndisabled");
/*  94 */               status = false;
/*  95 */             } else if (args[0].equalsIgnoreCase("enable")) {
/*  96 */               sendMessage(sender, "&aWater was &2&nenabled");
/*  97 */               status = true;
/*  98 */             } else if (args[0].equalsIgnoreCase("stop")) {
/*  99 */               Data.WaterFlow.processingQueue.clear();
/* 100 */               sendMessage(sender, "&aQueue cleared");
/* 101 */             } else if (args[0].equalsIgnoreCase("set")) {
/* 102 */               if (((Player)sender).getLocation().getBlock().getType() == Material.AIR)
/* 103 */               { Utility.setType(((Player)sender).getLocation().getBlock(), Material.WATER);
/* 104 */                 Utility.setData(((Player)sender).getLocation().getBlock(), Integer.valueOf(args[1]).intValue()); }
/*     */               else
/* 106 */               { Utility.setData(((Player)sender).getLocation().getBlock(), Integer.valueOf(args[1]).intValue()); } 
/* 107 */             } else if (args[0].equalsIgnoreCase("set2")) {
/* 108 */               sender.sendMessage((new StringBuilder(String.valueOf(Utility.getData(((Player)sender).getLocation().getBlock())))).toString());
/* 109 */             } else if (args[0].equalsIgnoreCase("set3")) {
/* 110 */               sender.sendMessage((String)((Player)sender).getLocation().getBlock().getType());
/*     */             } else {
/* 112 */               sendMessage(sender, "&m----------&r &3&lRealistic water&r &m----------");
/* 113 */               sendMessage(sender, "&cCommands:");
/* 114 */               sendMessage(sender, "&c+ &7/water reload &2Reloads the config");
/* 115 */               sendMessage(sender, "&c+ &7/water stop &2Clears the queue");
/* 116 */               sendMessage(sender, "&c+ &7/water disable &2Disable realistic mechanics");
/* 117 */               sendMessage(sender, "&c+ &7/water enable &2Enable realistic mechanics");
/*     */             } 
/*     */           }  }
/* 120 */         else { sendMessage(sender, "&m----------&r &3&lRealistic water&r &m----------");
/* 121 */           sendMessage(sender, "&cPlugin version: v" + getDescription().getVersion());
/* 122 */           sendMessage(sender, "&bThere is " + Data.WaterFlow.processingQueue.size() + " blocks in water queue");
/* 123 */           sendMessage(sender, "&bThere is " + Data.WaterFlowSync.list.size() + " blocks in visual updates");
/* 124 */           sendMessage(sender, "");
/* 125 */           sendMessage(sender, "&cCommands:");
/* 126 */           sendMessage(sender, "&c+ &7/water reload &2Reloads the config");
/* 127 */           sendMessage(sender, "&c+ &7/water stop &2Clears the queue");
/* 128 */           sendMessage(sender, "&c+ &7/water disable &2Disable realistic mechanics");
/* 129 */           sendMessage(sender, "&c+ &7/water enable &2Enable realistic mechanics"); }
/*     */       
/*     */       } else {
/* 132 */         sendMessage(sender, "&m----------&r &3&lRealistic water&r &m----------");
/* 133 */         sendMessage(sender, "&cPlugin version: v" + getDescription().getVersion());
/* 134 */         sendMessage(sender, "&cPlugin created by: AsVaidas");
/* 135 */         sendMessage(sender, "&c");
/* 136 */         sendMessage(sender, "&fPlugin adds realistic water mechanics into server");
/* 137 */         sendMessage(sender, "&fwith cool features like rain, water drain and vortexs.");
/* 138 */         sendMessage(sender, "&fFor more information please check at spigotmc.org");
/*     */       } 
/*     */     }
/* 141 */     return true;
/*     */   }
/*     */ 
/*     */   
/*     */   private void sendMessage(CommandSender s, String text) {
/* 146 */     s.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
/*     */   }
/*     */   
/*     */   private void checkForSettings() {
/* 150 */     FileConfiguration c = getConfig();
/*     */     
/* 152 */     boolean change = false;
/*     */     try {
/* 154 */       FileWriter fw = new FileWriter(String.valueOf(getDataFolder().getAbsolutePath()) + "/config.yml", true);
/*     */       
/* 156 */       if (!c.isSet("Enabled worlds")) {
/* 157 */         fw.write("\n\n#In which worlds will realistic mechanics work\nEnabled worlds:\n- world");
/*     */ 
/*     */ 
/*     */         
/* 161 */         change = true;
/*     */       } 
/* 163 */       fw.close();
/* 164 */     } catch (IOException e) {
/* 165 */       e.printStackTrace();
/*     */     } 
/* 167 */     if (change)
/* 168 */       reloadConfig(); 
/*     */   }
/*     */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\Main.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */