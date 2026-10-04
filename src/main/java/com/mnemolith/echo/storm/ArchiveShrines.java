package com.mnemolith.echo.storm;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mnemolith.Mnemolith;
import com.mnemolith.armory.ArmoryItems;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ImprintTag;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * Archive shrine challenge (B4 half). Right-clicking an idle shrine builds a temporary arena ring, spawns the Archive
 * Guardian deterministically (never a storm dice roll), and marks the shrine challenged. Defeating the guardian claims
 * the shrine and fills a reward chest beside it.
 */
public final class ArchiveShrines {
    public static final int ARENA_HEIGHT = 3;
    public static final int DEFAULT_RADIUS = 7;
    /** Guardian strength: five merged tempers → 160 HP via ScarEntity.maxHealthFor. */
    public static final int GUARDIAN_MERGED = 5;

    private ArchiveShrines() {}

    public static boolean enabled() {
        return CommonConfig.ARCHIVE_GUARDIAN_ENABLED.get();
    }

    public static int arenaRadius() {
        return CommonConfig.ARCHIVE_GUARDIAN_ARENA_RADIUS.get();
    }

    /**
     * Player activates the shrine. Returns true when a challenge started (arena + boss).
     */
    public static boolean challenge(ServerLevel level, BlockPos shrinePos, BlockState state, ServerPlayer player) {
        if (!enabled()) {
            player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.disabled"), true);
            return false;
        }
        if (state.getValue(com.mnemolith.content.block.ArchiveShrineBlock.CLAIMED)) {
            player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.claimed"), true);
            return false;
        }
        if (state.getValue(com.mnemolith.content.block.ArchiveShrineBlock.CHALLENGED)) {
            if (guardianNear(level, shrinePos) != null) {
                player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.fighting"), true);
                return false;
            }
            // Challenged but boss gone (despawn / peaceful): allow reclaim only via defeat path; treat as reclaimable idle.
        }
        ArchiveGuardianData data = ArchiveGuardianData.get(level.getServer());
        if (CommonConfig.ARCHIVE_GUARDIAN_ONCE_PER_WORLD.get() && data.defeated()) {
            player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.world_done"), true);
            return false;
        }
        int cooldown = CommonConfig.ARCHIVE_GUARDIAN_COOLDOWN_TICKS.get();
        long now = level.getGameTime();
        if (cooldown > 0 && data.lastChallengeAt() > 0 && now - data.lastChallengeAt() < cooldown) {
            player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.cooldown"), true);
            return false;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.peaceful"), true);
            return false;
        }
        if (guardianNear(level, shrinePos) != null) {
            player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.fighting"), true);
            return false;
        }

        int radius = arenaRadius();
        prepareArena(level, shrinePos, radius);
        BlockPos spawnAt = shrinePos.above();
        ScarEntity guardian = ModEntities.ARCHIVE_GUARDIAN.get().create(level, EntitySpawnReason.EVENT);
        if (guardian == null) {
            return false;
        }
        guardian.snapTo(spawnAt.getX() + 0.5D, spawnAt.getY() + 1.2D, spawnAt.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        int mask = (1 << Temper.HUSHED.id()) | (1 << Temper.DEEP.id()) | (1 << Temper.GRAVE.id());
        guardian.setup(mask, GUARDIAN_MERGED, shrinePos);
        if (!level.addFreshEntity(guardian)) {
            return false;
        }

        level.setBlock(shrinePos, state.setValue(com.mnemolith.content.block.ArchiveShrineBlock.CHALLENGED, true)
                .setValue(com.mnemolith.content.block.ArchiveShrineBlock.CLAIMED, false), Block.UPDATE_ALL);
        data.setLastChallengeAt(now);
        nudgePlayerToRing(level, player, shrinePos, radius);
        level.playSound(null, shrinePos, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.6F, 0.85F);
        level.playSound(null, shrinePos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                shrinePos.getX() + 0.5D, shrinePos.getY() + 1.5D, shrinePos.getZ() + 0.5D,
                80, 1.2D, 1.5D, 1.2D, 0.08D);
        player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.challenge"), false);
        Mnemolith.LOGGER.info("Mnemolith archive guardian challenge at {} by {}", shrinePos.toShortString(), player.getGameProfile().name());
        return true;
    }

    /** Called from the guardian's death: claim shrine, world flag, reward chest. */
    public static void onGuardianDefeated(ServerLevel level, ScarEntity guardian, @Nullable Entity killer) {
        BlockPos home = guardian.home();
        BlockState state = level.getBlockState(home);
        // A second guardian at a shrine that is already claimed (a rechallenge after the first wandered off or unloaded)
        // must not pay the chest out again.
        if (state.is(ModBlocks.ARCHIVE_SHRINE.get()) && !state.getValue(com.mnemolith.content.block.ArchiveShrineBlock.CLAIMED)) {
            level.setBlock(home, state.setValue(com.mnemolith.content.block.ArchiveShrineBlock.CHALLENGED, true)
                    .setValue(com.mnemolith.content.block.ArchiveShrineBlock.CLAIMED, true), Block.UPDATE_ALL);
            placeRewardChest(level, home);
        }
        ArchiveGuardianData data = ArchiveGuardianData.get(level.getServer());
        data.setDefeated(true);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(guardian) <= 48.0D * 48.0D) {
                player.sendSystemMessage(Component.translatable("mnemolith.archive_shrine.victory"), false);
            }
        }
        Mnemolith.LOGGER.info("Mnemolith archive guardian defeated at {} by {}", home.toShortString(),
                killer == null ? "?" : killer.getName().getString());
    }

    /** Summons a fight-ready guardian at {@code pos} for commands / QA (no shrine state change). */
    public static @Nullable ScarEntity summon(ServerLevel level, BlockPos pos) {
        ScarEntity guardian = ModEntities.ARCHIVE_GUARDIAN.get().spawn(level, pos, EntitySpawnReason.COMMAND);
        if (guardian == null) {
            guardian = ModEntities.ARCHIVE_GUARDIAN.get().spawn(level, pos.above(), EntitySpawnReason.COMMAND);
        }
        if (guardian != null) {
            int mask = (1 << Temper.HUSHED.id()) | (1 << Temper.DEEP.id()) | (1 << Temper.GRAVE.id());
            guardian.setup(mask, GUARDIAN_MERGED, pos);
        }
        return guardian;
    }

    public static @Nullable ScarEntity guardianNear(ServerLevel level, BlockPos shrinePos) {
        AABB box = new AABB(shrinePos).inflate(arenaRadius() + 4.0D);
        for (ScarEntity scar : level.getEntitiesOfClass(ScarEntity.class, box, ScarEntity::isAlive)) {
            if (scar.isGuardian()) {
                return scar;
            }
        }
        return null;
    }

    /** Temporary barrier ring of scar glass; only into air / replaceable, never over solids. */
    public static int prepareArena(ServerLevel level, BlockPos shrine, int radius) {
        boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
        int placed = 0;
        if (!griefing) {
            // Still clear soft plants so the fight has room; no glass ring without griefing.
            clearInterior(level, shrine, radius);
            return 0;
        }
        clearInterior(level, shrine, radius);
        BlockState glass = ModBlocks.SCAR_GLASS.get().defaultBlockState();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int dist2 = dx * dx + dz * dz;
                boolean onRing = dist2 >= (radius - 1) * (radius - 1) && dist2 <= radius * radius;
                if (!onRing) {
                    continue;
                }
                BlockPos base = shrine.offset(dx, 0, dz);
                for (int dy = 0; dy < ARENA_HEIGHT; dy++) {
                    BlockPos at = base.above(dy);
                    if (!level.getChunkSource().hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
                        continue;
                    }
                    if (open(level, at)) {
                        level.setBlock(at, glass, Block.UPDATE_ALL);
                        placed++;
                    }
                }
            }
        }
        // Soft floor under the shrine if air (archival accent).
        BlockPos under = shrine.below();
        if (open(level, under) && level.getBlockState(under.below()).isFaceSturdy(level, under.below(), Direction.UP)) {
            level.setBlock(under, ModBlocks.ARCHIVAL_STRATUM.get().defaultBlockState(), Block.UPDATE_ALL);
            placed++;
        }
        return placed;
    }

    private static void clearInterior(ServerLevel level, BlockPos shrine, int radius) {
        for (int dx = -radius + 1; dx <= radius - 1; dx++) {
            for (int dz = -radius + 1; dz <= radius - 1; dz++) {
                if (dx * dx + dz * dz > (radius - 1) * (radius - 1)) {
                    continue;
                }
                for (int dy = 0; dy <= ARENA_HEIGHT + 1; dy++) {
                    BlockPos at = shrine.offset(dx, dy, dz);
                    if (at.equals(shrine)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (state.canBeReplaced() && state.getFluidState().isEmpty() && !state.isAir()) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static void nudgePlayerToRing(ServerLevel level, ServerPlayer player, BlockPos shrine, int radius) {
        double angle = Math.atan2(player.getZ() - shrine.getZ(), player.getX() - shrine.getX());
        double x = shrine.getX() + 0.5D + Math.cos(angle) * (radius - 1.5D);
        double z = shrine.getZ() + 0.5D + Math.sin(angle) * (radius - 1.5D);
        double y = shrine.getY() + 1.0D;
        if (player.distanceToSqr(shrine.getX() + 0.5D, shrine.getY() + 0.5D, shrine.getZ() + 0.5D) < 9.0D) {
            player.teleportTo(x, y, z);
        }
    }

    /** Chest beside the shrine with unique guardian loot (schematic, disc, catalog, sturdy slip). */
    public static void placeRewardChest(ServerLevel level, BlockPos shrine) {
        BlockPos chestPos = null;
        for (Direction dir : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            BlockPos at = shrine.relative(dir);
            if (open(level, at) && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                chestPos = at;
                break;
            }
        }
        if (chestPos == null) {
            chestPos = shrine.above();
            if (!open(level, chestPos)) {
                // Spill as item entities at the shrine.
                for (ItemStack stack : rewardStacks(level, shrine)) {
                    Block.popResource(level, shrine, stack);
                }
                return;
            }
        }
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            List<ItemStack> rewards = rewardStacks(level, shrine);
            for (int i = 0; i < rewards.size() && i < chest.getContainerSize(); i++) {
                chest.setItem(i, rewards.get(i));
            }
        }
        level.playSound(null, chestPos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    public static List<ItemStack> rewardStacks(ServerLevel level, BlockPos home) {
        return List.of(
                new ItemStack(ModItems.ARCHIVE_SCHEMATIC.get()),
                new ItemStack(ModItems.MUSIC_DISC_RECOLLECTION.get()),
                new ItemStack(ModItems.CATALOG_FRAGMENT.get(), 3),
                new ItemStack(ModItems.ECHO_STURDY_SLIP.get()),
                new ItemStack(ModItems.SCAR_FRAGMENT.get()),
                Residues.shard(ImprintTag.SILENCE, 4, home, level.getGameTime()),
                new ItemStack(ArmoryItems.HUSH_FIBER.get(), 4));
    }
}
