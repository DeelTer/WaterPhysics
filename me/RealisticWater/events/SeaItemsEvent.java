/*     */ package me.RealisticWater.events;
/*     */ 
/*     */ import me.RealisticWater.Main;
/*     */ import me.RealisticWater.Utility;
/*     */ import org.bukkit.Bukkit;
/*     */ import org.bukkit.Location;
/*     */ import org.bukkit.Material;
/*     */ import org.bukkit.block.Block;
/*     */ import org.bukkit.block.BlockFace;
/*     */ import org.bukkit.event.EventHandler;
/*     */ import org.bukkit.event.Listener;
/*     */ import org.bukkit.event.block.BlockBreakEvent;
/*     */ import org.bukkit.event.block.BlockPlaceEvent;
/*     */ import org.bukkit.event.block.BlockSpreadEvent;
/*     */ import org.bukkit.scheduler.BukkitScheduler;
/*     */ 
/*     */ public class SeaItemsEvent
/*     */   implements Listener
/*     */ {
/*  20 */   BukkitScheduler scheduler = Bukkit.getScheduler();
/*     */   
/*     */   @EventHandler
/*     */   public void onBlockGrow(BlockSpreadEvent e) {
/*  24 */     Block b = e.getSource();
/*  25 */     if (b.getType() == Material.SEAGRASS || 
/*  26 */       b.getType() == Material.TALL_SEAGRASS || 
/*  27 */       b.getType() == Material.KELP || 
/*  28 */       b.getType() == Material.KELP_PLANT) {
/*     */       
/*  30 */       Block up = b.getRelative(BlockFace.UP);
/*  31 */       if (!Utility.isWater(up)) { e.setCancelled(true); return; }
/*  32 */        if (Utility.getData(up) != 0) { e.setCancelled(true); return; }
/*     */       
/*  34 */       Location l = b.getLocation();
/*  35 */       int x = l.getBlockX();
/*  36 */       int y = l.getBlockY();
/*  37 */       int z = l.getBlockZ();
/*     */       
/*  39 */       for (int x1 = x - 2; x1 < x + 2; x1++) {
/*  40 */         for (int y1 = y - 1; y1 < y + 2; y1++) {
/*  41 */           for (int z1 = z - 2; z1 < z + 2; z1++) {
/*  42 */             Block b2 = l.getWorld().getBlockAt(x1, y1, z1);
/*  43 */             if ((b2.getType() == Material.SEAGRASS || 
/*  44 */               b2.getType() == Material.TALL_SEAGRASS || 
/*  45 */               b2.getType() == Material.KELP || 
/*  46 */               b2.getType() == Material.KELP_PLANT) && 
/*  47 */               !Utility.remove.contains(b2)) {
/*  48 */               Utility.remove.add(b2);
/*  49 */               this.scheduler.runTaskLater(Main.instance, () -> Utility.remove.remove(paramBlock), 
/*     */                   
/*  51 */                   100L);
/*     */             } 
/*     */           } 
/*     */         } 
/*     */       } 
/*     */     } 
/*     */   } @EventHandler
/*     */   public void onBlockPlace(BlockPlaceEvent e) {
/*  59 */     Block b = e.getBlock();
/*  60 */     if (b.getType() == Material.SEAGRASS || 
/*  61 */       b.getType() == Material.TALL_SEAGRASS || 
/*  62 */       b.getType() == Material.KELP || 
/*  63 */       b.getType() == Material.KELP_PLANT) {
/*     */       
/*  65 */       Location l = b.getLocation();
/*  66 */       int x = l.getBlockX();
/*  67 */       int y = l.getBlockY();
/*  68 */       int z = l.getBlockZ();
/*     */       
/*  70 */       for (int x1 = x - 2; x1 < x + 2; x1++) {
/*  71 */         for (int z1 = z - 2; z1 < z + 2; z1++) {
/*  72 */           Block b2 = l.getWorld().getBlockAt(x1, y, z1);
/*  73 */           if ((b2.getType() == Material.SEAGRASS || 
/*  74 */             b2.getType() == Material.TALL_SEAGRASS || 
/*  75 */             b2.getType() == Material.KELP || 
/*  76 */             b2.getType() == Material.KELP_PLANT) && 
/*  77 */             !Utility.remove.contains(b2)) {
/*  78 */             Utility.remove.add(b2);
/*  79 */             this.scheduler.runTaskLater(Main.instance, () -> Utility.remove.remove(paramBlock), 
/*     */                 
/*  81 */                 100L);
/*     */           } 
/*     */         } 
/*     */       } 
/*     */     } 
/*     */   }
/*     */   @EventHandler
/*     */   public void onBlockBreak(BlockBreakEvent e) {
/*  89 */     Block b = e.getBlock();
/*  90 */     if (b.getType() == Material.SEAGRASS || 
/*  91 */       b.getType() == Material.TALL_SEAGRASS || 
/*  92 */       b.getType() == Material.KELP || 
/*  93 */       b.getType() == Material.KELP_PLANT) {
/*     */       
/*  95 */       Location l = b.getLocation();
/*  96 */       int x = l.getBlockX();
/*  97 */       int y = l.getBlockY();
/*  98 */       int z = l.getBlockZ();
/*     */       
/* 100 */       for (int x1 = x - 2; x1 < x + 2; x1++) {
/* 101 */         for (int z1 = z - 2; z1 < z + 2; z1++) {
/* 102 */           Block b2 = l.getWorld().getBlockAt(x1, y, z1);
/* 103 */           if ((b2.getType() == Material.SEAGRASS || 
/* 104 */             b2.getType() == Material.TALL_SEAGRASS || 
/* 105 */             b2.getType() == Material.KELP || 
/* 106 */             b2.getType() == Material.KELP_PLANT) && 
/* 107 */             !Utility.remove.contains(b2)) {
/* 108 */             Utility.remove.add(b2);
/* 109 */             this.scheduler.runTaskLater(Main.instance, () -> Utility.remove.remove(paramBlock), 
/*     */                 
/* 111 */                 100L);
/*     */           } 
/*     */         } 
/*     */       } 
/*     */     } 
/*     */   }
/*     */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\events\SeaItemsEvent.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */