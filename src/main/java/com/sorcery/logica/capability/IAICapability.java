package com.sorcery.logica.capability;

import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.ai.AIStrategy;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * AI能力接口
 *
 * 存储怪物的AI状态、策略和路径点信息
 */
public interface IAICapability {

    // ==================== AI状态 ====================

    AIState getState();
    void setState(AIState state);

    // ==================== AI策略 ====================

    AIStrategy getStrategy();
    void setStrategy(AIStrategy strategy);

    /**
     * 获取区域编号（0-15，用于区分不同区域的巡逻路线）
     */
    int getAreaTeam();
    void setAreaTeam(int team);

    // ==================== 位置信息 ====================

    /**
     * 获取生成位置（用于Guard策略）
     */
    BlockPos getSpawnPosition();
    void setSpawnPosition(BlockPos pos);

    /**
     * 获取策略标记方块位置
     */
    BlockPos getStrategyMarkerPos();
    void setStrategyMarkerPos(BlockPos pos);

    // ==================== 路径点 ====================

    /**
     * 获取路径点列表（用于Patrol/Sentries策略）
     */
    List<BlockPos> getWaypoints();
    void setWaypoints(List<BlockPos> waypoints);

    /**
     * 获取当前路径点索引
     */
    int getCurrentWaypointIndex();
    void setCurrentWaypointIndex(int index);

    // ==================== 追踪信息 ====================

    /**
     * 获取最后已知目标位置（用于TRACKING/SEARCHING状态）
     */
    BlockPos getLastKnownTargetPos();
    void setLastKnownTargetPos(BlockPos pos);

    /**
     * 获取追踪计时器
     */
    int getTrackingTicks();
    void setTrackingTicks(int ticks);
}
