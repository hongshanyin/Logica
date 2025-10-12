package com.sorcery.logica.blocks;

/**
 * 哨兵标记方块
 *
 * 功能：
 * - 生成在此区域的怪物将应用哨兵策略
 * - 快速巡逻，优先未访问位置
 * - 发现玩家时发出钟声警报
 * - 支持区域编号（0-15）区分不同巡逻区域
 * - 使用结构空位贴图（黄色变种）
 */
public class SentriesMarkerBlock extends BaseMarkerBlock {
    private final int teamId;

    public SentriesMarkerBlock(int teamId) {
        super();
        this.teamId = teamId;
    }

    public SentriesMarkerBlock() {
        this(0); // 默认编号0（向后兼容）
    }

    /**
     * 获取区域编号
     */
    public int getTeamId() {
        return teamId;
    }
}
