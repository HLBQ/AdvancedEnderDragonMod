package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PortalManager {
    private static final Set<BlockPos> destroyedPortalBlocks = new HashSet<>();
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // 检查所有末地世界
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() == net.minecraft.world.level.Level.END) {
                checkAndManagePortal(level);
            }
        }
    }
    
    private static void checkAndManagePortal(ServerLevel level) {
        // 检查是否有活跃的二阶段末影龙
        boolean hasPhase2Dragon = false;
        
        // 使用一个大的AABB来搜索整个末地
        AABB searchArea = new AABB(-300, 0, -300, 300, 256, 300);
        for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, searchArea)) {
            EnderDragonManager.DragonData data = EnderDragonManager.getDragonData(dragon.getUUID());
            if (data != null && data.phase == 2) {
                hasPhase2Dragon = true;
                break;
            }
        }
        
        // 如果有二阶段末影龙，检查并破坏传送门
        if (hasPhase2Dragon) {
            destroyPortalIfExists(level);
        } else {
            // 如果没有二阶段末影龙，恢复被破坏的传送门
            restorePortalIfDestroyed(level);
        }
    }
    
    private static void destroyPortalIfExists(ServerLevel level) {
        BlockPos portalCenter = findPortalCenter(level);
        if (portalCenter == null) return;
        
        // 破坏传送门方块
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = portalCenter.offset(x, 0, z);
                BlockState state = level.getBlockState(pos);
                
                if (state.is(Blocks.END_PORTAL)) {
                    // 记录被破坏的方块位置
                    destroyedPortalBlocks.add(pos);
                    // 破坏传送门方块
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }
    
    private static void restorePortalIfDestroyed(ServerLevel level) {
        if (destroyedPortalBlocks.isEmpty()) return;
        
        // 恢复所有被破坏的传送门方块
        for (BlockPos pos : destroyedPortalBlocks) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.END_PORTAL.defaultBlockState());
            }
        }
        
        // 清空记录
        destroyedPortalBlocks.clear();
    }
    
    private static BlockPos findPortalCenter(ServerLevel level) {
        // 查找末地传送门中心位置
        // 通常位于 (0, 65, 0) 附近
        BlockPos center = new BlockPos(0, 65, 0);
        
        // 检查该位置周围是否有传送门方块
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = center.offset(x, 0, z);
                if (level.getBlockState(pos).is(Blocks.END_PORTAL)) {
                    return center;
                }
            }
        }
        
        return null;
    }
    
    // 当二阶段末影龙死亡时调用此方法
    public static void onPhase2DragonDeath() {
        // 清空被破坏的传送门记录，以便下次可以重新生成
        destroyedPortalBlocks.clear();
    }
}
