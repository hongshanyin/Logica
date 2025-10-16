package com.sorcery.logica.goals;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import com.sorcery.logica.config.LogicaConfig;
import io.github.Sorcery_Dynasties.aperioculos.api.event.VibrationPerceivedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.EnumSet;
import java.util.List;

/**
 * 追踪Goal
 *
 * 功能：
 * - 在TRACKING状态时触发（战斗中丢失视觉）
 * - 积极响应所有声音，前往声音来源
 * - 碰撞检测（1.5格范围）
 * - 碰撞成功→重新获得仇恨，返回COMBAT
 * - 超时→进入SEARCHING状态
 *
 * 优先级：2（仅次于逃跑和攻击）
 */
public class TrackingGoal extends Goal {

    private final Mob mob;

    // 追踪状态
    private BlockPos lastSoundPosition;
    private int trackingTimer;

    public TrackingGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));

        // 注册事件监听器（用于接收声音事件）
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * 判断是否应该开始执行
     */
    @Override
    public boolean canUse() {
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return false;
        }

        // 只在TRACKING状态下执行
        if (aiCap.getState() != AIState.TRACKING) {
            return false;
        }

        // 获取最后已知位置
        BlockPos lastKnownPos = aiCap.getLastKnownTargetPos();
        if (lastKnownPos == null) {
            // 没有目标位置，直接进入SEARCHING
            aiCap.setState(AIState.SEARCHING);
            return false;
        }

        this.lastSoundPosition = lastKnownPos;
        this.trackingTimer = 0;

        return true;
    }

    /**
     * 判断是否应该继续执行
     */
    @Override
    public boolean canContinueToUse() {
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null) {
            return false;
        }

        // 如果状态改变（例如重新发现目标进入COMBAT），停止
        if (aiCap.getState() != AIState.TRACKING) {
            return false;
        }

        // 检查超时
        int maxDuration = LogicaConfig.MAX_TRACKING_DURATION_TICKS.get();
        if (trackingTimer >= maxDuration) {
            // 超时，进入SEARCHING状态
            aiCap.setState(AIState.SEARCHING);
            if (LogicaConfig.shouldLogStateTransitions()) {
                Logica.LOGGER.debug("Mob {} tracking timeout, switching to SEARCHING",
                        mob.getName().getString());
            }
            return false;
        }

        return true;
    }

    /**
     * 开始执行
     */
    @Override
    public void start() {
        if (LogicaConfig.shouldLogGoalLifecycle()) {
            Logica.LOGGER.debug("Mob {} starting tracking mode at {}",
                    mob.getName().getString(), lastSoundPosition);
        }

        // 前往最后已知位置
        navigateToLastSound();
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        mob.getNavigation().stop();
        this.lastSoundPosition = null;
    }

    /**
     * 每tick执行
     */
    @Override
    public void tick() {
        trackingTimer++;

        // 更新追踪计时器到Capability
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            cap.setTrackingTicks(trackingTimer);
        });

        // 碰撞检测
        if (checkCollision()) {
            return; // 碰撞成功，已经切换到COMBAT
        }

        // 继续前往最后的声音位置
        if (lastSoundPosition != null) {
            Vec3 mobPos = mob.position();
            Vec3 targetPos = Vec3.atCenterOf(lastSoundPosition);
            double distance = mobPos.distanceTo(targetPos);

            // 到达位置但没有发现目标
            if (distance < 3.0) {
                // 🔥 改进: 到达后在附近游荡,而不是站着不动
                if (mob.getNavigation().isDone()) {
                    // 在最后位置周围随机游荡
                    wanderAroundLastPosition();
                }
            } else {
                // 继续前往
                if (mob.getNavigation().isDone()) {
                    navigateToLastSound();
                }
            }
        }
    }

    /**
     * 碰撞检测
     * @return 是否成功碰撞到目标
     */
    private boolean checkCollision() {
        double collisionRadius = LogicaConfig.TRACKING_COLLISION_RADIUS.get();

        // 查找附近的玩家
        AABB searchBox = mob.getBoundingBox().inflate(collisionRadius);
        List<Player> nearbyPlayers = mob.level().getEntitiesOfClass(Player.class, searchBox);

        for (Player player : nearbyPlayers) {
            if (player.isSpectator()) {
                continue;
            }

            // 🔥 忽略创造模式玩家
            if (LogicaConfig.IGNORE_CREATIVE_PLAYERS.get() && player.isCreative()) {
                continue;
            }

            // 碰撞成功！重新获得仇恨
            mob.setTarget(player);

            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                cap.setState(AIState.COMBAT);
                cap.setLastKnownTargetPos(player.blockPosition());
                cap.setTrackingTicks(0);
            });

            if (LogicaConfig.shouldLogStateTransitions()) {
                Logica.LOGGER.debug("Mob {} collision detected, reacquiring target {}",
                        mob.getName().getString(), player.getName().getString());
            }

            return true;
        }

        return false;
    }

    /**
     * 导航到最后的声音位置
     */
    private void navigateToLastSound() {
        if (lastSoundPosition == null) {
            return;
        }

        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(lastSoundPosition, 1);
        if (path != null) {
            mob.getNavigation().moveTo(path, LogicaConfig.TRACKING_SPEED_MULTIPLIER.get());
        }

        // 朝向目标
        mob.getLookControl().setLookAt(
                lastSoundPosition.getX() + 0.5,
                lastSoundPosition.getY() + 1,
                lastSoundPosition.getZ() + 0.5,
                30.0F,
                mob.getMaxHeadXRot()
        );
    }

    /**
     * 在最后位置周围游荡
     *
     * 模拟原版RandomStrollGoal的行为:
     * - 在最后已知位置附近随机选择目标
     * - 使用原版速度移动
     */
    private void wanderAroundLastPosition() {
        if (lastSoundPosition == null) {
            return;
        }

        // 在最后位置周围10格范围内随机选择目标
        double offsetX = (mob.getRandom().nextDouble() - 0.5) * 20.0;  // -10 to +10
        double offsetZ = (mob.getRandom().nextDouble() - 0.5) * 20.0;

        BlockPos wanderTarget = new BlockPos(
            (int)(lastSoundPosition.getX() + offsetX),
            lastSoundPosition.getY(),
            (int)(lastSoundPosition.getZ() + offsetZ)
        );

        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(wanderTarget, 1);
        if (path != null) {
            // 使用原版速度游荡 (不是追踪速度)
            mob.getNavigation().moveTo(path, mob.getSpeed());
        }
    }

    /**
     * 监听声音事件（TRACKING状态下积极响应）
     */
    @SubscribeEvent
    public void onVibrationPerceived(VibrationPerceivedEvent event) {
        // 只处理自己的事件
        if (event.getListener() != this.mob) {
            return;
        }

        // 只在TRACKING状态下响应
        IAICapability aiCap = mob.getCapability(AICapabilityProvider.AI_CAPABILITY).orElse(null);
        if (aiCap == null || aiCap.getState() != AIState.TRACKING) {
            return;
        }

        // 🔥 过滤声音来源：忽略其他怪物产生的声音（只追踪玩家的声音）
        net.minecraft.world.entity.Entity sourceEntity = event.getSourceEntity();
        if (sourceEntity != null && sourceEntity instanceof Mob) {
            // 声音来自其他怪物（如绵羊、鸡等），忽略
            if (LogicaConfig.shouldLogPerceptionEvents()) {
                Logica.LOGGER.debug("Mob {} ignored sound from {} (not a player)",
                        mob.getName().getString(), sourceEntity.getName().getString());
            }
            return;
        }

        // 🔥 忽略创造模式玩家的声音
        if (LogicaConfig.IGNORE_CREATIVE_PLAYERS.get() && sourceEntity instanceof Player player) {
            if (player.isCreative()) {
                return;
            }
        }

        // 更新目标位置
        Vec3 sourcePos = event.getSourcePos();
        this.lastSoundPosition = new BlockPos((int)sourcePos.x, (int)sourcePos.y, (int)sourcePos.z);

        // 更新Capability
        aiCap.setLastKnownTargetPos(lastSoundPosition);

        // 立即前往新的声音位置
        navigateToLastSound();

        // 重置追踪计时器（延长追踪时间）
        this.trackingTimer = Math.max(0, trackingTimer - 100);

        if (LogicaConfig.shouldLogPerceptionEvents()) {
            Logica.LOGGER.debug("Mob {} tracking new sound at {}, timer reset",
                    mob.getName().getString(), lastSoundPosition);
        }
    }

    /**
     * 是否需要重复检查canUse
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
