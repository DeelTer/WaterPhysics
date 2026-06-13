/*    */ package me.RealisticWater.events;
/*    */ 
/*    */ import me.RealisticWater.Config;
/*    */ import me.RealisticWater.Data;
/*    */ import me.RealisticWater.Main;
/*    */ import me.RealisticWater.Utility;
/*    */ import me.RealisticWater.WaterList;
/*    */ import org.bukkit.Bukkit;
/*    */ import org.bukkit.Material;
/*    */ import org.bukkit.block.Block;
/*    */ import org.bukkit.event.EventHandler;
/*    */ import org.bukkit.event.Listener;
/*    */ import org.bukkit.event.block.BlockBreakEvent;
/*    */ import org.bukkit.event.block.BlockFromToEvent;
/*    */ import org.bukkit.event.block.BlockPhysicsEvent;
/*    */ import org.bukkit.event.block.BlockPlaceEvent;
/*    */ import org.bukkit.scheduler.BukkitScheduler;
/*    */ 
/*    */ 
/*    */ public class WaterEvents
/*    */   implements Listener
/*    */ {
/* 23 */   BukkitScheduler scheduler = Bukkit.getScheduler();
/*    */ 
/*    */   
/*    */   @EventHandler
/*    */   public void onBlockFromToEvent(BlockFromToEvent e) {
/* 28 */     if (!Main.status)
/* 29 */       return;  Block b = e.getBlock();
/* 30 */     if (!Utility.isWater(b))
/* 31 */       return;  if (!Config.isWorldEnabled(b.getLocation()))
/*    */       return; 
/* 33 */     e.setCancelled(true);
/* 34 */     Data.WaterFlow.addBlock(b);
/*    */   }
/*    */ 
/*    */   
/*    */   @EventHandler
/*    */   public void onBlockPhysicsEvent(BlockPhysicsEvent e) {
/* 40 */     if (e.getBlock().getType().toString().equals("SEAGRASS") || 
/* 41 */       e.getBlock().getType().toString().equals("TALL_SEAGRASS") || 
/* 42 */       e.getBlock().getType().toString().equals("KELP") || 
/* 43 */       e.getBlock().getType().toString().equals("KELP_PLANT")) {
/* 44 */       if (Utility.remove.contains(e.getBlock()))
/* 45 */         return;  Utility.remove.add(e.getBlock());
/* 46 */       this.scheduler.runTaskLater(Main.instance, () -> Utility.remove.remove(paramBlockPhysicsEvent.getBlock()), 
/*    */           
/* 48 */           100L);
/*    */       return;
/*    */     } 
/* 51 */     if (!Main.status)
/* 52 */       return;  if (!Utility.isWater(e.getBlock()))
/* 53 */       return;  if (!Config.isWorldEnabled(e.getBlock().getLocation()))
/*    */       return; 
/* 55 */     e.setCancelled(true);
/* 56 */     Data.WaterFlow.addBlock(e.getBlock());
/*    */   }
/*    */   
/*    */   @EventHandler
/*    */   public void onBlockBreak(BlockBreakEvent e) {
/* 61 */     if (!Main.status)
/* 62 */       return;  if (!e.isCancelled())
/* 63 */       WaterList.setType(e.getBlock(), Material.AIR); 
/*    */   }
/*    */   
/*    */   @EventHandler
/*    */   public void onBlockPlace(BlockPlaceEvent e) {
/* 68 */     if (!Main.status)
/* 69 */       return;  if (!e.isCancelled())
/* 70 */       WaterList.setType(e.getBlock(), e.getBlock().getType()); 
/*    */   }
/*    */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\events\WaterEvents.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */