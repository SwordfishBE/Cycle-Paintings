package net.cycle_paintings;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keep Cloth types out of this class so the missing-dependency notice can always load. */
public final class CyclePaintingsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return parent -> notice(parent, "Install Cloth Config to edit the configuration here. You can also edit config/cycle_paintings.json and use /cycle_paintings reload.");
        }
        if (!hasCompatibleClothConfig()) {
            return parent -> notice(parent, "This configuration menu requires Cloth Config for Minecraft 26.4. The installed older version cannot render it safely. Until it is updated, edit config/cycle_paintings.json and use /cycle_paintings reload.");
        }
        return CyclePaintingsConfigScreen::create;
    }

    static boolean hasCompatibleClothConfig() {
        var cloth = FabricLoader.getInstance().getModContainer("cloth-config");
        if (cloth.isEmpty()) return false;
        try {
            return net.fabricmc.loader.api.metadata.version.VersionPredicate.parse(">=26.4-")
                    .test(cloth.get().getMetadata().getVersion());
        } catch (net.fabricmc.loader.api.VersionParsingException exception) {
            return false;
        }
    }

    static Screen notice(Screen parent, String text) {
        return notice(parent, Component.literal("[Cycle Paintings] " + text));
    }

    static Screen notice(Screen parent, Component message) {
        return new AlertScreen(() -> Minecraft.getInstance().setScreenAndShow(parent),
                Component.literal("Cycle Paintings"), message);
    }
}
