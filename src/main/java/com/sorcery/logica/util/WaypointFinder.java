package com.sorcery.logica.util;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.blocks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 路径点查找器
 *
 * 使用BFS算法查找与策略标记方块相接的所有路径点
 * 无视Y轴高度差异，适用于立体路线
 */
public class WaypointFinder {

    /**
     * 查找与策略方块相接的所有路径点
     *
     * @param level 世界
     * @param strategyPos 策略标记方块位置
     * @param strategy AI策略类型
     * @param teamId 区域编号（0-15）
     * @return 排序后的路径点列表
     */
    public static List<BlockPos> findWaypoints(Level level, BlockPos strategyPos, AIStrategy strategy, int teamId) {
        Logica.LOGGER.info("Finding waypoints for {} strategy with team ID {}", strategy, teamId);

        // 巡逻策略：使用半径搜索（16格内所有同编号路径点，不需要相连）
        if (strategy == AIStrategy.PATROL) {
            return findWaypointsInRadius(level, strategyPos, strategy, teamId);
        }

        // 哨兵策略：使用BFS搜索（相连的路径点）
        return findConnectedWaypoints(level, strategyPos, strategy, teamId);
    }

    /**
     * 半径搜索路径点（巡逻策略专用）
     * 搜索标记方块16格半径内所有同编号路径点，不需要相连
     */
    private static List<BlockPos> findWaypointsInRadius(Level level, BlockPos strategyPos, AIStrategy strategy, int teamId) {
        Block waypointBlock = getWaypointBlock(strategy);
        if (waypointBlock == null) {
            return List.of();
        }

        List<BlockPos> waypoints = new ArrayList<>();
        int searchRadius = 16; // 固定16格半径

        // 扫描以策略标记为中心的立方体区域
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos checkPos = strategyPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);
                    Block block = state.getBlock();

                    // 检查是否为匹配的路径点
                    if (isMatchingWaypoint(block, waypointBlock, teamId)) {
                        // 高度调整：将路径点调整到地面
                        BlockPos groundPos = findGroundBelow(level, checkPos);
                        waypoints.add(groundPos);

                        Logica.LOGGER.debug("Found patrol waypoint at {} (adjusted from {})",
                                groundPos, checkPos);
                    }
                }
            }
        }

        // 按照坐标排序（Y -> X -> Z）
        waypoints.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        Logica.LOGGER.info("Found {} patrol waypoints within {} blocks radius",
                waypoints.size(), searchRadius);

        return waypoints;
    }

    /**
     * BFS搜索相连的路径点（哨兵策略专用）
     */
    private static List<BlockPos> findConnectedWaypoints(Level level, BlockPos strategyPos, AIStrategy strategy, int teamId) {
        Block waypointBlock = getWaypointBlock(strategy);
        if (waypointBlock == null) {
            return List.of();
        }

        List<BlockPos> waypoints = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(strategyPos);
        visited.add(strategyPos);

        // BFS搜索相连的路径点
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // 检查当前位置是否为路径点
            BlockState currentState = level.getBlockState(current);
            Block currentBlock = currentState.getBlock();

            if (isMatchingWaypoint(currentBlock, waypointBlock, teamId)) {
                // 🔥 高度调整：将路径点调整到地面
                BlockPos groundPos = findGroundBelow(level, current);
                waypoints.add(groundPos);

                Logica.LOGGER.debug("Found waypoint at {} (adjusted from {})",
                    groundPos, current);
            }

            // 检查6个方向的相邻方块
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);

                if (!visited.contains(neighbor)) {
                    BlockState neighborState = level.getBlockState(neighbor);
                    Block neighborBlock = neighborState.getBlock();

                    // 如果是相同编号的路径点或策略方块，继续搜索
                    if (isMatchingWaypoint(neighborBlock, waypointBlock, teamId) ||
                        isMatchingStrategyMarker(neighborBlock, strategy, teamId)) {

                        queue.add(neighbor);
                        visited.add(neighbor);
                    }
                }
            }
        }

        // 按照坐标排序（Y -> X -> Z）
        waypoints.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        return waypoints;
    }

    /**
     * 获取策略对应的路径点方块
     */
    private static Block getWaypointBlock(AIStrategy strategy) {
        return switch (strategy) {
            case PATROL -> ModBlocks.PATROL_WAYPOINT.get();
            case SENTRIES -> ModBlocks.SENTRIES_WAYPOINT.get();
            default -> null;
        };
    }

    /**
     * 检查是否为匹配的路径点（编号相同）
     */
    private static boolean isMatchingWaypoint(Block block, Block expectedType, int teamId) {
        // 检查方块类型是否匹配
        if (block.getClass() != expectedType.getClass()) {
            return false;
        }

        // 检查区域编号是否匹配
        if (block instanceof SentriesWaypointBlock sentries) {
            return sentries.getTeamId() == teamId;
        } else if (block instanceof PatrolWaypointBlock patrol) {
            return patrol.getTeamId() == teamId;
        }

        return false;
    }

    /**
     * 检查是否为匹配的策略标记方块（编号相同）
     */
    private static boolean isMatchingStrategyMarker(Block block, AIStrategy strategy, int teamId) {
        return switch (strategy) {
            case SENTRIES -> {
                if (block instanceof SentriesMarkerBlock sentries) {
                    yield sentries.getTeamId() == teamId;
                }
                yield false;
            }
            case PATROL -> {
                if (block instanceof PatrolMarkerBlock patrol) {
                    yield patrol.getTeamId() == teamId;
                }
                yield false;
            }
            default -> false;
        };
    }

    /**
     * 🔥 高度调整：从指定位置向下查找地面
     *
     * @param level 世界
     * @param pos 起始位置
     * @return 地面位置（固体方块上方1格）
     */
    private static BlockPos findGroundBelow(Level level, BlockPos pos) {
        // 从当前位置向下搜索，找到第一个固体方块
        for (int y = pos.getY(); y >= level.getMinBuildHeight(); y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            BlockState state = level.getBlockState(checkPos);

            // 检查是否为完整的固体方块（可站立）
            if (!state.isAir() && state.isCollisionShapeFullBlock(level, checkPos)) {
                BlockPos groundPos = checkPos.above(); // 返回固体方块上方

                if (!groundPos.equals(pos)) {
                    Logica.LOGGER.debug("Adjusted waypoint from Y={} to Y={}",
                        pos.getY(), groundPos.getY());
                }

                return groundPos;
            }
        }

        // 找不到地面（可能在虚空），返回原位置
        Logica.LOGGER.warn("Could not find ground below waypoint at {}, using original position", pos);
        return pos;
    }
}
