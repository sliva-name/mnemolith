package com.mnemolith.content.block;

import java.util.Optional;

import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The hush loom: threaded with hush filament, it keeps one imprint tag of its chunk. Hushlings cannot thin that tag
 * and ember braziers cannot burn it; writes are not affected. Using a filament on it threads it with the tag of the
 * loudest imprint of the chunk (one filament each time); an empty chunk takes nothing. The tag lives in the block
 * state ({@link #KEPT}, ordinal + 1, 0 = unthreaded) and in the chunk memory's loom marks. See
 * {@code docs/design/pale-chorus.md}, stage 2.
 */
public class HushLoomBlock extends Block {
    public static final MapCodec<HushLoomBlock> CODEC = simpleCodec(HushLoomBlock::new);
    public static final IntegerProperty KEPT = IntegerProperty.create("kept", 0, ImprintTag.values().length);
    public static final BooleanProperty THREADED = BooleanProperty.create("threaded");

    public HushLoomBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KEPT, 0).setValue(THREADED, false));
    }

    @Override
    public MapCodec<HushLoomBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KEPT, THREADED);
    }

    /** The tag this loom keeps, if threaded. */
    public static Optional<ImprintTag> kept(BlockState state) {
        if (!(state.getBlock() instanceof HushLoomBlock) || state.getValue(KEPT) == 0) {
            return Optional.empty();
        }
        return Optional.of(ImprintTag.values()[state.getValue(KEPT) - 1]);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!stack.is(ModItems.HUSH_FILAMENT.get())) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        Optional<ImprintTag> tag = thread(server, pos);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(tag
                    .map(t -> Component.translatable("message.mnemolith.hush_loom.keeps", Component.translatable(t.translationKey())))
                    .orElse(Component.translatable("message.mnemolith.hush_loom.empty")));
        }
        if (tag.isEmpty()) {
            return InteractionResult.FAIL;
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(kept(state)
                    .map(t -> Component.translatable("message.mnemolith.hush_loom.keeps", Component.translatable(t.translationKey())))
                    .orElse(Component.translatable("message.mnemolith.hush_loom.unthreaded")));
        }
        return InteractionResult.SUCCESS;
    }

    /** Threads the loom with the tag of the loudest imprint of its chunk; empty (nothing changes) when the chunk holds none. */
    public static Optional<ImprintTag> thread(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HushLoomBlock)) {
            return Optional.empty();
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        Optional<ImprintTag> loudest = memory == null ? Optional.empty() : memory.highest().map(Imprint::tag);
        if (loudest.isEmpty()) {
            return Optional.empty();
        }
        return keep(level, pos, loudest.get()) ? loudest : Optional.empty();
    }

    /** Sets the kept tag directly (threading, and QA). */
    public static boolean keep(ServerLevel level, BlockPos pos, ImprintTag tag) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HushLoomBlock)) {
            return false;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        if (!LoadedChunkMemory.getOrCreate(chunk).setLoom(pos.immutable(), tag)) {
            return false;
        }
        chunk.markUnsaved();
        level.setBlock(pos, state.setValue(KEPT, tag.ordinal() + 1).setValue(THREADED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.6F);
        level.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, 12, 0.3D, 0.2D, 0.3D, 0.01D);
        return true;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (level instanceof ServerLevel server && !oldState.is(this)) {
            kept(state).ifPresent(tag -> {
                LevelChunk chunk = server.getChunkAt(pos);
                if (LoadedChunkMemory.getOrCreate(chunk).setLoom(pos.immutable(), tag)) {
                    chunk.markUnsaved();
                }
            });
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkMemory memory = LoadedChunkMemory.existing(chunk);
        if (memory != null && memory.removeLoom(pos)) {
            chunk.markUnsaved();
        }
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }
}
