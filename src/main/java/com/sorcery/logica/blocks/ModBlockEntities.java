package com.sorcery.logica.blocks;

import com.sorcery.logica.Logica;
import com.sorcery.logica.blocks.entity.GuardMarkerBlockEntity;
import com.sorcery.logica.blocks.entity.PatrolMarkerBlockEntity;
import com.sorcery.logica.blocks.entity.SentriesMarkerBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockEntity注册类
 *
 * 策略方块通过BlockEntity实现自主tick，像信标一样工作
 */
public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Logica.MOD_ID);

    /**
     * 守卫标记BlockEntity
     */
    public static final RegistryObject<BlockEntityType<GuardMarkerBlockEntity>> GUARD_MARKER =
            BLOCK_ENTITIES.register("guard_marker", () ->
                    BlockEntityType.Builder.of(
                            GuardMarkerBlockEntity::new,
                            ModBlocks.GUARD_MARKER.get()
                    ).build(null)
            );

    /**
     * 哨兵标记BlockEntity（支持所有编号变种）
     */
    public static final RegistryObject<BlockEntityType<SentriesMarkerBlockEntity>> SENTRIES_MARKER =
            BLOCK_ENTITIES.register("sentries_marker", () -> {
                // 收集所有哨兵标记方块（包括旧版和新版编号方块）
                List<Block> blocks = new ArrayList<>();
                blocks.add(ModBlocks.SENTRIES_MARKER.get()); // 旧版
                ModBlocks.SENTRIES_MARKERS.forEach(ro -> blocks.add(ro.get())); // 新版0-15

                return BlockEntityType.Builder.of(
                        SentriesMarkerBlockEntity::new,
                        blocks.toArray(new Block[0])
                ).build(null);
            });

    /**
     * 巡逻标记BlockEntity（支持所有编号变种）
     */
    public static final RegistryObject<BlockEntityType<PatrolMarkerBlockEntity>> PATROL_MARKER =
            BLOCK_ENTITIES.register("patrol_marker", () -> {
                // 收集所有巡逻标记方块（包括旧版和新版编号方块）
                List<Block> blocks = new ArrayList<>();
                blocks.add(ModBlocks.PATROL_MARKER.get()); // 旧版
                ModBlocks.PATROL_MARKERS.forEach(ro -> blocks.add(ro.get())); // 新版0-15

                return BlockEntityType.Builder.of(
                        PatrolMarkerBlockEntity::new,
                        blocks.toArray(new Block[0])
                ).build(null);
            });
}
