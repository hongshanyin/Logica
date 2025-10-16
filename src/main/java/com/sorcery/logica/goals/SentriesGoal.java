package com.sorcery.logica.goals;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import com.sorcery.logica.config.LogicaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 哨兵Goal
 *
 * 功能：
 * - 在SENTRIES策略且IDLE状态时触发
 * - 快速移动（1.3倍速度）
 * - 几乎不停歇（10%概率短暂停留）
 * - 如果有路径点，沿路径点巡逻
 * - 如果没有路径点，在标记方块附近大范围游荡
 * - 优先前往未访问位置
 *
 * 优先级：4（低于攻击、追踪、调查）
 */
public class SentriesGoal extends Goal {

    private final Mob mob;
    private final Random random = new Random();

    // 路径点系统
    private List<BlockPos> waypoints;
    private int currentWaypointIndex;
    private Set<BlockPos> visitedWaypoints;

    // 无路径点模式：大范围游荡
    private BlockPos centerPosition;

    // 移动控制
    private int restCooldown;
    private boolean isResting;

    // 返回中断位置标记
    private boolean isReturningToInterruptedPosition;
    private int returnFailureCount; // 返回失败计数器

    // DEBUG: 日志计数器
    private int logCounter;

    public SentriesGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
        this.visitedWaypoints = new HashSet<>();
        this.logCounter = 0;
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

        // 只在SENTRIES策略且IDLE状态下执行
        if (aiCap.getStrategy() != AIStrategy.SENTRIES) {
            return false;
        }

        if (aiCap.getState() != AIState.IDLE) {
            return false;
        }

        // 获取路径点
        this.waypoints = aiCap.getWaypoints();
        this.currentWaypointIndex = aiCap.getCurrentWaypointIndex();

        // 如果没有路径点，使用标记方块位置作为中心
        if (waypoints == null || waypoints.isEmpty()) {
            BlockPos markerPos = aiCap.getStrategyMarkerPos();
            if (markerPos == null) {
                return false;
            }
            this.centerPosition = markerPos;
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
            if (LogicaConfig.shouldLogGoalLifecycle()) {
                Logica.LOGGER.warn("🔥 SentriesGoal.canContinueToUse() - No AI capability, stopping");
            }
            return false;
        }

        AIState currentState = aiCap.getState();

        // 如果状态改变，停止
        if (currentState != AIState.IDLE) {
            if (LogicaConfig.shouldLogGoalLifecycle()) {
                Logica.LOGGER.info("🔥 SentriesGoal.canContinueToUse() - State changed to {}, STOPPING Goal",
                        currentState);
            }
            return false;
        }

