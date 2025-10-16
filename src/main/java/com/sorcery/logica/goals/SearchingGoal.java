package com.sorcery.logica.goals;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import com.sorcery.logica.config.LogicaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Random;

/**
 * 搜索Goal
 *
 * 功能：
 * - 在SEARCHING状态时触发
 * - 在当前位置停留10秒
 * - 使用原版RandomStrollGoal自然游荡（Priority 5）
 * - 超时后返回IDLE状态
 *
 * 优先级：3（高于RandomStrollGoal的5）
 * 注意：此Goal不控制移动,只控制状态和超时,移动由原版RandomStrollGoal接管
 */
public class SearchingGoal extends Goal {

    private final Mob mob;
    private int searchTimer; // 搜索计时器

    public SearchingGoal(Mob mob) {
        this.mob = mob;
        // 🔥 不设置任何Flag,让原版RandomStrollGoal接管移动
        this.setFlags(EnumSet.noneOf(Flag.class));
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

        // 只在SEARCHING状态下执行
        if (aiCap.getState() != AIState.SEARCHING) {
            return false;
        }

        this.searchTimer = 0;

        if (LogicaConfig.shouldLogGoalLifecycle()) {
            Logica.LOGGER.info("🔥 SearchingGoal.canUse(): Mob {} starting search (will use vanilla RandomStrollGoal for movement)",
                    mob.getName().getString());
        }

        return true;
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

        // 如果状态改变，停止
        if (aiCap.getState() != AIState.SEARCHING) {
            return false;
        }

        // 超时检查：10秒
        int maxSearchDuration = LogicaConfig.INVESTIGATION_DURATION_TICKS.get();
        if (searchTimer >= maxSearchDuration) {
            if (LogicaConfig.shouldLogStateTransitions()) {
                Logica.LOGGER.info("🔥 SearchingGoal timeout for {} (searchTimer={}/{}s)",
                        mob.getName().getString(), searchTimer, maxSearchDuration/20);
            }
            return false;
        }

        return true;
    }

    /**
     * 开始执行
     */
    @Override
    public void start() {
        if (LogicaConfig.shouldLogGoalLifecycle()) {
            Logica.LOGGER.info("🔥 SearchingGoal.start() - Mob {} will now wander using vanilla RandomStrollGoal",
                    mob.getName().getString());
        }
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return;
        }

        // 如果状态仍然是SEARCHING，说明搜索完成，返回IDLE
        if (aiCap.getState() == AIState.SEARCHING) {
            aiCap.setState(AIState.IDLE);
            aiCap.setLastKnownTargetPos(null);

            if (LogicaConfig.shouldLogStateTransitions()) {
                Logica.LOGGER.info("🔥 SearchingGoal.stop() - Mob {} finished searching, returning to IDLE (searchTimer={})",
                        mob.getName().getString(), searchTimer);
            }
        }
    }

    /**
     * 每tick执行
     *
     * 简化后的行为：
     * - 只负责计时
     * - 不控制移动（由原版RandomStrollGoal接管）
     * - 不控制转头（由原版LookAtPlayerGoal等接管）
     */
    @Override
    public void tick() {
        searchTimer++;
    }

    /**
     * 是否需要重复检查canUse
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
