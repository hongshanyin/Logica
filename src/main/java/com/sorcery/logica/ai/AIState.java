package com.sorcery.logica.ai;

/**
 * AI状态枚举
 *
 * 状态转换：
 * IDLE → ALERT → COMBAT ↔ TRACKING → SEARCHING → IDLE
 */
public enum AIState {
    /**
     * 空闲 - 正常游荡，转向受限
     */
    IDLE,

    /**
     * 警戒 - 听到声音，快速转向调查
     */
    ALERT,

    /**
     * 战斗 - 发现目标，主动攻击
     */
    COMBAT,

    /**
     * 追踪 - 战斗中丢失视觉，追踪声音+碰撞检测
     */
    TRACKING,

    /**
     * 搜索 - 前往最后已知位置，环顾四周
     */
    SEARCHING
}
