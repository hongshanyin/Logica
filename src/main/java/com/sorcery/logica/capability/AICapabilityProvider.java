package com.sorcery.logica.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * AI能力提供者
 */
public class AICapabilityProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final Capability<IAICapability> AI_CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    private final IAICapability capability = new AICapability();
    private final LazyOptional<IAICapability> optional = LazyOptional.of(() -> capability);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == AI_CAPABILITY) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        // TODO: 序列化capability数据（如果需要持久化）
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        // TODO: 反序列化capability数据
    }
}
