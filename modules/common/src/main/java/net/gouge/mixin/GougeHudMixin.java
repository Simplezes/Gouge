package net.gouge.mixin;

import net.gouge.client.GripHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GougeHudMixin {

    @Inject(method = "renderCrosshair", at = @At("TAIL"))
    private void gouge$gripArcs(GuiGraphics graphics, CallbackInfo ci) {
        GripHud.render(graphics, net.minecraft.client.Minecraft.getInstance().getFrameTime());
    }
}
