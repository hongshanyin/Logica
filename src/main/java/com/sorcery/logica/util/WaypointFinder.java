package com.sorcery.logica.util;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.blocks.*;
import com.sorcery.logica.config.LogicaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 路径点查找器 - BFS链式连接算法
 *
 * <h2>工作原理</h2>
 * <pre>
 * 策略标记方块 → [1格] → 第一个路径点 → [N格] → 第二个路径点 → ...
 *               BFS搜索      链式连接         链式连接
 * </pre>
 *
 * <h2>搜索半径（可配置）</h2>
 * <ul>
 *   <li><strong>PATROL策略</strong>: 16格（支持稀疏路径点，适合长距离巡逻）</li>
 *   <li><strong>SENTRIES策略</strong>: 1格（要求紧密相邻，确保完整覆盖）</li>
 * </ul>
 *
 * <h2>特性</h2>
 * <ul>
 *   <li>无视Y轴高度差异，支持立体路线</li>
 *   <li>自动调整路径点到地面（findGroundBelow）</li>
 *   <li>按坐标排序（Y → X → Z）</li>
 *   <li>避免回路（只从标记方块开始，不重复搜索标记）</li>
 * </ul>
 *
 * @see com.sorcery.logica.config.LogicaConfig#PATROL_WAYPOINT_SEARCH_RADIUS
 * @see com.sorcery.logica.config.LogicaConfig#SENTRIES_WAYPOINT_SEARCH_RADIUS
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
        if (LogicaConfig.shouldLogWaypointSearch()) {
            Logica.LOGGER.info("Finding waypoints for {} strategy with team ID {}", strategy, teamId);
        }

        // 所有策略都使用BFS搜索相连的路径点
        return findConnectedWaypoints(level, strategyPos, strategy, teamId);
    }

    /**
     * BFS搜索相连的路径点（链式连接算法）
     *
     * <pre>
     * 搜索示例（PATROL策略，半径=16）：
     *
     *     [标记方块] ─┬─ (1格) ─→ [路径点1] ─┬─ (16格内) ─→ [路径点2]
     *                 │                       │
     *                 └─ (1格) ─→ [路径点1'] └─ (16格内) ─→ [路径点3]
     *
     * 搜索示例（SENTRIES策略，半径=1）：
     *
     *     [标记方块] ── (1格) ─→ [路径点1] ── (1格) ─→ [路径点2] ── (1格) ─→ ...
     *
     * </pre>
     *
     * <p><strong>搜索策略</strong>：
     * <ul>
     *   <li>策略标记方块：只检查直接相邻（1格）</li>
     *   <li>路径点方块：检查周围半径内的下一个路径点</li>
     *   <li>避免回路：不再搜索标记方块，只搜索路径点</li>
     *   <li>高度调整：所有路径点自动调整到地面</li>
     * </ul>
     *
     * @param level 世界
     * @param strategyPos 策略标记方块位置
     * @param strategy AI策略类型
     * @param teamId 区域编号（0-15）
     * @return 排序后的路径点列表（按Y→X→Z排序）
     */
    private static List<BlockPos> findConnectedWaypoints(Level level, BlockPos strategyPos, AIStrategy strategy, int teamId) {
        Block waypointBlock = getWaypointBlock(strategy);
        if (waypointBlock == null) {
            return List.of();
        }

        List<BlockPos> waypoints = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        // 根据策略获取路径点搜索半径
        int waypointSearchRadius;
        if (strategy == AIStrategy.PATROL) {
            waypointSearchRadius = LogicaConfig.PATROL_WAYPOINT_SEARCH_RADIUS.get().intValue();
        } else if (strategy == AIStrategy.SENTRIES) {
            waypointSearchRadius = LogicaConfig.SENTRIES_WAYPOINT_SEARCH_RADIUS.get().intValue();
        } else {
            waypointSearchRadius = 1; // 默认只检查直接相邻
        }

        queue.add(strategyPos);
        visited.add(strategyPos);

        if (LogicaConfig.shouldLogWaypointSearch()) {
            Logica.LOGGER.info("Starting BFS waypoint search for {} from {} with search radius {}",
                    strategy, strategyPos, waypointSearchRadius);
        }

        // BFS搜索相连的路径点
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();

            // 检查当前位置是否为路径点
            BlockState currentState = level.getBlockState(current);
            Block currentBlock = currentState.getBlock();

            boolean isWaypoint = isMatchingWaypoint(currentBlock, waypointBlock, teamId);
            if (isWaypoint) {
                // 高度调整：将路径点调整到地面
                BlockPos groundPos = findGroundBelow(level, current);
                waypoints.add(groundPos);

                if (LogicaConfig.shouldLogWaypointSearch()) {
                    Logica.LOGGER.debug("Found waypoint at {} (adjusted from {})",
                            groundPos, current);
                }
            }

            // 搜索策略：
            // - 如果是策略标记方块：只检查直接相邻（1格）的路径点
            // - 如果是路径点方块：检查周围半径内的下一个路径点
            boolean isMarker = isMatchingStrategyMarker(currentBlock, strategy, teamId);
            int searchRadius = isMarker ? 1 : waypointSearchRadius;

            // 在指定半径内搜索相连的方块
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                    for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                        // 跳过中心点
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        BlockPos neighbor = current.offset(dx, dy, dz);

                        if (!visited.contains(neighbor)) {
                            BlockState neighborState = level.getBlockState(neighbor);
                            Block neighborBlock = neighborState.getBlock();

                            // 只搜索路径点（不再搜索标记方块，避免回路）
                            if (isMatchingWaypoint(neighborBlock, waypointBlock, teamId)) {
                                queue.add(neighbor);
                                visited.add(neighbor);

                                if (LogicaConfig.shouldLogWaypointSearch()) {
                                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                    Logica.LOGGER.debug("Connected waypoint {} to {} (distance: {:.1f})",
                                            current, neighbor, distance);
                                }
                            }
                        }
                    }
                }
            }
        }

        // 按照坐标排序（Y -> X -> Z）
        waypoints.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));

        if (LogicaConfig.shouldLogWaypointSearch()) {
            Logica.LOGGER.info("BFS completed: found {} waypoints", waypoints.size());
        }

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
     * 高度调整：从指定位置向下查找地面
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
