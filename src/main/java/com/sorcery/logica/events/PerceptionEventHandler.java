package com.sorcery.logica.events;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import com.sorcery.logica.config.LogicaConfig;
import io.github.Sorcery_Dynasties.aperioculos.api.event.TargetSpottedEvent;
import io.github.Sorcery_Dynasties.aperioculos.api.event.VibrationPerceivedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 感知事件处理器
 *
 * 功能：
 * - 监听Aperi Oculos的VibrationPerceivedEvent（听觉）
 * - 监听Aperi Oculos的TargetSpottedEvent（视觉）
 * - 播放警报音效和粒子效果
 * - 切换AI状态
 * - Sentries策略特殊处理（钟声警报）
 */
@Mod.EventBusSubscriber(modid = Logica.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PerceptionEventHandler {

    // 哨兵警报系统：存储待播放的钟声（UUID -> 剩余次数）
    private static final Map<UUID, Integer> sentriesBellSchedule = new HashMap<>();
    private static final Map<UUID, Integer> sentriesBellCooldown = new HashMap<>();

    /**
     * 监听Aperi Oculos的听觉事件
     */
    @SubscribeEvent
    public static void onVibrationPerceived(VibrationPerceivedEvent event) {
        LivingEntity listenerEntity = event.getListener();
        Vec3 sourcePos = event.getSourcePos();

        // 只处理Mob
        if (!(listenerEntity instanceof Mob listener)) {
            return;
        }

        // 服务端处理
        if (listener.level().isClientSide()) {
            return;
        }

        // 获取AI Capability
        IAICapability aiCap = listener.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return; // 没有AI Capability，忽略
        }

        AIState currentState = aiCap.getState();

        // 只在特定状态下响应听觉（IDLE、ALERT、SEARCHING）
        // COMBAT和TRACKING状态下的听觉响应由对应的Goal处理
        if (currentState == AIState.COMBAT || currentState == AIState.TRACKING) {
            return;
        }

        // 播放警报音效
        if (LogicaConfig.ENABLE_ALERT_SOUND.get()) {
            listener.level().playSound(null, listener.blockPosition(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,  // 进入游戏提示音
                    SoundSource.HOSTILE,
                    LogicaConfig.ALERT_SOUND_VOLUME.get().floatValue(),
                    0.6F);
        }

        // 生成粒子效果
        if (LogicaConfig.ENABLE_ALERT_PARTICLES.get() && listener.level() instanceof ServerLevel serverLevel) {
            int particleCount = LogicaConfig.ALERT_PARTICLE_COUNT.get();
            serverLevel.sendParticles(
                    ParticleTypes.POOF,
                    listener.getX(), listener.getY() + listener.getBbHeight(), listener.getZ(),
                    particleCount, 0.3, 0.5, 0.3, 0.05
            );
        }

        // 快速转向声音来源（30度/tick，而IDLE状态只有10度/tick）
        listener.getLookControl().setLookAt(
                sourcePos.x, sourcePos.y, sourcePos.z,
                30.0F,  // 快速转向
                listener.getMaxHeadXRot()
        );

        // 记录调查位置（自动寻找地面）
        BlockPos rawPos = new BlockPos((int)sourcePos.x, (int)sourcePos.y, (int)sourcePos.z);
        BlockPos investigationPos = findGroundBelow(listener.level(), rawPos);
        aiCap.setLastKnownTargetPos(investigationPos);

        // 切换到ALERT状态
        aiCap.setState(AIState.ALERT);

        Logica.LOGGER.info("Mob {} heard vibration at {}, switching to ALERT state, investigation target set to {} (ground adjusted from {})",
                listener.getName().getString(), sourcePos, investigationPos, rawPos);
    }

    /**
     * 监听Aperi Oculos的视觉发现事件
     */
    @SubscribeEvent
    public static void onTargetSpotted(TargetSpottedEvent event) {
        LivingEntity observerEntity = event.getObserver();
        net.minecraft.world.entity.LivingEntity target = event.getTarget();

        // 只处理Mob发现Player的情况
        if (!(observerEntity instanceof Mob observer) || !(target instanceof Player player)) {
            return;
        }

        // 服务端处理
        if (observer.level().isClientSide()) {
            return;
        }

        // 获取AI Capability
        IAICapability aiCap = observer.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return; // 没有AI Capability，忽略
        }

        // 设置目标
        observer.setTarget(player);

        // 记录最后已知位置
        aiCap.setLastKnownTargetPos(player.blockPosition());

        // Sentries策略：播放钟声并广播警报
        if (aiCap.getStrategy() == AIStrategy.SENTRIES) {
            scheduleSentriesBellAlert(observer);
            broadcastAlert(observer, player);
        }

        // 切换到COMBAT状态
        aiCap.setState(AIState.COMBAT);

        Logica.LOGGER.debug("Mob {} spotted target {}, switching to COMBAT state",
                observer.getName().getString(), player.getName().getString());
    }

    /**
     * 向下搜索地面位置
     *
     * @param level 世界
     * @param startPos 起始位置
     * @return 地面位置（最多向下搜索10格）
     */
    private static BlockPos findGroundBelow(net.minecraft.world.level.Level level, BlockPos startPos) {
        // 向下搜索最多10格
        for (int i = 0; i < 10; i++) {
            BlockPos checkPos = startPos.below(i);
            BlockPos belowPos = checkPos.below();

            // 检查当前位置是否为空气，且下方是固体方块
            if (level.getBlockState(checkPos).isAir() &&
                !level.getBlockState(belowPos).isAir() &&
                level.getBlockState(belowPos).isSolid()) {
                return checkPos;
            }
        }

        // 如果找不到地面，返回原位置
        return startPos;
    }

    /**
     * 安排哨兵钟声警报
     */
    private static void scheduleSentriesBellAlert(Mob mob) {
        int bellCount = LogicaConfig.SENTRIES_BELL_COUNT.get();
        sentriesBellSchedule.put(mob.getUUID(), bellCount);
        sentriesBellCooldown.put(mob.getUUID(), 0); // 立即播放第一声
    }

    /**
     * 广播警报到附近的怪物
     */
    private static void broadcastAlert(Mob alerter, Player target) {
        double radius = LogicaConfig.SENTRIES_ALERT_RADIUS.get();
        boolean alertAllTypes = LogicaConfig.SENTRIES_ALERT_ALL_TYPES.get();

        // 查找范围内的怪物
        AABB searchBox = new AABB(alerter.blockPosition()).inflate(radius);
        List<Mob> nearbyMobs = alerter.level().getEntitiesOfClass(Mob.class, searchBox, mob -> {
            if (mob == alerter) return false; // 排除自己
            if (!alertAllTypes && mob.getType() != alerter.getType()) return false; // 只警报同类
            return mob.distanceToSqr(alerter) <= radius * radius;
        });

        // 传递目标信息
        for (Mob mob : nearbyMobs) {
            mob.setTarget(target);

            // 切换到COMBAT状态
            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                cap.setState(AIState.COMBAT);
                cap.setLastKnownTargetPos(target.blockPosition());
            });
        }

        Logica.LOGGER.debug("Sentries {} alerted {} nearby mobs",
                alerter.getName().getString(), nearbyMobs.size());
    }

    /**
     * 每tick处理钟声播放
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // 处理钟声计时器
        sentriesBellCooldown.entrySet().removeIf(entry -> {
            UUID mobUUID = entry.getKey();
            int cooldown = entry.getValue();

            if (cooldown > 0) {
                // 减少冷却
                sentriesBellCooldown.put(mobUUID, cooldown - 1);
                return false;
            }

            // 冷却结束，播放钟声
            Integer remainingBells = sentriesBellSchedule.get(mobUUID);
            if (remainingBells == null || remainingBells <= 0) {
                // 播放完毕，清理
                sentriesBellSchedule.remove(mobUUID);
                return true;
            }

            // 查找实体
            for (ServerLevel level : event.getServer().getAllLevels()) {
                net.minecraft.world.entity.Entity entity = level.getEntity(mobUUID);
                if (entity instanceof Mob mob) {
                    // 播放钟声
                    level.playSound(null, mob.blockPosition(),
                            SoundEvents.BELL_BLOCK,
                            SoundSource.HOSTILE,
                            1.0F, 1.0F);

                    // 减少剩余次数
                    sentriesBellSchedule.put(mobUUID, remainingBells - 1);

                    // 重置冷却
                    sentriesBellCooldown.put(mobUUID, LogicaConfig.SENTRIES_BELL_INTERVAL.get());

                    return false;
                }
            }

            // 实体不存在，清理
            sentriesBellSchedule.remove(mobUUID);
            return true;
        });
    }
}
