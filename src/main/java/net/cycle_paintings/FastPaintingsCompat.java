package net.cycle_paintings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;

/** Optional interoperability through Fast Paintings' block state and saved variant identifier. */
final class FastPaintingsCompat {
    private FastPaintingsCompat() {}

    static InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!CyclePaintings.canCycle(player, hand)) return InteractionResult.PASS;
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!state.getBlock().builtInRegistryHolder().key().identifier().getNamespace().equals("fastpaintings")
                || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.PASS;
        var cycle = CyclePaintings.cycle(level);
        if (cycle == null) return InteractionResult.PASS;
        var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos master = hit.getBlockPos().above(offset(state, "y_offset"))
                .relative(facing.getClockWise(), offset(state, "x_offset"));
        if (!level.hasChunkAt(master)) return InteractionResult.PASS;
        var blockEntity = level.getBlockEntity(master);
        if (blockEntity == null || blockEntity.getBlockState().getBlock() != state.getBlock())
            return InteractionResult.PASS;
        var tag = blockEntity.saveWithoutMetadata(level.registryAccess());
        Identifier id = Identifier.tryParse(tag.getStringOr("variant", ""));
        if (id == null) return InteractionResult.PASS;
        var registry = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
        var current = registry.get(id);
        if (current.isEmpty()) return InteractionResult.PASS;
        var next = cycle.next(current.get(), player.isShiftKeyDown());
        if (next == null || next.unwrapKey().isEmpty()) return InteractionResult.PASS;
        // Same dimensions: the existing multiblock remains valid. Preserve all other saved data,
        // including placed_with_nbt, and synchronize the master block entity to tracking clients.
        tag.putString("variant", next.unwrapKey().orElseThrow().identifier().toString());
        blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        blockEntity.setChanged();
        var masterState = blockEntity.getBlockState();
        level.sendBlockUpdated(master, masterState, masterState, 3);
        return InteractionResult.SUCCESS;
    }

    private static int offset(BlockState state, String name) {
        var property = state.getBlock().getStateDefinition().getProperty(name);
        return property instanceof IntegerProperty integer ? state.getValue(integer) : 0;
    }
}
