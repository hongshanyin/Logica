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
            Logica.LOGGER.info("🔥 PatrolGoal.start() CALLED for {} with {} waypoints, current index: {}",
                    mob.getName().getString(), waypoints.size(), currentWaypointIndex);
        }

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
                    mob.getNavigation().moveTo(path, LogicaConfig.PATROL_SPEED_MULTIPLIER.get());
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

        // 生成到下一个路径点的搜索路径
        generateSearchPath();
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        mob.getNavigation().stop();

        // 🔥 记录离开巡逻时的位置（只在首次被吸引离开时记录）
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

                        // 生成到下一个路径点的搜索路径，继续正常巡逻
                        generateSearchPath();
                    } else {
                        // 继续前往（如果导航完成，重新设置）
                        if (mob.getNavigation().isDone()) {
                            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(interruptedPos, 1);
                            if (path != null) {
                                mob.getNavigation().moveTo(path, LogicaConfig.PATROL_SPEED_MULTIPLIER.get());
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
                                    // 生成正常巡逻路径
                                    generateSearchPath();
                                }
                            }
                        }
                    }
                } else {
                    // 离开点不存在了，取消返回状态
                    isReturningToInterruptedPosition = false;
                    returnFailureCount = 0;
                    generateSearchPath(); // 生成正常巡逻路径
                }
            });

            // 🔥 如果没有放弃，继续返回模式
            if (isReturningToInterruptedPosition) {
                return; // 阻止正常巡逻
            }
            // 否则继续执行下面的正常巡逻逻辑
        }

        if (isWaiting) {
            // 停留并环顾
            waitTimer++;

            // 环顾四周
            if (--lookAroundCooldown <= 0) {
                lookAroundCooldown = LogicaConfig.LOOK_AROUND_INTERVAL.get();

                // 朝向随机方向
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = 10.0;
                double lookX = mob.getX() + Math.cos(angle) * radius;
                double lookZ = mob.getZ() + Math.sin(angle) * radius;
                double lookY = mob.getY() + mob.getEyeHeight();

                mob.getLookControl().setLookAt(lookX, lookY, lookZ, 10.0F, mob.getMaxHeadXRot());
            }

            // 停留结束
            if (waitTimer >= 60) { // 3秒
                isWaiting = false;
                waitTimer = 0;

                // 前往下一个路径点
                currentWaypointIndex = (currentWaypointIndex + 1) % waypoints.size();
                generateSearchPath();

                if (LogicaConfig.shouldLogNavigation()) {
                    Logica.LOGGER.debug("Mob {} moving to next waypoint: {}",
                            mob.getName().getString(), currentWaypointIndex);
                }
            }
        } else {
            // 移动到搜索路径
            followSearchPath();
        }
    }

    /**
     * 生成从当前路径点到下一路径点的搜索路径（中间点）
     */
    private void generateSearchPath() {
        searchPath.clear();
        currentSearchIndex = 0;

        BlockPos targetWaypoint = waypoints.get(currentWaypointIndex);

        // 🔥 从当前位置到目标路径点生成搜索路径
        Vec3 startPos = mob.position();
        Vec3 endPos = Vec3.atCenterOf(targetWaypoint);

        // 🔥 如果距离太近（已经在路径点上），直接标记为到达
        double distanceToTarget = startPos.distanceTo(endPos);
        if (distanceToTarget < 5.0) {
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
        int intermediateCount = 2 + random.nextInt(3); // 2, 3, or 4

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

        // 到达搜索点（距离判定5格，宽松判定确保流畅移动）
        if (distance < 5.0) {
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
