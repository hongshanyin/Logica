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
 *
 * 性能优化：
 * - 缓存世界中是否存在策略方块，避免重复检测
 * - 没有策略方块的世界直接跳过所有处理
 * - 普通怪物延迟注册Goals（懒加载）
 */
@Mod.EventBusSubscriber(modid = Logica.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EntitySpawnHandler {


    /**
     * 实体加入世界时的处理
     *
     * 功能：
     * 1. 为新生成的实体：延迟注册Goals（由BlockEventHandler或PerceptionEventHandler处理）
     * 2. 为重新加载的实体：检查Capability并重新注册Goals（恢复保存的策略）
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只处理服务端
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 只处理Mob
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }

        // 检查是否有保存的策略数据（重新加载的实体）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(aiCap -> {
            AIStrategy strategy = aiCap.getStrategy();

            // 情况1：策略怪物（GUARD/SENTRIES/PATROL）
            if (strategy != AIStrategy.NONE) {
                // 检查Goals是否已存在（通过检查Goal列表）
                boolean hasStrategyGoals = mob.goalSelector.getAvailableGoals().stream()
                        .anyMatch(goal -> {
                            String name = goal.getGoal().getClass().getSimpleName();
                            return name.equals("GuardGoal") ||
                                   name.equals("SentriesGoal") ||
                                   name.equals("PatrolGoal");
                        });

                if (!hasStrategyGoals) {
                    Logica.LOGGER.info("Restoring strategy {} goals for reloaded entity: {}",
                            strategy, mob.getName().getString());

                    // 根据策略重新注册Goals
                    registerStrategyGoals(mob, strategy);

                    // 标记已注册（用于其他系统判断）
                    mob.getPersistentData().putBoolean("logica_strategy_goals_registered", true);
                    mob.getPersistentData().putBoolean("logica_basic_goals_registered", true);
                }
            }
            // 情况2：基础策略怪物（只有 InvestigateGoal 和 SearchingGoal）
            else if (aiCap.hasBasicGoals()) {
                // 检查是否有基础Goals（通过检查Goal列表）
                boolean hasInvestigateGoal = mob.goalSelector.getAvailableGoals().stream()
                        .anyMatch(goal -> goal.getGoal().getClass().getSimpleName().equals("InvestigateGoal"));

                // 如果没有InvestigateGoal，说明需要恢复
                if (!hasInvestigateGoal) {
                    Logica.LOGGER.info("Restoring basic investigation goals for reloaded entity: {}",
                            mob.getName().getString());

                    // 移除冲突的原版Goals
                    removeConflictingGoals(mob);

                    // 重新注册基础调查Goals
                    mob.goalSelector.addGoal(3, new InvestigateGoal(mob));
                    mob.goalSelector.addGoal(6, new SearchingGoal(mob));

                    // 标记已注册（用于当前会话）
                    mob.getPersistentData().putBoolean("logica_basic_goals_registered", true);
                }
            }
        });
    }


    /**
     * 为策略怪物注册完整的Goals（包括基础Goals和策略Goals）
     *
     * 注意：此方法用于重新加载实体时恢复Goals
     * 调用者负责检查是否需要注册（避免重复）
     */
    public static void registerStrategyGoals(Mob mob, AIStrategy strategy) {
        // 移除冲突的Goals
        removeConflictingGoals(mob);

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
     * 为普通怪物注册基础调查Goals
     * 允许它们响应声音诱饵
     *
     * 注意：此方法由PerceptionEventHandler在怪物首次感知玩家时调用
     */
    public static void registerBasicInvestigationGoals(Mob mob) {
        // 防止重复注册（使用独立的标记）
        if (mob.getPersistentData().getBoolean("logica_basic_goals_registered")) {
            return;
        }

        // 移除干扰的原版Goals
        removeConflictingGoals(mob);

        // 注册基础调查Goals
        // Priority 3: 调查Goal (ALERT状态)
        // Priority 6: 搜索Goal (SEARCHING状态, 低优先级不干扰RandomStrollGoal)
        mob.goalSelector.addGoal(3, new InvestigateGoal(mob));  // ALERT状态
        mob.goalSelector.addGoal(6, new SearchingGoal(mob));    // SEARCHING状态

        // 标记已注册基础Goals（持久化到Capability）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setHasBasicGoals(true);
        });

        // 也设置PersistentData标记（用于当前会话）
        mob.getPersistentData().putBoolean("logica_basic_goals_registered", true);

        Logica.LOGGER.info("Registered basic investigation goals for {}", mob.getName().getString());
    }

    /**
     * 移除可能干扰策略Goals的原版Goals
     *
     * 激进策略：移除所有Raid相关Goals和干扰性Goals
     *
     * 注意：此方法被BlockEventHandler调用，需要public访问权限
     */
    public static void removeConflictingGoals(Mob mob) {
        mob.goalSelector.getAvailableGoals().removeIf(goal -> {
            String goalName = goal.getGoal().getClass().getSimpleName();
            int priority = goal.getPriority();

            // 🔥 只移除Raid相关Goals（会完全接管AI）
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
