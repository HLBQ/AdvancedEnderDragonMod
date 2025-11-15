package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnderDragonManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // 存储末影龙状态数据
    private static final Map<UUID, DragonData> dragonDataMap = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> endermanParticlesMap = new ConcurrentHashMap<>();
    
    // 配置常量
    public static final int PHASE_1_HP = 1000;
    public static final int PHASE_2_HP = 1000;
    public static final int INVULNERABLE_DURATION = 1200; // 50秒 * 20 ticks
    public static final int SKILL_COOLDOWN = 600; // 30秒 * 20 ticks
    public static final int BEAM_LENGTH = 100;
    public static final int ENDERMAN_RADIUS = 64;
    public static final int ENDERMAN_HEAL_AMOUNT = 50;
    public static final int CRYSTAL_RESTORE_INTERVAL = 50; // 每2.5秒恢复一个水晶
    
    public static class DragonData {
        public int phase = 1;
        public int invulnerableTicks = 0;
        public int skillCharge = 0;
        public boolean isInvulnerable = false;
        public boolean isTransforming = false;
        public Vec3 transformPosition;
        public List<UUID> endermanTargets = new ArrayList<>();
        public Map<UUID, Integer> endermanAbsorbTimers = new HashMap<>();
        public int endermanHealCounter = 0;
        public int endermanHealDelay = 0;
        
        public DragonData() {}
    }
    
    @SubscribeEvent
    public static void onDragonDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        
        Level level = dragon.level();
        if (level.isClientSide()) return;
        
        ServerLevel serverLevel = (ServerLevel) level;
        DragonData data = dragonDataMap.get(dragon.getUUID());
        
        if (data == null || data.phase == 1) {
            // 一阶段死亡，进入二阶段
            startPhase2Transformation(serverLevel, dragon);
        } else if (data.phase == 2) {
            // 二阶段死亡，给予玩家奖励
            givePlayerRewards(serverLevel, dragon);
            // 取消绑定
            dragonDataMap.remove(dragon.getUUID());
            endermanParticlesMap.remove(dragon.getUUID());
            // 发送最终死亡消息
            sendMessageToAllPlayers(serverLevel, "末影龙已被彻底击败！");
            LOGGER.info("二阶段末影龙已死亡，UUID: {}", dragon.getUUID());
            // 不取消事件，让死亡正常进行
        }
    }
    
    @SubscribeEvent
    public static void onDragonHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) return;
        
        DragonData data = dragonDataMap.get(dragon.getUUID());
        if (data != null && data.isInvulnerable) {
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // 检查所有末地世界中的末影龙，确保它们都被绑定
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.dimension() == net.minecraft.world.level.Level.END) {
                // 搜索末地中的所有末影龙
                List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
                    new AABB(-300, 0, -300, 300, 256, 300));
                
                for (EnderDragon dragon : dragons) {
                    // 如果末影龙没有被绑定，则绑定它
                    if (!dragonDataMap.containsKey(dragon.getUUID())) {
                        LOGGER.info("检测到未绑定的末影龙，UUID: {}", dragon.getUUID());
                        bindDragon(dragon);
                    }
                }
            }
        }
        
        // 处理所有末影龙的状态更新
        for (Map.Entry<UUID, DragonData> entry : dragonDataMap.entrySet()) {
            UUID dragonId = entry.getKey();
            DragonData data = entry.getValue();
            
            // 查找末影龙实体
            for (ServerLevel level : event.getServer().getAllLevels()) {
                Entity entity = level.getEntity(dragonId);
                if (entity instanceof EnderDragon dragon) {
                    updateDragonState(dragon, data, level);
                    break;
                }
            }
        }
    }
    
    private static void startPhase2Transformation(ServerLevel level, EnderDragon dragon) {
        // 取消死亡事件
        dragon.setHealth(1.0F);
        
        // 创建二阶段数据
        DragonData data = new DragonData();
        data.phase = 2;
        data.isTransforming = true;
        data.invulnerableTicks = INVULNERABLE_DURATION;
        data.isInvulnerable = true;
        data.transformPosition = dragon.position();
        
        dragonDataMap.put(dragon.getUUID(), data);
        
        // 删除原末影龙
        dragon.remove(Entity.RemovalReason.DISCARDED);
        
        // 创建新的二阶段末影龙
        createPhase2Dragon(level, data.transformPosition);
    }
    
    private static void createPhase2Dragon(ServerLevel level, Vec3 position) {
        EnderDragon newDragon = new EnderDragon(EntityType.ENDER_DRAGON, level);
        newDragon.setPos(position);
        
        // 强制设置二阶段末影龙血量属性为1000
        newDragon.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(PHASE_2_HP);
        // 设置初始血量为1，在无敌时间内逐渐恢复
        newDragon.setHealth(1.0F);
        
        // 添加到世界
        level.addFreshEntity(newDragon);
        
        // 开始水晶恢复过程
        startCrystalRestoration(level);
        
        // 更新数据
        DragonData data = dragonDataMap.values().stream()
                .filter(d -> d.isTransforming && d.transformPosition.equals(position))
                .findFirst()
                .orElse(new DragonData());
        
        data.isTransforming = false;
        dragonDataMap.put(newDragon.getUUID(), data);
        
        // 播放音效
        level.playSound(null, position.x, position.y, position.z, 
                       SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 5.0F, 1.0F);
        
        // 发送阶段转换消息
        sendMessageToAllPlayers(level, "末影龙即将进入第二阶段！进入60秒无敌状态！");
    }
    
    private static void updateDragonState(EnderDragon dragon, DragonData data, ServerLevel level) {
        // 更新末影龙名称显示
        updateDragonName(dragon, data);
        
        // 处理无敌状态
        if (data.isInvulnerable) {
            data.invulnerableTicks--;
            
            // 血量恢复
            float healAmount = (float)PHASE_2_HP / INVULNERABLE_DURATION;
            dragon.setHealth(Math.min(dragon.getHealth() + healAmount, PHASE_2_HP));
            
            // 粒子效果
            createTransformationParticles(dragon, data, level);
            
            if (data.invulnerableTicks <= 0) {
                data.isInvulnerable = false;
                // 发送无敌结束消息
                sendMessageToAllPlayers(level, "末影龙的无敌状态已结束！");
            }
        }
        
        // 技能充能
        if (!data.isInvulnerable && data.phase == 2) {
            data.skillCharge++;
            if (data.skillCharge >= SKILL_COOLDOWN) {
                data.skillCharge = 0;
                // 发送技能就绪消息
                sendMessageToAllPlayers(level, "末影龙即将释放声波攻击！");
                useSonicAttack(dragon, level);
            }
        }
        
        // 处理末影人召唤 - 只有在无敌时间结束后且血量低于50%时才开始吸收
        if (data.phase == 2 && !data.isInvulnerable && dragon.getHealth() < PHASE_2_HP * 0.5f && data.endermanTargets.isEmpty()) {
            startEndermanSummon(dragon, data, level);
        }
        
        // 处理末影人治疗
        if (!data.endermanTargets.isEmpty()) {
            processEndermanHealing(dragon, data, level);
        }
        
        // 每5个tick发送一次网络同步，确保客户端及时更新
        if (level.getGameTime() % 5 == 0) {
            NetworkHandler.sendDragonDataToAll(dragon, data);
            LOGGER.debug("发送末影龙数据同步: 阶段={}, 技能充能={}, 无敌={}", data.phase, data.skillCharge, data.isInvulnerable);
        }
    }
    
    private static void createTransformationParticles(EnderDragon dragon, DragonData data, ServerLevel level) {
        Vec3 pos = dragon.position();
        
        // 围绕末影龙的白色粒子
        for (int i = 0; i < 20; i++) {
            double angle = (System.currentTimeMillis() / 50.0 + i * 18) % 360;
            double radius = 5.0 + Math.sin(System.currentTimeMillis() / 200.0) * 2.0;
            double x = pos.x + radius * Math.cos(Math.toRadians(angle));
            double z = pos.z + radius * Math.sin(Math.toRadians(angle));
            double y = pos.y + 2.0 + Math.cos(System.currentTimeMillis() / 150.0) * 1.0;
            
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
        
        // 从上到下的信标效果
        for (int i = 0; i < BEAM_LENGTH; i += 2) {
            double y = pos.y + dragon.getBbHeight() - i;
            level.sendParticles(ParticleTypes.END_ROD, pos.x, y, pos.z, 1, 0.1, 0, 0.1, 0);
        }
        
        // 扩散的粒子圈
        double progress = 1.0 - (double)data.invulnerableTicks / INVULNERABLE_DURATION;
        for (int ring = 0; ring < 3; ring++) {
            double ringRadius = 3.0 + ring * 2.0 + progress * 10.0;
            for (int i = 0; i < 12; i++) {
                double angle = (System.currentTimeMillis() / 100.0 + i * 30) % 360;
                double x = pos.x + ringRadius * Math.cos(Math.toRadians(angle));
                double z = pos.z + ringRadius * Math.sin(Math.toRadians(angle));
                level.sendParticles(ParticleTypes.END_ROD, x, pos.y + 1.0, z, 1, 0, 0, 0, 0);
            }
        }
    }
    
    private static void useSonicAttack(EnderDragon dragon, ServerLevel level) {
        // 寻找随机玩家
        List<? extends Player> players = level.players();
        if (players.isEmpty()) return;
        
        Player target = players.get(level.random.nextInt(players.size()));
        
        // 发射声波（模拟循声守卫攻击）
        Vec3 startPos = dragon.position().add(0, dragon.getBbHeight() / 2, 0);
        Vec3 targetPos = target.position();
        Vec3 direction = targetPos.subtract(startPos).normalize();
        
        // 创建声波效果
        for (int i = 0; i <= 64; i += 2) {
            Vec3 particlePos = startPos.add(direction.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, particlePos.x, particlePos.y, particlePos.z, 1, 0, 0, 0, 0);
            
            // 检查碰撞
            BlockPos blockPos = new BlockPos((int)particlePos.x, (int)particlePos.y, (int)particlePos.z);
            if (i == 64 || level.getBlockState(blockPos).isSolid()) {
                createLightningExplosion(level, particlePos);
                break;
            }
        }
    }
    
    private static void createLightningExplosion(ServerLevel level, Vec3 center) {
        // 半径5格的闪电圈
        for (int i = 0; i < 8; i++) {
            double angle = i * 45;
            double x = center.x + 5 * Math.cos(Math.toRadians(angle));
            double z = center.z + 5 * Math.sin(Math.toRadians(angle));
            LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            lightning.setPos(x, center.y, z);
            level.addFreshEntity(lightning);
        }
        
        // 半径10格的闪电圈
        for (int i = 0; i < 12; i++) {
            double angle = i * 30;
            double x = center.x + 10 * Math.cos(Math.toRadians(angle));
            double z = center.z + 10 * Math.sin(Math.toRadians(angle));
            LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
            lightning.setPos(x, center.y, z);
            level.addFreshEntity(lightning);
        }
        
        // 中心闪电
        LightningBolt centerLightning = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        centerLightning.setPos(center.x, center.y, center.z);
        level.addFreshEntity(centerLightning);
        
        // 中心粒子效果
        for (int i = 0; i < 50; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 3;
            double offsetY = (level.random.nextDouble() - 0.5) * 3;
            double offsetZ = (level.random.nextDouble() - 0.5) * 3;
            level.sendParticles(ParticleTypes.END_ROD, 
                              center.x + offsetX, center.y + offsetY, center.z + offsetZ, 
                              1, 0, 0, 0, 0);
        }
    }
    
    private static void startEndermanSummon(EnderDragon dragon, DragonData data, ServerLevel level) {
        // 停止末影龙运动
        dragon.setDeltaMovement(Vec3.ZERO);
        
        // 寻找范围内的末影人
        AABB searchArea = new AABB(dragon.position().add(-ENDERMAN_RADIUS, -ENDERMAN_RADIUS, -ENDERMAN_RADIUS),
                                 dragon.position().add(ENDERMAN_RADIUS, ENDERMAN_RADIUS, ENDERMAN_RADIUS));
        
        List<EnderMan> endermen = level.getEntitiesOfClass(EnderMan.class, searchArea);
        data.endermanTargets.clear();
        data.endermanAbsorbTimers.clear();
        
        for (EnderMan enderman : endermen) {
            data.endermanTargets.add(enderman.getUUID());
            // 初始化吸收计时器（3秒 = 60 ticks）
            data.endermanAbsorbTimers.put(enderman.getUUID(), 60);
            // 创建粒子包围效果
            createEndermanParticles(enderman, level);
            // 设置发光效果
            enderman.setGlowingTag(true);
        }
        
        endermanParticlesMap.put(dragon.getUUID(), new HashSet<>(data.endermanTargets));
    }
    
    private static void createEndermanParticles(EnderMan enderman, ServerLevel level) {
        Vec3 pos = enderman.position();
        for (int i = 0; i < 8; i++) {
            double angle = i * 45;
            double radius = 1.0;
            double x = pos.x + radius * Math.cos(Math.toRadians(angle));
            double z = pos.z + radius * Math.sin(Math.toRadians(angle));
            level.sendParticles(ParticleTypes.PORTAL, x, pos.y + 0.5, z, 1, 0, 0.1, 0, 0);
        }
    }
    
    
    private static void createHealingParticles(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();
        for (int i = 0; i < 10; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2;
            double offsetY = level.random.nextDouble() * dragon.getBbHeight();
            double offsetZ = (level.random.nextDouble() - 0.5) * 2;
            level.sendParticles(ParticleTypes.HEART, 
                              pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 
                              1, 0, 0, 0, 0);
        }
    }
    
    private static void createStormParticles(EnderDragon dragon, ServerLevel level) {
        Vec3 pos = dragon.position();
        for (int i = 0; i < 15; i++) {
            double angle = (System.currentTimeMillis() / 50.0 + i * 24) % 360;
            double radius = 3.0 + Math.sin(System.currentTimeMillis() / 100.0) * 1.5;
            double x = pos.x + radius * Math.cos(Math.toRadians(angle));
            double z = pos.z + radius * Math.sin(Math.toRadians(angle));
            double y = pos.y + level.random.nextDouble() * dragon.getBbHeight();
            
            // 白色和粉色粒子混合
            if (level.random.nextBoolean()) {
                level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            } else {
                level.sendParticles(ParticleTypes.ENTITY_EFFECT, x, y, z, 1, 1.0, 0.5, 1.0, 0);
            }
        }
    }
    
    // 获取末影龙数据（用于客户端同步）
    public static DragonData getDragonData(UUID dragonId) {
        return dragonDataMap.get(dragonId);
    }
    
    // 绑定末影龙到数据系统
    private static void bindDragon(EnderDragon dragon) {
        DragonData data = new DragonData();
        data.phase = 1; // 默认为一阶段
        
        // 强制修改末影龙血量属性
        dragon.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(PHASE_1_HP);
        dragon.setHealth(PHASE_1_HP);
        
        dragonDataMap.put(dragon.getUUID(), data);
        LOGGER.info("成功绑定末影龙，UUID: {}，血量设置为: {}", dragon.getUUID(), PHASE_1_HP);
    }
    
    // 当玩家到达末地时自动查找并绑定末影龙
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getTo() == net.minecraft.world.level.Level.END) {
            Player player = event.getEntity();
            ServerLevel level = (ServerLevel) player.level();
            
            LOGGER.info("玩家 {} 到达末地，开始查找末影龙", player.getName().getString());
            
            // 搜索末地中的末影龙
            List<EnderDragon> dragons = level.getEntitiesOfClass(EnderDragon.class, 
                new AABB(-300, 0, -300, 300, 256, 300));
            
            LOGGER.info("在末地中找到 {} 个末影龙", dragons.size());
            
            for (EnderDragon dragon : dragons) {
                if (!dragonDataMap.containsKey(dragon.getUUID())) {
                    LOGGER.info("检测到未绑定的末影龙，UUID: {}，开始绑定", dragon.getUUID());
                    bindDragon(dragon);
                } else {
                    LOGGER.info("末影龙 {} 已绑定", dragon.getUUID());
                }
            }
            
            // 如果没有找到末影龙，记录日志
            if (dragons.isEmpty()) {
                LOGGER.info("在末地中未找到末影龙");
            }
        }
    }
    
    // 更新末影龙名称显示
    private static void updateDragonName(EnderDragon dragon, DragonData data) {
        String name = String.format("末影龙 [阶段%d] %d/%d", 
            data.phase, (int)dragon.getHealth(), data.phase == 1 ? PHASE_1_HP : PHASE_2_HP);
        
        // 设置自定义名称
        dragon.setCustomName(Component.literal(name));
        dragon.setCustomNameVisible(true);
    }
    
    // 向所有玩家发送消息
    private static void sendMessageToAllPlayers(ServerLevel level, String message) {
        for (Player player : level.players()) {
            player.sendSystemMessage(Component.literal("§6[末影龙]§r " + message));
        }
    }
    
    // 改进末影人吸收效果 - 使用计时器机制
    private static void processEndermanHealing(EnderDragon dragon, DragonData data, ServerLevel level) {
        // 处理所有正在吸收的末影人
        Iterator<UUID> iterator = data.endermanTargets.iterator();
        int healedCount = 0;
        int totalHeal = 0;
        
        while (iterator.hasNext()) {
            UUID endermanId = iterator.next();
            Entity enderman = level.getEntity(endermanId);
            
            if (enderman instanceof EnderMan) {
                // 让末影人飞到末影龙上方20格位置
                Vec3 targetPos = dragon.position().add(0, 20, 0);
                Vec3 currentPos = enderman.position();
                
                // 计算强大的吸引力
                Vec3 direction = targetPos.subtract(currentPos).normalize().scale(1.5);
                enderman.setDeltaMovement(direction);
                
                // 创建飞行粒子效果
                createFlyingParticles(enderman, level);
                
                // 更新吸收计时器
                int timer = data.endermanAbsorbTimers.getOrDefault(endermanId, 60);
                timer--;
                data.endermanAbsorbTimers.put(endermanId, timer);
                
                // 如果计时器归零，删除末影人并治疗
                if (timer <= 0) {
                    // 记录删除位置用于粒子效果
                    Vec3 deletePos = enderman.position();
                    
                    // 删除末影人
                    enderman.remove(Entity.RemovalReason.DISCARDED);
                    
                    // 创建红色爆炸粒子效果
                    createExplosionParticles(deletePos, level);
                    
                    // 治疗末影龙
                    dragon.heal(ENDERMAN_HEAL_AMOUNT);
                    data.endermanHealCounter++;
                    healedCount++;
                    totalHeal += ENDERMAN_HEAL_AMOUNT;
                    
                    // 治疗粒子效果
                    createHealingParticles(dragon, level);
                    
                    // 从列表中移除
                    iterator.remove();
                    data.endermanAbsorbTimers.remove(endermanId);
                }
            } else {
                // 如果末影人不存在，从列表中移除
                iterator.remove();
                data.endermanAbsorbTimers.remove(endermanId);
            }
        }
        
        // 如果有末影人被吸收，发送总结消息
        if (healedCount > 0) {
            sendMessageToAllPlayers(level, String.format("末影龙吸收了%d只末影人，恢复了%d点生命！", healedCount, totalHeal));
        }
        
        // 创建风暴粒子效果
        createStormParticles(dragon, level);
        
        // 如果所有末影人都处理完毕，恢复运动
        if (data.endermanTargets.isEmpty()) {
            endermanParticlesMap.remove(dragon.getUUID());
            
        }
    }
    
    // 创建红色爆炸粒子效果
    private static void createExplosionParticles(Vec3 pos, ServerLevel level) {
        for (int i = 0; i < 20; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 3;
            double offsetY = (level.random.nextDouble() - 0.5) * 3;
            double offsetZ = (level.random.nextDouble() - 0.5) * 3;
            level.sendParticles(ParticleTypes.FLAME, 
                              pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 
                              1, 0, 0, 0, 0);
        }
        
        // 添加爆炸音效
        level.playSound(null, pos.x, pos.y, pos.z, 
                       SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 1.0F);
    }
    
    // 创建飞行粒子效果
    private static void createFlyingParticles(Entity entity, ServerLevel level) {
        Vec3 pos = entity.position();
        for (int i = 0; i < 5; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 1.5;
            double offsetY = level.random.nextDouble() * 0.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 1.5;
            level.sendParticles(ParticleTypes.PORTAL, 
                              pos.x + offsetX, pos.y + offsetY, pos.z + offsetZ, 
                              1, 0, 0.1, 0, 0);
        }
    }
    
    // 开始水晶恢复过程
    private static void startCrystalRestoration(ServerLevel level) {
        // 发送水晶恢复消息
        sendMessageToAllPlayers(level, "水晶正在依次恢复...");
        
        // 在服务器tick中处理水晶恢复
        new Thread(() -> {
            try {
                // 在指定高度位置依次创建水晶
                int[] crystalHeights = {80, 90, 100, 110, 120, 130, 140, 67};
                
                for (int i = 0; i < crystalHeights.length; i++) {
                    int height = crystalHeights[i];
                    restoreCrystalAtHeight(level, height);
                    
                    // 等待间隔时间
                    Thread.sleep(CRYSTAL_RESTORE_INTERVAL * 50); // 转换为毫秒
                }
                
                sendMessageToAllPlayers(level, "所有水晶已恢复完成！");
            } catch (InterruptedException e) {
                LOGGER.error("水晶恢复过程被中断", e);
            }
        }).start();
    }
    
    // 在指定高度位置恢复水晶
    private static void restoreCrystalAtHeight(ServerLevel level, int height) {
        // 在末地中心位置 (0, height, 0) 创建水晶
        BlockPos crystalPos = new BlockPos(0, height, 0);
        
        // 创建末影水晶
        net.minecraft.world.entity.boss.enderdragon.EndCrystal crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(EntityType.END_CRYSTAL, level);
        crystal.setPos(crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
        crystal.setShowBottom(false);
        
        // 添加到世界
        level.addFreshEntity(crystal);
        
        // 创建恢复粒子效果
        createCrystalRestoreParticles(crystalPos, level);
        
        // 播放音效
        level.playSound(null, crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(), 
                       SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
        


    }
    
    // 创建水晶恢复粒子效果
    private static void createCrystalRestoreParticles(BlockPos pos, ServerLevel level) {
        for (int i = 0; i < 30; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 2;
            double offsetY = level.random.nextDouble() * 3;
            double offsetZ = (level.random.nextDouble() - 0.5) * 2;
            level.sendParticles(ParticleTypes.END_ROD, 
                              pos.getX() + 0.5 + offsetX, pos.getY() + offsetY, pos.getZ() + 0.5 + offsetZ, 
                              1, 0, 0, 0, 0);
        }
        
        // 创建光束效果
        for (int i = 0; i < 20; i++) {
            double y = pos.getY() + i * 0.5;
            level.sendParticles(ParticleTypes.END_ROD, 
                              pos.getX() + 0.5, y, pos.getZ() + 0.5, 
                              1, 0.1, 0, 0.1, 0);
        }
    }
    
    // 检查是否有活跃的末影龙
    public static boolean hasActiveDragon(ServerLevel level) {
        for (UUID dragonId : dragonDataMap.keySet()) {
            Entity entity = level.getEntity(dragonId);
            if (entity instanceof EnderDragon) {
                return true;
            }
        }
        return false;
    }
    
    // 给予玩家奖励
    private static void givePlayerRewards(ServerLevel level, EnderDragon dragon) {
        // 获取末地中的所有玩家
        List<? extends Player> players = level.players();
        
        // 杀死所有末影龙实体
        killAllEnderDragons(level);
        
        for (Player player : players) {
            // 给予解放末地成就
            grantEndAchievement(player);
            
            // 在玩家头上创建经验球
            createExperienceOrbsAbovePlayer(player, level);
        }
        
        // 播放胜利音效
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), 
                       SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
    
    // 杀死所有末影龙实体
    private static void killAllEnderDragons(ServerLevel level) {
        // 搜索所有世界中的末影龙
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            List<EnderDragon> dragons = serverLevel.getEntitiesOfClass(EnderDragon.class, 
                new AABB(-300, 0, -300, 300, 256, 300));
            
            for (EnderDragon dragon : dragons) {
                // 杀死末影龙
                dragon.kill();
                LOGGER.info("已杀死末影龙实体，UUID: {}", dragon.getUUID());
            }
        }
    }
    
    // 给予解放末地成就
    private static void grantEndAchievement(Player player) {
        // 给予玩家经验奖励
        player.giveExperiencePoints(1000);
        
        // 使用1.20.1版本的统计字段
        player.awardStat(net.minecraft.stats.Stats.ENTITY_KILLED.get(net.minecraft.world.entity.EntityType.ENDER_DRAGON));
        player.awardStat(net.minecraft.stats.Stats.ENTITY_KILLED.get(net.minecraft.world.entity.EntityType.ENDERMAN));
        
        // 发送成就消息
        player.sendSystemMessage(Component.literal("§e成就达成：§A[解放末地]§e！"));
        player.sendSystemMessage(Component.literal("§6你获得了1000点经验奖励！"));
    }
    
    // 在玩家头上创建经验球
    private static void createExperienceOrbsAbovePlayer(Player player, ServerLevel level) {
        Vec3 playerPos = player.position();
        
        // 在玩家头上创建大量经验球（模拟末影人掉落）
        for (int i = 0; i < 20; i++) {
            // 创建经验球
            net.minecraft.world.entity.ExperienceOrb expOrb = new net.minecraft.world.entity.ExperienceOrb(
                level, 
                playerPos.x + (level.random.nextDouble() - 0.5) * 2, 
                playerPos.y + 3 + level.random.nextDouble() * 2, 
                playerPos.z + (level.random.nextDouble() - 0.5) * 2, 
                5 + level.random.nextInt(10) // 5-15点经验值
            );
            
            // 添加到世界
            level.addFreshEntity(expOrb);
        }
        
        // 创建庆祝粒子效果
        for (int i = 0; i < 30; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 3;
            double offsetY = level.random.nextDouble() * 4;
            double offsetZ = (level.random.nextDouble() - 0.5) * 3;
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, 
                              playerPos.x + offsetX, playerPos.y + offsetY, playerPos.z + offsetZ, 
                              1, 0, 0, 0, 0);
        }
    }
}
