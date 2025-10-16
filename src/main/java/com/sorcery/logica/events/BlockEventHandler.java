package com.sorcery.logica.events;

import com.sorcery.logica.Logica;
import com.sorcery.logica.blocks.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 方块事件处理器
 *
 * 功能：
 * - 记录策略方块的放置和破坏（用于调试日志）
 * - 策略方块通过BlockEntity自主工作，不需要全局管理
 */
@Mod.EventBusSubscriber(modid = Logica.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlockEventHandler {

    /**
     * 监听方块放置事件（仅用于调试日志）
     */
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Block block = event.getPlacedBlock().getBlock();

        // 检查是否是策略方块
        if (block instanceof GuardMarkerBlock ||
            block instanceof SentriesMarkerBlock ||
            block instanceof PatrolMarkerBlock) {

            Level level = (Level) event.getLevel();
            if (level.isClientSide()) return;

            Logica.LOGGER.info("Strategy marker placed at {} (type: {}), BlockEntity will handle beacon effect",
                    event.getPos(), block.getClass().getSimpleName());
        }
    }

    /**
     * 监听方块破坏事件（仅用于调试日志）
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Block block = event.getState().getBlock();

        if (block instanceof GuardMarkerBlock ||
            block instanceof SentriesMarkerBlock ||
            block instanceof PatrolMarkerBlock) {

            Level level = (Level) event.getLevel();
            if (level.isClientSide()) return;

            Logica.LOGGER.info("Strategy marker removed at {} (type: {}), BlockEntity destroyed",
                    event.getPos(), block.getClass().getSimpleName());
        }
    }
}
