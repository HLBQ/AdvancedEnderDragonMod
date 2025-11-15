package com.example.examplemod;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public class ClientDragonDataManager {
    private static final Map<UUID, EnderDragonManager.DragonData> clientDragonDataMap = new ConcurrentHashMap<>();
    
    public static void updateDragonData(UUID dragonId, EnderDragonManager.DragonData data) {
        clientDragonDataMap.put(dragonId, data);
    }
    
    public static EnderDragonManager.DragonData getDragonData(UUID dragonId) {
        return clientDragonDataMap.get(dragonId);
    }
    
    public static void removeDragonData(UUID dragonId) {
        clientDragonDataMap.remove(dragonId);
    }
    
    public static void clearAllData() {
        clientDragonDataMap.clear();
    }
}
