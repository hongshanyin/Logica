package com.sorcery.logica.util;

import com.sorcery.logica.Logica;
import com.sorcery.logica.config.LogicaConfig;
import org.slf4j.Logger;

/**
 * 集中的日志管理工具
 *
 * 功能：
 * - 提供配置化的日志输出控制
 * - 避免关闭时的性能开销
 * - 统一日志格式和管理
 *
 * 性能优化：
 * - 所有日志方法在配置关闭时会直接返回，零开销
 * - 字符串格式化只在日志启用时执行
 */
public class LogHelper {

    private static final Logger LOGGER = Logica.LOGGER;

    // ==================== Goal生命周期日志 ====================

    /**
     * 记录Goal启动事件
     */
    public static void logGoalStart(String goalName, String mobName, Object... extraInfo) {
        if (!shouldLog(LogCategory.GOAL_LIFECYCLE)) return;
        LOGGER.info("🔥 {}.start() CALLED for {} {}",
                goalName, mobName, formatExtra(extraInfo));
    }

    /**
     * 记录Goal停止事件
     */
    public static void logGoalStop(String goalName, String mobName, Object... extraInfo) {
        if (!shouldLog(LogCategory.GOAL_LIFECYCLE)) return;
        LOGGER.info("🔥 {}.stop() CALLED for {} {}",
                goalName, mobName, formatExtra(extraInfo));
    }

    /**
     * 记录Goal条件检查
     */
    public static void logGoalCanUse(String goalName, String mobName, boolean result, String reason) {
        if (!shouldLog(LogCategory.GOAL_LIFECYCLE)) return;
        LOGGER.debug("{}.canUse() for {} = {} ({})",
                goalName, mobName, result, reason);
    }

    // ==================== 状态转换日志 ====================

    /**
     * 记录AI状态转换
     */
    public static void logStateTransition(String mobName, String oldState, String newState, String strategy) {
        if (!shouldLog(LogCategory.STATE_TRANSITIONS)) return;
        LOGGER.debug("AICapability state changed: {} -> {} (strategy: {}) for {}",
                oldState, newState, strategy, mobName);
    }

    /**
     * 记录状态转换失败
     */
    public static void logStateTransitionFailed(String mobName, String attemptedState, String reason) {
        if (!shouldLog(LogCategory.STATE_TRANSITIONS)) return;
        LOGGER.warn("State transition to {} failed for {}: {}",
                attemptedState, mobName, reason);
    }

    // ==================== 导航日志 ====================

    /**
     * 记录导航成功
     */
    public static void logNavigationSuccess(String mobName, String target, double speed) {
        if (!shouldLog(LogCategory.NAVIGATION)) return;
        LOGGER.info("🔥 {} moveTo {} returned: true (speed: {})",
                mobName, target, speed);
    }

    /**
     * 记录导航失败
     */
    public static void logNavigationFailed(String mobName, String target, String reason) {
        if (!shouldLog(LogCategory.NAVIGATION)) return;
        LOGGER.warn("🔥 {} navigation to {} failed: {}",
                mobName, target, reason);
    }

    /**
     * 记录导航重试
     */
    public static void logNavigationRetry(String mobName, int failedTicks, int maxTicks) {
        if (!shouldLog(LogCategory.NAVIGATION)) return;
        LOGGER.warn("🔥 {} re-navigation failed! failedTicks: {}/{}",
                mobName, failedTicks, maxTicks);
    }

    // ==================== 感知事件日志 ====================

    /**
     * 记录视觉发现事件
     */
    public static void logTargetSpotted(String mobName, String targetName, double distance) {
        if (!shouldLog(LogCategory.PERCEPTION_EVENTS)) return;
        LOGGER.debug("Mob {} spotted target {} at distance {}, switching to COMBAT state",
                mobName, targetName, distance);
    }

    /**
     * 记录听觉感知事件
     */
    public static void logVibrationHeard(String mobName, String position, String investigationTarget) {
        if (!shouldLog(LogCategory.PERCEPTION_EVENTS)) return;
        LOGGER.info("Mob {} heard vibration at {}, switching to ALERT state, investigation target set to {}",
                mobName, position, investigationTarget);
    }

    /**
     * 记录警报广播
     */
    public static void logAlertBroadcast(String mobName, int alerted) {
        if (!shouldLog(LogCategory.PERCEPTION_EVENTS)) return;
        LOGGER.debug("Sentries {} alerted {} nearby mobs",
                mobName, alerted);
    }

    // ==================== 路径点搜索日志 ====================

