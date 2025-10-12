package com.sorcery.logica.blocks;

/**
 * 巡逻路径点方块
 *
 * 功能：
 * - 与Patrol标记方块相接
 * - 定义巡逻怪物的移动路线
 * - 使用BFS算法自动搜索相连的路径点
 * - 支持区域编号（0-15）区分不同巡逻区域
 * - 使用结构空位贴图（蓝色路径变种）
 */
public class PatrolWaypointBlock extends BaseMarkerBlock {
    private final int teamId;

    public PatrolWaypointBlock(int teamId) {
        super();
        this.teamId = teamId;
    }

    public PatrolWaypointBlock() {
        this(0); // 默认编号0（向后兼容）
    }

    /**
     * 获取区域编号
     */
    public int getTeamId() {
        return teamId;
    }
}
