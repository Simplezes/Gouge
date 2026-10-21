package net.gouge.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface GougeLivingAccessor {
    @Accessor("jumping")
    boolean gouge$isJumping();
}
