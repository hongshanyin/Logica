package com.sorcery.logica.events;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.blocks.*;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.goals.*;
import com.sorcery.logica.util.WaypointFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 实体生成处理器
 *
 * 功能：
 * - 检测实体周围3x3x3范围的策略标记方块
 * - 自动应用对应的AI策略
 * - 搜索并记录路径点
 */
@Mod.EventBusSubscriber(modid = Logica.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntitySpawnHandler {

    /**
     * 实体加入世界时检测策略标记方块
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // DEBUG: 记录所有实体加入事件
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof Mob mob)) return;

        // DEBUG: 记录检测到的Mob
        Logica.LOGGER.info("Detecting Mob spawn: {} at {}",
                mob.getName().getString(), mob.blockPosition());

        BlockPos mobPos = mob.blockPosition();
        Level level = mob.level();

        // 扫描3x3x3范围查找策略标记方块
        AIStrategy strategy = null;
        BlockPos strategyMarkerPos = null;
        int areaTeam = 0; // 区域编号

        Logica.LOGGER.debug("Scanning 3x3x3 area around {} for strategy markers...", mobPos);

        outerLoop:
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos checkPos = mobPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    Block block = state.getBlock();

                    if (block instanceof GuardMarkerBlock) {
                        strategy = AIStrategy.GUARD;
                        strategyMarkerPos = checkPos;
                        areaTeam = 0; // Guard不使用区域编号
                        Logica.LOGGER.info("Found GUARD marker at {}", checkPos);
                        break outerLoop;
                    } else if (block instanceof SentriesMarkerBlock sentries) {
                        strategy = AIStrategy.SENTRIES;
                        strategyMarkerPos = checkPos;
                        areaTeam = sentries.getTeamId();
                        Logica.LOGGER.info("Found SENTRIES marker (team {}) at {}", areaTeam, checkPos);
                        break outerLoop;
                    } else if (block instanceof PatrolMarkerBlock patrol) {
                        strategy = AIStrategy.PATROL;
                        strategyMarkerPos = checkPos;
                        areaTeam = patrol.getTeamId();
                        Logica.LOGGER.info("Found PATROL marker (team {}) at {}", areaTeam, checkPos);
                        break outerLoop;
                    }
                }
            }
        }

        // 应用策略
        if (strategy != null) {
            applyStrategy(mob, strategy, strategyMarkerPos, level, areaTeam);
        } else {
            Logica.LOGGER.debug("No strategy marker found near {}", mob.getName().getString());
            // 即使没有策略，也注册基础的调查Goals（响应声音诱饵）
            registerBasicInvestigationGoals(mob);
        }
    }

    /**
     * 应用AI策略到实体
     */
    private static void applyStrategy(Mob mob, AIStrategy strategy, BlockPos markerPos, Level level, int areaTeam) {
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setStrategy(strategy);
            cap.setSpawnPosition(mob.blockPosition());
            cap.setStrategyMarkerPos(markerPos);
            cap.setAreaTeam(areaTeam); // 🔥 保存区域编号

            Logica.LOGGER.info("Applied {} strategy (team {}) to {} at {}",
                    strategy, areaTeam, mob.getName().getString(), mob.blockPosition());
        });

        // 如果是Patrol或Sentries策略，查找路径点
        if (strategy == AIStrategy.PATROL || strategy == AIStrategy.SENTRIES) {
            // 🔥 传递区域编号到 WaypointFinder
            List<BlockPos> waypoints = WaypointFinder.findWaypoints(level, markerPos, strategy, areaTeam);

            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                cap.setWaypoints(waypoints);
            });

            if (!waypoints.isEmpty()) {
                Logica.LOGGER.info("Found {} waypoints for {} strategy (team {})",
                        waypoints.size(), strategy, areaTeam);
            } else {
                Logica.LOGGER.warn("No waypoints found for {} strategy (team {})",
                        strategy, areaTeam);
            }
        }

        // 注册对应的Goals
        registerStrategyGoals(mob, strategy);
    }

    /**
     * 注册AI Goals到实体
     *
     * 优先级架构（方案B改进）：
     * Priority -1: CombatMonitorGoal (新增 - 监控战斗状态，不占用标志位)
     * Priority  0: FloatGoal (原版，保留不动)
     *              TrackingGoal (TRACKING状态 - 战斗中丢失目标时追踪)
     * Priority  1: InvestigateGoal (ALERT状态 - 调查声音来源)
     * Priority  2: SearchingGoal (SEARCHING状态 - 搜索模式)
     * Priority  3: GuardGoal / SentriesGoal / PatrolGoal (IDLE状态 - 策略行为)
     * Priority  4+: 原版Raid Goals (被自然抑制)
     *
     * 设计原理：
     * - 使用阶梯优先级避免同一优先级的Goals相互竞争
     * - 状态机确保每个优先级只有一个Goal的canUse()返回true
     * - TRACKING(0) > ALERT(1) > SEARCHING(2) > IDLE(3) 符合紧急程度
     * - 所有自定义Goals优先级低于FloatGoal，确保浮水功能
     */
    private static void registerStrategyGoals(Mob mob, AIStrategy strategy) {
        // 移除可能干扰的原版随机移动Goals
        removeConflictingGoals(mob);

        // Priority -1: CombatMonitorGoal（最高优先级，不占用标志位）
        mob.goalSelector.addGoal(-1, new CombatMonitorGoal(mob));

        // Priority 0: TrackingGoal（TRACKING状态，最紧急）
        mob.goalSelector.addGoal(0, new TrackingGoal(mob));

        // Priority 1: InvestigateGoal（ALERT状态，次紧急）
        mob.goalSelector.addGoal(1, new InvestigateGoal(mob));

        // Priority 2: SearchingGoal（SEARCHING状态）
        mob.goalSelector.addGoal(2, new SearchingGoal(mob));

        // Priority 3: 策略特定的Goal（IDLE状态，基础巡逻）
        switch (strategy) {
            case GUARD:
                mob.goalSelector.addGoal(3, new GuardGoal(mob));
                Logica.LOGGER.debug("Registered GuardGoal for {}", mob.getName().getString());
                break;

            case SENTRIES:
                mob.goalSelector.addGoal(3, new SentriesGoal(mob));
                Logica.LOGGER.debug("Registered SentriesGoal for {}", mob.getName().getString());
                break;

            case PATROL:
                mob.goalSelector.addGoal(3, new PatrolGoal(mob));
                Logica.LOGGER.debug("Registered PatrolGoal for {}", mob.getName().getString());
                break;
        }

        // 输出所有Goal及其优先级
        Logica.LOGGER.info("Goals registered for {} with {} strategy", mob.getName().getString(), strategy);
        Logica.LOGGER.info("=== All Goals for {} ===", mob.getName().getString());
        mob.goalSelector.getAvailableGoals().forEach(goal -> {
            Logica.LOGGER.info("  Priority {}: {} (flags: {})",
                goal.getPriority(),
                goal.getGoal().getClass().getSimpleName(),
                goal.getFlags());
        });
        Logica.LOGGER.info("=== End Goals List ===");
    }

    /**
     * 为未受策略影响的怪物注册基础调查Goals
     * 允许它们响应声音诱饵
     */
    private static void registerBasicInvestigationGoals(Mob mob) {
        // 移除干扰的原版Goals
        removeConflictingGoals(mob);

        // 使用阶梯优先级，避免Goals竞争
        mob.goalSelector.addGoal(1, new InvestigateGoal(mob));  // ALERT状态
        mob.goalSelector.addGoal(2, new SearchingGoal(mob));    // SEARCHING状态

        Logica.LOGGER.debug("Registered basic investigation goals for {}", mob.getName().getString());
    }

    /**
     * 移除可能干扰策略Goals的原版Goals
     *
     * 激进策略：移除所有Raid相关Goals和干扰性Goals
     */
    private static void removeConflictingGoals(Mob mob) {
        mob.goalSelector.getAvailableGoals().removeIf(goal -> {
            String goalName = goal.getGoal().getClass().getSimpleName();
            int priority = goal.getPriority();

            // 移除RandomStrollGoal - 会干扰策略移动
            if (goalName.contains("RandomStroll")) {
                Logica.LOGGER.debug("Removed {} from {}", goalName, mob.getName().getString());
                return true;
            }

            // 移除RandomLookAroundGoal - 会干扰转向控制
            if (goalName.contains("RandomLookAround")) {
                Logica.LOGGER.debug("Removed {} from {}", goalName, mob.getName().getString());
                return true;
            }

            // 移除WaterAvoidingRandomStrollGoal
            if (goalName.contains("WaterAvoidingRandomStroll")) {
                Logica.LOGGER.debug("Removed {} from {}", goalName, mob.getName().getString());
                return true;
            }

            // 移除LookAtPlayerGoal - 会干扰转向声音来源
            if (goalName.contains("LookAtPlayer")) {
                Logica.LOGGER.debug("Removed {} from {}", goalName, mob.getName().getString());
                return true;
            }

            // 🔥 激进移除：所有Raid相关Goals（彻底解决竞争问题）
            if (goalName.contains("Raid") || goalName.contains("Raider")) {
                Logica.LOGGER.info("Removed Raid Goal: {} (Priority {}) from {}",
                        goalName, priority, mob.getName().getString());
                return true;
            }

            // 移除Pillager的LongDistancePatrolGoal - 会干扰策略移动
            if (goalName.contains("LongDistancePatrol")) {
                Logica.LOGGER.info("Removed {} (Priority {}) from {}",
                        goalName, priority, mob.getName().getString());
                return true;
            }

            return false;
        });
    }
}
