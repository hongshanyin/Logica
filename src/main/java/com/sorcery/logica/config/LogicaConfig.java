package com.sorcery.logica.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Logica模组配置
 */
public class    LogicaConfig {

    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // ==================== 转向限制 ====================

    public static final ForgeConfigSpec.DoubleValue MAX_IDLE_TURN_ANGLE;
    public static final ForgeConfigSpec.DoubleValue GLOBAL_ROTATION_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_ROTATION_SPEEDS;

    // ==================== 警报效果 ====================

    public static final ForgeConfigSpec.BooleanValue ENABLE_ALERT_SOUND;
    public static final ForgeConfigSpec.DoubleValue ALERT_SOUND_VOLUME;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ALERT_PARTICLES;
    public static final ForgeConfigSpec.IntValue ALERT_PARTICLE_COUNT;

    // ==================== 调查行为 ====================

    public static final ForgeConfigSpec.DoubleValue INVESTIGATION_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue INVESTIGATION_DURATION_TICKS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EVENT_INVESTIGATION_DURATIONS;
    public static final ForgeConfigSpec.DoubleValue INVESTIGATION_ARRIVAL_DISTANCE;
    public static final ForgeConfigSpec.IntValue LOOK_AROUND_INTERVAL;

    // ==================== 策略配置 ====================

    public static final ForgeConfigSpec.DoubleValue GUARD_RADIUS;
    public static final ForgeConfigSpec.DoubleValue GUARD_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SENTRIES_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SENTRIES_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue SENTRIES_REST_CHANCE;
    public static final ForgeConfigSpec.DoubleValue SENTRIES_WAYPOINT_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue PATROL_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue PATROL_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue PATROL_WAYPOINT_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue STUCK_DETECTION_THRESHOLD;

    // ==================== 哨兵警报 ====================

    public static final ForgeConfigSpec.DoubleValue SENTRIES_ALERT_RADIUS;
    public static final ForgeConfigSpec.IntValue SENTRIES_BELL_COUNT;
    public static final ForgeConfigSpec.IntValue SENTRIES_BELL_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue SENTRIES_ALERT_ALL_TYPES;

    // ==================== 追踪模式 ====================

    public static final ForgeConfigSpec.IntValue MAX_TRACKING_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue TRACKING_COLLISION_RADIUS;
    public static final ForgeConfigSpec.DoubleValue TRACKING_SPEED_MULTIPLIER;

    // ==================== 玩家检测 ====================

    public static final ForgeConfigSpec.BooleanValue IGNORE_CREATIVE_PLAYERS;

    // ==================== 日志系统 ====================

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGS;
    public static final ForgeConfigSpec.BooleanValue LOG_GOAL_LIFECYCLE;
    public static final ForgeConfigSpec.BooleanValue LOG_STATE_TRANSITIONS;
    public static final ForgeConfigSpec.BooleanValue LOG_NAVIGATION;
    public static final ForgeConfigSpec.BooleanValue LOG_PERCEPTION_EVENTS;
    public static final ForgeConfigSpec.BooleanValue LOG_WAYPOINT_SEARCH;
    public static final ForgeConfigSpec.BooleanValue LOG_STRATEGY_APPLICATION;

    // ==================== 日志辅助方法 ====================

    /**
     * 检查是否应该记录Goal生命周期日志
     * @return 总开关启用 && Goal生命周期日志启用
     */
    public static boolean shouldLogGoalLifecycle() {
        return ENABLE_DEBUG_LOGS.get() && LOG_GOAL_LIFECYCLE.get();
    }

    /**
     * 检查是否应该记录状态转换日志
     * @return 总开关启用 && 状态转换日志启用
     */
    public static boolean shouldLogStateTransitions() {
        return ENABLE_DEBUG_LOGS.get() && LOG_STATE_TRANSITIONS.get();
    }

    /**
     * 检查是否应该记录导航日志
     * @return 总开关启用 && 导航日志启用
     */
    public static boolean shouldLogNavigation() {
        return ENABLE_DEBUG_LOGS.get() && LOG_NAVIGATION.get();
    }