        // 如果策略改变，停止
        if (aiCap.getStrategy() != AIStrategy.SENTRIES) {
            if (LogicaConfig.shouldLogGoalLifecycle()) {
                Logica.LOGGER.info("🔥 SentriesGoal.canContinueToUse() - Strategy changed, STOPPING Goal");
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
            Logica.LOGGER.info("🔥 SentriesGoal.start() CALLED for {} (was resting: {})",
                    mob.getName().getString(), isResting);
        }

        // 🔥 FIX: 重置休息状态（防止从其他Goal返回后卡在休息）
        this.restCooldown = 0;
        this.isResting = false;
        this.isReturningToInterruptedPosition = false;
        this.returnFailureCount = 0;

        // 🔥 优先返回离开点（如果存在）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            BlockPos interruptedPos = cap.getInterruptedPatrolPosition();
            if (interruptedPos != null) {
                if (LogicaConfig.shouldLogGoalLifecycle()) {
                    Logica.LOGGER.info("Mob {} returning to interrupted patrol position: {}",
                            mob.getName().getString(), interruptedPos);
                }

                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(interruptedPos, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, LogicaConfig.SENTRIES_SPEED_MULTIPLIER.get());
                    // 🔥 标记正在返回，但不清除离开点（等到达后再清除）
                    this.isReturningToInterruptedPosition = true;
                } else {
                    // 🔥 无法创建路径，直接放弃返回
                    if (LogicaConfig.shouldLogGoalLifecycle()) {
                        Logica.LOGGER.warn("Mob {} cannot create path to interrupted position {}, giving up",
                                mob.getName().getString(), interruptedPos);
                    }
                    cap.setInterruptedPatrolPosition(null);
                }
                return;
            }
        });

        if (waypoints != null && !waypoints.isEmpty()) {
            if (LogicaConfig.shouldLogGoalLifecycle()) {
                Logica.LOGGER.info("Mob {} starting sentries patrol with {} waypoints",
                        mob.getName().getString(), waypoints.size());
            }

            // 立即开始前往第一个路径点
            BlockPos targetWaypoint = waypoints.get(currentWaypointIndex);
            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetWaypoint, 0);
            boolean moveToSuccess = false;
            if (path != null) {
                // 直接传入速度倍率，让导航系统自动处理
                double speedMultiplier = LogicaConfig.SENTRIES_SPEED_MULTIPLIER.get();
                moveToSuccess = mob.getNavigation().moveTo(path, speedMultiplier);

                if (LogicaConfig.shouldLogNavigation()) {
                    Logica.LOGGER.info("🔥 moveTo() returned: {} (speed multiplier: {})",
                            moveToSuccess, speedMultiplier);
                }
            }

            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.info("Mob {} starting navigation to first waypoint: {} (path: {}, moveTo: {})",
                        mob.getName().getString(), targetWaypoint, path != null, moveToSuccess);
            }
        } else {
            if (LogicaConfig.shouldLogGoalLifecycle()) {
                Logica.LOGGER.info("Mob {} starting sentries patrol in free-roam mode around {}",
                        mob.getName().getString(), centerPosition);
            }
        }
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        if (LogicaConfig.shouldLogGoalLifecycle()) {
            Logica.LOGGER.info("🔥 SentriesGoal.stop() CALLED for {} (was resting: {})",
                    mob.getName().getString(), isResting);
        }

        mob.getNavigation().stop();

        // 🔥 记录离开巡逻时的位置（只在首次被吸引离开时记录）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            if (waypoints != null && !waypoints.isEmpty()) {
                cap.setCurrentWaypointIndex(currentWaypointIndex);
            }

            // 只在状态不是IDLE且没有已存在的离开点时才记录
            // 这样可以避免返回途中再次被打断时覆盖原始离开点
            if (cap.getState() != AIState.IDLE && cap.getInterruptedPatrolPosition() == null) {
                BlockPos currentPos = mob.blockPosition();
                cap.setInterruptedPatrolPosition(currentPos);

                if (LogicaConfig.shouldLogGoalLifecycle()) {
                    Logica.LOGGER.info("Mob {} interrupted from patrol at position: {} (state: {})",
                            mob.getName().getString(), currentPos, cap.getState());
                }
            }
        });
    }

    /**
     * 每tick执行
     */
    @Override
    public void tick() {
        // DEBUG: 每20 tick记录一次
        if (LogicaConfig.shouldLogGoalLifecycle() && ++logCounter >= 20) {
            logCounter = 0;

            // 记录当前所有正在运行的Goals
            StringBuilder runningGoals = new StringBuilder();
            mob.goalSelector.getRunningGoals().forEach(goal -> {
                runningGoals.append(goal.getGoal().getClass().getSimpleName()).append(", ");
            });

            Logica.LOGGER.info("SentriesGoal.tick() - mob: {}, resting: {}, returning: {}, navigation: {}, pos: {}, running goals: [{}]",
                    mob.getName().getString(), isResting, isReturningToInterruptedPosition,
                    mob.getNavigation().isDone() ? "done" : "moving",
                    mob.blockPosition(),
                    runningGoals.toString());
        } else if (!LogicaConfig.shouldLogGoalLifecycle()) {
            // 🔥 如果日志关闭，重置计数器避免累积
            logCounter = 0;
        }

        // 🔥 处理返回中断位置
        if (isReturningToInterruptedPosition) {
            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                BlockPos interruptedPos = cap.getInterruptedPatrolPosition();
                if (interruptedPos != null) {
                    Vec3 mobPos = mob.position();
                    Vec3 targetPos = Vec3.atCenterOf(interruptedPos);
                    double distance = mobPos.distanceTo(targetPos);

                    // 到达离开点（距离<3格）
                    if (distance < 3.0) {
                        if (LogicaConfig.shouldLogNavigation()) {
                            Logica.LOGGER.info("Mob {} reached interrupted position {}, clearing and resuming patrol",
                                    mob.getName().getString(), interruptedPos);
                        }

                        // 清除离开点
                        cap.setInterruptedPatrolPosition(null);
                        isReturningToInterruptedPosition = false;
                        returnFailureCount = 0;

                        // 继续正常巡逻（不return，让下面的逻辑继续执行）
                    } else {
                        // 继续前往（如果导航完成，重新设置）
                        if (mob.getNavigation().isDone()) {
                            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(interruptedPos, 1);
                            if (path != null) {
                                mob.getNavigation().moveTo(path, LogicaConfig.SENTRIES_SPEED_MULTIPLIER.get());
                                returnFailureCount = 0; // 成功创建路径，重置计数器
                            } else {
                                // 🔥 无法创建路径，增加失败计数
                                returnFailureCount++;
                                if (LogicaConfig.shouldLogNavigation()) {
                                    Logica.LOGGER.warn("Mob {} failed to create path to interrupted position {} (attempt {}/10)",
                                            mob.getName().getString(), interruptedPos, returnFailureCount);
                                }

                                // 🔥 失败10次后放弃返回
                                if (returnFailureCount >= 10) {
                                    if (LogicaConfig.shouldLogNavigation()) {
                                        Logica.LOGGER.warn("Mob {} giving up returning to interrupted position {} after 10 failures",
                                                mob.getName().getString(), interruptedPos);
                                    }
                                    cap.setInterruptedPatrolPosition(null);
                                    isReturningToInterruptedPosition = false;
                                    returnFailureCount = 0;
                                    // 不return，让下面的逻辑继续执行（切换到正常巡逻）
                                } else {
                                    return; // 继续尝试
                                }
                            }
                        } else {
                            return; // 导航未完成，继续等待
                        }
                    }
                } else {
                    // 离开点不存在了，取消返回状态
                    isReturningToInterruptedPosition = false;
                    returnFailureCount = 0;
                }
            });

            // 🔥 如果没有return，说明已经到达或放弃，继续执行下面的正常巡逻逻辑
            if (!isReturningToInterruptedPosition) {
                // 继续执行正常巡逻（不return）
            } else {
                return; // 仍在返回途中，阻止正常巡逻
            }
        }

        // 休息逻辑
        if (isResting) {
            if (--restCooldown <= 0) {
                isResting = false;
            }
            return;
        }

        // 随机决定是否休息（10%概率）
        if (random.nextDouble() < LogicaConfig.SENTRIES_REST_CHANCE.get()) {
            if (random.nextDouble() < 0.1) { // 额外的10%检查，避免太频繁
                isResting = true;
                restCooldown = 20 + random.nextInt(40); // 1-3秒
                mob.getNavigation().stop();
                return;
            }
        }

        // 移动逻辑
        if (waypoints != null && !waypoints.isEmpty()) {
            // 路径点模式
            patrolWaypoints();
        } else {
            // 自由游荡模式
            freeRoam();
        }
    }

    /**
     * 路径点巡逻模式
     */
    private void patrolWaypoints() {
        BlockPos targetWaypoint = waypoints.get(currentWaypointIndex);
        Vec3 mobPos = mob.position();
        Vec3 waypointPos = Vec3.atCenterOf(targetWaypoint);

        double distance = mobPos.distanceTo(waypointPos);

        // 到达路径点（距离判定1.0格，acceptableRadius设为0以避免提前停止）
        if (distance < 1.0) {
            // 标记为已访问
            visitedWaypoints.add(targetWaypoint);

            // 如果所有路径点都访问过，清空记录重新开始
            if (visitedWaypoints.size() >= waypoints.size()) {
                visitedWaypoints.clear();
                if (LogicaConfig.shouldLogNavigation()) {
                    Logica.LOGGER.info("Mob {} completed sentries circuit, restarting",
                            mob.getName().getString());
                }
            }

            // 选择下一个路径点（优先未访问的）
            selectNextWaypoint();

            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.info("Mob {} reached waypoint {}, moving to next: {}",
                        mob.getName().getString(), currentWaypointIndex,
                        waypoints.get(currentWaypointIndex));
            }
        }

        // 前往当前目标路径点（如果导航完成或失败，重新设置）
        if (mob.getNavigation().isDone()) {
            targetWaypoint = waypoints.get(currentWaypointIndex);
            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetWaypoint, 0);
            boolean success = false;
            if (path != null) {
                success = mob.getNavigation().moveTo(path, LogicaConfig.SENTRIES_SPEED_MULTIPLIER.get());
            }

            if (!success) {
                if (LogicaConfig.shouldLogNavigation()) {
                    Logica.LOGGER.warn("Mob {} failed to navigate to waypoint {}, skipping",
                            mob.getName().getString(), targetWaypoint);
                }
                // 跳到下一个路径点
                selectNextWaypoint();
            }
        }
    }

    /**
     * 选择下一个路径点（优先未访问的）
     */
    private void selectNextWaypoint() {
        // 首先尝试找到未访问的路径点
        for (int i = 0; i < waypoints.size(); i++) {
            int index = (currentWaypointIndex + 1 + i) % waypoints.size();
            BlockPos waypoint = waypoints.get(index);

            if (!visitedWaypoints.contains(waypoint)) {
                currentWaypointIndex = index;
                return;
            }
        }

        // 如果所有路径点都访问过，选择下一个
        currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.size();
    }

    /**
     * 自由游荡模式
     */
    private void freeRoam() {
        if (centerPosition == null) {
            return;
        }

        double sentriesRadius = LogicaConfig.SENTRIES_RADIUS.get();
        Vec3 currentPos = mob.position();
        Vec3 centerPos = Vec3.atCenterOf(centerPosition);
        double distanceToCenter = currentPos.distanceTo(centerPos);

        // 检查是否到达目标或导航完成
        if (mob.getNavigation().isDone() || distanceToCenter > sentriesRadius * 1.5) {
            // 在中心附近随机选择一个位置
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * sentriesRadius;

            int targetX = (int) (centerPosition.getX() + Math.cos(angle) * distance);
            int targetZ = (int) (centerPosition.getZ() + Math.sin(angle) * distance);
            BlockPos targetPos = new BlockPos(targetX, centerPosition.getY(), targetZ);

            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetPos, 1);
            if (path != null) {
                mob.getNavigation().moveTo(path, LogicaConfig.SENTRIES_SPEED_MULTIPLIER.get());
            }

            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.debug("Mob {} choosing new sentries target at distance {} from center: {}",
                        mob.getName().getString(), distance, targetPos);
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
