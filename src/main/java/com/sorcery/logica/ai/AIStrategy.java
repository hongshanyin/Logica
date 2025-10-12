package com.sorcery.logica.ai;

/**
 * AI策略枚举
 *
 * 对应标记方块类型
 */
public enum AIStrategy {
    /**
     * 无策略 - 默认状态
     */
    NONE,

    /**
     * 守卫 - 返回原位，游荡半径6格
     */
    GUARD,

    /**
     * 哨兵 - 快速巡逻，发现玩家发出警报
     */
    SENTRIES,

    /**
     * 巡逻 - 沿路径点反复巡逻
     */
    PATROL;

    /**
     * 从字符串解析策略
     */
    public static AIStrategy fromString(String str) {
        return switch (str.toLowerCase()) {
            case "guard" -> GUARD;
            case "sentries" -> SENTRIES;
            case "patrol" -> PATROL;
            default -> NONE;
        };
    }
}
