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
 * 调查目标Goal
 *
 * 功能：
 * - 在ALERT状态时触发
 * - 前往lastKnownTargetPos调查
 * - 到达后环顾四周
 * - 调查完成后返回IDLE状态
 *
 * 优先级：3（仅次于逃跑、攻击和追踪）
 */
public class InvestigateGoal extends Goal {

    private final Mob mob;
    private final Random random = new Random();

    // 调查状态
    private BlockPos investigationTarget;
    private int lookAroundTimer;
    private int lookAroundCooldown;
    private boolean hasArrived;

    // 导航失败检测
    private int navigationFailedTicks;
    private static final int MAX_NAVIGATION_FAILED_TICKS = 100; // 5秒后放弃

    // DEBUG: 日志计数器
    private int tickLogCounter;

    public InvestigateGoal(Mob mob) {
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
            Logica.LOGGER.debug("InvestigateGoal.canUse(): No AI capability for {}",
                    mob.getName().getString());
            return false;
        }

        AIState currentState = aiCap.getState();
        Logica.LOGGER.debug("InvestigateGoal.canUse(): Mob {} state is {}",
                mob.getName().getString(), currentState);

        // 只在ALERT状态下执行
        if (currentState != AIState.ALERT) {
            return false;
        }

        // 必须有调查目标
        BlockPos targetPos = aiCap.getLastKnownTargetPos();
        Logica.LOGGER.debug("InvestigateGoal.canUse(): Mob {} investigation target: {}",
                mob.getName().getString(), targetPos);

        if (targetPos == null) {
            // 没有调查目标，直接返回IDLE
            aiCap.setState(AIState.IDLE);
            Logica.LOGGER.debug("InvestigateGoal.canUse(): No investigation target, returning to IDLE");
            return false;
        }

        // 开始调查
        this.investigationTarget = targetPos;
        this.hasArrived = false;
        this.lookAroundTimer = 0;
        this.lookAroundCooldown = 0;
        this.navigationFailedTicks = 0;

        Logica.LOGGER.info("Mob {} starting investigation at {}",
                mob.getName().getString(), investigationTarget);

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

        // 如果状态改变（例如发现目标进入COMBAT），停止调查
        if (aiCap.getState() != AIState.ALERT) {
            return false;
        }

        // 如果已经到达并完成环顾，停止
        if (hasArrived && lookAroundTimer >= LogicaConfig.INVESTIGATION_DURATION_TICKS.get()) {
            return false;
        }

