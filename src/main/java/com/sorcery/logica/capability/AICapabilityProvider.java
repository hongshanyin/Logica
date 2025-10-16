package com.sorcery.logica.capability;

import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.ai.AIStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * AI能力提供者
 */
public class AICapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final Capability<IAICapability> AI_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final IAICapability capability = new AICapability();
    private final LazyOptional<IAICapability> optional = LazyOptional.of(() -> capability);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == AI_CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();

        // 保存 AI 状态
        nbt.putString("state", capability.getState().name());

        // 保存 AI 策略
        nbt.putString("strategy", capability.getStrategy().name());

        // 保存区域编号
        nbt.putInt("areaTeam", capability.getAreaTeam());

        // 保存生成位置
        if (capability.getSpawnPosition() != null) {
            nbt.putLong("spawnPos", capability.getSpawnPosition().asLong());
        }

        // 保存策略标记位置
        if (capability.getStrategyMarkerPos() != null) {
            nbt.putLong("markerPos", capability.getStrategyMarkerPos().asLong());
        }

        // 保存最后已知目标位置
        if (capability.getLastKnownTargetPos() != null) {
            nbt.putLong("lastTargetPos", capability.getLastKnownTargetPos().asLong());
        }

        // 保存中断巡逻位置
        if (capability.getInterruptedPatrolPosition() != null) {
            nbt.putLong("interruptedPos", capability.getInterruptedPatrolPosition().asLong());
        }

        // 保存路径点列表
        List<BlockPos> waypoints = capability.getWaypoints();
        if (waypoints != null && !waypoints.isEmpty()) {
            ListTag waypointsList = new ListTag();
            for (BlockPos pos : waypoints) {
                CompoundTag posTag = new CompoundTag();
                posTag.putLong("pos", pos.asLong());
                waypointsList.add(posTag);
            }
            nbt.put("waypoints", waypointsList);
        }

        // 保存当前路径点索引
        nbt.putInt("waypointIndex", capability.getCurrentWaypointIndex());

        // 保存追踪计时器
        nbt.putInt("trackingTicks", capability.getTrackingTicks());

        // 保存基础Goals标记
        nbt.putBoolean("hasBasicGoals", capability.hasBasicGoals());

        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // 读取 AI 状态
        if (nbt.contains("state")) {
            try {
                capability.setState(AIState.valueOf(nbt.getString("state")));
            } catch (IllegalArgumentException e) {
                capability.setState(AIState.IDLE);
            }
        }

        // 读取 AI 策略
        if (nbt.contains("strategy")) {
            try {
                capability.setStrategy(AIStrategy.valueOf(nbt.getString("strategy")));
            } catch (IllegalArgumentException e) {
                capability.setStrategy(AIStrategy.NONE);
            }
        }

        // 读取区域编号
        if (nbt.contains("areaTeam")) {
            capability.setAreaTeam(nbt.getInt("areaTeam"));
        }

        // 读取生成位置
        if (nbt.contains("spawnPos")) {
            capability.setSpawnPosition(BlockPos.of(nbt.getLong("spawnPos")));
        }

        // 读取策略标记位置
        if (nbt.contains("markerPos")) {
            capability.setStrategyMarkerPos(BlockPos.of(nbt.getLong("markerPos")));
        }

        // 读取最后已知目标位置
        if (nbt.contains("lastTargetPos")) {
            capability.setLastKnownTargetPos(BlockPos.of(nbt.getLong("lastTargetPos")));
        }

        // 读取中断巡逻位置
        if (nbt.contains("interruptedPos")) {
            capability.setInterruptedPatrolPosition(BlockPos.of(nbt.getLong("interruptedPos")));
        }

        // 读取路径点列表
        if (nbt.contains("waypoints")) {
            ListTag waypointsList = nbt.getList("waypoints", Tag.TAG_COMPOUND);
            List<BlockPos> waypoints = new ArrayList<>();
            for (int i = 0; i < waypointsList.size(); i++) {
                CompoundTag posTag = waypointsList.getCompound(i);
                waypoints.add(BlockPos.of(posTag.getLong("pos")));
            }
            capability.setWaypoints(waypoints);
        }

        // 读取当前路径点索引
        if (nbt.contains("waypointIndex")) {
            capability.setCurrentWaypointIndex(nbt.getInt("waypointIndex"));
        }

        // 读取追踪计时器
        if (nbt.contains("trackingTicks")) {
            capability.setTrackingTicks(nbt.getInt("trackingTicks"));
        }

        // 读取基础Goals标记
        if (nbt.contains("hasBasicGoals")) {
            capability.setHasBasicGoals(nbt.getBoolean("hasBasicGoals"));
        }
    }
}
