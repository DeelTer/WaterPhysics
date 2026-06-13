/*    */ package me.RealisticWater.mechanics;
/*    */ 
/*    */ import java.util.concurrent.ConcurrentLinkedQueue;
/*    */ import me.RealisticWater.Data;
/*    */ import me.RealisticWater.Utility;
/*    */ import me.RealisticWater.WaterList;
/*    */ import org.bukkit.block.Block;
/*    */ 
/*    */ 
/*    */ 
/*    */ public class WaterFlowSync
/*    */   implements Runnable
/*    */ {
/*    */   public void run() {
/* 15 */     ConcurrentLinkedQueue<Block> bntemp = new ConcurrentLinkedQueue<>(Data.WaterFlowSync.breakNaturally);
/* 16 */     Data.WaterFlowSync.breakNaturally.clear();
/* 17 */     for (Block b : bntemp) {
/* 18 */       b.breakNaturally();
/*    */     }
/* 20 */     ConcurrentLinkedQueue<Block> tempList = new ConcurrentLinkedQueue<>(Data.WaterFlowSync.list);
/* 21 */     Data.WaterFlowSync.list.clear();
/* 22 */     for (Block b : tempList) {
/* 23 */       if (b == null)
/* 24 */         continue;  if (!b.getType().equals(WaterList.getType(b))) {
/* 25 */         b.setType(WaterList.getType(b));
/*    */       }
/* 27 */       if (!Utility.isWater(b))
/* 28 */         continue;  int data1 = WaterList.getData(b);
/* 29 */       int data2 = Utility.getDataSync(b);
/*    */       
/* 31 */       if (data1 != data2)
/* 32 */         Utility.setDataSync(b, data1); 
/*    */     } 
/*    */   }
/*    */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\mechanics\WaterFlowSync.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */