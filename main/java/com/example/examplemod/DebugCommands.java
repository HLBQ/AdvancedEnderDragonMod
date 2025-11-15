package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class DebugCommands {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("dragon")
                .requires(source -> source.hasPermission(2)) // 需要操作员权限
                .then(Commands.literal("sethealth")
                    .then(Commands.argument("health", IntegerArgumentType.integer(1, 1000))
                        .executes(context -> setDragonHealth(context, IntegerArgumentType.getInteger(context, "health")))
                    )
                )
                .then(Commands.literal("setphase")
                    .then(Commands.argument("phase", IntegerArgumentType.integer(1, 2))
                        .executes(context -> setDragonPhase(context, IntegerArgumentType.getInteger(context, "phase")))
                    )
                )
                .then(Commands.literal("info")
                    .executes(DebugCommands::getDragonInfo)
                )
                .then(Commands.literal("forcephase2")
                    .executes(DebugCommands::forcePhase2)
                )
                .then(Commands.literal("setinvincible")
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                        .executes(context -> setInvincible(context, IntegerArgumentType.getInteger(context, "seconds")))
                    )
                )
        );
    }

    private static int setDragonHealth(CommandContext<CommandSourceStack> context, int health) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        // 搜索末地中的末影龙
        List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
            new AABB(-300, 0, -300, 300, 256, 300));
        
        if (dragons.isEmpty()) {
            source.sendFailure(Component.literal("未找到末影龙"));
            return 0;
        }
        
        EnderDragon dragon = dragons.get(0);
        EnderDragonManager.DragonData data = EnderDragonManager.getDragonData(dragon.getUUID());
        
        if (data != null) {
            // 设置血量
            dragon.setHealth(health);
            
            // 同步数据到客户端
            NetworkHandler.sendDragonDataToAll(dragon, data);
            
            source.sendSuccess(() -> Component.literal("设置末影龙血量为: " + health), true);
            LOGGER.info("操作员 {} 设置末影龙血量为: {}", source.getTextName(), health);
            return 1;
        } else {
            source.sendFailure(Component.literal("未找到末影龙数据"));
            return 0;
        }
    }

    private static int setDragonPhase(CommandContext<CommandSourceStack> context, int phase) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        // 搜索末地中的末影龙
        List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
            new AABB(-300, 0, -300, 300, 256, 300));
        
        if (dragons.isEmpty()) {
            source.sendFailure(Component.literal("未找到末影龙"));
            return 0;
        }
        
        EnderDragon dragon = dragons.get(0);
        EnderDragonManager.DragonData data = EnderDragonManager.getDragonData(dragon.getUUID());
        
        if (data != null) {
            // 设置阶段
            data.phase = phase;
            
            // 如果是阶段2，设置1000血量
            if (phase == 2) {
                dragon.setHealth(EnderDragonManager.PHASE_2_HP);
            }
            
            // 同步数据到客户端
            NetworkHandler.sendDragonDataToAll(dragon, data);
            
            source.sendSuccess(() -> Component.literal("设置末影龙阶段为: " + phase), true);
            LOGGER.info("操作员 {} 设置末影龙阶段为: {}", source.getTextName(), phase);
            return 1;
        } else {
            source.sendFailure(Component.literal("未找到末影龙数据"));
            return 0;
        }
    }

    private static int getDragonInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        // 搜索末地中的末影龙
        List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
            new AABB(-300, 0, -300, 300, 256, 300));
        
        if (dragons.isEmpty()) {
            source.sendFailure(Component.literal("未找到末影龙"));
            return 0;
        }
        
        EnderDragon dragon = dragons.get(0);
        EnderDragonManager.DragonData data = EnderDragonManager.getDragonData(dragon.getUUID());
        
        if (data != null) {
            source.sendSuccess(() -> Component.literal("末影龙信息:"), false);
            source.sendSuccess(() -> Component.literal("  UUID: " + dragon.getUUID()), false);
            source.sendSuccess(() -> Component.literal("  血量: " + dragon.getHealth() + "/" + dragon.getMaxHealth()), false);
            source.sendSuccess(() -> Component.literal("  阶段: " + data.phase), false);
            source.sendSuccess(() -> Component.literal("  无敌状态: " + data.isInvulnerable), false);
            source.sendSuccess(() -> Component.literal("  无敌剩余时间: " + data.invulnerableTicks + "tick"), false);
            source.sendSuccess(() -> Component.literal("  技能充能: " + data.skillCharge + "/" + EnderDragonManager.SKILL_COOLDOWN), false);
            source.sendSuccess(() -> Component.literal("  正在转换: " + data.isTransforming), false);
            source.sendSuccess(() -> Component.literal("  末影人目标数: " + data.endermanTargets.size()), false);
            
            LOGGER.info("操作员 {} 查询末影龙信息", source.getTextName());
            return 1;
        } else {
            source.sendFailure(Component.literal("未找到末影龙数据"));
            return 0;
        }
    }

    private static int forcePhase2(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        // 搜索末地中的末影龙
        List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
            new AABB(-300, 0, -300, 300, 256, 300));
        
        if (dragons.isEmpty()) {
            source.sendFailure(Component.literal("未找到末影龙"));
            return 0;
        }
        
        EnderDragon dragon = dragons.get(0);
        
        // 强制进入二阶段 - 模拟死亡事件
        EnderDragonManager.onDragonDeath(new net.minecraftforge.event.entity.living.LivingDeathEvent(dragon, null));
        
        source.sendSuccess(() -> Component.literal("强制末影龙进入二阶段"), true);
        LOGGER.info("操作员 {} 强制末影龙进入二阶段", source.getTextName());
        return 1;
    }

    private static int setInvincible(CommandContext<CommandSourceStack> context, int seconds) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        
        // 搜索末地中的末影龙
        List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
            new AABB(-300, 0, -300, 300, 256, 300));
        
        if (dragons.isEmpty()) {
            source.sendFailure(Component.literal("未找到末影龙"));
            return 0;
        }
        
        EnderDragon dragon = dragons.get(0);
        EnderDragonManager.DragonData data = EnderDragonManager.getDragonData(dragon.getUUID());
        
        if (data != null) {
            // 设置无敌状态
            data.isInvulnerable = true;
            data.invulnerableTicks = seconds * 20; // 转换为tick
            
            // 同步数据到客户端
            NetworkHandler.sendDragonDataToAll(dragon, data);
            
            source.sendSuccess(() -> Component.literal("设置末影龙无敌状态: " + seconds + "秒"), true);
            LOGGER.info("操作员 {} 设置末影龙无敌状态: {}秒", source.getTextName(), seconds);
            return 1;
        } else {
            source.sendFailure(Component.literal("未找到末影龙数据"));
            return 0;
        }
    }
}
