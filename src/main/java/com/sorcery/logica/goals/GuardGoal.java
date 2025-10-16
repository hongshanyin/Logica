package com.sorcery.logica.goals;

import com.sorcery.logica.Logica;
import com.sorcery.logica.ai.AIState;
import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.capability.AICapabilityProvider;
import com.sorcery.logica.capability.IAICapability;
import com.sorcery.logica.config.LogicaConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Random;

/**
 * 守卫Goal
 *
 * 功能：
 * - 在GUARD策略且IDLE状态时触发
 * - 以spawnPosition为家，在附近游荡
 * - 距离家太远时返回
 * - 实现卡墙检测和脱困逻辑
 *
 * 优先级：4（低于攻击、追踪、调查）
 */
public class GuardGoal extends Goal {

    private final Mob mob;
    private final Random random = new Random();

    // 家的位置
    private BlockPos homePosition;

    // 卡墙检测
    private Vec3 lastPosition;
    private int stuckTicks;

    // 游荡计时器
    private int wanderCooldown;

    // 返回中断位置标记
    private boolean isReturningToInterruptedPosition;
    private int returnFailureCount; // 返回失败计数器

    public GuardGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE));
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

        // 只在GUARD策略且IDLE状态下执行
        if (aiCap.getStrategy() != AIStrategy.GUARD) {
            return false;
        }

        if (aiCap.getState() != AIState.IDLE) {
            return false;
        }

        // 获取家的位置
        BlockPos spawnPos = aiCap.getSpawnPosition();
        if (spawnPos == null) {
            return false;
        }

        this.homePosition = spawnPos;
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

        // 如果状态改变，停止
        if (aiCap.getState() != AIState.IDLE) {
            return false;
        }

        // 如果策略改变，停止
        if (aiCap.getStrategy() != AIStrategy.GUARD) {
            return false;
        }

        return true;
    }

    /**
     * 开始执行
     */
    @Override
    public void start() {
        this.lastPosition = mob.position();
        this.stuckTicks = 0;
        this.wanderCooldown = 0;
        this.isReturningToInterruptedPosition = false;
        this.returnFailureCount = 0;

        if (LogicaConfig.shouldLogGoalLifecycle()) {
            Logica.LOGGER.info("🔥 GuardGoal.start() CALLED for {} at home position {}",
                    mob.getName().getString(), homePosition);
        }

        // 🔥 优先返回离开点（如果存在）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            BlockPos interruptedPos = cap.getInterruptedPatrolPosition();
            if (interruptedPos != null) {
                if (LogicaConfig.shouldLogGoalLifecycle()) {
                    Logica.LOGGER.info("Mob {} returning to interrupted guard position: {}",
                            mob.getName().getString(), interruptedPos);
                }

                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(interruptedPos, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, LogicaConfig.GUARD_SPEED_MULTIPLIER.get());
                    // 🔥 标记正在返回，但不清除离开点（等到达后再清除）
                    this.isReturningToInterruptedPosition = true;
                } else {
                    // 🔥 无法创建路径，直接放弃返回
                    if (LogicaConfig.shouldLogGoalLifecycle()) {
                        Logica.LOGGER.warn("Mob {} cannot create path to interrupted position {}, giving up",
                                mob.getName().getString(), interruptedPos);
                    }
                    cap.setInterruptedPatrolPosition(null);
                }
            }
        });
    }

    /**
     * 停止执行
     */
    @Override
    public void stop() {
        mob.getNavigation().stop();
        this.lastPosition = null;

        // 🔥 记录离开守卫时的位置（只在首次被吸引离开时记录）
        mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
            // 只在状态不是IDLE且没有已存在的离开点时才记录
            // 这样可以避免返回途中再次被打断时覆盖原始离开点
            if (cap.getState() != AIState.IDLE && cap.getInterruptedPatrolPosition() == null) {
                BlockPos currentPos = mob.blockPosition();
                cap.setInterruptedPatrolPosition(currentPos);

                if (LogicaConfig.shouldLogGoalLifecycle()) {
                    Logica.LOGGER.info("Mob {} interrupted from guard at position: {} (state: {})",
                            mob.getName().getString(), currentPos, cap.getState());
                }
            }
        });
    }

    /**
     * 每tick执行
     */
    @Override
    public void tick() {
        if (homePosition == null) {
            return;
        }

        // 🔥 处理返回中断位置
        if (isReturningToInterruptedPosition) {
            mob.getCapability(AICapabilityProvider.AI_CAPABILITY).ifPresent(cap -> {
                BlockPos interruptedPos = cap.getInterruptedPatrolPosition();
                if (interruptedPos != null) {
                    Vec3 mobPos = mob.position();
                    Vec3 targetPos = Vec3.atCenterOf(interruptedPos);
                    double distance = mobPos.distanceTo(targetPos);

                    // 到达离开点（距离<3格）
                    if (distance < 3.0) {
                        if (LogicaConfig.shouldLogNavigation()) {
                            Logica.LOGGER.info("Mob {} reached interrupted position {}, clearing and resuming guard",
                                    mob.getName().getString(), interruptedPos);
                        }

                        // 清除离开点
                        cap.setInterruptedPatrolPosition(null);
                        isReturningToInterruptedPosition = false;
                        returnFailureCount = 0;

                        // 继续正常守卫（不return，让下面的逻辑继续执行）
                    } else {
                        // 继续前往（如果导航完成，重新设置）
                        if (mob.getNavigation().isDone()) {
                            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(interruptedPos, 1);
                            if (path != null) {
                                mob.getNavigation().moveTo(path, LogicaConfig.GUARD_SPEED_MULTIPLIER.get());
                                returnFailureCount = 0; // 成功创建路径，重置计数器
                            } else {
                                // 🔥 无法创建路径，增加失败计数
                                returnFailureCount++;
                                if (LogicaConfig.shouldLogNavigation()) {
                                    Logica.LOGGER.warn("Mob {} failed to create path to interrupted position {} (attempt {}/10)",
                                            mob.getName().getString(), interruptedPos, returnFailureCount);
                                }

                                // 🔥 失败10次后放弃返回
                                if (returnFailureCount >= 10) {
                                    if (LogicaConfig.shouldLogNavigation()) {
                                        Logica.LOGGER.warn("Mob {} giving up returning to interrupted position {} after 10 failures",
                                                mob.getName().getString(), interruptedPos);
                                    }
                                    cap.setInterruptedPatrolPosition(null);
                                    isReturningToInterruptedPosition = false;
                                    returnFailureCount = 0;
                                    // 继续正常守卫（不return）
                                }
                            }
                        }
                    }
                } else {
                    // 离开点不存在了，取消返回状态
                    isReturningToInterruptedPosition = false;
                    returnFailureCount = 0;
                }
            });

            // 🔥 如果没有放弃，继续返回模式
            if (isReturningToInterruptedPosition) {
                return; // 阻止正常守卫
            }
            // 否则继续执行下面的正常守卫逻辑
        }

        double guardRadius = LogicaConfig.GUARD_RADIUS.get();
        Vec3 currentPos = mob.position();
        Vec3 homePos = Vec3.atCenterOf(homePosition);
        double distanceToHome = currentPos.distanceTo(homePos);

        // 检查是否距离家太远
        if (distanceToHome > guardRadius * 2) {
            // 返回家
            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(homePosition, 1);
            if (path != null) {
                mob.getNavigation().moveTo(path, LogicaConfig.GUARD_SPEED_MULTIPLIER.get());
            }

            if (LogicaConfig.shouldLogNavigation()) {
                Logica.LOGGER.debug("Mob {} too far from home ({}), returning",
                        mob.getName().getString(), distanceToHome);
            }

            return;
        }

        // 卡墙检测
        detectAndResolveStuck(currentPos);

        // 游荡逻辑
        if (--wanderCooldown <= 0) {
            wanderCooldown = 100 + random.nextInt(100); // 5-10秒

            // 在家附近随机游荡
            if (distanceToHome < guardRadius) {
                // 在家附近，随机选择一个方向
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = random.nextDouble() * guardRadius;

                int targetX = (int) (homePosition.getX() + Math.cos(angle) * distance);
                int targetZ = (int) (homePosition.getZ() + Math.sin(angle) * distance);
                BlockPos targetPos = new BlockPos(targetX, homePosition.getY(), targetZ);

                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetPos, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, LogicaConfig.GUARD_SPEED_MULTIPLIER.get());
                }
            } else {
                // 离家较远，靠近家
                net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(homePosition, 1);
                if (path != null) {
                    mob.getNavigation().moveTo(path, LogicaConfig.GUARD_SPEED_MULTIPLIER.get());
                }
            }
        }
    }

    /**
     * 检测并解决卡墙问题
     */
    private void detectAndResolveStuck(Vec3 currentPos) {
        if (lastPosition == null) {
            lastPosition = currentPos;
            return;
        }

        // 检查是否移动了
        double movementDistance = currentPos.distanceTo(lastPosition);

        if (movementDistance < 0.1) {
            // 几乎没有移动，可能卡住了
            stuckTicks++;

            int threshold = LogicaConfig.STUCK_DETECTION_THRESHOLD.get();

            if (stuckTicks > threshold) {
                // 确认卡住，执行脱困
                resolveStuck();
                stuckTicks = 0;
            }
        } else {
            // 正常移动
            stuckTicks = 0;
        }

        lastPosition = currentPos;
    }

    /**
     * 脱困逻辑
     */
    private void resolveStuck() {
        if (LogicaConfig.shouldLogNavigation()) {
            Logica.LOGGER.debug("Mob {} detected stuck, attempting to unstuck",
                    mob.getName().getString());
        }

        // 停止当前导航
        mob.getNavigation().stop();

        // 后退2格
        Vec3 currentPos = mob.position();
        Vec3 lookVec = mob.getLookAngle();
        Vec3 backwardVec = lookVec.scale(-2.0);
        Vec3 targetPos = currentPos.add(backwardVec);
        BlockPos targetBlockPos = new BlockPos((int) targetPos.x, (int) targetPos.y, (int) targetPos.z);

        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(targetBlockPos, 1);
        if (path != null) {
            mob.getNavigation().moveTo(path, LogicaConfig.GUARD_SPEED_MULTIPLIER.get());
        }

        // 随机转向
        double randomAngle = random.nextDouble() * Math.PI * 2;
        double lookX = currentPos.x + Math.cos(randomAngle) * 5;
        double lookZ = currentPos.z + Math.sin(randomAngle) * 5;
        mob.getLookControl().setLookAt(lookX, currentPos.y, lookZ, 10.0F, mob.getMaxHeadXRot());

        // 重置游荡计时器，强制选择新路径
        wanderCooldown = 10;
    }

    /**
     * 是否需要重复检查canUse
     */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
