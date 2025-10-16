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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * 巡逻Goal（方案B：顺序访问所有路径点）
 *
 * 功能：
 * - 在PATROL策略且IDLE状态时触发
 * - 按照距离标记方块的远近排序路径点
 * - 强制按顺序访问所有路径点（从最近到最远，循环）
 * - 到达路径点后短暂停留，环顾四周
 * - 速度1.2x，确保完整覆盖所有路径点
 *
 * 优先级：3（低于攻击、追踪、调查）
 */
public class PatrolGoal extends Goal {

    // ==================== 常量定义 ====================

    /** 到达路径点的距离阈值（格） */
    private static final double ARRIVAL_DISTANCE = 5.0;

    /** 返回中断位置的到达距离（格） */
    private static final double RETURN_ARRIVAL_DISTANCE = 3.0;

    /** 在路径点等待的时长（tick），60 tick = 3秒 */
    private static final int WAIT_DURATION_TICKS = 60;

    /** 返回中断位置的最大失败次数 */
    private static final int MAX_RETURN_FAILURE_COUNT = 10;

    /** 搜索路径中间点最小数量 */
    private static final int MIN_INTERMEDIATE_POINTS = 2;

    /** 搜索路径中间点最大数量 */
    private static final int MAX_INTERMEDIATE_POINTS = 4;

    /** 环顾四周的视野半径（格） */
    private static final double LOOK_AROUND_RADIUS = 10.0;

    /** 快速转向速度（正常为10.0F） */
    private static final float LOOK_SPEED = 10.0F;

    // ==================== 实例变量 ====================

    private final Mob mob;
    private final Random random = new Random();

    // 路径点系统
    private List<BlockPos> waypoints;           // 按距离标记方块排序的路径点
    private int currentWaypointIndex;

    // 中间点搜索路径（保留巡逻的随机性）
    private List<Vec3> searchPath;
    private int currentSearchIndex;

    // 停留逻辑
    private boolean isWaiting;
    private int waitTimer;
    private int lookAroundCooldown;

    // 返回中断位置标记
    private boolean isReturningToInterruptedPosition;
    private int returnFailureCount; // 返回失败计数器

    public PatrolGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.searchPath = new ArrayList<>();
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

        // 只在PATROL策略且IDLE状态下执行
        if (aiCap.getStrategy() != AIStrategy.PATROL) {
            return false;
        }

        if (aiCap.getState() != AIState.IDLE) {
            return false;
        }

        // 必须有路径点
        List<BlockPos> waypointsList = aiCap.getWaypoints();
        if (waypointsList == null || waypointsList.isEmpty()) {
            if (LogicaConfig.shouldLogStrategyApplication()) {
                Logica.LOGGER.warn("Mob {} has PATROL strategy but no waypoints!",
                        mob.getName().getString());
            }
            return false;
        }

