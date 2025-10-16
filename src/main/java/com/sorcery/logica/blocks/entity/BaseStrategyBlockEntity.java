package com.sorcery.logica.blocks.entity;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.events.EntitySpawnHandler;
import com.sorcery.logica.goals.*;
import com.sorcery.logica.util.WaypointFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 策略方块BlockEntity基类
 *
 * 工作原理：
 * - 每秒tick一次（像信标）
 * - 自己查找周围3x3x3的怪物
 * - 对新生成的怪物应用策略
 * - 无需全局管理器，方块本身就有能力
 */
public abstract class BaseStrategyBlockEntity extends BlockEntity {

    /**
     * Tick检查间隔（20 tick = 1秒）
     */
    private static final int CHECK_INTERVAL = 20;

    /**
     * 策略方块的影响范围（格）- 3x3x3光环
     */
    private static final int MARKER_RANGE = 1;

    /**
     * Tick计数器
     */
    private int tickCounter = 0;

    public BaseStrategyBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 每tick执行
     */
    public void tick() {
        if (this.level == null || this.level.isClientSide()) return;

        // 控制频率：每秒执行一次
        if (++tickCounter < CHECK_INTERVAL) return;
        tickCounter = 0;

        ServerLevel serverLevel = (ServerLevel) this.level;
        applyBeaconEffect(serverLevel);
    }

    /**
     * 对周围怪物应用信标效果
     */
    private void applyBeaconEffect(ServerLevel level) {
        // 查找3x3x3范围内的怪物
        AABB searchBox = new AABB(this.worldPosition).inflate(MARKER_RANGE);
        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, searchBox);

        // 只在有新怪物需要处理时记录日志
        if (nearbyMobs.isEmpty()) {
            return; // 提前返回，不记录日志
        }

        for (Mob mob : nearbyMobs) {
            // 🔥 关键检查：只对刚生成的怪物施加策略
            // 如果怪物存在时间超过2秒（40 ticks），说明是进入光环的，不应用策略
            if (mob.tickCount > 40) {
                continue; // 不记录日志
            }

            // 检查怪物是否已经被标记处理
            if (mob.getPersistentData().getBoolean("logica_marker_applied")) {
                continue; // 不记录日志
            }

            // 检查距离（精确距离检查）
            double distance = mob.blockPosition().distSqr(this.worldPosition);
            if (distance > MARKER_RANGE * MARKER_RANGE) {
                Logica.LOGGER.debug("  - {} skipped: too far (distance={})",
                        mob.getName().getString(), Math.sqrt(distance));
                continue;
            }

            // 应用策略
            applyStrategyToMob(mob, level);

            // 标记为已处理
            mob.getPersistentData().putBoolean("logica_marker_applied", true);

            Logica.LOGGER.info("Applied {} strategy to {} (spawned {} ticks ago) via beacon effect",
                    getStrategy(), mob.getName().getString(), mob.tickCount);
        }
    }

    /**
     * 对单个怪物应用策略
     */
    private void applyStrategyToMob(Mob mob, ServerLevel level) {
        AIStrategy strategy = getStrategy();
        int areaTeam = getAreaTeam();

        // 设置Capability
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setStrategy(strategy);
            cap.setSpawnPosition(mob.blockPosition());
            cap.setStrategyMarkerPos(this.worldPosition);
            cap.setAreaTeam(areaTeam);
        });

        // 查找路径点
        if (strategy == AIStrategy.PATROL || strategy == AIStrategy.SENTRIES) {
            List<BlockPos> waypoints = WaypointFinder.findWaypoints(level, this.worldPosition, strategy, areaTeam);

            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                cap.setWaypoints(waypoints);
            });

            if (!waypoints.isEmpty()) {
                Logica.LOGGER.debug("Found {} waypoints for {} (team {})",
                        waypoints.size(), mob.getName().getString(), areaTeam);
            }
        }

        // 注册Goals
        registerStrategyGoals(mob, strategy);
    }

    /**
     * 为策略怪物注册完整的Goals（包括基础Goals和策略Goals）
     */
    private void registerStrategyGoals(Mob mob, AIStrategy strategy) {
        // 防止重复注册策略Goals
        if (mob.getPersistentData().getBoolean("logica_strategy_goals_registered")) {
            return;
        }

        // 移除冲突的Goals
        EntitySpawnHandler.removeConflictingGoals(mob);

        // 注册完整的Goals（包括基础调查Goals和策略Goals）
        mob.goalSelector.addGoal(-1, new CombatMonitorGoal(mob));
        mob.goalSelector.addGoal(2, new TrackingGoal(mob));
        mob.goalSelector.addGoal(3, new InvestigateGoal(mob));  // 基础Goal (ALERT状态)
        mob.goalSelector.addGoal(6, new SearchingGoal(mob));    // 基础Goal (SEARCHING状态, 低优先级不干扰移动)

        // 注册策略Goal
        switch (strategy) {
            case GUARD:
                mob.goalSelector.addGoal(3, new GuardGoal(mob));
                break;
            case SENTRIES:
                mob.goalSelector.addGoal(3, new SentriesGoal(mob));
                break;
            case PATROL:
                mob.goalSelector.addGoal(3, new PatrolGoal(mob));
                break;
        }

        // 标记已注册策略Goals（也包含了基础Goals）
        mob.getPersistentData().putBoolean("logica_strategy_goals_registered", true);
        mob.getPersistentData().putBoolean("logica_basic_goals_registered", true);

        Logica.LOGGER.info("Registered complete strategy goals ({}) for {}",
                strategy, mob.getName().getString());
    }

    /**
     * 获取策略类型（子类实现）
     */
    protected abstract AIStrategy getStrategy();

    /**
     * 获取区域编号（子类实现）
     */
    protected abstract int getAreaTeam();
}
