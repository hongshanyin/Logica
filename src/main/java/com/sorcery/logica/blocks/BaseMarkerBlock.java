package com.sorcery.logica.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * 基础标记方块
 *
 * 特性：
 * - 使用结构空位贴图
 * - 创造模式：可见、可破坏
 * - 生存模式：不可见、不可破坏
 * - 无碰撞箱
 * - 可被结构方块保存
 */
public abstract class BaseMarkerBlock extends Block {

    public BaseMarkerBlock() {
        super(Properties.of()
                .strength(50.0F, 3600000.0F) // 高硬度但可被创造模式破坏
                .noCollission()              // 无碰撞箱
                .noOcclusion()               // 不遮挡光线
                .isValidSpawn((state, level, pos, entityType) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
        );
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // 默认不渲染，通过客户端事件处理
    }

    /**
     * 选择框形状 - 必须返回完整方块才能被选中
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level,
                               BlockPos pos, CollisionContext context) {
        return Shapes.block(); // 完整形状，允许选中
    }

    /**
     * 碰撞箱 - 返回空，玩家和实体可以穿过
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                       BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // 无碰撞
    }

    /**
     * 遮挡形状 - 返回空，不遮挡视线
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty(); // 不遮挡
    }

    /**
     * 视觉形状 - 返回完整形状，显示边框
     */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block(); // 显示完整边框
    }

    /**
     * 创造模式瞬间破坏，生存模式无法破坏
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (player.isCreative()) {
            return 1.0F; // 创造模式：瞬间破坏
        }
        return 0.0F; // 生存模式：无法破坏
    }

    /**
     * 控制掉落：生存模式不掉落（创造模式默认不调用此方法）
     */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // 生存模式：不掉落任何物品
        return List.of();
    }

}
