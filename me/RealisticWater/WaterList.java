/*    */ package me.RealisticWater;
/*    */ 
/*    */ import java.util.concurrent.ConcurrentHashMap;
/*    */ import org.bukkit.Material;
/*    */ import org.bukkit.block.Block;
/*    */ 
/*    */ 
/*    */ public class WaterList
/*    */ {
/* 10 */   private static ConcurrentHashMap<Block, Integer> mapData = new ConcurrentHashMap<>();
/* 11 */   private static ConcurrentHashMap<Block, Material> mapType = new ConcurrentHashMap<>();
/*    */   
/*    */   public static int getData(Block b) {
/* 14 */     if (mapData.containsKey(b))
/* 15 */       return ((Integer)mapData.get(b)).intValue(); 
/*    */     try {
/* 17 */       int data = Utility.getDataSync(b);
/* 18 */       mapData.put(b, Integer.valueOf(data));
/* 19 */       return data;
/* 20 */     } catch (Exception exception) {
/*    */       
/* 22 */       return 0;
/*    */     } 
/*    */   }
/*    */   public static void setData(Block b, int level) {
/* 26 */     mapData.put(b, Integer.valueOf(level));
/*    */   }
/*    */   
/*    */   public static void setType(Block b, Material type) {
/* 30 */     mapType.put(b, type);
/*    */   }
/*    */   
/*    */   public static Material getType(Block b) {
/* 34 */     if (mapType.containsKey(b))
/* 35 */       return mapType.get(b); 
/* 36 */     Material type = b.getType();
/* 37 */     if (type.equals(Material.WATER))
/* 38 */       mapType.put(b, type); 
/* 39 */     return type;
/*    */   }
/*    */   
/*    */   public static void clearAllblocks() {
/* 43 */     mapData.clear();
/* 44 */     mapType.clear();
/*    */   }
/*    */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\WaterList.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */