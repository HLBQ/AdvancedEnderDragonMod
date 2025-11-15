package com.example.examplemod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.UUID;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExampleMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    
    private static int packetId = 0;
    
    public static void register() {
        INSTANCE.registerMessage(packetId++, DragonDataPacket.class,
                DragonDataPacket::encode,
                DragonDataPacket::decode,
                DragonDataPacket::handle);
    }
    
    // 数据包类
    public static class DragonDataPacket {
        private final UUID dragonId;
        private final int phase;
        private final int skillCharge;
        private final boolean isInvulnerable;
        private final int invulnerableTicks;
        
        public DragonDataPacket(UUID dragonId, int phase, int skillCharge, boolean isInvulnerable, int invulnerableTicks) {
            this.dragonId = dragonId;
            this.phase = phase;
            this.skillCharge = skillCharge;
            this.isInvulnerable = isInvulnerable;
            this.invulnerableTicks = invulnerableTicks;
        }
        
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(dragonId);
            buffer.writeInt(phase);
            buffer.writeInt(skillCharge);
            buffer.writeBoolean(isInvulnerable);
            buffer.writeInt(invulnerableTicks);
        }
        
        public static DragonDataPacket decode(FriendlyByteBuf buffer) {
            return new DragonDataPacket(
                    buffer.readUUID(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readBoolean(),
                    buffer.readInt()
            );
        }
        
        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> {
                // 在客户端处理数据包
                if (context.get().getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                    // 更新客户端数据
                    EnderDragonManager.DragonData data = new EnderDragonManager.DragonData();
                    data.phase = phase;
                    data.skillCharge = skillCharge;
                    data.isInvulnerable = isInvulnerable;
                    data.invulnerableTicks = invulnerableTicks;
                    
                    // 存储到客户端数据管理器
                    ClientDragonDataManager.updateDragonData(dragonId, data);
                }
            });
            context.get().setPacketHandled(true);
        }
    }
    
    // 向所有玩家发送末影龙数据更新
    public static void sendDragonDataToAll(EnderDragon dragon, EnderDragonManager.DragonData data) {
        DragonDataPacket packet = new DragonDataPacket(
                dragon.getUUID(),
                data.phase,
                data.skillCharge,
                data.isInvulnerable,
                data.invulnerableTicks
        );
        
        // 向所有跟踪该末影龙的玩家发送数据包
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> dragon), packet);
    }
    
    // 向特定玩家发送末影龙数据
    public static void sendDragonDataToPlayer(ServerPlayer player, EnderDragon dragon, EnderDragonManager.DragonData data) {
        DragonDataPacket packet = new DragonDataPacket(
                dragon.getUUID(),
                data.phase,
                data.skillCharge,
                data.isInvulnerable,
                data.invulnerableTicks
        );
        
        INSTANCE.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
