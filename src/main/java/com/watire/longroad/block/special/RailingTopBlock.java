package com.watire.longroad.block.special;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

public class RailingTopBlock extends Block implements SimpleWaterloggedBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // 为不同朝向直接定义碰撞箱（避免复杂的旋转计算）
    private static final VoxelShape SHAPE_NORTH = createNorthShape();
    private static final VoxelShape SHAPE_SOUTH = createSouthShape();
    private static final VoxelShape SHAPE_EAST = createEastShape();
    private static final VoxelShape SHAPE_WEST = createWestShape();

    public RailingTopBlock(BlockBehaviour.Properties properties) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(1.5f, 6.0f)
                .lightLevel(state -> 15)  // 亮度15
                .requiresCorrectToolForDrops());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)  // 默认朝北
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        Direction direction = state.getValue(FACING);

        switch (direction) {
            case SOUTH:
                return SHAPE_SOUTH;
            case EAST:
                return SHAPE_EAST;
            case WEST:
                return SHAPE_WEST;
            case NORTH:
            default:
                return SHAPE_NORTH;
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return getShape(state, world, pos, context);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter world, BlockPos pos) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        FluidState fluidState = context.getLevel().getFluidState(pos);

        // 获取玩家的水平朝向
        Direction facing = context.getHorizontalDirection();
        // 让方块面向玩家（通常装饰方块都这样）
        facing = facing.getOpposite();

        System.out.println("🔧 放置RAILING_TOP: 玩家水平朝向=" + context.getHorizontalDirection() +
                ", 设置朝向=" + facing);

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    // 直接为每个方向定义形状（避免旋转计算的错误）
    private static VoxelShape createNorthShape() {
        // 朝北的形状（原始形状）
        VoxelShape corePillar = Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0);
        VoxelShape leftBar = Block.box(1.0, 7.0, 6.0, 6.0, 9.0, 10.0);
        VoxelShape wideBar = Block.box(-5.0, 7.0, 5.0, 4.0, 9.0, 11.0);
        VoxelShape bottomBar = Block.box(-4.0, 6.0, 6.0, 2.0, 7.0, 10.0);

        return Shapes.or(corePillar, leftBar, wideBar, bottomBar);
    }

    private static VoxelShape createSouthShape() {
        // 朝南的形状（相对于朝北旋转180度）
        // 计算公式：16 - x
        VoxelShape corePillar = Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0);
        VoxelShape leftBar = Block.box(10.0, 7.0, 6.0, 15.0, 9.0, 10.0); // 16-6=10, 16-1=15
        VoxelShape wideBar = Block.box(12.0, 7.0, 5.0, 21.0, 9.0, 11.0); // 16-4=12, 16-(-5)=21
        VoxelShape bottomBar = Block.box(14.0, 6.0, 6.0, 20.0, 7.0, 10.0); // 16-2=14, 16-(-4)=20

        return Shapes.or(corePillar, leftBar, wideBar, bottomBar);
    }

    private static VoxelShape createEastShape() {
        // 朝东的形状（相对于朝北旋转90度）
        // 计算公式：x->z, z->16-x
        VoxelShape corePillar = Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0);
        VoxelShape leftBar = Block.box(6.0, 7.0, 1.0, 10.0, 9.0, 6.0); // z:1-6
        VoxelShape wideBar = Block.box(5.0, 7.0, -5.0, 11.0, 9.0, 4.0); // z:-5-4
        VoxelShape bottomBar = Block.box(6.0, 6.0, -4.0, 10.0, 7.0, 2.0); // z:-4-2
        return Shapes.or(corePillar, leftBar, wideBar, bottomBar);
    }

    private static VoxelShape createWestShape() {
        // 朝西的形状（相对于朝北旋转270度）
        // 计算公式：x->16-z, z->x
        VoxelShape corePillar = Block.box(6.0, 0.0, 6.0, 10.0, 8.0, 10.0);
        VoxelShape leftBar = Block.box(6.0, 7.0, 10.0, 10.0, 9.0, 15.0); // z:16-6=10, z:16-1=15
        VoxelShape wideBar = Block.box(5.0, 7.0, 12.0, 11.0, 9.0, 21.0); // z:16-4=12, z:16-(-5)=21
        VoxelShape bottomBar = Block.box(6.0, 6.0, 14.0, 10.0, 7.0, 20.0); // z:16-2=14, z:16-(-4)=20
        return Shapes.or(corePillar, leftBar, wideBar, bottomBar);
    }
}