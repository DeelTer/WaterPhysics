/*     */ package me.RealisticWater;
/*     */ 
/*     */ import java.util.ArrayList;
/*     */ import java.util.Collections;
/*     */ import java.util.List;
/*     */ import org.bukkit.Bukkit;
/*     */ import org.bukkit.Location;
/*     */ import org.bukkit.Material;
/*     */ import org.bukkit.block.Block;
/*     */ import org.bukkit.block.BlockFace;
/*     */ import org.bukkit.block.data.BlockData;
/*     */ import org.bukkit.block.data.Levelled;
/*     */ import org.bukkit.block.data.Waterlogged;
/*     */ import org.bukkit.entity.Player;
/*     */ 
/*     */ public class Utility
/*     */ {
/*     */   public static List<Block> getSurroundingBlocks(Block block) {
/*  19 */     List<Block> surroundingBlocks = new ArrayList<>();
/*     */     
/*  21 */     BlockFace[] faces = new BlockFace[4];
/*  22 */     faces[0] = BlockFace.NORTH;
/*  23 */     faces[1] = BlockFace.EAST;
/*  24 */     faces[2] = BlockFace.SOUTH;
/*  25 */     faces[3] = BlockFace.WEST;
/*     */     
/*  27 */     for (int a = 0; a < faces.length; a++) {
/*  28 */       checkIfSeaItems(block.getRelative(faces[a]));
/*  29 */       if (arTinka(block.getRelative(faces[a]))) {
/*  30 */         surroundingBlocks.add(block.getRelative(faces[a]));
/*     */       }
/*     */     } 
/*  33 */     Collections.shuffle(surroundingBlocks);
/*  34 */     return surroundingBlocks;
/*     */   }
/*     */   
/*     */   public static boolean arTinka(Block block) {
/*  38 */     String type = getType(block).toString();
/*  39 */     if (type.equals("AIR") || 
/*  40 */       type.equals("CAVE_AIR") || 
/*  41 */       type.equals("STATIONARY_WATER") || 
/*  42 */       type.equals("WATER") || 
/*  43 */       type.equals("STATIONARY_LAVA") || 
/*  44 */       type.equals("LAVA")) {
/*  45 */       return true;
/*     */     }
/*     */     
/*  48 */     return false;
/*     */   }
/*     */   
/*     */   public static boolean isNear(Location location) {
/*  52 */     for (Player p : Bukkit.getOnlinePlayers()) {
/*  53 */       if (p.getWorld().equals(location.getWorld()) && 
/*  54 */         p.getLocation().distance(location) < 64.0D)
/*  55 */         return true; 
/*  56 */     }  return false;
/*     */   }
/*     */   
/*     */   public static boolean isWater(Block block) {
/*  60 */     if (block == null)
/*  61 */       return false; 
/*  62 */     if (getType(block) == Material.WATER) {
/*  63 */       return true;
/*     */     }
/*  65 */     return false;
/*     */   }
/*     */   
/*     */   public static boolean isAir(Block block) {
/*  69 */     if (block == null)
/*  70 */       return false; 
/*  71 */     if (getType(block).toString().equals("AIR") || 
/*  72 */       getType(block).toString().equals("CAVE_AIR"))
/*  73 */       return true; 
/*  74 */     return false;
/*     */   }
/*     */ 
/*     */   
/*     */   public static void debug(String string) {}
/*     */ 
/*     */   
/*     */   public static boolean isChunkLoaded(Location l) {
/*  82 */     return l.getWorld().isChunkLoaded(l.getBlockX() / 16, l.getBlockZ() / 16);
/*     */   }
/*     */   
/*     */   public static Material getType(Block b) {
/*  86 */     return WaterList.getType(b);
/*     */   }
/*     */ 
/*     */   
/*     */   public static void setType(Block b, Material type) {
/*  91 */     WaterList.setType(b, type);
/*  92 */     Data.WaterFlowSync.add(b);
/*     */   }
/*     */ 
/*     */   
/*     */   public static void setData(Block b, int data) {
/*  97 */     if (data < 8) {
/*  98 */       WaterList.setData(b, data);
/*  99 */       Data.WaterFlow.addBlock(b);
/* 100 */       Data.WaterFlowSync.add(b);
/*     */     } else {
/* 102 */       setType(b, Material.AIR);
/*     */     } 
/*     */   }
/*     */   
/*     */   public static int getDataSync(Block b) {
/* 107 */     if (b.getType().equals(Material.WATER))
/* 108 */       return ((Levelled)b.getBlockData()).getLevel(); 
/* 109 */     return 0;
/*     */   }
/*     */ 
/*     */   
/*     */   public static void setDataSync(Block b, int data) {
/* 114 */     if (data < 8) {
/* 115 */       Levelled levelledData = (Levelled)b.getBlockData();
/* 116 */       levelledData.setLevel(data);
/* 117 */       b.setBlockData((BlockData)levelledData, false);
/* 118 */       WaterList.setData(b, data);
/* 119 */       Data.WaterFlow.addBlock(b);
/*     */     } else {
/* 121 */       WaterList.setType(b, Material.AIR);
/* 122 */       setType(b, Material.AIR);
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   public static void setTypeSync(Block b, Material type) {
/* 128 */     b.setType(type);
/* 129 */     WaterList.setType(b, type);
/*     */   }
/*     */   
/*     */   public static int getData(Block b) {
/* 133 */     return WaterList.getData(b);
/*     */   }
/*     */   
/* 136 */   public static List<Block> remove = new ArrayList<>();
/*     */   public static void checkIfSeaItems(Block from) {
/* 138 */     if (from.getType() == Material.SEAGRASS || 
/* 139 */       from.getType() == Material.TALL_SEAGRASS || 
/* 140 */       from.getType() == Material.KELP || 
/* 141 */       from.getType() == Material.KELP_PLANT) {
/* 142 */       if (!remove.contains(from))
/* 143 */         WaterList.setType(from, Material.WATER); 
/*     */       return;
/*     */     } 
/* 146 */     if (from.getType().equals(Material.BUBBLE_COLUMN)) {
/* 147 */       WaterList.setType(from, Material.WATER);
/*     */       return;
/*     */     } 
/*     */     try {
/* 151 */       Waterlogged blockWithWater = (Waterlogged)from.getBlockData();
/* 152 */       if (blockWithWater.isWaterlogged()) {
/* 153 */         blockWithWater.setWaterlogged(false);
/* 154 */         from.setBlockData((BlockData)blockWithWater);
/*     */         return;
/*     */       } 
/* 157 */     } catch (Exception exception) {}
/*     */   }
/*     */   
/*     */   public static void setWaterType(Block to) {
/* 161 */     setType(to, Material.WATER);
/*     */   }
/*     */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\Utility.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */