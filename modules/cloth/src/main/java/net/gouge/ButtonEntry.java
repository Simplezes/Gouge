package net.gouge;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;

final class ButtonEntry extends AbstractConfigListEntry<Object> {

    private final Button button;

    ButtonEntry(Component label, Component text, Runnable action) {
        super(label, true);
        this.button = Button.builder(text, b -> action.run()).bounds(0, 0, 200, 20).build();
    }

    @Override
    public Object getValue() {
        return null;
    }

    @Override
    public Optional<Object> getDefaultValue() {
        return Optional.empty();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(button);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(button);
    }

    @Override
    public void render(GuiGraphics g, int index, int y, int x, int entryWidth, int entryHeight,
                                   int mouseX, int mouseY, boolean hovered, float delta) {
        super.render(g, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, delta);
        button.setWidth(Math.min(240, entryWidth - 20));
        button.setX(x + (entryWidth - button.getWidth()) / 2);
        button.setY(y + 2);
        button.render(g, mouseX, mouseY, delta);
    }
}
