package com.sorcery.logica;

import com.mojang.logging.LogUtils;
import com.sorcery.logica.blocks.ModBlockEntities;
import com.sorcery.logica.blocks.ModBlocks;
import com.sorcery.logica.capability.CapabilityHandler;
import com.sorcery.logica.config.LogicaConfig;
import com.sorcery.logica.events.EntitySpawnHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Logica - 基于Aperi Oculos的AI增强模组
 *
 * 功能：
 * - 怪物转向限制系统
 * - 听觉触发视觉提示
 * - 声音追踪与调查
 * - Guard/Sentries/Patrol策略系统
 * - 战斗中声音追踪
 */
@Mod(Logica.MOD_ID)
public class Logica {
    public static final String MOD_ID = "logica";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 创造标签页注册器
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    // Logica创造标签页
    public static final RegistryObject<CreativeModeTab> LOGICA_TAB = CREATIVE_MODE_TABS.register("logica_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.logica"))
                    .icon(() -> ModBlocks.GUARD_MARKER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 守卫标记（无编号）
                        output.accept(ModBlocks.GUARD_MARKER_ITEM.get());

                        // 哨兵标记（16个编号 0-15）
                        for (int i = 0; i < 16; i++) {
                            output.accept(ModBlocks.SENTRIES_MARKERS.get(i).get());
                        }

                        // 哨兵路径点（16个编号 0-15）
                        for (int i = 0; i < 16; i++) {
                            output.accept(ModBlocks.SENTRIES_WAYPOINTS.get(i).get());
                        }

                        // 巡逻标记（16个编号 0-15）
                        for (int i = 0; i < 16; i++) {
                            output.accept(ModBlocks.PATROL_MARKERS.get(i).get());
                        }

                        // 巡逻路径点（16个编号 0-15）
                        for (int i = 0; i < 16; i++) {
                            output.accept(ModBlocks.PATROL_WAYPOINTS.get(i).get());
                        }
                    })
                    .build()
    );

    public Logica() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;

        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, LogicaConfig.SPEC);

        // 注册方块和物品
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);

        // 注册BlockEntity
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // 注册创造标签页
        CREATIVE_MODE_TABS.register(modEventBus);

        // 注册Capability
        modEventBus.addListener(CapabilityHandler::registerCapabilities);

        // 通用设置
        modEventBus.addListener(this::commonSetup);

        // Forge事件监听
        forgeEventBus.register(EntitySpawnHandler.class);

        LOGGER.info("Logica AI Mod initialized");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Logica common setup");
    }
}
