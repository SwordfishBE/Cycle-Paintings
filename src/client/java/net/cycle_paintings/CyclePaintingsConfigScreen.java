package net.cycle_paintings;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CyclePaintingsConfigScreen {
    private CyclePaintingsConfigScreen() {}

    static Screen create(Screen parent) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null && !minecraft.hasSingleplayerServer()) {
            return CyclePaintingsModMenu.notice(parent, "Multiplayer uses the server configuration. Edit the server file and run /cycle_paintings reload on the server.");
        }
        final ModConfig initial;
        try {
            initial = CyclePaintings.configForEditing();
        } catch (java.io.IOException | RuntimeException exception) {
            return CyclePaintingsModMenu.notice(parent, "Cannot open configuration. Existing file preserved; check the log.");
        }
        String[] ignored = {initial.ignorePaintingsInCycleIdentifier()};
        boolean[] debug = {initial.showRegisteredPaintingsDebug()};
        var builder = ConfigBuilder.create().setParentScreen(parent).setTitle(Component.literal("Cycle Paintings Config"));
        var category = builder.getOrCreateCategory(Component.literal("General"));
        category.addEntry(builder.entryBuilder().startStrField(Component.literal("Excluded paintings"), ignored[0])
                .setDefaultValue(ModConfig.DEFAULT.ignorePaintingsInCycleIdentifier())
                .setTooltip(Component.literal("Comma-separated namespaces or painting IDs, e.g. examplemod,minecraft:kebab."),
                        Component.literal("Applies immediately in singleplayer. Empty means no exclusions."))
                .setSaveConsumer(value -> ignored[0] = value).build());
        category.addEntry(builder.entryBuilder().startBooleanToggle(Component.literal("Show registered paintings in debug log"), debug[0])
                .setDefaultValue(false)
                .setTooltip(Component.literal("Lists allowed and ignored painting IDs when the cycle is rebuilt."),
                        Component.literal("Requires DEBUG logging for cycle_paintings; does not add INFO logging."))
                .setSaveConsumer(value -> debug[0] = value).build());
        builder.setSavingRunnable(() -> {
            var server = minecraft.getSingleplayerServer();
            if (minecraft.getConnection() != null && server == null) return;
            var edited = new ModConfig(ignored[0], debug[0]);
            if (server != null) {
                server.execute(() -> {
                    var message = CyclePaintings.saveEditedConfig(edited, server);
                    minecraft.execute(() -> minecraft.setScreenAndShow(CyclePaintingsModMenu.notice(parent, message)));
                });
            } else {
                var message = CyclePaintings.saveEditedConfig(edited, null);
                // Run after Cloth has closed the editor, otherwise it would hide the feedback.
                minecraft.schedule(() -> minecraft.setScreenAndShow(CyclePaintingsModMenu.notice(parent, message)));
            }
        });
        return builder.build();
    }
}
