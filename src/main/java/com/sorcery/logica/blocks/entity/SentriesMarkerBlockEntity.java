package com.sorcery.logica.blocks.entity;

import com.sorcery.logica.ai.AIStrategy;
import com.sorcery.logica.blocks.ModBlockEntities;
import com.sorcery.logica.blocks.SentriesMarkerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 哨兵标记BlockEntity
 *
 * 功能：
 * - 每秒tick一次
 * - 对周围3x3x3新生成的怪物应用哨兵策略
 * - 支持区域编号（从方块读取）
 */
public class SentriesMarkerBlockEntity extends BaseStrategyBlockEntity {

    public SentriesMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SENTRIES_MARKER.get(), pos, state);
    }

    @Override
    protected AIStrategy getStrategy() {
        return AIStrategy.SENTRIES;
    }

    @Override
    protected int getAreaTeam() {
        // 从方块读取区域编号
        if (this.level != null && this.getBlockState().getBlock() instanceof SentriesMarkerBlock block) {
            return block.getTeamId();
        }
        return 0; // 默认编号0
    }
}
