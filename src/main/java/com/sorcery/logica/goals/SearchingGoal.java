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
 * - 前往最后已知位置
 * - 到达后环顾四周搜索
 * - 超时后返回IDLE状态
 *
 * 优先级：3（与InvestigateGoal相同）
 */
public class SearchingGoal extends Goal {

    private final Mob mob;
    private final Random random = new Random();

    private BlockPos searchTarget;
    private int searchTimer;
    private int lookAroundCooldown;
    private boolean hasArrived;

    public SearchingGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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

        // 获取搜索位置
        BlockPos targetPos = aiCap.getLastKnownTargetPos();
        if (targetPos == null) {
            // 没有搜索目标，直接返回IDLE
            aiCap.setState(AIState.IDLE);
            return false;
        }

        this.searchTarget = targetPos;
        this.hasArrived = false;
        this.searchTimer = 0;
        this.lookAroundCooldown = 0;

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

        // 检查搜索超时（比调查时间长一些）
        if (hasArrived && searchTimer >= LogicaConfig.INVESTIGATION_DURATION_TICKS.get() * 2) {
            return false;
        }

        return true;
    }

    /**
     * 开始执行
     */
    @Override
    public void start() {
        Logica.LOGGER.debug("Mob {} starting search at {}",
                mob.getName().getString(), searchTarget);

        // 前往搜索位置
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(searchTarget, 1);
        if (path != null) {
            mob.getNavigation().moveTo(path, mob.getSpeed() * 1.2); // 稍快速度
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

            Logica.LOGGER.debug("Mob {} finished searching, returning to IDLE",
                    mob.getName().getString());
        }

        mob.getNavigation().stop();
        this.searchTarget = null;
        this.hasArrived = false;
    }

    /**
     * 每tick执行
     */
    @Override
    public void tick() {
        if (searchTarget == null) {
            return;
        }

        if (!hasArrived) {
            // 前往搜索位置
            Vec3 mobPos = mob.position();
            Vec3 targetPos = Vec3.atCenterOf(searchTarget);
            double distance = mobPos.distanceTo(targetPos);

            if (distance < 4.0) {
                // 到达搜索区域
                hasArrived = true;
                mob.getNavigation().stop();

                Logica.LOGGER.debug("Mob {} arrived at search area, searching",
                        mob.getName().getString());
            } else {
                // 继续前往
                if (mob.getNavigation().isDone()) {
                    net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(searchTarget, 1);
                    if (path != null) {
                        mob.getNavigation().moveTo(path, mob.getSpeed() * 1.2);
                    }
                }
            }
        } else {
            // 已到达，搜索中
            searchTimer++;

            // 在搜索区域附近游荡
            if (mob.getNavigation().isDone()) {
                // 随机选择附近的位置
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = 3.0 + random.nextDouble() * 3.0;
                int targetX = (int) (searchTarget.getX() + Math.cos(angle) * radius);
                int targetZ = (int) (searchTarget.getZ() + Math.sin(angle) * radius);
                BlockPos targetPos = new BlockPos(targetX, searchTarget.getY(), targetZ);

                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetPos, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, mob.getSpeed());
                }
            }

            // 定期环顾
            if (--lookAroundCooldown <= 0) {
                lookAroundCooldown = LogicaConfig.LOOK_AROUND_INTERVAL.get() / 2; // 更频繁

                double angle = random.nextDouble() * Math.PI * 2;
                double radius = 10.0;
                double lookX = mob.getX() + Math.cos(angle) * radius;
                double lookZ = mob.getZ() + Math.sin(angle) * radius;
                double lookY = mob.getY() + mob.getEyeHeight();

                mob.getLookControl().setLookAt(lookX, lookY, lookZ, 10.0F, mob.getMaxHeadXRot());
            }
        }
    }

    /**
     * 是否需要重复检查canUse
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
