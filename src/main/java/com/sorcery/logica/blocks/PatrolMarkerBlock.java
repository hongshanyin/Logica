package com.sorcery.logica.blocks;

/**
 * 巡逻标记方块
 *
 * 功能：
 * - 生成在此区域的怪物将应用巡逻策略
 * - 沿相连的巡逻路径点反复巡逻
 * - 支持区域编号（0-15）区分不同巡逻区域
 * - 使用结构空位贴图（蓝色变种）
 */
public class PatrolMarkerBlock extends BaseMarkerBlock {
    private final int teamId;

    public PatrolMarkerBlock(int teamId) {
        super();
        this.teamId = teamId;
    }

    public PatrolMarkerBlock() {
        this(0); // 默认编号0（向后兼容）
    }

    /**
     * 获取区域编号
     */
    public int getTeamId() {
        return teamId;
    }
}
