package net.cycle_paintings;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class CyclePaintingsGameTests {
    @GameTest
    public void supportedMinecraftVersionBoundaries(GameTestHelper h) throws Exception {
        var metadata = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cycle_paintings")
                .orElseThrow().getMetadata();
        var dependency = metadata.getDependencies().stream()
                .filter(value -> value.getKind() == net.fabricmc.loader.api.metadata.ModDependency.Kind.DEPENDS
                        && value.getModId().equals("minecraft")).findFirst().orElseThrow();
        for (String raw : List.of("26.4-snapshot-6", "26.4-snapshot-7", "26.4-snapshot-10", "26.4-pre-1", "26.4-rc-1", "26.4")) {
            String normalized = net.fabricmc.loader.impl.game.minecraft.McVersionLookup.normalizeVersion(raw,
                    net.fabricmc.loader.impl.game.minecraft.McVersionLookup.getRelease(raw));
            h.assertTrue(dependency.matches(net.fabricmc.loader.api.Version.parse(normalized)), "Must allow " + raw);
        }
        for (String raw : List.of("26.3", "26.4-snapshot-1", "26.4-snapshot-5", "26.4.1-pre-1", "26.4.1", "26.5-snapshot-1", "26.5", "27.1")) {
            String normalized = net.fabricmc.loader.impl.game.minecraft.McVersionLookup.normalizeVersion(raw,
                    net.fabricmc.loader.impl.game.minecraft.McVersionLookup.getRelease(raw));
            h.assertFalse(dependency.matches(net.fabricmc.loader.api.Version.parse(normalized)), "Must reject " + raw);
        }
        h.succeed();
    }

    private List<Holder<PaintingVariant>> group(GameTestHelper h) {
        List<Holder<PaintingVariant>> result = new ArrayList<>();
        for (var holder : h.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT)
                .getTagOrEmpty(PaintingVariantTags.PLACEABLE)) {
            if (holder.value().width() == 1 && holder.value().height() == 1) result.add(holder);
        }
        h.assertTrue(result.size() > 2, "Need several 1x1 paintings");
        return result;
    }

    @GameTest
    public void forwardReverseAndWrap(GameTestHelper h) {
        var group = group(h);
        var cycle = new PaintingCycle(h.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT), ModConfig.DEFAULT);
        for (int i = 0; i < group.size(); i++) {
            h.assertTrue(cycle.next(group.get(i), false).equals(group.get((i + 1) % group.size())), "Forward and wrap");
            h.assertTrue(cycle.next(group.get(i), true).equals(group.get(Math.floorMod(i - 1, group.size()))), "Reverse and wrap");
        }
        h.succeed();
    }

    @GameTest
    public void everyPlaceablePaintingKeepsItsSize(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
        var cycle = new PaintingCycle(registry, new ModConfig("", false));
        for (var current : registry.getTagOrEmpty(PaintingVariantTags.PLACEABLE)) {
            var next = cycle.next(current, false);
            h.assertTrue(next != null && next.value().width() == current.value().width()
                    && next.value().height() == current.value().height(), "All sizes remain unchanged");
            h.assertTrue(cycle.next(next, true).equals(current), "Reverse undoes forward for every size");
        }
        h.succeed();
    }

    @GameTest
    public void realEntityCallbackPreservesPaintingAndItems(GameTestHelper h) {
        var group = group(h);
        var pos = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlock(pos.relative(Direction.SOUTH), Blocks.STONE.defaultBlockState(), 3);
        var painting = new Painting(h.getLevel(), pos, Direction.NORTH, group.getFirst());
        h.getLevel().addFreshEntity(painting);
        var player = h.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAINTING, 5));
        var bounds = painting.getBoundingBox();
        var uuid = painting.getUUID();
        var result = UseEntityCallback.EVENT.invoker().interact(player, h.getLevel(), InteractionHand.MAIN_HAND,
                painting, new EntityHitResult(painting));
        h.assertTrue(result == InteractionResult.SUCCESS, "Registered event consumes interaction");
        h.assertTrue(painting.getVariant().equals(group.get(1)), "Actual entity variant changes");
        h.assertTrue(painting.getBoundingBox().equals(bounds) && painting.getUUID().equals(uuid)
                && painting.getPos().equals(pos) && painting.getDirection() == Direction.NORTH, "Entity, position and bounds preserved");
        h.assertTrue(player.getMainHandItem().getCount() == 5, "No painting consumed");
        player.setShiftKeyDown(true);
        UseEntityCallback.EVENT.invoker().interact(player, h.getLevel(), InteractionHand.MAIN_HAND,
                painting, new EntityHitResult(painting));
        h.assertTrue(painting.getVariant().equals(group.getFirst()), "Sneaking restores previous art");
        h.assertTrue(painting.survives(), "Changed painting remains attached to wall");
        h.succeed();
    }

    @GameTest
    public void handsAndGameModes(GameTestHelper h) {
        var group = group(h);
        var painting = new Painting(h.getLevel(), h.absolutePos(new BlockPos(1, 2, 1)), Direction.NORTH, group.getFirst());
        var survival = h.makeMockServerPlayer(GameType.SURVIVAL);
        h.assertTrue(CyclePaintings.cycleEntity(survival, h.getLevel(), InteractionHand.MAIN_HAND, painting)
                == InteractionResult.PASS, "Empty hand ignored");
        survival.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.PAINTING));
        h.assertTrue(CyclePaintings.cycleEntity(survival, h.getLevel(), InteractionHand.OFF_HAND, painting)
                == InteractionResult.SUCCESS, "Offhand supported");
        for (var mode : List.of(GameType.SPECTATOR, GameType.ADVENTURE)) {
            var player = h.makeMockServerPlayer(mode);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAINTING));
            h.assertTrue(CyclePaintings.cycleEntity(player, h.getLevel(), InteractionHand.MAIN_HAND, painting)
                    == InteractionResult.PASS, "Cannot bypass game mode " + mode);
        }
        h.succeed();
    }

    @GameTest
    public void exclusionsAndEmptyLists(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
        var group = group(h);
        String excluded = group.get(1).unwrapKey().orElseThrow().identifier().toString();
        var cycle = new PaintingCycle(registry, new ModConfig(" , " + excluded.toUpperCase(java.util.Locale.ROOT) + ", ", false));
        h.assertTrue(cycle.next(group.getFirst(), false).equals(group.get(2)), "Excluded art skipped; whitespace and case accepted");
        h.assertTrue(cycle.next(group.get(1), false) == null, "Excluded current art left alone");
        var none = new PaintingCycle(registry, new ModConfig("minecraft", false));
        h.assertTrue(none.next(group.getFirst(), false) == null, "Whole namespace excluded");
        h.assertTrue(ModConfig.excludes(new ModConfig("examplemod", false).exclusions(), Identifier.parse("examplemod:art")), "Modded namespace excluded");
        h.assertFalse(ModConfig.excludes(new ModConfig("examplemod", false).exclusions(), Identifier.parse("otherexamplemod:art")), "Namespace matching is exact");
        h.succeed();
    }

    @GameTest
    public void customDatapackArtAndNonPlaceableArt(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
        var custom = registry.get(Identifier.parse("cycle_paintings-test:test_art")).orElseThrow();
        var hidden = registry.get(Identifier.parse("cycle_paintings-test:hidden_art")).orElseThrow();
        var cycle = new PaintingCycle(registry, ModConfig.DEFAULT);
        var previous = cycle.next(custom, true);
        h.assertTrue(previous != null && cycle.next(previous, false).equals(custom), "Custom art participates in normal cycle");
        h.assertTrue(cycle.next(hidden, false) == null, "Registered but non-placeable art is excluded");
        String ignored = group(h).stream().filter(art -> !art.equals(custom))
                .map(art -> art.unwrapKey().orElseThrow().identifier().toString()).collect(java.util.stream.Collectors.joining(","));
        var single = new PaintingCycle(registry, new ModConfig(ignored, false));
        h.assertTrue(single.next(custom, false).equals(custom) && single.next(custom, true).equals(custom), "Single allowed artwork remains stable");
        h.succeed();
    }

    @GameTest
    public void fastPaintingsMasterAndOffsetProtocol(GameTestHelper h) {
        var group = group(h);
        var player = h.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAINTING, 3));
        for (var facing : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            var master = h.absolutePos(new BlockPos(1, 3, 1));
            var part = master.below().relative(facing.getCounterClockWise());
            var state = FastPaintingFixture.BLOCK.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            h.getLevel().setBlock(master, state, 3);
            h.getLevel().setBlock(part, state.setValue(FastPaintingFixture.X, 1).setValue(FastPaintingFixture.Y, 1), 3);
            var entity = (FastPaintingFixture.ArtEntity) h.getLevel().getBlockEntity(master);
            entity.variant = group.getFirst().unwrapKey().orElseThrow().identifier().toString();
            var hit = new BlockHitResult(Vec3.atCenterOf(part), facing, part, false);
            var result = UseBlockCallback.EVENT.invoker().interact(player, h.getLevel(), InteractionHand.MAIN_HAND, hit);
            h.assertTrue(result == InteractionResult.SUCCESS, "Fast Paintings part interaction handled for " + facing);
            h.assertTrue(entity.variant.equals(group.get(1).unwrapKey().orElseThrow().identifier().toString()), "Master art changed");
            h.assertTrue(entity.placedWithNbt && h.getLevel().getBlockEntity(master) == entity, "Other data and original block entity preserved");
            player.setShiftKeyDown(true);
            UseBlockCallback.EVENT.invoker().interact(player, h.getLevel(), InteractionHand.MAIN_HAND, hit);
            player.setShiftKeyDown(false);
            h.assertTrue(entity.variant.equals(group.getFirst().unwrapKey().orElseThrow().identifier().toString()), "Reverse restores master art");
            h.getLevel().removeBlock(part, false);
            h.getLevel().removeBlock(master, false);
        }
        h.assertTrue(player.getMainHandItem().getCount() == 3, "Compatibility does not consume paintings");
        h.succeed();
    }

    @GameTest
    public void reloadCommandPermissionsFeedbackAndFailure(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var messages = new ArrayList<String>();
        var output = new net.minecraft.commands.CommandSource() {
            public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        var source = server.createCommandSourceStack().withSource(output)
                .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER);
        try {
            dispatcher.execute("cycle_paintings reload", source.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.ALL));
            h.fail("Non-operators must not be allowed to reload");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            // Permission failures should prevent command execution entirely.
        }
        var path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("cycle_paintings.json");
        byte[] original = Files.readAllBytes(path);
        try {
            Files.writeString(path, "{\"ignorePaintingsInCycleIdentifier\":\"minecraft,cycle_paintings-test\"}");
            h.assertTrue(dispatcher.execute("cycle_paintings reload", source) == 1, "Operator reload succeeds");
            h.assertTrue(messages.getLast().equals("[Cycle Paintings] Configuration reloaded."), "Success feedback has required prefix");
            h.assertTrue(CyclePaintings.cycle(h.getLevel()).next(group(h).getFirst(), false) == null, "Changed exclusions take effect immediately");
            var active = CyclePaintings.cycle(h.getLevel());
            Files.writeString(path, "{ invalid");
            h.assertTrue(dispatcher.execute("cycle_paintings reload", source) == 0, "Invalid config returns failure");
            h.assertTrue(messages.getLast().startsWith("[Cycle Paintings] Reload failed."), "Failure feedback has required prefix");
            h.assertTrue(CyclePaintings.cycle(h.getLevel()) == active, "Failed reload keeps previous settings");
            h.assertTrue(Files.readString(path).equals("{ invalid"), "Failed reload preserves file");
        } finally {
            Files.write(path, original);
            dispatcher.execute("cycle_paintings reload", source);
        }
        h.succeed();
    }

    @GameTest
    public void configCreationAndPreservation(GameTestHelper h) throws Exception {
        var dir = Files.createTempDirectory("cycle-paintings-test-");
        var path = dir.resolve("config.json");
        try {
            h.assertTrue(ModConfig.load(path).equals(ModConfig.DEFAULT), "Default config created");
            Files.writeString(path, "{\"ignorePaintingsInCycleResourceLocation\":\"minecraft:kebab\",\"showRegisteredPaintingsDebug\":true,\"custom\":42}");
            String original = Files.readString(path);
            var config = ModConfig.load(path);
            h.assertTrue(config.ignorePaintingsInCycleIdentifier().equals("minecraft:kebab") && config.showRegisteredPaintingsDebug(), "Legacy option loaded");
            h.assertTrue(Files.readString(path).equals(original), "Existing options and unknown keys preserved");
            Files.writeString(path, "{ invalid");
            h.assertTrue(ModConfig.load(path).equals(ModConfig.DEFAULT), "Invalid config falls back safely");
            h.assertTrue(Files.readString(path).equals("{ invalid"), "Invalid file preserved");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(dir);
        }
        h.succeed();
    }
}
