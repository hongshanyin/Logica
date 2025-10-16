package com.sorcery.logica.blocks;

import com.sorcery.logica.blocks.entity.PatrolMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 巡逻标记方块
 *
 * 功能：
 * - 生成在此区域的怪物将应用巡逻策略
 * - 沿相连的巡逻路径点反复巡逻
 * - 支持区域编号（0-15）区分不同巡逻区域
 * - 使用结构空位贴图（蓝色变种）
 * - 通过BlockEntity自主tick，像信标一样工作
 */
public class PatrolMarkerBlock extends BaseMarkerBlock implements EntityBlock {
    private final int teamId;

    public PatrolMarkerBlock(int teamId) {
        super();
        this.teamId = teamId;
    }

    public PatrolMarkerBlock() {
        this(0); // 默认编号0（向后兼容）
    }

    /**
     * 获取区域编号
     */
    public int getTeamId() {
        return teamId;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PatrolMarkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 只在服务器端tick
        return level.isClientSide() ? null : (lvl, pos, st, be) -> {
            if (be instanceof PatrolMarkerBlockEntity entity) {
                entity.tick();
            }
        };
    }
}
