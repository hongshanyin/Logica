package com.sorcery.logica.blocks;

import com.sorcery.logica.Logica;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块注册类
 * 包含所有AI策略标记方块和路径点方块
 *
 * 区域编号系统：
 * - 每种策略方块和路径点都有0-15共16个编号变种
 * - 相同编号的方块会被视为同一区域
 * - 用于区分不同区域的巡逻路线
 */
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Logica.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Logica.MOD_ID);

    // ==================== 守卫标记方块 ====================

    /**
     * 守卫标记方块 - 怪物返回原位，游荡半径6格
     */
    public static final RegistryObject<Block> GUARD_MARKER = BLOCKS.register(
            "guard_marker",
            GuardMarkerBlock::new
    );

    public static final RegistryObject<Item> GUARD_MARKER_ITEM = registerBlockItem(
            "guard_marker", GUARD_MARKER
    );

    // ==================== 哨兵标记方块（0-15编号）====================

    /**
     * 哨兵标记方块列表（16个编号）
     */
    public static final List<RegistryObject<Block>> SENTRIES_MARKERS = new ArrayList<>();
    public static final List<RegistryObject<Item>> SENTRIES_MARKER_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < 16; i++) {
            final int teamId = i;
            String name = "sentries_marker_" + i;

            RegistryObject<Block> block = BLOCKS.register(name, () -> new SentriesMarkerBlock(teamId));
            RegistryObject<Item> item = registerBlockItem(name, block);

            SENTRIES_MARKERS.add(block);
            SENTRIES_MARKER_ITEMS.add(item);
        }
    }

    // ==================== 哨兵路径点方块（0-15编号）====================

    /**
     * 哨兵路径点方块列表（16个编号）
     */
    public static final List<RegistryObject<Block>> SENTRIES_WAYPOINTS = new ArrayList<>();
    public static final List<RegistryObject<Item>> SENTRIES_WAYPOINT_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < 16; i++) {
            final int teamId = i;
            String name = "sentries_waypoint_" + i;

            RegistryObject<Block> block = BLOCKS.register(name, () -> new SentriesWaypointBlock(teamId));
            RegistryObject<Item> item = registerBlockItem(name, block);

            SENTRIES_WAYPOINTS.add(block);
            SENTRIES_WAYPOINT_ITEMS.add(item);
        }
    }

    // ==================== 巡逻标记方块（0-15编号）====================

    /**
     * 巡逻标记方块列表（16个编号）
     */
    public static final List<RegistryObject<Block>> PATROL_MARKERS = new ArrayList<>();
    public static final List<RegistryObject<Item>> PATROL_MARKER_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < 16; i++) {
            final int teamId = i;
            String name = "patrol_marker_" + i;

            RegistryObject<Block> block = BLOCKS.register(name, () -> new PatrolMarkerBlock(teamId));
            RegistryObject<Item> item = registerBlockItem(name, block);

            PATROL_MARKERS.add(block);
            PATROL_MARKER_ITEMS.add(item);
        }
    }

    // ==================== 巡逻路径点方块（0-15编号）====================

    /**
     * 巡逻路径点方块列表（16个编号）
     */
    public static final List<RegistryObject<Block>> PATROL_WAYPOINTS = new ArrayList<>();
    public static final List<RegistryObject<Item>> PATROL_WAYPOINT_ITEMS = new ArrayList<>();

    static {
        for (int i = 0; i < 16; i++) {
            final int teamId = i;
            String name = "patrol_waypoint_" + i;

            RegistryObject<Block> block = BLOCKS.register(name, () -> new PatrolWaypointBlock(teamId));
            RegistryObject<Item> item = registerBlockItem(name, block);

            PATROL_WAYPOINTS.add(block);
            PATROL_WAYPOINT_ITEMS.add(item);
        }
    }

    // ==================== 向后兼容：旧版无编号方块 ====================

    /**
     * 旧版哨兵标记方块（向后兼容，等同于编号0）
     * @deprecated 使用 SENTRIES_MARKERS.get(0) 代替
     */
    @Deprecated
    public static final RegistryObject<Block> SENTRIES_MARKER = BLOCKS.register(
            "sentries_marker",
            SentriesMarkerBlock::new
    );

    @Deprecated
    public static final RegistryObject<Item> SENTRIES_MARKER_ITEM = registerBlockItem(
            "sentries_marker", SENTRIES_MARKER
    );

    /**
     * 旧版哨兵路径点方块（向后兼容，等同于编号0）
     * @deprecated 使用 SENTRIES_WAYPOINTS.get(0) 代替
     */
    @Deprecated
    public static final RegistryObject<Block> SENTRIES_WAYPOINT = BLOCKS.register(
            "sentries_waypoint",
            SentriesWaypointBlock::new
    );

    @Deprecated
    public static final RegistryObject<Item> SENTRIES_WAYPOINT_ITEM = registerBlockItem(
            "sentries_waypoint", SENTRIES_WAYPOINT
    );

    /**
     * 旧版巡逻标记方块（向后兼容，等同于编号0）
     * @deprecated 使用 PATROL_MARKERS.get(0) 代替
     */
    @Deprecated
    public static final RegistryObject<Block> PATROL_MARKER = BLOCKS.register(
            "patrol_marker",
            PatrolMarkerBlock::new
    );

    @Deprecated
    public static final RegistryObject<Item> PATROL_MARKER_ITEM = registerBlockItem(
            "patrol_marker", PATROL_MARKER
    );

    /**
     * 旧版巡逻路径点方块（向后兼容，等同于编号0）
     * @deprecated 使用 PATROL_WAYPOINTS.get(0) 代替
     */
    @Deprecated
    public static final RegistryObject<Block> PATROL_WAYPOINT = BLOCKS.register(
            "patrol_waypoint",
            PatrolWaypointBlock::new
    );

    @Deprecated
    public static final RegistryObject<Item> PATROL_WAYPOINT_ITEM = registerBlockItem(
            "patrol_waypoint", PATROL_WAYPOINT
    );

    // ==================== 辅助方法 ====================

    /**
     * 注册方块物品
     */
    private static RegistryObject<Item> registerBlockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(),
                new Item.Properties()
                        .rarity(Rarity.EPIC)
        ));
    }
}
