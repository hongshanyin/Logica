package com.sorcery.logica.goals;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import io.github.Sorcery_Dynasties.aperioculos.api.AperiOculosAPI;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 战斗监控Goal
 *
 * 功能：
 * - 在COMBAT状态时运行
 * - 定期检查是否还能看到目标
 * - 丢失视线→切换到TRACKING状态
 * - 目标死亡/消失→切换到SEARCHING状态
 *
 * 优先级：1（高优先级，确保及时响应）
 */
public class CombatMonitorGoal extends Goal {

    private final Mob mob;
    private int visionCheckCooldown;

    public CombatMonitorGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.noneOf(Flag.class)); // 不占用任何标志位
        this.visionCheckCooldown = 0;
    }

    /**
     * 判断是否应该开始执行
     */
    @Override
    public boolean canUse() {
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return false;
        }

        // 只在COMBAT状态下执行
        return aiCap.getState() == AIState.COMBAT;
    }

    /**
     * 判断是否应该继续执行
     */
    @Override
    public boolean canContinueToUse() {
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return false;
        }

        // 只要还在COMBAT状态就继续监控
        return aiCap.getState() == AIState.COMBAT;
    }

    /**
     * 每tick执行
     */
    @Override
    public void tick() {
        // 定期检查视线（每10 tick = 0.5秒）
        if (--visionCheckCooldown > 0) {
            return;
        }
        visionCheckCooldown = 10;

        LivingEntity target = mob.getTarget();

        // 检查目标是否存在
        if (target == null || !target.isAlive() || target.isRemoved()) {
            // 目标消失，切换到SEARCHING
            switchToSearching("target disappeared");
            return;
        }

        // 使用Aperi Oculos检查视线
        boolean canSee = AperiOculosAPI.canSee(mob, target);

        if (!canSee) {
            // 丢失视线，切换到TRACKING
            switchToTracking(target);
        } else {
            // 仍然可见，更新最后已知位置
            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                cap.setLastKnownTargetPos(target.blockPosition());
            });
        }
    }

    /**
     * 切换到TRACKING状态
     */
    private void switchToTracking(LivingEntity target) {
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setState(AIState.TRACKING);
            cap.setLastKnownTargetPos(target.blockPosition());
            cap.setTrackingTicks(0);

            Logica.LOGGER.debug("Mob {} lost sight of target, switching to TRACKING at {}",
                    mob.getName().getString(), target.blockPosition());
        });
    }

    /**
     * 切换到SEARCHING状态
     */
    private void switchToSearching(String reason) {
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setState(AIState.SEARCHING);

            Logica.LOGGER.debug("Mob {} switching to SEARCHING (reason: {})",
                    mob.getName().getString(), reason);
        });

        // 清除目标
        mob.setTarget(null);
    }

    /**
     * 是否需要重复检查canUse
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
