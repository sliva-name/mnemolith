package com.mnemolith.content.block;

import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The ember brazier: fed memory embers, it burns away the loudest imprint of its chunk every {@link #burnPeriod()}
 * ticks and writes nothing. One ember is {@link #chargePerEmber()} burns; it holds up to {@link #MAX_CHARGE}. A burn
 * that finds the chunk empty spends nothing. Feeding one awards the {@link #ADVANCEMENT}. See
 * {@code docs/design/embers.md}, stages 2 and 3.
 */
public class EmberBrazierBlock extends Block {
    public static final MapCodec<EmberBrazierBlock> CODEC = simpleCodec(EmberBrazierBlock::new);
    /** Defaults of the server options {@code emberBrazierChargePerEmber} and {@code emberBrazierBurnTicks}. */
    public static final int CHARGE_PER_EMBER = 4;
    public static final int MAX_CHARGE = 16;
    public static final int BURN_PERIOD = 100;
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, MAX_CHARGE);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final net.minecraft.resources.Identifier ADVANCEMENT = net.minecraft.resources.Identifier.fromNamespaceAndPath(
            com.mnemolith.Mnemolith.MOD_ID, "ember_brazier_kindled");

    /** What the last burn did, for QA: "burned", "empty", "cold". */
    public enum Burn { BURNED, EMPTY, COLD }

    public EmberBrazierBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(CHARGE, 0).setValue(LIT, false));
    }

    @Override
    public MapCodec<EmberBrazierBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE, LIT);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(ModItems.MEMORY_EMBER.get())) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (!feed(server, pos, state)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.mnemolith.ember_brazier.full"));
            }
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            var advancement = server.getServer().getAdvancements().get(ADVANCEMENT);
            if (advancement != null) {
                serverPlayer.getAdvancements().award(advancement, "kindled");
            }
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    public static int chargePerEmber() {
        return com.mnemolith.config.ServerConfig.EMBER_BRAZIER_CHARGE_PER_EMBER.get();
    }

    public static int burnPeriod() {
        return com.mnemolith.config.ServerConfig.EMBER_BRAZIER_BURN_TICKS.get();
    }

    /** Adds one ember's worth of charge; false (nothing changes) when it would overflow. */
    public static boolean feed(ServerLevel level, BlockPos pos, BlockState state) {
        int charge = state.getValue(CHARGE);
        int add = chargePerEmber();
        if (charge + add > MAX_CHARGE) {
            return false;
        }
        level.setBlock(pos, state.setValue(CHARGE, charge + add).setValue(LIT, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 1.2F);
        if (!level.getBlockTicks().hasScheduledTick(pos, state.getBlock())) {
            level.scheduleTick(pos, state.getBlock(), burnPeriod());
        }
        return true;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (burn(level, pos) != Burn.COLD && level.getBlockState(pos).getValue(LIT)) {
            level.scheduleTick(pos, this, burnPeriod());
        }
    }

    /** One burn: takes the loudest imprint of the chunk and spends a charge; an empty chunk spends nothing. */
    public static Burn burn(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof EmberBrazierBlock) || state.getValue(CHARGE) <= 0) {
            if (state.getBlock() instanceof EmberBrazierBlock && state.getValue(LIT)) {
                level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            }
            return Burn.COLD;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory == null || memory.removeHighestUnkept().isEmpty()) {
            return Burn.EMPTY;
        }
        MemoryPressure.recompute(chunk, memory);
        int charge = state.getValue(CHARGE) - 1;
        level.setBlock(pos, state.setValue(CHARGE, charge).setValue(LIT, charge > 0), Block.UPDATE_ALL);
        // the memory part is the shared erased cue; a little soul fire says what burned it
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 3, 0.2D, 0.3D, 0.2D, 0.02D);
        com.mnemolith.particle.MemoryFeel.cue(level, com.mnemolith.particle.MemoryFeel.Kind.ERASED, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 6);
        return Burn.BURNED;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3D + random.nextDouble() * 0.4D, pos.getY() + 1.02D,
                pos.getZ() + 0.3D + random.nextDouble() * 0.4D, 0.0D, 0.02D, 0.0D);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 0.0D, 0.04D, 0.0D);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, net.minecraft.core.Direction direction) {
        return Math.min(15, state.getValue(CHARGE));
    }
}