        // 🔥 按距离标记方块排序路径点（从近到远）
        BlockPos markerPos = aiCap.getStrategyMarkerPos();
        if (markerPos != null && (this.waypoints == null || !this.waypoints.equals(waypointsList))) {
            // 只在首次或路径点列表改变时排序
            List<BlockPos> sortedWaypoints = new ArrayList<>(waypointsList);
            sortedWaypoints.sort((pos1, pos2) -> {
                double dist1 = markerPos.distSqr(pos1);
                double dist2 = markerPos.distSqr(pos2);
                return Double.compare(dist1, dist2);
            });

            this.waypoints = sortedWaypoints;

            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.info("Sorted {} waypoints for patrol from marker at {}",
                        sortedWaypoints.size(), markerPos);
            }
        } else if (this.waypoints == null) {
            // 没有标记方块位置，使用原始顺序
            this.waypoints = new ArrayList<>(waypointsList);
        }

        this.currentWaypointIndex = aiCap.getCurrentWaypointIndex();

        // 确保索引有效
        if (currentWaypointIndex < 0 || currentWaypointIndex >= waypoints.size()) {
            currentWaypointIndex = 0;
            aiCap.setCurrentWaypointIndex(0);
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
        if (aiCap.getState() != AIState.IDLE) {
            return false;
        }

        // 如果策略改变，停止
        if (aiCap.getStrategy() != AIStrategy.PATROL) {
            return false;
        }

        return true;
    }

    /**
     * 开始执行
     */
    @Override
    public void start() {
        this.isWaiting = false;
        this.waitTimer = 0;
        this.lookAroundCooldown = 0;
        this.isReturningToInterruptedPosition = false;
        this.returnFailureCount = 0;

        if (LogicaConfig.shouldLogGoalLifecycle()) {
            Logica.LOGGER.info("PatrolGoal.start() for {} with {} waypoints, current index: {}",
                    mob.getName().getString(), waypoints.size(), currentWaypointIndex);
        }

        // 优先返回离开点（如果存在）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            BlockPos interruptedPos = cap.getInterruptedPatrolPosition();
            if (interruptedPos != null) {
                if (LogicaConfig.shouldLogGoalLifecycle()) {
                    Logica.LOGGER.info("Mob {} returning to interrupted patrol position: {}",
                            mob.getName().getString(), interruptedPos);
                }

                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(interruptedPos, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, LogicaConfig.PATROL_SPEED_MULTIPLIER.get());
                    // 标记正在返回，但不清除离开点（等到达后再清除）
                    this.isReturningToInterruptedPosition = true;
                } else {
                    // 无法创建路径，直接放弃返回
                    if (LogicaConfig.shouldLogGoalLifecycle()) {
                        Logica.LOGGER.warn("Mob {} cannot create path to interrupted position {}, giving up",
                                mob.getName().getString(), interruptedPos);
                    }
                    cap.setInterruptedPatrolPosition(null);
                }
                return;
            }
        });

        // 生成到下一个路径点的搜索路径
        generateSearchPath();
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        mob.getNavigation().stop();

        // 记录离开巡逻时的位置（只在首次被吸引离开时记录）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setCurrentWaypointIndex(currentWaypointIndex);

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
        if (waypoints == null || waypoints.isEmpty()) {
            return;
        }

        if (isReturningToInterruptedPosition) {
            tickReturnToInterruptedPosition();
            return;
        }

        if (isWaiting) {
            tickWaitingAtWaypoint();
        } else {
            tickMovingToWaypoint();
        }
    }

    /**
     * 处理返回中断位置的逻辑
     * 检查是否到达、继续导航、或放弃返回
     */
    private void tickReturnToInterruptedPosition() {
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            BlockPos interruptedPos = cap.getInterruptedPatrolPosition();

            if (interruptedPos == null) {
                cancelReturnMode();
                generateSearchPath();
                return;
            }

            if (hasReachedInterruptedPosition(interruptedPos)) {
                completeReturnToInterruptedPosition(cap, interruptedPos);
            } else {
                continueNavigatingToInterruptedPosition(interruptedPos);
            }
        });

        // 如果没有放弃，继续返回模式
        if (!isReturningToInterruptedPosition) {
            // 已取消返回，继续正常巡逻
            return;
        }
    }

    /**
     * 检查是否已到达中断位置
     */
    private boolean hasReachedInterruptedPosition(BlockPos interruptedPos) {
        Vec3 mobPos = mob.position();
        Vec3 targetPos = Vec3.atCenterOf(interruptedPos);
        return mobPos.distanceTo(targetPos) < RETURN_ARRIVAL_DISTANCE;
    }

    /**
     * 完成返回中断位置，恢复正常巡逻
     */
    private void completeReturnToInterruptedPosition(IAICapability cap, BlockPos interruptedPos) {
        if (LogicaConfig.shouldLogNavigation()) {
            Logica.LOGGER.info("Mob {} reached interrupted position {}, clearing and resuming patrol",
                    mob.getName().getString(), interruptedPos);
        }

        cap.setInterruptedPatrolPosition(null);
        isReturningToInterruptedPosition = false;
        returnFailureCount = 0;

        generateSearchPath();
    }

    /**
     * 继续前往中断位置
     */
    private void continueNavigatingToInterruptedPosition(BlockPos interruptedPos) {
        if (mob.getNavigation().isDone()) {
            net.minecraft.world.level.pathfinder.Path path =
                mob.getNavigation().createPath(interruptedPos, 1);

            if (path != null) {
                mob.getNavigation().moveTo(path, LogicaConfig.PATROL_SPEED_MULTIPLIER.get());
                returnFailureCount = 0;
            } else {
                handleReturnNavigationFailure(interruptedPos);
            }
        }
    }

    /**
     * 处理返回导航失败
     */
    private void handleReturnNavigationFailure(BlockPos interruptedPos) {
        returnFailureCount++;

        if (LogicaConfig.shouldLogNavigation()) {
            Logica.LOGGER.warn("Mob {} failed to create path to interrupted position {} (attempt {}/{})",
                    mob.getName().getString(), interruptedPos,
                    returnFailureCount, MAX_RETURN_FAILURE_COUNT);
        }

        if (returnFailureCount >= MAX_RETURN_FAILURE_COUNT) {
            abandonReturnToInterruptedPosition(interruptedPos);
        }
    }

    /**
     * 放弃返回中断位置
     */
    private void abandonReturnToInterruptedPosition(BlockPos interruptedPos) {
        if (LogicaConfig.shouldLogNavigation()) {
            Logica.LOGGER.warn("Mob {} giving up returning to interrupted position {} after {} failures",
                    mob.getName().getString(), interruptedPos, MAX_RETURN_FAILURE_COUNT);
        }

        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setInterruptedPatrolPosition(null);
        });

        cancelReturnMode();
        generateSearchPath();
    }

    /**
     * 取消返回模式
     */
    private void cancelReturnMode() {
        isReturningToInterruptedPosition = false;
        returnFailureCount = 0;
    }

    /**
     * 处理在路径点等待的逻辑
     * 包括环顾四周和等待计时
     */
    private void tickWaitingAtWaypoint() {
        waitTimer++;

        if (--lookAroundCooldown <= 0) {
            lookAroundRandomly();
            lookAroundCooldown = LogicaConfig.LOOK_AROUND_INTERVAL.get();
        }

        if (waitTimer >= WAIT_DURATION_TICKS) {
            finishWaitingAndMoveToNextWaypoint();
        }
    }

    /**
     * 随机环顾四周
     */
    private void lookAroundRandomly() {
        double angle = random.nextDouble() * Math.PI * 2;
        double lookX = mob.getX() + Math.cos(angle) * LOOK_AROUND_RADIUS;
        double lookZ = mob.getZ() + Math.sin(angle) * LOOK_AROUND_RADIUS;
        double lookY = mob.getY() + mob.getEyeHeight();

        mob.getLookControl().setLookAt(lookX, lookY, lookZ, LOOK_SPEED, mob.getMaxHeadXRot());
    }

    /**
     * 完成等待，前往下一个路径点
     */
    private void finishWaitingAndMoveToNextWaypoint() {
        isWaiting = false;
        waitTimer = 0;

        currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.size();
        generateSearchPath();

        if (LogicaConfig.shouldLogNavigation()) {
            Logica.LOGGER.debug("Mob {} moving to next waypoint: {}",
                    mob.getName().getString(), currentWaypointIndex);
        }
    }

    /**
     * 处理移动到路径点的逻辑
     */
    private void tickMovingToWaypoint() {
        followSearchPath();
    }

    /**
     * 生成从当前路径点到下一路径点的搜索路径（中间点）
     */
    private void generateSearchPath() {
        searchPath.clear();
        currentSearchIndex = 0;

        BlockPos targetWaypoint = waypoints.get(currentWaypointIndex);

        // 从当前位置到目标路径点生成搜索路径
        Vec3 startPos = mob.position();
        Vec3 endPos = Vec3.atCenterOf(targetWaypoint);

        // 如果距离太近（已经在路径点上），直接标记为到达
        double distanceToTarget = startPos.distanceTo(endPos);
        if (distanceToTarget < ARRIVAL_DISTANCE) {
            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.info("Mob {} already near waypoint {} (distance: {}), starting wait",
                        mob.getName().getString(), currentWaypointIndex, distanceToTarget);
            }
            // 直接进入等待状态
            mob.getNavigation().stop();
            isWaiting = true;
            waitTimer = 0;
            lookAroundCooldown = 0;
            return;
        }

        Vec3 direction = endPos.subtract(startPos);
        Vec3 perpendicular = new Vec3(-direction.z, 0, direction.x).normalize(); // 垂直向量

        double searchRadius = LogicaConfig.PATROL_SEARCH_RADIUS.get();

        // 生成2-4个随机中间点
        int intermediateCount = MIN_INTERMEDIATE_POINTS +
                               random.nextInt(MAX_INTERMEDIATE_POINTS - MIN_INTERMEDIATE_POINTS + 1);

        for (int i = 1; i <= intermediateCount; i++) {
            double progress = (double) i / (intermediateCount + 1);
            Vec3 linePoint = startPos.lerp(endPos, progress);

            // 随机偏移（±搜索半径）
            double offset = (random.nextDouble() * 2 - 1) * searchRadius;
            Vec3 searchPoint = linePoint.add(perpendicular.scale(offset));

            searchPath.add(searchPoint);
        }

        // 最后添加终点路径点
        searchPath.add(endPos);

        if (LogicaConfig.shouldLogNavigation()) {
            Logica.LOGGER.info("Generated search path with {} intermediate points for patrol from current pos to waypoint {}",
                    intermediateCount, currentWaypointIndex);
        }

        // 立即前往第一个搜索点
        navigateToCurrentSearchPoint();
    }

    /**
     * 沿搜索路径移动
     */
    private void followSearchPath() {
        if (searchPath.isEmpty() || currentSearchIndex >= searchPath.size()) {
            return;
        }

        Vec3 targetPoint = searchPath.get(currentSearchIndex);
        Vec3 mobPos = mob.position();
        double distance = mobPos.distanceTo(targetPoint);

        // 到达搜索点（宽松判定确保流畅移动）
        if (distance < ARRIVAL_DISTANCE) {
            currentSearchIndex++;

            if (currentSearchIndex >= searchPath.size()) {
                // 到达最终路径点，开始停留
                mob.getNavigation().stop();
                isWaiting = true;
                waitTimer = 0;
                lookAroundCooldown = 0;

                if (LogicaConfig.shouldLogNavigation()) {
                    Logica.LOGGER.debug("Mob {} reached waypoint {}, waiting",
                            mob.getName().getString(), currentWaypointIndex);
                }
            } else {
                // 前往下一个搜索点
                navigateToCurrentSearchPoint();
            }
        } else {
            // 继续前往（处理可能的路径丢失）
            if (mob.getNavigation().isDone()) {
                navigateToCurrentSearchPoint();
            }
        }
    }

    /**
     * 导航到当前搜索点
     */
    private void navigateToCurrentSearchPoint() {
        if (searchPath.isEmpty() || currentSearchIndex >= searchPath.size()) {
            return;
        }

        Vec3 targetPoint = searchPath.get(currentSearchIndex);
        BlockPos targetPos = new BlockPos((int)targetPoint.x, (int)targetPoint.y, (int)targetPoint.z);

        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetPos, 0);
        if (path != null) {
            mob.getNavigation().moveTo(path, LogicaConfig.PATROL_SPEED_MULTIPLIER.get());

            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.debug("Patrol navigating to search point {} ({}/{})",
                        targetPos, currentSearchIndex + 1, searchPath.size());
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