        return true;
    }

    /**
     * 开始执行
     */
    @Override
    public void start() {
        Logica.LOGGER.info("🔥 InvestigateGoal.start() CALLED for {}", mob.getName().getString());

        // 前往调查位置
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(investigationTarget, 1);
        boolean moveToSuccess = false;

        if (path != null) {
            // 直接传入速度倍率，让导航系统自动处理
            double speedMultiplier = LogicaConfig.INVESTIGATION_SPEED_MULTIPLIER.get();
            moveToSuccess = mob.getNavigation().moveTo(path, speedMultiplier);

            Logica.LOGGER.info("🔥 moveTo() returned: {} (speed multiplier: {})",
                    moveToSuccess, speedMultiplier);
        }

        Logica.LOGGER.info("Mob {} navigating to investigation point {} (path: {}, moveTo success: {})",
                mob.getName().getString(), investigationTarget, path != null ? "created" : "null", moveToSuccess);
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        Logica.LOGGER.info("🔥 InvestigateGoal.stop() CALLED - hasArrived: {}, lookAroundTimer: {}",
                hasArrived, lookAroundTimer);

        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return;
        }

        // 如果状态仍然是ALERT，说明调查完成，返回IDLE
        if (aiCap.getState() == AIState.ALERT) {
            aiCap.setState(AIState.IDLE);
            aiCap.setLastKnownTargetPos(null); // 清除调查目标

            Logica.LOGGER.info("🔥 Mob {} finished investigation, returning to IDLE",
                    mob.getName().getString());
        } else {
            Logica.LOGGER.warn("🔥 InvestigateGoal.stop() but state is NOT ALERT: {}",
                    aiCap.getState());
        }

        // 停止导航
        mob.getNavigation().stop();
        this.investigationTarget = null;
        this.hasArrived = false;
    }

    /**
     * 每tick执行
     */
    @Override
    public void tick() {
        // DEBUG: 每20 tick记录一次
        if (++tickLogCounter >= 20) {
            tickLogCounter = 0;
            Logica.LOGGER.info("InvestigateGoal.tick() - mob: {}, target: {}, arrived: {}, navigation: {}, pos: {}, navState: [isDone={}, isStuck={}, hasPath={}]",
                    mob.getName().getString(), investigationTarget, hasArrived,
                    mob.getNavigation().isDone() ? "done" : "moving",
                    mob.blockPosition(),
                    mob.getNavigation().isDone(),
                    mob.getNavigation().isStuck(),
                    mob.getNavigation().getPath() != null);
        }

        if (investigationTarget == null) {
            Logica.LOGGER.warn("InvestigateGoal.tick() - investigationTarget is null!");
            return;
        }

        // 🔥 FIX: 检查目标是否已变化（听到新声音）
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap != null) {
            BlockPos currentTarget = aiCap.getLastKnownTargetPos();
            if (currentTarget != null && !currentTarget.equals(investigationTarget)) {
                // 目标已变化，重新开始调查
                Logica.LOGGER.info("🔥 InvestigateGoal target changed from {} to {}, restarting investigation",
                        investigationTarget, currentTarget);
                investigationTarget = currentTarget;
                hasArrived = false;
                lookAroundTimer = 0;
                lookAroundCooldown = 0;
                navigationFailedTicks = 0; // 重置失败计数

                // 立即前往新目标
                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(investigationTarget, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, LogicaConfig.INVESTIGATION_SPEED_MULTIPLIER.get());
                }
            }
        }

        // 检查是否已经到达
        if (!hasArrived) {
            Vec3 mobPos = mob.position();
            Vec3 targetPos = Vec3.atCenterOf(investigationTarget);
            double distance = mobPos.distanceTo(targetPos);

            double arrivalDistance = LogicaConfig.INVESTIGATION_ARRIVAL_DISTANCE.get();

            if (distance <= arrivalDistance) {
                // 到达目标点
                hasArrived = true;
                mob.getNavigation().stop();
                lookAroundCooldown = 5; // 到达后立即开始第一次转向（5 tick后）

                Logica.LOGGER.info("🔥 Mob {} ARRIVED at investigation point (distance: {}), starting lookAround (duration: {} ticks)",
                        mob.getName().getString(), distance, LogicaConfig.INVESTIGATION_DURATION_TICKS.get());
            } else {
                // 继续前往（处理可能的路径丢失）
                if (mob.getNavigation().isDone()) {
                    net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(investigationTarget, 1);
                    boolean moveToSuccess = false;
                    if (path != null) {
                        moveToSuccess = mob.getNavigation().moveTo(path, LogicaConfig.INVESTIGATION_SPEED_MULTIPLIER.get());
                    }

                    if (!moveToSuccess) {
                        // 导航失败，增加计数
                        navigationFailedTicks++;

                        if (navigationFailedTicks >= MAX_NAVIGATION_FAILED_TICKS) {
                            // 超时，强制视为到达并开始环顾
                            Logica.LOGGER.warn("🔥 InvestigateGoal navigation failed for {} ticks, forcing arrival at current position",
                                    navigationFailedTicks);
                            hasArrived = true;
                            lookAroundCooldown = 5;
                            mob.getNavigation().stop();
                        } else {
                            Logica.LOGGER.warn("🔥 InvestigateGoal re-navigation failed! path: {}, moveTo: {}, failedTicks: {}/{}",
                                    path != null, moveToSuccess, navigationFailedTicks, MAX_NAVIGATION_FAILED_TICKS);
                        }
                    } else {
                        // 导航成功，重置失败计数
                        navigationFailedTicks = 0;
                    }
                }
            }
        } else {
            // 已到达，环顾四周
            lookAroundTimer++;

            // 定期转向随机方向
            if (--lookAroundCooldown <= 0) {
                lookAroundCooldown = LogicaConfig.LOOK_AROUND_INTERVAL.get();

                // 随机选择一个方向
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = 10.0;
                double lookX = mob.getX() + Math.cos(angle) * radius;
                double lookZ = mob.getZ() + Math.sin(angle) * radius;
                double lookY = mob.getY() + mob.getEyeHeight();

                // 转向该方向
                mob.getLookControl().setLookAt(lookX, lookY, lookZ, 10.0F, mob.getMaxHeadXRot());

                Logica.LOGGER.debug("Looking at ({}, {}, {}) - timer: {}/{} ticks",
                        (int)lookX, (int)lookY, (int)lookZ,
                        lookAroundTimer, LogicaConfig.INVESTIGATION_DURATION_TICKS.get());
            }

            // 检查是否完成环顾
            if (lookAroundTimer >= LogicaConfig.INVESTIGATION_DURATION_TICKS.get()) {
                Logica.LOGGER.info("🔥 Mob {} completed lookAround ({} ticks), Goal should stop now",
                        mob.getName().getString(), lookAroundTimer);
            }
        }

        // 实时检查是否发现目标（通过Aperi Oculos）
        // 如果发现目标，PerceptionEventHandler会切换状态到COMBAT，这个Goal会自动停止
    }

    /**
     * 是否需要重复检查canUse
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