    /**
     * 检查是否应该记录感知事件日志
     * @return 总开关启用 && 感知事件日志启用
     */
    public static boolean shouldLogPerceptionEvents() {
        return ENABLE_DEBUG_LOGS.get() && LOG_PERCEPTION_EVENTS.get();
    }

    /**
     * 检查是否应该记录路径点搜索日志
     * @return 总开关启用 && 路径点搜索日志启用
     */
    public static boolean shouldLogWaypointSearch() {
        return ENABLE_DEBUG_LOGS.get() && LOG_WAYPOINT_SEARCH.get();
    }

    /**
     * 检查是否应该记录策略应用日志
     * @return 总开关启用 && 策略应用日志启用
     */
    public static boolean shouldLogStrategyApplication() {
        return ENABLE_DEBUG_LOGS.get() && LOG_STRATEGY_APPLICATION.get();
    }

    // ==================== 配置解析辅助方法 ====================

    /**
     * 解析事件调查时长配置
     * @return GameEvent ID -> 调查时长（游戏刻）的映射
     */
    public static Map<String, Integer> parseEventInvestigationDurations() {
        Map<String, Integer> durations = new HashMap<>();

        for (String entry : EVENT_INVESTIGATION_DURATIONS.get()) {
            String[] parts = entry.split("=");
            if (parts.length == 2) {
                try {
                    String eventId = parts[0].trim();
                    int duration = Integer.parseInt(parts[1].trim());
                    durations.put(eventId, duration);
                } catch (NumberFormatException e) {
                    // 日志记录将在主类中处理
                    System.err.println("[Logica] Invalid event investigation duration format: " + entry);
                }
            }
        }

        return durations;
    }

    // 缓存解析结果
    private static Map<String, Integer> cachedEventDurations = null;

    /**
     * 获取缓存的事件调查时长映射
     */
    public static Map<String, Integer> getEventInvestigationDurations() {
        if (cachedEventDurations == null) {
            cachedEventDurations = parseEventInvestigationDurations();
        }
        return cachedEventDurations;
    }

    /**
     * 获取特定GameEvent的调查时长
     * @param eventId GameEvent的ResourceLocation字符串（如 "minecraft:explode"）
     * @return 调查时长（游戏刻），如果未配置则返回默认值
     */
    public static int getInvestigationDurationForEvent(String eventId) {
        return getEventInvestigationDurations()
                .getOrDefault(eventId, INVESTIGATION_DURATION_TICKS.get());
    }

