package net.cycle_paintings;

import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A test double for the saved-data protocol, not a bundled copy of Fast Paintings. */
public final class FastPaintingFixture implements ModInitializer {
    static final IntegerProperty X = IntegerProperty.create("x_offset", 0, 15);
    static final IntegerProperty Y = IntegerProperty.create("y_offset", 0, 15);
    static Block BLOCK;
    static BlockEntityType<ArtEntity> TYPE;

    @Override
    public void onInitialize() {
        var id = Identifier.parse("fastpaintings:cycle_test");
        BLOCK = Registry.register(BuiltInRegistries.BLOCK, id,
                new ArtBlock(BlockBehaviour.Properties.of().noCollision().setId(ResourceKey.create(Registries.BLOCK, id))));
        TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, new BlockEntityType<>(ArtEntity::new, Set.of(BLOCK)));
    }

    static final class ArtBlock extends Block implements EntityBlock {
        ArtBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                    .setValue(X, 0).setValue(Y, 0));
        }
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BlockStateProperties.HORIZONTAL_FACING, X, Y);
        }
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ArtEntity(pos, state); }
    }

    static final class ArtEntity extends BlockEntity {
        String variant = "minecraft:kebab";
        boolean placedWithNbt = true;
        ArtEntity(BlockPos pos, BlockState state) { super(TYPE, pos, state); }
        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            output.putString("variant", variant);
            output.putBoolean("placed_with_nbt", placedWithNbt);
        }
        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            variant = input.getStringOr("variant", "");
            placedWithNbt = input.getBooleanOr("placed_with_nbt", false);
        }
    }
}
