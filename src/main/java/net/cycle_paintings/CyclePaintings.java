package net.cycle_paintings;

import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CyclePaintings implements ModInitializer {
    public static final String MOD_ID = "cycle_paintings";
    static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<MinecraftServer, PaintingCycle> CYCLES = new IdentityHashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(CyclePaintings::reload);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) reload(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(CYCLES::remove);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> cycleEntity(player, world, hand, entity));
        UseBlockCallback.EVENT.register(FastPaintingsCompat::interact);
        String version = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        LOGGER.info("[Cycle Paintings] Mod initialized. Version: {}", version);
    }

    private static void reload(MinecraftServer server) {
        var config = ModConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
        CYCLES.put(server, new PaintingCycle(server.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT), config));
    }

    static PaintingCycle cycle(Level level) { return CYCLES.get(level.getServer()); }

    static boolean canCycle(Player player, InteractionHand hand) {
        return !player.isSpectator() && player.mayBuild() && player.getItemInHand(hand).is(Items.PAINTING);
    }

    static InteractionResult cycleEntity(Player player, Level level, InteractionHand hand, Entity entity) {
        if (!canCycle(player, hand) || !(entity instanceof Painting painting)) return InteractionResult.PASS;
        // Vanilla clients already send the interaction packet. All changes belong to the server.
        if (level.isClientSide()) return InteractionResult.PASS;
        var cycle = cycle(level);
        if (cycle == null || painting.isRemoved()) return InteractionResult.PASS;
        var next = cycle.next(painting.getVariant(), player.isShiftKeyDown());
        if (next == null) return InteractionResult.PASS;
        painting.setComponent(DataComponents.PAINTING_VARIANT, next);
        return InteractionResult.SUCCESS;
    }
}
