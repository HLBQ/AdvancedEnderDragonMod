package com.example.examplemod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class EnderDragonHudRenderer {
    private static final ResourceLocation BOSS_BAR_TEXTURE = new ResourceLocation("textures/gui/bars.png");
    
    @SubscribeEvent
    public static void onRenderGui(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay().id().getPath().equals("boss_health")) {
            renderEnderDragonHud(event.getGuiGraphics(), event.getPartialTick());
        }
    }
    
    private static void renderEnderDragonHud(GuiGraphics guiGraphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        
        // 查找末影龙
        EnderDragon dragon = findActiveDragon(minecraft);
        if (dragon == null) return;
        
        EnderDragonManager.DragonData data = ClientDragonDataManager.getDragonData(dragon.getUUID());
        if (data == null) return;
        
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        
        // 计算血条位置（屏幕顶部中央）
        int barWidth = 182;
        int barHeight = 10;
        int x = (screenWidth - barWidth) / 2;
        int y = 12;
        
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        
        // 渲染阶段信息
        String phaseText = "阶段: " + data.phase;
        int textWidth = minecraft.font.width(phaseText);
        int textX = (screenWidth - textWidth) / 2;
        int textY = y - 12;
        
        guiGraphics.drawString(minecraft.font, phaseText, textX, textY, 0xFFFFFF, true);
        
        // 渲染血量条背景
        guiGraphics.blit(BOSS_BAR_TEXTURE, x, y, 0, 74, barWidth, barHeight);
        
        // 计算血量百分比
        float healthPercent = dragon.getHealth() / dragon.getMaxHealth();
        int healthWidth = (int)(barWidth * healthPercent);
        
        // 渲染血量条
        if (healthWidth > 0) {
            guiGraphics.blit(BOSS_BAR_TEXTURE, x, y, 0, 84, healthWidth, barHeight);
        }
        
        // 渲染血量数值
        String healthText = String.format("%.0f/%.0f", dragon.getHealth(), dragon.getMaxHealth());
        int healthTextWidth = minecraft.font.width(healthText);
        int healthTextX = x + (barWidth - healthTextWidth) / 2;
        int healthTextY = y + 1;
        guiGraphics.drawString(minecraft.font, healthText, healthTextX, healthTextY, 0xFFFFFF, true);
        
        // 如果是二阶段，渲染技能条
        if (data.phase == 2) {
            int skillBarY = y + barHeight + 2;
            int skillBarHeight = barHeight / 2;
            
            // 技能条背景
            guiGraphics.blit(BOSS_BAR_TEXTURE, x, skillBarY, 0, 74, barWidth, skillBarHeight);
            
            // 计算技能充能百分比
            float skillPercent = (float)data.skillCharge / EnderDragonManager.SKILL_COOLDOWN;
            int skillWidth = (int)(barWidth * skillPercent);
            
            // 渲染技能条
            if (skillWidth > 0) {
                // 使用不同的颜色表示技能条（蓝色）
                RenderSystem.setShaderColor(0.0f, 0.5f, 1.0f, 1.0f);
                guiGraphics.blit(BOSS_BAR_TEXTURE, x, skillBarY, 0, 94, skillWidth, skillBarHeight);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
            
            // 如果技能条满了，显示提示
            if (data.skillCharge >= EnderDragonManager.SKILL_COOLDOWN) {
                String readyText = "技能就绪!";
                int readyTextWidth = minecraft.font.width(readyText);
                int readyTextX = x + (barWidth - readyTextWidth) / 2;
                int readyTextY = skillBarY + skillBarHeight + 2;
                guiGraphics.drawString(minecraft.font, readyText, readyTextX, readyTextY, 0x00FF00, true);
            }
        }
        
        // 如果处于无敌状态，显示无敌提示
        if (data.isInvulnerable) {
            String invulnText = "无敌状态";
            int invulnTextWidth = minecraft.font.width(invulnText);
            int invulnTextX = (screenWidth - invulnTextWidth) / 2;
            int invulnTextY = y + barHeight + 15;
            guiGraphics.drawString(minecraft.font, invulnText, invulnTextX, invulnTextY, 0xFFFF00, true);
        }
        
        poseStack.popPose();
    }
    
    private static EnderDragon findActiveDragon(Minecraft minecraft) {
        // 在玩家周围寻找末影龙
        for (EnderDragon dragon : minecraft.level.getEntitiesOfClass(EnderDragon.class, 
                minecraft.player.getBoundingBox().inflate(100))) {
            EnderDragonManager.DragonData data = ClientDragonDataManager.getDragonData(dragon.getUUID());
            if (data != null) {
                return dragon;
            }
        }
        return null;
    }
}
