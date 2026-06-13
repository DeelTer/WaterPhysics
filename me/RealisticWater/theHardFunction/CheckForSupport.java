/*    */ package me.RealisticWater.theHardFunction;
/*    */ 
/*    */ import java.util.HashSet;
/*    */ import me.RealisticWater.Utility;
/*    */ import org.bukkit.block.Block;
/*    */ import org.bukkit.block.BlockFace;
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class CheckForSupport
/*    */ {
/*    */   public boolean stop = false;
/* 19 */   public HashSet<Block> pass = new HashSet<>();
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   public CheckForSupport(Block block) {
/* 25 */     this.pass = new HashSet<>();
/* 26 */     this.stop = false;
/* 27 */     getAllTheBlocks(block, block, 0);
/*    */   }
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */   
/*    */   private void mainFunction(Block block, Block past) {
/* 40 */     if (this.stop)
/*    */       return; 
/* 42 */     if (Utility.getData(past) < Utility.getData(block) - 1) {
/* 43 */       Utility.setData(block, Utility.getData(block) - 1);
/* 44 */       Utility.setData(past, Utility.getData(past) + 1);
/* 45 */       this.stop = true;
/*    */       
/*    */       return;
/*    */     } 
/* 49 */     if (Utility.isWater(past.getRelative(BlockFace.UP))) {
/* 50 */       Block up = past.getRelative(BlockFace.UP);
/* 51 */       int size = Utility.getData(block);
/* 52 */       if (size == 0)
/*    */         return; 
/* 54 */       int upas = Utility.getData(up);
/* 55 */       while (size != 0 && upas < 8) {
/* 56 */         size--;
/* 57 */         upas++;
/*    */       } 
/* 59 */       Utility.setData(block, size);
/* 60 */       Utility.setData(up, upas);
/* 61 */       this.stop = true;
/*    */       return;
/*    */     } 
/*    */   }
/*    */   
/*    */   private void getAllTheBlocks(Block block, Block from, int time) {
/* 67 */     if (time > 15)
/*    */       return; 
/* 69 */     time++;
/* 70 */     for (Block past : Utility.getSurroundingBlocks(from)) {
/* 71 */       if (this.stop)
/* 72 */         return;  if (this.pass.contains(past) || 
/* 73 */         !Utility.isWater(past) || 
/* 74 */         block.equals(past))
/* 75 */         continue;  this.pass.add(past);
/* 76 */       if (Utility.getData(past) > Utility.getData(block)) {
/*    */         continue;
/*    */       }
/* 79 */       mainFunction(block, past);
/*    */       
/* 81 */       getAllTheBlocks(block, past, time);
/*    */     } 
/*    */   }
/*    */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\theHardFunction\CheckForSupport.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */