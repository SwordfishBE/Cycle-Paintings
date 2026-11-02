package net.cycle_paintings;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/** No Cloth imports: verifies optional dependency isolation on a real client. */
public final class MissingClothClientTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cloth-config"))
                throw new AssertionError("This test must run without Cloth Config");
            var parent = new TitleScreen();
            var screen = new CyclePaintingsModMenu().getModConfigScreenFactory().create(parent);
            if (!(screen instanceof AlertScreen)) throw new AssertionError("Missing Cloth Config must show a notice");
            client.setScreenAndShow(screen);
        });
        context.waitTicks(3);
        context.takeScreenshot("cycle-paintings-missing-cloth");
        context.setScreen(TitleScreen::new);
    }
}
