package com.sorcery.logica.blocks;

import com.sorcery.logica.blocks.entity.GuardMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 守卫标记方块
 *
 * 功能：
 * - 生成在此区域的怪物将应用守卫策略
 * - 返回原位，游荡半径6格
 * - 使用结构空位贴图（红色变种）
 * - 通过BlockEntity自主tick，像信标一样工作
 */
public class GuardMarkerBlock extends BaseMarkerBlock implements EntityBlock {
    public GuardMarkerBlock() {
        super();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuardMarkerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 只在服务器端tick
        return level.isClientSide() ? null : (lvl, pos, st, be) -> {
            if (be instanceof GuardMarkerBlockEntity entity) {
                entity.tick();
            }
        };
    }
}
