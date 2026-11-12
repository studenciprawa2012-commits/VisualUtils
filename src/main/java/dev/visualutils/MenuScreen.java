package dev.visualutils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Menu otwierane klawiszem . - przyciski wlaczaja i wylaczaja moduly. */
public class MenuScreen extends Screen {

    private int titleY;

    public MenuScreen() {
        super(Component.literal("VisualUtils"));
    }

    private static Component label(Module m) {
        boolean on = Config.isOn(m);
        return Component.literal(m.label() + ": ")
                .append(Component.literal(on ? "WŁ." : "WYŁ.")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    protected void init() {
        int buttonWidth = 230;
        int buttonHeight = 20;
        int gap = 4;
        Module[] modules = Module.values();

        int total = modules.length * (buttonHeight + gap) + buttonHeight + 14;
        int x = this.width / 2 - buttonWidth / 2;
        int y = this.height / 2 - total / 2 + 12;
        this.titleY = y - 16;

        for (Module m : modules) {
            Button button = Button.builder(label(m), btn -> {
                        Config.toggle(m);
                        btn.setMessage(label(m));
                    })
                    .bounds(x, y, buttonWidth, buttonHeight)
                    .build();
            this.addRenderableWidget(button);
            y += buttonHeight + gap;
        }

        this.addRenderableWidget(Button.builder(Component.literal("Zamknij"), btn -> this.onClose())
                .bounds(x, y + 10, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.titleY, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }
}