    /**
     * 记录路径点搜索开始
     */
    public static void logWaypointSearchStart(String strategy, int teamId) {
        if (!shouldLog(LogCategory.WAYPOINT_SEARCH)) return;
        LOGGER.info("Finding waypoints for {} strategy with team ID {}",
                strategy, teamId);
    }

    /**
     * 记录路径点搜索结果
     */
    public static void logWaypointSearchResult(String strategy, int count, int radius) {
        if (!shouldLog(LogCategory.WAYPOINT_SEARCH)) return;
        if (radius > 0) {
            LOGGER.info("Found {} patrol waypoints within {} blocks radius", count, radius);
        } else {
            LOGGER.info("Found {} connected waypoints for {}", count, strategy);
        }
    }

    /**
     * 记录单个路径点发现
     */
    public static void logWaypointFound(String position, String adjustedFrom) {
        if (!shouldLog(LogCategory.WAYPOINT_SEARCH)) return;
        if (adjustedFrom != null) {
            LOGGER.debug("Found waypoint at {} (adjusted from {})", position, adjustedFrom);
        } else {
            LOGGER.debug("Found waypoint at {}", position);
        }
    }

    // ==================== 策略应用日志 ====================

    /**
     * 记录策略应用
     */
    public static void logStrategyApplied(String strategy, int teamId, String mobName, String position) {
        if (!shouldLog(LogCategory.STRATEGY_APPLICATION)) return;
        LOGGER.info("Applied {} strategy (team {}) to {} at {}",
                strategy, teamId, mobName, position);
    }

    /**
     * 记录Goal注册
     */
    public static void logGoalRegistered(String goalName, String mobName) {
        if (!shouldLog(LogCategory.STRATEGY_APPLICATION)) return;
        LOGGER.debug("Registered {} for {}", goalName, mobName);
    }

    /**
     * 记录Goal移除
     */
    public static void logGoalRemoved(String goalName, int priority, String mobName) {
        if (!shouldLog(LogCategory.STRATEGY_APPLICATION)) return;
        LOGGER.info("Removed {} (Priority {}) from {}", goalName, priority, mobName);
    }

    /**
     * 记录策略标记检测
     */
    public static void logMarkerDetection(String mobName, String position) {
        if (!shouldLog(LogCategory.STRATEGY_APPLICATION)) return;
        LOGGER.info("Detecting Mob spawn: {} at {}", mobName, position);
    }

    /**
     * 记录标记扫描过程
     */
    public static void logMarkerScanning(String position) {
        if (!shouldLog(LogCategory.STRATEGY_APPLICATION)) return;
        LOGGER.debug("Scanning 3x3x3 area around {} for strategy markers...", position);
    }

    // ==================== 通用工具方法 ====================

    /**
     * 记录警告信息（总是输出）
     */
    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    /**
     * 记录错误信息（总是输出）
     */
    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    /**
     * 记录调试信息（受总开关控制）
     */
    public static void debug(String message, Object... args) {
        if (!LogicaConfig.ENABLE_DEBUG_LOGS.get()) return;
        LOGGER.debug(message, args);
    }

    /**
     * 记录一般信息（总是输出）
     */
    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 判断是否应该输出日志
     */
    private static boolean shouldLog(LogCategory category) {
        // 主开关关闭，直接返回false
        if (!LogicaConfig.ENABLE_DEBUG_LOGS.get()) {
            return false;
        }

        // 检查分类开关
        return switch (category) {
            case GOAL_LIFECYCLE -> LogicaConfig.LOG_GOAL_LIFECYCLE.get();
            case STATE_TRANSITIONS -> LogicaConfig.LOG_STATE_TRANSITIONS.get();
            case NAVIGATION -> LogicaConfig.LOG_NAVIGATION.get();
            case PERCEPTION_EVENTS -> LogicaConfig.LOG_PERCEPTION_EVENTS.get();
            case WAYPOINT_SEARCH -> LogicaConfig.LOG_WAYPOINT_SEARCH.get();
            case STRATEGY_APPLICATION -> LogicaConfig.LOG_STRATEGY_APPLICATION.get();
        };
    }

    /**
     * 格式化额外信息
     */
    private static String formatExtra(Object... extraInfo) {
        if (extraInfo == null || extraInfo.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object info : extraInfo) {
            sb.append(info).append(" ");
        }
        return sb.toString().trim();
    }

    // ==================== 日志分类枚举 ====================

    private enum LogCategory {
        GOAL_LIFECYCLE,
        STATE_TRANSITIONS,
        NAVIGATION,
        PERCEPTION_EVENTS,
        WAYPOINT_SEARCH,
        STRATEGY_APPLICATION
    }
}
