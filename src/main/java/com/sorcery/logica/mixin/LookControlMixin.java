package com.sorcery.logica.mixin;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import com.sorcery.logica.config.LogicaConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Mixin到LookControl，实现转向限制功能
 *
 * 功能：
 * - IDLE/PATROL状态：限制转向角度（≤90度）+ 降低转向速度
 * - ALERT/COMBAT/TRACKING/SEARCHING状态：正常转向
 * - 支持全局和单独实体的转向速度配置
 */
@Mixin(LookControl.class)
public class LookControlMixin {

    @Shadow(remap = false) @Final protected Mob mob;
    @Shadow(remap = false) protected double wantedX;
    @Shadow(remap = false) protected double wantedY;
    @Shadow(remap = false) protected double wantedZ;

    // 缓存单独实体的转向速度配置
    private static Map<String, Double> entityRotationSpeedCache = null;

    /**
     * 在tick()方法开始时注入，修改转向逻辑
     *
     * 注意：已禁用转向限制，因为会干扰AI行为
     */
    // @Inject(method = "tick()V", at = @At("RETURN"), remap = false)
    // private void afterTick(CallbackInfo ci) {
    //     // 转向限制已禁用
    // }

    /**
     * 在IDLE状态下平滑降低转向速度
     *
     * 这个方法在原版tick之后执行，不会覆盖LookControl设置的目标
     * 只是减缓达到目标的速度
     */
    private void applyIdleRotationSmoothing() {
        // 暂时禁用转向限制，因为它会干扰正常的AI行为
        // TODO: 未来可以实现更智能的转向平滑算法

        // 获取转向速度倍率
        // double speedMultiplier = getRotationSpeedMultiplier();
        // 如果需要降低转向速度，可以在这里实现插值逻辑
    }

    /**
     * 获取转向速度倍率
     * 优先级：单独实体配置 > 全局配置
     */
    private double getRotationSpeedMultiplier() {
        // 初始化缓存（只在第一次调用时）
        if (entityRotationSpeedCache == null) {
            initializeEntityRotationSpeedCache();
        }

        // 获取实体类型ID
        String entityId = mob.getType().toString();

        // 检查是否有单独配置
        if (entityRotationSpeedCache.containsKey(entityId)) {
            return entityRotationSpeedCache.get(entityId);
        }

        // 使用全局配置
        return LogicaConfig.GLOBAL_ROTATION_SPEED_MULTIPLIER.get();
    }

    /**
     * 初始化实体转向速度缓存
     */
    private static void initializeEntityRotationSpeedCache() {
        entityRotationSpeedCache = new HashMap<>();

        try {
            for (String entry : LogicaConfig.ENTITY_ROTATION_SPEEDS.get()) {
                String[] parts = entry.split("=");
                if (parts.length == 2) {
                    String entityId = parts[0].trim();
                    double speed = Double.parseDouble(parts[1].trim());
                    entityRotationSpeedCache.put(entityId, speed);
                }
            }
            Logica.LOGGER.info("Loaded {} entity-specific rotation speeds", entityRotationSpeedCache.size());
        } catch (Exception e) {
            Logica.LOGGER.error("Failed to parse entity rotation speeds config", e);
        }
    }

    /**
     * 清空缓存（用于配置重载）
     */
    private static void clearCache() {
        entityRotationSpeedCache = null;
    }
}
