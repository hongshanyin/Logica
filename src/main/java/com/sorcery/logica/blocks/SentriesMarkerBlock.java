package com.sorcery.logica.blocks;

import com.sorcery.logica.blocks.entity.SentriesMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 哨兵标记方块
 *
 * 功能：
 * - 生成在此区域的怪物将应用哨兵策略
 * - 快速巡逻，优先未访问位置
 * - 发现玩家时发出钟声警报
 * - 支持区域编号（0-15）区分不同巡逻区域
 * - 使用结构空位贴图（黄色变种）
 * - 通过BlockEntity自主tick，像信标一样工作
 */
public class SentriesMarkerBlock extends BaseMarkerBlock implements EntityBlock {
    private final int teamId;

    public SentriesMarkerBlock(int teamId) {
        super();
        this.teamId = teamId;
    }

    public SentriesMarkerBlock() {
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
        return new SentriesMarkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 只在服务器端tick
        return level.isClientSide() ? null : (lvl, pos, st, be) -> {
            if (be instanceof SentriesMarkerBlockEntity entity) {
                entity.tick();
            }
        };
    }
}
