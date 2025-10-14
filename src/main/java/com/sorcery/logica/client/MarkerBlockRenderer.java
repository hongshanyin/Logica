package com.sorcery.logica.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorcery.logica.Logica;
import com.sorcery.logica.blocks.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端渲染处理器
 *
 * 功能：
 * - 当玩家手持marker物品时，渲染附近的对应marker方块边框
 * - 类似原版structure_void的行为
 */
@Mod.EventBusSubscriber(modid = Logica.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class MarkerBlockRenderer {

    // 渲染范围（格）
    private static final int RENDER_RADIUS = 16;

    // 方块到物品的映射（延迟初始化，避免在类加载时访问未注册的对象）
    private static Map<Block, Item> blockToItemMap = null;

    /**
     * 初始化方块到物品的映射（延迟初始化）
     */
    private static void initializeMapping() {
        if (blockToItemMap == null) {
            blockToItemMap = new HashMap<>();

            // 守卫标记（单个）
            blockToItemMap.put(ModBlocks.GUARD_MARKER.get(), ModBlocks.GUARD_MARKER_ITEM.get());

            // 哨兵标记（0-15编号）
            for (int i = 0; i < 16; i++) {
                blockToItemMap.put(ModBlocks.SENTRIES_MARKERS.get(i).get(),
                                 ModBlocks.SENTRIES_MARKER_ITEMS.get(i).get());
            }

            // 哨兵路径点（0-15编号）
            for (int i = 0; i < 16; i++) {
                blockToItemMap.put(ModBlocks.SENTRIES_WAYPOINTS.get(i).get(),
                                 ModBlocks.SENTRIES_WAYPOINT_ITEMS.get(i).get());
            }

            // 巡逻标记（0-15编号）
            for (int i = 0; i < 16; i++) {
                blockToItemMap.put(ModBlocks.PATROL_MARKERS.get(i).get(),
                                 ModBlocks.PATROL_MARKER_ITEMS.get(i).get());
            }

            // 巡逻路径点（0-15编号）
            for (int i = 0; i < 16; i++) {
                blockToItemMap.put(ModBlocks.PATROL_WAYPOINTS.get(i).get(),
                                 ModBlocks.PATROL_WAYPOINT_ITEMS.get(i).get());
            }

            // 向后兼容：旧版无编号方块
            blockToItemMap.put(ModBlocks.SENTRIES_MARKER.get(), ModBlocks.SENTRIES_MARKER_ITEM.get());
            blockToItemMap.put(ModBlocks.PATROL_MARKER.get(), ModBlocks.PATROL_MARKER_ITEM.get());
            blockToItemMap.put(ModBlocks.PATROL_WAYPOINT.get(), ModBlocks.PATROL_WAYPOINT_ITEM.get());
            blockToItemMap.put(ModBlocks.SENTRIES_WAYPOINT.get(), ModBlocks.SENTRIES_WAYPOINT_ITEM.get());
        }
    }

    /**
     * 渲染方块边框
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // 在半透明方块渲染后执行
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        // 初始化映射（延迟初始化）
        initializeMapping();

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }

        Level level = player.level();
        if (level == null) {
            return;
        }

        // 检查玩家手持的物品
        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = player.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return; // 没有手持任何物品
        }

        // 查找对应的方块
        Block targetBlock = null;
        for (Map.Entry<Block, Item> entry : blockToItemMap.entrySet()) {
            if (heldItem.is(entry.getValue())) {
                targetBlock = entry.getKey();
                break;
            }
        }

        if (targetBlock == null) {
            return; // 不是marker物品
        }

        // 渲染附近的对应方块
        renderNearbyMarkers(event, player, level, targetBlock);
    }

    /**
     * 渲染玩家附近的marker方块
     */
    private static void renderNearbyMarkers(RenderLevelStageEvent event, Player player, Level level, Block targetBlock) {
        Vec3 cameraPos = event.getCamera().getPosition();
        BlockPos playerPos = player.blockPosition();

        PoseStack poseStack = event.getPoseStack();

        // 从Minecraft实例获取渲染缓冲区
        Minecraft mc = Minecraft.getInstance();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // 扫描附近的方块
        for (int x = -RENDER_RADIUS; x <= RENDER_RADIUS; x++) {
            for (int y = -RENDER_RADIUS; y <= RENDER_RADIUS; y++) {
                for (int z = -RENDER_RADIUS; z <= RENDER_RADIUS; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);

                    // 检查是否是目标方块
                    if (level.getBlockState(pos).getBlock() == targetBlock) {
                        // 渲染边框
                        renderBlockOutline(poseStack, consumer, pos, cameraPos);
                    }
                }
            }
        }
    }

    /**
     * 渲染单个方块的边框
     */
    private static void renderBlockOutline(PoseStack poseStack, VertexConsumer consumer, BlockPos pos, Vec3 cameraPos) {
        poseStack.pushPose();

        // 调整到方块位置（相对于相机）
        poseStack.translate(
                pos.getX() - cameraPos.x,
                pos.getY() - cameraPos.y,
                pos.getZ() - cameraPos.z
        );

        // 渲染完整方块边框（白色半透明）
        VoxelShape shape = net.minecraft.world.phys.shapes.Shapes.block();
        AABB box = shape.bounds();

        LevelRenderer.renderLineBox(
                poseStack,
                consumer,
                box,
                1.0F, 1.0F, 1.0F, 0.4F  // 白色，40%不透明度
        );

        poseStack.popPose();
    }
}
