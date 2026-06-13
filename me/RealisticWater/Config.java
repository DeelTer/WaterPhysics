/*    */ package me.RealisticWater;
/*    */ 
/*    */ import org.bukkit.Location;
/*    */ import org.bukkit.configuration.file.FileConfiguration;
/*    */ 
/*    */ 
/*    */ public class Config
/*    */ {
/*    */   static Main plugin;
/*    */   public static FileConfiguration config;
/*    */   
/*    */   public Config(Main instence) {
/* 13 */     plugin = instence;
/* 14 */     config = plugin.getConfig();
/*    */   }
/*    */ 
/*    */   
/*    */   public static boolean isWorldEnabled(Location loc) {
/* 19 */     if (config.getStringList("Enabled worlds").contains(loc.getWorld().getName()) || 
/* 20 */       config.getStringList("Enabled worlds").contains("*"))
/* 21 */       return true; 
/* 22 */     return false;
/*    */   }
/*    */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\Config.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */