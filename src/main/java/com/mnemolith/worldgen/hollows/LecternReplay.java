package com.mnemolith.worldgen.hollows;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A lectern reads an imprint slip aloud: right-click any lectern holding a slip and its memory plays as a flicker in
 * front of the lectern, to everyone near, and the player hears what the lectern's chunk still holds. The slip is not
 * used up and the figure cannot be caught. One reading per lectern every {@link #COOLDOWN} ticks.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class LecternReplay {
    public static final int COOLDOWN = 100;
    public static final Identifier ADVANCEMENT = Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "lectern_replay");
    private static final Map<Long, Long> NEXT = new HashMap<>();

    private LecternReplay() {}

    public enum Result { NONE, PLAYED, COOLDOWN }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !ImprintSlips.isSlip(event.getItemStack())) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(Blocks.LECTERN)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            use(level, event.getPos(), player, event.getItemStack());
        }
    }

    /** The server half of a reading. */
    public static Result use(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack slip) {
        ImprintCast cast = slip.get(ModDataComponents.IMPRINT_CAST.get());
        BlockState state = level.getBlockState(pos);
        if (cast == null || !state.is(Blocks.LECTERN)) {
            return Result.NONE;
        }
        long now = level.getGameTime();
        Long next = NEXT.get(pos.asLong());
        if (next != null && now < next) {
            player.sendSystemMessage(Component.translatable("mnemolith.message.lectern_wait"), true);
            return Result.COOLDOWN;
        }
        if (NEXT.size() > 512) {
            NEXT.values().removeIf(until -> until < now);
        }
        NEXT.put(pos.asLong(), now + COOLDOWN);
        Direction facing = state.getValue(LecternBlock.FACING);
        BlockPos spot = pos.relative(facing);
        // the figure stands in front of the lectern and faces it, as a reader would
        HollowFlickers.replay(level, spot, facing.getOpposite().toYRot(), cast.tag());
        level.playSound(null, pos, ModSounds.LECTERN_REPLAY.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
        player.sendSystemMessage(Component.translatable("mnemolith.message.lectern_read",
                Component.translatable(cast.tag().translationKey()), holdings(LoadedChunkMemory.existing(level.getChunkAt(pos)))), true);
        var advancement = level.getServer().getAdvancements().get(ADVANCEMENT);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "read");
        }
        return Result.PLAYED;
    }

    /** "path ×2, trade" or "nothing", for the chunk's imprints. */
    public static Component holdings(ChunkMemory memory) {
        Map<ImprintTag, Integer> counts = new EnumMap<>(ImprintTag.class);
        if (memory != null) {
            for (int i = 0; i < memory.imprintCount(); i++) {
                counts.merge(memory.imprintAt(i).tag(), 1, Integer::sum);
            }
        }
        if (counts.isEmpty()) {
            return Component.translatable("mnemolith.message.lectern_nothing");
        }
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<ImprintTag, Integer> entry : counts.entrySet()) {
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Component.translatable(entry.getKey().translationKey()));
            if (entry.getValue() > 1) {
                out.append(" ×" + entry.getValue());
            }
        }
        return out;
    }

    public static void clear() {
        NEXT.clear();
    }
}
