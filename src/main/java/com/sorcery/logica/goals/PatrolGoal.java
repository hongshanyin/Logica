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
 * 巡逻Goal（方案A：中间点游荡法）
 *
 * 功能：
 * - 在PATROL策略且IDLE状态时触发
 * - 在路径点之间生成2-4个随机偏移的中间点
 * - 依次访问中间点，形成"之字形"游荡搜索路径
 * - 到达路径点后短暂停留，环顾四周
 * - 速度1.2x，覆盖面积比哨兵策略大300-400%
 *
 * 优先级：3（低于攻击、追踪、调查）
 */
public class PatrolGoal extends Goal {

    private final Mob mob;
    private final Random random = new Random();

    // 路径点系统
    private List<BlockPos> waypoints;
    private int currentWaypointIndex;

    // 中间点游荡系统
    private List<Vec3> searchPath;      // 当前段的搜索路径（中间点列表）
    private int currentSearchIndex;     // 当前搜索路径中的索引

    // 停留逻辑
    private boolean isWaiting;
    private int waitTimer;
    private int lookAroundCooldown;

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
            Logica.LOGGER.warn("Mob {} has PATROL strategy but no waypoints!",
                    mob.getName().getString());
            return false;
        }

        this.waypoints = waypointsList;
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

        Logica.LOGGER.info("🔥 PatrolGoal.start() CALLED for {} with {} waypoints, current index: {}",
                mob.getName().getString(), waypoints.size(), currentWaypointIndex);

        // 生成到下一个路径点的搜索路径
        generateSearchPath();
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        mob.getNavigation().stop();

        // 保存当前路径点索引
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setCurrentWaypointIndex(currentWaypointIndex);
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

                Logica.LOGGER.debug("Mob {} moving to next waypoint: {}",
                        mob.getName().getString(), currentWaypointIndex);
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

        BlockPos startWaypoint = waypoints.get(currentWaypointIndex);
        BlockPos endWaypoint = waypoints.get((currentWaypointIndex + 1) % waypoints.size());

        // 计算方向向量和垂直向量
        Vec3 startPos = Vec3.atCenterOf(startWaypoint);
        Vec3 endPos = Vec3.atCenterOf(endWaypoint);
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

        Logica.LOGGER.info("Generated search path with {} intermediate points for patrol from {} to {}",
                intermediateCount, startWaypoint, endWaypoint);

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

        // 到达搜索点（放宽距离判定）
        if (distance < 3.0) {
            currentSearchIndex++;

            if (currentSearchIndex >= searchPath.size()) {
                // 到达最终路径点，开始停留
                mob.getNavigation().stop();
                isWaiting = true;
                waitTimer = 0;
                lookAroundCooldown = 0;

                Logica.LOGGER.debug("Mob {} reached waypoint {}, waiting",
                        mob.getName().getString(), currentWaypointIndex);
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

        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetPos, 1);
        if (path != null) {
            double baseSpeed = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double speed = baseSpeed * LogicaConfig.PATROL_SPEED_MULTIPLIER.get();
            mob.getNavigation().moveTo(path, speed);

            Logica.LOGGER.debug("Patrol navigating to search point {} ({}/{})",
                    targetPos, currentSearchIndex + 1, searchPath.size());
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
