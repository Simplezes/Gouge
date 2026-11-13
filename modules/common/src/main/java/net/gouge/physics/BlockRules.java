package net.gouge.physics;

import net.gouge.config.GougeConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.state.BlockState;

public final class BlockRules {

    private BlockRules() {
    }

    public static double blockGripMultiplier(BlockState state) {
        var map = GougeConfig.INSTANCE.block_grip;
        if (map.isEmpty()) {
            return 1.0;
        }
        Double direct = map.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (direct != null) {
            return direct;
        }
        for (var e : map.entrySet()) {
            if (e.getKey().startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(e.getKey().substring(1));
                if (tag != null && state.is(TagKey.create(Registries.BLOCK, tag))) {
                    return e.getValue();
                }
            }
        }
        return 1.0;
    }

    public static boolean isHardBlock(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String override = GougeConfig.INSTANCE.block_overrides.get(id.toString());
        if (override != null) {
            return override.equals("hard");
        }
        if (GougeConfig.INSTANCE.mechanics.cling_everything) {
            return true;
        }
        if (!id.getNamespace().equals("minecraft")) {
            return true;
        }
        return state.requiresCorrectToolForDrops();
    }
}
