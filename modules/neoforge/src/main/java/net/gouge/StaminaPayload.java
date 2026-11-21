package net.gouge;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record StaminaPayload(float level) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<StaminaPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Gouge.MOD_ID, "stamina"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StaminaPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, StaminaPayload::level, StaminaPayload::new);

    @Override
    public CustomPacketPayload.Type<StaminaPayload> type() {
        return TYPE;
    }
}
