package com.sorcery.logica.capability;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.ai.AIStrategy;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * AI能力实现类
 */
public class AICapability implements IAICapability {

    private AIState state = AIState.IDLE;
    private AIStrategy strategy = AIStrategy.NONE;
    private int areaTeam = 0; // 区域编号（0-15）

    private BlockPos spawnPosition = null;
    private BlockPos strategyMarkerPos = null;
    private BlockPos lastKnownTargetPos = null;

    private List<BlockPos> waypoints = new ArrayList<>();
    private int currentWaypointIndex = 0;

    private int trackingTicks = 0;

    @Override
    public AIState getState() {
        return state;
    }

    @Override
    public void setState(AIState state) {
        if (this.state != state) {
            AIState oldState = this.state;
            this.state = state;

            // DEBUG: 记录状态变化
            Logica.LOGGER.debug("AICapability state changed: {} -> {} (strategy: {})",
                    oldState, state, strategy);
        }
    }

    @Override
    public AIStrategy getStrategy() {
        return strategy;
    }

    @Override
    public void setStrategy(AIStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public int getAreaTeam() {
        return areaTeam;
    }

    @Override
    public void setAreaTeam(int team) {
        this.areaTeam = team;
    }

    @Override
    public BlockPos getSpawnPosition() {
        return spawnPosition;
    }

    @Override
    public void setSpawnPosition(BlockPos pos) {
        this.spawnPosition = pos;
    }

    @Override
    public BlockPos getStrategyMarkerPos() {
        return strategyMarkerPos;
    }

    @Override
    public void setStrategyMarkerPos(BlockPos pos) {
        this.strategyMarkerPos = pos;
    }

    @Override
    public List<BlockPos> getWaypoints() {
        return waypoints;
    }

    @Override
    public void setWaypoints(List<BlockPos> waypoints) {
        this.waypoints = waypoints != null ? waypoints : new ArrayList<>();
    }

    @Override
    public int getCurrentWaypointIndex() {
        return currentWaypointIndex;
    }

    @Override
    public void setCurrentWaypointIndex(int index) {
        this.currentWaypointIndex = index;
    }

    @Override
    public BlockPos getLastKnownTargetPos() {
        return lastKnownTargetPos;
    }

    @Override
    public void setLastKnownTargetPos(BlockPos pos) {
        this.lastKnownTargetPos = pos;
    }

    @Override
    public int getTrackingTicks() {
        return trackingTicks;
    }

    @Override
    public void setTrackingTicks(int ticks) {
        this.trackingTicks = ticks;
    }
}
