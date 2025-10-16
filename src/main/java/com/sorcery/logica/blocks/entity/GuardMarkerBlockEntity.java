package com.sorcery.logica.blocks.entity;

import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.blocks.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 守卫标记BlockEntity
 *
 * 功能：
 * - 每秒tick一次
 * - 对周围3x3x3新生成的怪物应用守卫策略
 */
public class GuardMarkerBlockEntity extends BaseStrategyBlockEntity {

    public GuardMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUARD_MARKER.get(), pos, state);
    }

    @Override
    protected AIStrategy getStrategy() {
        return AIStrategy.GUARD;
    }

    @Override
    protected int getAreaTeam() {
        return 0; // 守卫不需要区域编号
    }
}
