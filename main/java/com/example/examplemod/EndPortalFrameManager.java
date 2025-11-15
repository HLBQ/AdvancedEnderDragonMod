package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EndPortalFrameManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        
        Player player = event.getEntity();
        Level level = player.level();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        
        // 检查是否是手持末影之眼右键点击末地传送门框架
        if (state.is(Blocks.END_PORTAL_FRAME) && itemStack.is(Items.ENDER_EYE)) {
            LOGGER.info("玩家 {} 在位置 {} 使用末影之眼点击传送门框架", player.getName().getString(), pos);
            // 延迟处理，等待系统刷新
            if (!level.isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) level;
                serverLevel.getServer().execute(() -> {
                    checkAndActivatePortal(serverLevel, pos, player);
                });
            }
        }
    }
    
    private static void checkAndActivatePortal(ServerLevel level, BlockPos clickedPos, Player player) {
        // 以点击的方块为中心，搜索半径10格内的末地传送门框架
        List<BlockPos> framePositions = findEndPortalFrames(level, clickedPos, 10);
        LOGGER.info("在半径10格内找到 {} 个传送门框架", framePositions.size());
        
        // 检查是否有12个框架且都有眼睛
        if (framePositions.size() == 12 && allFramesHaveEyes(level, framePositions)) {
            LOGGER.info("找到12个有眼睛的传送门框架，开始计算中心位置");
            // 计算中心位置
            BlockPos centerPos = calculateCenterPosition(framePositions);
            LOGGER.info("计算出的中心位置: {}", centerPos);
            
            // 检查每个框架到中心的距离是否正确（大于1个方块，小于2个方块）
            if (isValidPortalStructure(framePositions, centerPos)) {
                LOGGER.info("传送门结构验证通过，开始创建传送门方块");
                // 创建9个传送门方块到传送门框架中间
                createPortalBlocks(level, centerPos);
                // 消耗末影之眼
                player.getItemInHand(InteractionHand.MAIN_HAND).shrink(1);
                LOGGER.info("传送门激活成功，末影之眼已消耗");
            } else {
                LOGGER.info("传送门结构验证失败，距离不符合要求");
            }
        } else {
            LOGGER.info("未找到12个有眼睛的传送门框架，当前数量: {}", framePositions.size());
        }
    }
    
    private static List<BlockPos> findEndPortalFrames(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> framePositions = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -radius; y <= radius; y++) {
                    BlockPos checkPos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(checkPos);
                    
                    if (state.is(Blocks.END_PORTAL_FRAME)) {
                        framePositions.add(checkPos);
                    }
                }
            }
        }
        
        return framePositions;
    }
    
    private static boolean allFramesHaveEyes(ServerLevel level, List<BlockPos> framePositions) {
        for (BlockPos pos : framePositions) {
            BlockState state = level.getBlockState(pos);
            if (!state.getValue(EndPortalFrameBlock.HAS_EYE)) {
                return false;
            }
        }
        return true;
    }
    
    private static BlockPos calculateCenterPosition(List<BlockPos> framePositions) {
        if (framePositions.isEmpty()) return BlockPos.ZERO;
        
        int totalX = 0;
        int totalY = 0;
        int totalZ = 0;
        
        for (BlockPos pos : framePositions) {
            totalX += pos.getX();
            totalY += pos.getY();
            totalZ += pos.getZ();
        }
        
        int centerX = totalX / framePositions.size();
        int centerY = totalY / framePositions.size();
        int centerZ = totalZ / framePositions.size();
        
        return new BlockPos(centerX, centerY, centerZ);
    }
    
    private static boolean isValidPortalStructure(List<BlockPos> framePositions, BlockPos centerPos) {
        // 检查框架数量是否为12
        if (framePositions.size() != 12) return false;
        
        // 检查每个框架到中心的距离是否正确
        for (BlockPos framePos : framePositions) {
            double distance = Math.sqrt(
                Math.pow(framePos.getX() - centerPos.getX(), 2) +
                Math.pow(framePos.getZ() - centerPos.getZ(), 2)
            );
            
            // 距离应该大于1个方块，小于2个方块
            if (distance <= 1.0 || distance >= 2.9) {
                return false;
            }
        }
        
        return true;
    }
    
    private static void createPortalBlocks(ServerLevel level, BlockPos centerPos) {
        // 在框架中间创建3x3的传送门方块（模拟原版传送门开启）
        int createdCount = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos portalPos = centerPos.offset(x, 0, z);
                
                // 检查该位置是否为空（允许创建传送门）
                if (level.getBlockState(portalPos).isAir()) {
                    level.setBlockAndUpdate(portalPos, Blocks.END_PORTAL.defaultBlockState());
                    createdCount++;
                    LOGGER.info("创建传送门方块在位置: {}", portalPos);
                } else {
                    LOGGER.info("位置 {} 不是空气，无法创建传送门方块", portalPos);
                }
            }
        }
        
        LOGGER.info("总共创建了 {} 个传送门方块", createdCount);
        
        // 播放音效和粒子效果
        level.levelEvent(3003, centerPos, 0);
    }
}
