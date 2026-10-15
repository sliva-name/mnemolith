package com.mnemolith.worldgen.hollows;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.audio.ModSounds;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Faded;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Memory Hollows, stage 4: the archive keeper. The first time a player opens a sunken archive chest (its loot not yet
 * rolled), a keeper rises beside it: a faded that guards that chest, is angry at the opener, stays within
 * {@link Faded#KEEPER_RADIUS} of it, and always drops a forgotten slip. One keeper per chest: once the loot is
 * rolled the chest no longer wakes anything, and a chest whose keeper still stands only turns that keeper on the new
 * opener.
 */
@EventBusSubscriber(modid = Mnemolith.MOD_ID)
public final class ArchiveKeepers {
    public static final ResourceKey<LootTable> ARCHIVE_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "chests/sunken_archive"));
    /** How far from a chest an existing keeper counts as that chest's keeper. */
    public static final double KEEPER_SEARCH = 16.0D;

    private static long woken;

    private ArchiveKeepers() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (level.getBlockEntity(event.getPos()) instanceof RandomizableContainerBlockEntity chest && ARCHIVE_LOOT.equals(chest.getLootTable())) {
            wake(level, event.getPos(), player);
        }
    }

    /**
     * {@code player} opens the unrolled archive chest at {@code chest}: the chest's keeper (an existing one nearby, or a
     * new one beside the chest) turns on the player. Creative and spectator players wake nothing. Returns the keeper,
     * or null when none could stand there.
     */
    public static @Nullable Faded wake(ServerLevel level, BlockPos chest, ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return null;
        }
        for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(chest).inflate(KEEPER_SEARCH), f -> f.isAlive() && f.keeper())) {
            if (faded.hasHome() && faded.getHomePosition().closerThan(chest, KEEPER_SEARCH)) {
                faded.provoke(player);
                return faded;
            }
        }
        Faded keeper = ModEntities.FADED.get().create(level, EntitySpawnReason.EVENT);
        if (keeper == null) {
            return null;
        }
        Vec3 at = spot(level, chest, keeper);
        if (at == null) {
            keeper.discard();
            return null;
        }
        keeper.snapTo(at, level.getRandom().nextFloat() * 360.0F, 0.0F);
        keeper.makeKeeper(chest);
        level.addFreshEntity(keeper);
        keeper.provoke(player);
        level.playSound(null, chest, ModSounds.FADED_AMBIENT.get(), SoundSource.HOSTILE, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 1.0D, at.z, 16, 0.3D, 0.6D, 0.3D, 0.02D);
        woken++;
        Mnemolith.LOGGER.debug("Mnemolith archive keeper woken at {} by {}", chest.toShortString(), player.getGameProfile().name());
        return keeper;
    }

    /** A free standing spot 2 to 3 blocks from the chest (same floor or one up/down), or null. */
    private static @Nullable Vec3 spot(ServerLevel level, BlockPos chest, Faded keeper) {
        int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {1, 0}, {-1, 0},
                {0, 1}, {0, -1}};
        for (int dy : new int[] {0, 1, -1}) {
            for (int[] o : offsets) {
                BlockPos feet = chest.offset(o[0], dy, o[1]);
                if (!level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) {
                    continue;
                }
                Vec3 at = Vec3.atBottomCenterOf(feet);
                if (level.noCollision(keeper, keeper.getType().getSpawnAABB(at.x, at.y, at.z))) {
                    return at;
                }
            }
        }
        return null;
    }

    /** Keepers woken since the server started (QA). */
    public static long wokenCount() {
        return woken;
    }
}