    static {
        BUILDER.push("Rotation Control");
        MAX_IDLE_TURN_ANGLE = BUILDER
                .comment("Maximum turn angle in degrees when IDLE/PATROL (default: 90)")
                .defineInRange("maxIdleTurnAngle", 90.0, 0.0, 360.0);

        GLOBAL_ROTATION_SPEED_MULTIPLIER = BUILDER
                .comment("Global rotation speed multiplier when IDLE (default: 0.3)")
                .defineInRange("globalRotationSpeedMultiplier", 0.3, 0.0, 1.0);

        ENTITY_ROTATION_SPEEDS = BUILDER
                .comment("Per-entity rotation speeds (format: 'entity_id=multiplier')")
                .defineList("entityRotationSpeeds",
                        List.of("minecraft:zombie=0.2", "minecraft:skeleton=0.5"),
                        obj -> true);
        BUILDER.pop();

        BUILDER.push("Alert Effects");
        ENABLE_ALERT_SOUND = BUILDER
                .comment("Enable alert sound when perceiving vibration")
                .define("enableAlertSound", true);

        ALERT_SOUND_VOLUME = BUILDER
                .comment("Alert sound volume (0.0 - 1.0)")
                .defineInRange("alertSoundVolume", 1.0, 0.0, 1.0);

        ENABLE_ALERT_PARTICLES = BUILDER
                .comment("Enable particle effects on alert")
                .define("enableAlertParticles", true);

        ALERT_PARTICLE_COUNT = BUILDER
                .comment("Number of particles spawned on alert")
                .defineInRange("alertParticleCount", 20, 0, 100);
        BUILDER.pop();

        BUILDER.push("Investigation");
        INVESTIGATION_SPEED_MULTIPLIER = BUILDER
                .comment("Movement speed multiplier during investigation (default: 1.0)")
                .defineInRange("investigationSpeedMultiplier", 1.0, 0.5, 3.0);

        INVESTIGATION_DURATION_TICKS = BUILDER
                .comment("Default investigation duration in ticks (200 = 10 seconds)")
                .defineInRange("investigationDurationTicks", 200, 0, 1200);

        EVENT_INVESTIGATION_DURATIONS = BUILDER
                .comment(
                        "Custom investigation durations for specific GameEvents (in ticks).",
                        "Format: 'event_id = duration'",
                        "If not set, uses investigationDurationTicks as default.",
                        "Examples:",
                        "  'minecraft:explode = 600'          (Explosion: 30 seconds)",
                        "  'minecraft:projectile_land = 200'  (Projectile landing: 10 seconds)",
                        "  'minecraft:step = 100'             (Footstep: 5 seconds)"
                )
                .defineList("eventInvestigationDurations",
                        List.of(
                                "minecraft:explode = 600",
                                "minecraft:projectile_land = 200",
                                "minecraft:projectile_shoot = 200",
                                "minecraft:hit_ground = 150",
                                "minecraft:step = 100",
                                "minecraft:swim = 80",
                                "minecraft:block_place = 150",
                                "minecraft:block_destroy = 150",
                                "minecraft:item_interact_finish = 100"
                        ),
                        obj -> true);

        INVESTIGATION_ARRIVAL_DISTANCE = BUILDER
                .comment("Arrival distance to investigation point (blocks)")
                .defineInRange("arrivalDistance", 1.5, 0.5, 10.0);

        LOOK_AROUND_INTERVAL = BUILDER
                .comment("Look around interval in ticks (40 = 2 seconds)")
                .defineInRange("lookAroundInterval", 40, 10, 200);
        BUILDER.pop();

        BUILDER.push("Strategies");
        GUARD_RADIUS = BUILDER
                .comment("Guard strategy wandering radius (blocks)")
                .defineInRange("guardRadius", 6.0, 1.0, 32.0);

        GUARD_SPEED_MULTIPLIER = BUILDER
                .comment("Guard movement speed multiplier (default: 1.0 = normal speed)")
                .defineInRange("guardSpeedMultiplier", 1.0, 0.5, 2.0);

        SENTRIES_RADIUS = BUILDER
                .comment("Sentries strategy patrol radius (blocks)")
                .defineInRange("sentriesRadius", 32.0, 8.0, 64.0);

        SENTRIES_SPEED_MULTIPLIER = BUILDER
                .comment("Sentries movement speed multiplier (default: 1.0 = normal speed)")
                .defineInRange("sentriesSpeedMultiplier", 1.0, 0.5, 2.0);

        SENTRIES_REST_CHANCE = BUILDER
                .comment("Sentries rest chance per tick (0.0 - 1.0, default: 0.1)")
                .defineInRange("sentriesRestChance", 0.1, 0.0, 1.0);

        SENTRIES_WAYPOINT_SEARCH_RADIUS = BUILDER
                .comment(
                        "Sentries waypoint chain search radius (blocks, default: 1)",
                        "Sentries waypoints must be directly adjacent (1 block apart)",
                        "This ensures tight patrol routes"
                )
                .defineInRange("sentriesWaypointSearchRadius", 1.0, 1.0, 5.0);

        PATROL_SPEED_MULTIPLIER = BUILDER
                .comment("Patrol movement speed multiplier (default: 1.2 = 20% faster)")
                .defineInRange("patrolSpeedMultiplier", 1.2, 0.5, 2.0);

        PATROL_SEARCH_RADIUS = BUILDER
                .comment("Patrol search radius around waypoint path (blocks, default: 8.0)")
                .defineInRange("patrolSearchRadius", 8.0, 2.0, 16.0);

        PATROL_WAYPOINT_SEARCH_RADIUS = BUILDER
                .comment(
                        "Patrol waypoint chain search radius (blocks, default: 16.0)",
                        "Each waypoint can connect to next waypoint within this radius",
                        "Allows sparse waypoint placement for long patrol routes"
                )
                .defineInRange("patrolWaypointSearchRadius", 16.0, 8.0, 64.0);

        STUCK_DETECTION_THRESHOLD = BUILDER
                .comment("Stuck detection threshold in ticks (60 = 3 seconds)")
                .defineInRange("stuckDetectionThreshold", 60, 20, 200);
        BUILDER.pop();

        BUILDER.push("Sentries Alert");
        SENTRIES_ALERT_RADIUS = BUILDER
                .comment("Alert radius for sentries (blocks)")
                .defineInRange("alertRadius", 32.0, 8.0, 64.0);

        SENTRIES_BELL_COUNT = BUILDER
                .comment("Number of bell sounds on alert")
                .defineInRange("bellCount", 3, 1, 10);

        SENTRIES_BELL_INTERVAL = BUILDER
                .comment("Bell sound interval in ticks (10 = 0.5 seconds)")
                .defineInRange("bellInterval", 10, 5, 40);

        SENTRIES_ALERT_ALL_TYPES = BUILDER
                .comment("Alert all mob types (false = same type only)")
                .define("alertAllTypes", true);
        BUILDER.pop();

        BUILDER.push("Tracking");
        MAX_TRACKING_DURATION_TICKS = BUILDER
                .comment("Max tracking duration in ticks (600 = 30 seconds)")
                .defineInRange("maxTrackingDurationTicks", 600, 100, 2400);

        TRACKING_COLLISION_RADIUS = BUILDER
                .comment("Collision detection radius during tracking (blocks)")
                .defineInRange("collisionDetectionRadius", 1.5, 0.5, 5.0);

        TRACKING_SPEED_MULTIPLIER = BUILDER
                .comment("Movement speed multiplier during tracking (default: 1.0)")
                .defineInRange("trackingSpeedMultiplier", 1.0, 0.5, 3.0);
        BUILDER.pop();

        BUILDER.push("Player Detection");
        IGNORE_CREATIVE_PLAYERS = BUILDER
                .comment(
                        "Ignore players in Creative mode (default: true)",
                        "When enabled, mobs will not react to creative mode players:",
                        "  - No visual detection (TargetSpottedEvent ignored)",
                        "  - No sound detection (VibrationPerceivedEvent ignored)",
                        "  - No collision detection during tracking",
                        "Recommended: true (vanilla-like behavior)"
                )
                .define("ignoreCreativePlayers", true);
        BUILDER.pop();

        BUILDER.push("Logging");
        ENABLE_DEBUG_LOGS = BUILDER
                .comment(
                        "Master switch for all debug logging (disable for better performance)",
                        "When disabled, ALL debug logs are skipped without any overhead",
                        "Recommended: false for production, true for development/testing"
                )
                .define("enableDebugLogs", false);

        LOG_GOAL_LIFECYCLE = BUILDER
                .comment("Log Goal lifecycle events (start/stop/canUse)")
                .define("logGoalLifecycle", true);

        LOG_STATE_TRANSITIONS = BUILDER
                .comment("Log AI state transitions (IDLE -> ALERT -> COMBAT, etc.)")
                .define("logStateTransitions", true);

        LOG_NAVIGATION = BUILDER
                .comment("Log navigation and pathfinding details")
                .define("logNavigation", false);

        LOG_PERCEPTION_EVENTS = BUILDER
                .comment("Log perception events (vision spotted, vibration heard)")
                .define("logPerceptionEvents", true);

        LOG_WAYPOINT_SEARCH = BUILDER
                .comment("Log waypoint search and BFS algorithm details")
                .define("logWaypointSearch", true);

        LOG_STRATEGY_APPLICATION = BUILDER
                .comment("Log strategy application and Goal registration")
                .define("logStrategyApplication", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
