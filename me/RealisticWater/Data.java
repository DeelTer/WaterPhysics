/*    */ package me.RealisticWater;
/*    */ 
/*    */ import java.util.concurrent.ConcurrentLinkedQueue;
/*    */ import org.bukkit.Material;
/*    */ import org.bukkit.block.Block;
/*    */ 
/*    */ 
/*    */ public class Data
/*    */ {
/*    */   public static boolean isMechanicBlocked = false;
/*    */   
/*    */   public static class WaterFlow
/*    */   {
/* 14 */     public static ConcurrentLinkedQueue<Block> processingQueue = new ConcurrentLinkedQueue<>();
/*    */ 
/*    */     
/*    */     public static void addBlock(Block block) {
/* 18 */       if (!processingQueue.contains(block)) {
/* 19 */         processingQueue.offer(block);
/*    */       }
/*    */     }
/*    */   }
/*    */   
/*    */   public static class WaterFlowSync
/*    */   {
/* 26 */     public static ConcurrentLinkedQueue<Block> list = new ConcurrentLinkedQueue<>();
/* 27 */     public static ConcurrentLinkedQueue<Block> breakNaturally = new ConcurrentLinkedQueue<>();
/*    */     
/*    */     public static void add(Block b) {
/* 30 */       if (!list.contains(b))
/* 31 */         list.add(b); 
/*    */     }
/*    */     
/*    */     public static void addBreak(Block b) {
/* 35 */       if (!breakNaturally.contains(b)) {
/* 36 */         breakNaturally.add(b);
/* 37 */         WaterList.setType(b, Material.AIR);
/*    */       } 
/*    */     }
/*    */   }
/*    */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\Data.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */