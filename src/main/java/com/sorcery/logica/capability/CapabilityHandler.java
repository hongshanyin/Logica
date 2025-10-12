package com.sorcery.logica.capability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.sorcery.logica.Logica;
import net.minecraft.resources.ResourceLocation;

/**
 * Capability注册和附加处理器
 */
@Mod.EventBusSubscriber(modid = Logica.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CapabilityHandler {

    /**
     * 注册Capability类型
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IAICapability.class);
        Logica.LOGGER.info("Registered AI Capability");
    }

    /**
     * 为所有Mob实体附加AI Capability
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Mob) {
            AICapabilityProvider provider = new AICapabilityProvider();
            event.addCapability(
                    new ResourceLocation(Logica.MOD_ID, "ai_capability"),
                    provider
            );
        }
    }
}
