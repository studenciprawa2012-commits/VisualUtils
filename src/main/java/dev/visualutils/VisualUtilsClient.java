package dev.visualutils;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class VisualUtilsClient implements ClientModInitializer {

    public static final String MOD_ID = "visualutils";
    private static final int ZOOM_FOV = 30;

    private static KeyMapping menuKey;
    private static Integer savedFov = null;
    private static boolean fullbrightApplied = false;
    private static int ticks = 0;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        // domyslnie klawisz . (kropka), da sie zmienic w Opcje -> Sterowanie
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.visualutils.menu",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_PERIOD,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(VisualUtilsClient::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(VisualUtilsClient::onStopping);
        HudRenderCallback.EVENT.register(Overlay::render);
    }

    private static void onTick(Minecraft mc) {
        while (menuKey.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MenuScreen());
            }
        }
        ticks++;
        applyFullbright(mc);
        applyZoom(mc);
    }

    /** Fullbright dziala jako lokalny efekt widzenia w ciemnosci (serwer o nim nie wie). */
    private static void applyFullbright(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            fullbrightApplied = false;
            return;
        }
        if (Config.isOn(Module.FULLBRIGHT)) {
            if (!fullbrightApplied || ticks % 20 == 0) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0, false, false));
                fullbrightApplied = true;
            }
        } else if (fullbrightApplied) {
            player.removeEffect(MobEffects.NIGHT_VISION);
            fullbrightApplied = false;
        }
    }

    /** Zoom zmienia FOV na 30, a po wylaczeniu przywraca poprzednia wartosc. */
    private static void applyZoom(Minecraft mc) {
        var fov = mc.options.fov();
        if (Config.isOn(Module.ZOOM)) {
            if (savedFov == null) {
                savedFov = fov.get();
            }
            if (fov.get() != ZOOM_FOV) {
                fov.set(ZOOM_FOV);
            }
        } else if (savedFov != null) {
            fov.set(savedFov);
            savedFov = null;
        }
    }

    private static void onStopping(Minecraft mc) {
        if (savedFov != null) {
            mc.options.fov().set(savedFov);
            savedFov = null;
        }
        Config.save();
    }
}
