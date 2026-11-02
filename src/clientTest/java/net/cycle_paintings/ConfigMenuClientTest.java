package net.cycle_paintings;

import java.nio.file.Files;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;

public final class ConfigMenuClientTest implements FabricClientGameTest {
    private ClothConfigScreen editor;

    private void open(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = new CyclePaintingsModMenu().getModConfigScreenFactory().create(new TitleScreen());
            if (!CyclePaintingsModMenu.hasCompatibleClothConfig()) {
                if (!(screen instanceof AlertScreen)) throw new AssertionError("Old Cloth Config must show a safe notice");
                client.setScreenAndShow(screen);
                // Construct prepared entries but never render known-incompatible 26.3 widgets.
                editor = (ClothConfigScreen) CyclePaintingsConfigScreen.create(new TitleScreen());
            } else {
                if (!(screen instanceof ClothConfigScreen cloth)) throw new AssertionError("Expected Cloth screen");
                editor = cloth;
                client.setScreenAndShow(editor);
            }
        });
        context.waitTicks(3);
    }

    private StringListEntry exclusions() {
        return (StringListEntry) editor.getCategorizedEntries().values().iterator().next().get(0);
    }

    private BooleanListEntry debug() {
        return (BooleanListEntry) editor.getCategorizedEntries().values().iterator().next().get(1);
    }

    private void save(ClientGameTestContext context, String ignored, boolean showDebug) {
        open(context);
        context.runOnClient(client -> {
            exclusions().setValue(ignored);
            if (debug().getValue() != showDebug) {
                ((net.minecraft.client.gui.components.Button) debug().children().getFirst()).onPress(null);
            }
            editor.saveAll(true);
        });
        context.waitTicks(5);
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cycle_paintings.json");
        try {
            boolean existed = Files.exists(path);
            byte[] original = existed ? Files.readAllBytes(path) : null;
            try {
                Files.createDirectories(path.toAbsolutePath().getParent());
                Files.writeString(path, "{\"ignorePaintingsInCycleIdentifier\":\"\",\"showRegisteredPaintingsDebug\":false,\"futureOption\":73}");
                open(context);
                context.takeScreenshot("cycle-paintings-cloth-notice-or-menu");
                context.runOnClient(client -> exclusions().setValue("cancelled_edit"));
                context.setScreen(TitleScreen::new);
                if (CyclePaintings.configForEditing().ignorePaintingsInCycleIdentifier().equals("cancelled_edit"))
                    throw new AssertionError("Closing without saving must discard the draft");
                save(context, "minecraft,cycle_paintings-test", true);
                if (!CyclePaintings.configForEditing().showRegisteredPaintingsDebug())
                    throw new AssertionError("Debug option must save");
                if (!Files.readString(path).contains("futureOption")) throw new AssertionError("Unknown config options must survive");
                context.setScreen(TitleScreen::new);
                try (var world = context.worldBuilder().create()) {
                    world.getServer().runOnServer(server -> {
                        var registry = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT);
                        var art = registry.get(net.minecraft.resources.Identifier.parse("minecraft:kebab")).orElseThrow();
                        if (CyclePaintings.cycle(server.overworld()).next(art, false) != null)
                            throw new AssertionError("Title-screen exclusions must apply when world opens");
                    });
                    save(context, "", false);
                    world.getServer().runOnServer(server -> {
                        var registry = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT);
                        var art = registry.get(net.minecraft.resources.Identifier.parse("minecraft:kebab")).orElseThrow();
                        if (CyclePaintings.cycle(server.overworld()).next(art, false) == null)
                            throw new AssertionError("In-world GUI save must update live cycle immediately");
                    });
                }
            } finally {
                if (existed) Files.write(path, original);
                else Files.deleteIfExists(path);
            }
        } catch (java.io.IOException exception) {
            throw new RuntimeException(exception);
        }
    }
}
