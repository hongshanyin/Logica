package com.sorcery.logica.blocks;

/**
 * 哨兵路径点方块
 *
 * 功能：
 * - 与Sentries标记方块相接
 * - 定义哨兵怪物的巡逻路线
 * - 使用BFS算法自动搜索相连的路径点
 * - 支持区域编号（0-15）区分不同巡逻区域
 * - 使用结构空位贴图（黄色路径变种）
 */
public class SentriesWaypointBlock extends BaseMarkerBlock {
    private final int teamId;

    public SentriesWaypointBlock(int teamId) {
        super();
        this.teamId = teamId;
    }

    public SentriesWaypointBlock() {
        this(0); // 默认编号0（向后兼容）
    }

    /**
     * 获取区域编号
     */
    public int getTeamId() {
        return teamId;
    }
}
