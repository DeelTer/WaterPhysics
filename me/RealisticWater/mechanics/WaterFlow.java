/*     */ package me.RealisticWater.mechanics;
/*     */ 
/*     */ import java.util.HashSet;
/*     */ import me.RealisticWater.Config;
/*     */ import me.RealisticWater.Data;
/*     */ import me.RealisticWater.Main;
/*     */ import me.RealisticWater.Utility;
/*     */ import me.RealisticWater.WaterList;
/*     */ import org.bukkit.Material;
/*     */ import org.bukkit.block.Block;
/*     */ import org.bukkit.block.BlockFace;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ public class WaterFlow
/*     */   implements Runnable
/*     */ {
/*     */   public static boolean stopForASec = false;
/*     */   boolean stop = false;
/*     */   boolean blocked = false;
/*  23 */   int times = 0;
/*     */   
/*  25 */   long time1 = 0L;
/*  26 */   long time2 = 0L;
/*  27 */   long time3 = 0L;
/*  28 */   long time4 = 0L;
/*  29 */   long time5 = 0L;
/*  30 */   long time6 = 0L;
/*     */ 
/*     */ 
/*     */   
/*     */   public void run() {
/*  35 */     if (this.blocked)
/*  36 */       return;  if (!Main.status)
/*     */       return; 
/*  38 */     int size = Data.WaterFlow.processingQueue.size();
/*  39 */     if (size == 0) {
/*  40 */       this.times++;
/*     */       
/*  42 */       if (this.times > 100) {
/*  43 */         this.times = 0;
/*  44 */         WaterList.clearAllblocks();
/*     */       } 
/*     */       return;
/*     */     } 
/*  48 */     this.blocked = true;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/*  54 */     long max1 = 0L;
/*  55 */     long max2 = 0L;
/*  56 */     long max3 = 0L;
/*  57 */     long max4 = 0L;
/*  58 */     long max5 = 0L;
/*  59 */     long ns = System.currentTimeMillis();
/*  60 */     for (int i = 0; i < size; i++) {
/*  61 */       flow();
/*  62 */       if (this.time1 > max1)
/*  63 */         max1 = this.time1; 
/*  64 */       if (this.time2 > max2)
/*  65 */         max2 = this.time2; 
/*  66 */       if (this.time3 > max3)
/*  67 */         max3 = this.time3; 
/*  68 */       if (this.time4 > max4)
/*  69 */         max4 = this.time4; 
/*  70 */       if (this.time5 > max5)
/*  71 */         max5 = this.time5; 
/*     */     } 
/*  73 */     this.time6 = System.currentTimeMillis() - ns;
/*     */     
/*  75 */     Utility.debug(String.valueOf(max1) + " / " + max2 + " / " + max3 + " / " + max4 + " / " + max5 + " / [" + this.time6 + "] <<<< " + size);
/*  76 */     this.blocked = false;
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   private void flow() {
/*  82 */     Block block = Data.WaterFlow.processingQueue.poll();
/*  83 */     if (block == null)
/*  84 */       return;  if (!Utility.isChunkLoaded(block.getLocation()))
/*  85 */       return;  if (!Utility.isNear(block.getLocation()))
/*  86 */       return;  if (!Config.isWorldEnabled(block.getLocation()))
/*  87 */       return;  if (!Utility.isWater(block))
/*     */       return; 
/*  89 */     Block down = block.getRelative(BlockFace.DOWN);
/*  90 */     Block up = block.getRelative(BlockFace.UP);
/*     */     
/*  92 */     long ns = System.currentTimeMillis();
/*  93 */     if (Utility.isAir(down) || (Utility.isWater(down) && Utility.getData(down) != 0)) {
/*  94 */       flowDown(block);
/*     */       return;
/*     */     } 
/*  97 */     this.time1 = System.currentTimeMillis() - ns;
/*     */     
/*  99 */     ns = System.currentTimeMillis();
/* 100 */     if (Utility.isWater(block) && Utility.getData(block) != 0);
/*     */     
/* 102 */     this.time2 = System.currentTimeMillis() - ns;
/*     */     
/* 104 */     ns = System.currentTimeMillis();
/* 105 */     flowSideways(block);
/* 106 */     this.time3 = System.currentTimeMillis() - ns;
/*     */     
/* 108 */     ns = System.currentTimeMillis();
/* 109 */     this.stop = false;
/* 110 */     if (Utility.isWater(block) && Utility.getData(block) == 6)
/* 111 */       giveToSevenAir(block, block, 0, new HashSet<>()); 
/* 112 */     this.time4 = System.currentTimeMillis() - ns;
/*     */     
/* 114 */     ns = System.currentTimeMillis();
/* 115 */     this.stop = false;
/* 116 */     if (Utility.isWater(block) && Utility.getData(block) == 7 && Utility.isWater(down) && Utility.getData(down) == 0)
/* 117 */       givefromSevenUp(block, down, 0, new HashSet<>()); 
/* 118 */     this.time5 = System.currentTimeMillis() - ns;
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */     
/* 131 */     if (Utility.isWater(block) && Utility.getData(block) != 0 && Utility.isWater(up)) {
/* 132 */       Data.WaterFlow.addBlock(up);
/*     */     }
/*     */   }
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */ 
/*     */   
/*     */   private void givefromSevenUp(Block block, Block from, int time, HashSet<Block> pass) {
/* 172 */     if (time > 15)
/* 173 */       return;  if (this.stop)
/* 174 */       return;  if (!Utility.isWater(block))
/* 175 */       return;  time++;
/* 176 */     for (Block to : Utility.getSurroundingBlocks(from)) {
/* 177 */       if (this.stop)
/* 178 */         return;  if (block.equals(to) || 
/* 179 */         pass.contains(to) || 
/* 180 */         !Utility.isWater(to))
/* 181 */         continue;  pass.add(to);
/* 182 */       if (Utility.getData(to) == 0) {
/* 183 */         givefromSevenUp(block, to, time, pass); continue;
/*     */       } 
/* 185 */       Utility.setData(to, Utility.getData(to) - 1);
/* 186 */       Utility.setType(block, Material.AIR);
/* 187 */       this.stop = true;
/*     */       return;
/*     */     } 
/*     */   }
/*     */ 
/*     */ 
/*     */   
/*     */   private void giveToSevenAir(Block block, Block from, int time, HashSet<Block> pass) {
/* 195 */     if (time > 15)
/* 196 */       return;  if (this.stop)
/* 197 */       return;  if (Utility.getData(block) != 6)
/* 198 */       return;  time++;
/* 199 */     for (Block to : Utility.getSurroundingBlocks(from)) {
/* 200 */       if (this.stop)
/* 201 */         return;  if (block.equals(to))
/* 202 */         continue;  if (Utility.isWater(to)) {
/* 203 */         if (Utility.getData(to) != 7 || 
/* 204 */           pass.contains(to))
/* 205 */           continue;  pass.add(to);
/* 206 */         giveToSevenAir(block, to, time, pass); continue;
/* 207 */       }  if (Utility.isAir(to)) {
/* 208 */         Block down = block.getRelative(BlockFace.DOWN);
/* 209 */         if (Utility.isAir(down)) {
/* 210 */           while (Utility.isAir(to.getRelative(BlockFace.DOWN))) {
/* 211 */             to = to.getRelative(BlockFace.DOWN);
/*     */           }
/* 213 */           if (Utility.isWater(to) && Utility.getData(to) != 0) {
/* 214 */             int data = Utility.getData(to);
/* 215 */             data--;
/* 216 */             Utility.setData(to, data);
/* 217 */             this.stop = true;
/*     */             return;
/*     */           } 
/* 220 */           to = to.getRelative(BlockFace.UP);
/*     */         } 
/* 222 */         Utility.setWaterType(to);
/* 223 */         Utility.setData(to, 7);
/* 224 */         Utility.setData(block, 7);
/* 225 */         Data.WaterFlow.processingQueue.add(to);
/* 226 */         this.stop = true;
/*     */         return;
/*     */       } 
/*     */     } 
/*     */   }
/*     */ 
/*     */   
/*     */   private void flowSideways(Block block) {
/* 234 */     if (!Utility.isWater(block))
/* 235 */       return;  int blockdata = Utility.getData(block);
/* 236 */     for (Block to : Utility.getSurroundingBlocks(block)) {
/* 237 */       if (blockdata < 7) {
/* 238 */         if (Utility.isAir(to)) {
/* 239 */           int size = 7;
/*     */           
/* 241 */           if (blockdata == 0) {
/* 242 */             blockdata = 4;
/* 243 */             size = 4;
/* 244 */           } else if (blockdata == 1) {
/* 245 */             blockdata = 4;
/* 246 */             size = 5;
/* 247 */           } else if (blockdata == 2) {
/* 248 */             blockdata = 5;
/* 249 */             size = 5;
/* 250 */           } else if (blockdata == 3) {
/* 251 */             blockdata = 5;
/* 252 */             size = 6;
/* 253 */           } else if (blockdata == 4) {
/* 254 */             blockdata = 6;
/* 255 */             size = 6;
/* 256 */           } else if (blockdata == 5) {
/* 257 */             blockdata = 6;
/* 258 */             size = 7;
/* 259 */           } else if (blockdata == 6) {
/* 260 */             blockdata = 7;
/* 261 */             size = 7;
/*     */           } 
/*     */           
/* 264 */           Utility.setWaterType(to);
/* 265 */           Utility.setData(to, size); continue;
/*     */         } 
/* 267 */         if (Utility.isWater(to)) {
/* 268 */           int size = Utility.getData(to);
/* 269 */           if (size - 1 > blockdata) {
/* 270 */             while (size - 1 > blockdata && blockdata < 8) {
/* 271 */               size--;
/* 272 */               blockdata++;
/*     */             } 
/* 274 */             Utility.setData(to, size);
/*     */           }  continue;
/* 276 */         }  if (to.getType().equals(Material.LAVA)) {
/* 277 */           Utility.setType(to, Material.COBBLESTONE);
/* 278 */           blockdata++;
/*     */         } 
/*     */       } 
/*     */     } 
/* 282 */     if (blockdata < 8 && blockdata != Utility.getData(block)) {
/* 283 */       Utility.setData(block, blockdata);
/*     */     }
/*     */   }
/*     */ 
/*     */   
/*     */   private void flowDown(Block block) {
/* 289 */     for (Block from : Utility.getSurroundingBlocks(block))
/* 290 */       Data.WaterFlow.addBlock(from); 
/* 291 */     Block down = block.getRelative(BlockFace.DOWN);
/* 292 */     if (Utility.isAir(down)) {
/*     */       
/* 294 */       while (Utility.isAir(down) && 
/* 295 */         down.getY() >= 0) {
/* 296 */         down = down.getRelative(BlockFace.DOWN);
/*     */       }
/*     */       
/* 299 */       if (Utility.isWater(down)) {
/* 300 */         int size = Utility.getData(down);
/* 301 */         int from = Utility.getData(block);
/*     */         
/* 303 */         int needs = 8 - size;
/*     */         
/* 305 */         if (from == needs) {
/* 306 */           size = 0;
/* 307 */           from = 8;
/*     */         } else {
/* 309 */           while (size != 0 && from < 8) {
/* 310 */             size--;
/* 311 */             from++;
/*     */           } 
/*     */         } 
/*     */         
/* 315 */         if (from < 8) {
/* 316 */           Utility.setWaterType(down.getRelative(BlockFace.UP));
/* 317 */           Utility.setData(down.getRelative(BlockFace.UP), from);
/*     */         } 
/*     */         
/* 320 */         Utility.setType(block, Material.AIR);
/* 321 */         Utility.setData(down, size);
/*     */         return;
/*     */       } 
/* 324 */       down = down.getRelative(BlockFace.UP);
/*     */       
/* 326 */       Utility.setWaterType(down);
/* 327 */       Utility.setData(down, Utility.getData(block));
/* 328 */       Utility.setType(block, Material.AIR);
/* 329 */     } else if (Utility.isWater(down) && Utility.getData(down) != 0) {
/* 330 */       int size = Utility.getData(down);
/* 331 */       int from = Utility.getData(block);
/*     */       
/* 333 */       int needs = 8 - size;
/*     */       
/* 335 */       if (from == needs) {
/* 336 */         size = 0;
/* 337 */         from = 8;
/*     */       } else {
/* 339 */         while (size != 0 && from < 8) {
/* 340 */           size--;
/* 341 */           from++;
/*     */         } 
/*     */       } 
/*     */       
/* 345 */       Utility.setData(block, from);
/* 346 */       Utility.setData(down, size);
/*     */     } 
/*     */   }
/*     */ }


/* Location:              C:\Users\DeelTer\Downloads\water.jar!\me\RealisticWater\mechanics\WaterFlow.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */