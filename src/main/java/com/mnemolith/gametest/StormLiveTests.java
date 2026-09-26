package com.mnemolith.gametest;

import java.util.List;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.echo.residue.Residues;
import com.mnemolith.echo.storm.RecollectionStorm;
import com.mnemolith.echo.storm.ScarSites;
import com.mnemolith.echo.storm.Storms;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Recollection storms and the Scar against real server players ({@link LivePlayers}). Setup writes chunk memory
 * (scenery); the storm itself runs on the server tick, the shard and the mute stone go through real item use
 * ({@code ServerPlayerGameMode.useItemOn}), the lens through a real item use and the player's gaze, and the hit
 * through {@link ServerPlayer#attack}. Each test has its own batch (see {@link LivePlayers.Environment}).
 */
final class StormLiveTests {
    private StormLiveTests() {}

    /** Storm sites: 8 chunks apart (area plus margin), north of the residue sites. */
    private static BlockPos site(GameTestHelper helper, int index) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        BlockPos pos = LivePlayers.surface(level, (origin.getX() >> 4) + 8 * index, (origin.getZ() >> 4) - 34);
        LivePlayers.force(level, pos);
        ChunkPos center = ChunkPos.containing(pos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LoadedChunkMemory.clear(level.getChunk(center.x() + dx, center.z() + dz));
            }
        }
        // A flat stone pad with open air above: the site stands on solid ground, and nothing hides the Scar.
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2 | 16);
                for (int dy = 0; dy <= 10; dy++) {
                    level.setBlock(pos.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }
        return pos;
    }

    /**
     * A player standing outside a fractured area frees a residual shard on its centre: the storm gathers for 10 s,
     * rages for six waves (its first wave condenses the three memories the area holds), and with four storm residues
     * still standing at the end they merge into the Scar: the centre becomes a Scar site, with its heart when
     * mobGriefing allows, and the boss unless the difficulty is peaceful.
     */
    static void shardCallsStormIntoScar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 0);
        ChunkPos chunk = ChunkPos.containing(site);
        ImprintWriter.write(level, site.offset(-3, 0, -3), List.of(ImprintTag.SILENCE), null, false);
        ImprintWriter.write(level, site.offset(16, 0, 2), List.of(ImprintTag.FALL), null, false);
        ImprintWriter.write(level, site.offset(2, 0, -16), List.of(ImprintTag.SILENCE), null, false);
        keepLoud(level, site, PressureBand.FRACTURE);
        helper.assertTrue(Storms.gate(level, chunk) == Storms.Gate.OK, "the fractured centre does not let a storm gather: " + Storms.gate(level, chunk));
        // Outside the storm's 3x3 area, so the storm's own residues and acts never reach the player.
        BlockPos stand = site.offset(40, 0, 0);
        LivePlayers.force(level, stand);
        ServerPlayer player = LivePlayers.join(helper, "LiveStormCaller", Vec3.atBottomCenterOf(stand));
        player.getAbilities().invulnerable = true;
        ItemStack shard = Residues.shard(ImprintTag.FALL, 4, site, level.getGameTime());
        player.setItemInHand(InteractionHand.MAIN_HAND, shard);
        BlockPos ground = site.below();
        InteractionResult used = player.gameMode.useItemOn(player, level, shard, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false));
        RecollectionStorm storm = Storms.at(level, chunk);
        helper.assertTrue(used.consumesAction() && storm != null && storm.phase() == RecollectionStorm.Phase.GATHERING,
                "freeing the shard did not call a storm: " + used + " storm " + storm);
        long id = storm.id();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(storm.phase() == RecollectionStorm.Phase.RAGING, "still gathering at " + storm.ticks()))
                .thenExecute(() -> {
                    Storms.Wave wave = Storms.lastWave();
                    helper.assertTrue(wave != null && wave.condensed() == 3, "the first wave condensed " + (wave == null ? "nothing" : wave.condensed()));
                })
                .thenWaitUntil(() -> {
                    ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(site));
                    helper.assertTrue(memory != null && memory.scar() != null, "no Scar yet: storm " + (Storms.isActive(level, id)
                            ? storm.phase() + " waves left " + storm.wavesLeft() + " standing " + storm.residues().size() : "ended " + Storms.lastEnd()));
                })
                .thenExecute(() -> {
                    Storms.Ended end = Storms.lastEnd();
                    helper.assertTrue(end != null && end.id() == id && end.end() == Storms.End.SCAR, "the storm ended " + end);
                    boolean heart = ScarSites.findHeart(level, level.getChunkAt(site)) != null;
                    boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
                    helper.assertTrue(heart == griefing, "heart placed=" + heart + " with mobGriefing=" + griefing);
                    List<ScarEntity> bosses = level.getEntitiesOfClass(ScarEntity.class, new AABB(site).inflate(24.0D), ScarEntity::isAlive);
                    boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;
                    helper.assertTrue(peaceful ? bosses.isEmpty() : bosses.size() == 1 && bosses.getFirst().merged() == 4,
                            "bosses " + bosses.size() + " (difficulty " + level.getDifficulty() + ")");
                    helper.assertTrue(Storms.gate(level, chunk) != Storms.Gate.OK && LoadedChunkMemory.stormProof(level, site), "the Scar site is not storm-proof");
                })
                .thenSucceed();
    }

    /** While a storm gathers, the player places a mute stone in its centre (real block placement): it is contained. */
    static void muteStoneContains(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 1);
        ChunkPos chunk = ChunkPos.containing(site);
        keepLoud(level, site, PressureBand.FRACTURE);
        helper.assertTrue(Storms.gate(level, chunk) == Storms.Gate.OK, "the fractured centre does not let a storm gather: " + Storms.gate(level, chunk));
        RecollectionStorm storm = Storms.start(level, chunk, "gametest");
        long id = storm.id();
        ServerPlayer player = LivePlayers.join(helper, "LiveStormMuter", Vec3.atBottomCenterOf(site.offset(4, 0, 4)));
        player.getAbilities().invulnerable = true;
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(Storms.isActive(level, id) && storm.phase() == RecollectionStorm.Phase.GATHERING, "the storm is not gathering");
                    ItemStack stone = new ItemStack(ModItems.MUTE_STONE.get());
                    player.setItemInHand(InteractionHand.MAIN_HAND, stone);
                    BlockPos ground = site.offset(2, -1, 2);
                    helper.assertTrue(level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP), "no ground to place the stone on");
                    InteractionResult placed = player.gameMode.useItemOn(player, level, stone, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false));
                    helper.assertTrue(placed.consumesAction() && level.getBlockState(ground.above()).is(ModBlocks.MUTE_STONE.get()),
                            "the mute stone was not placed: " + placed);
                })
                .thenWaitUntil(() -> helper.assertTrue(!Storms.isActive(level, id), "still active: " + storm.phase() + " at " + storm.ticks()))
                .thenExecute(() -> {
                    Storms.Ended end = Storms.lastEnd();
                    helper.assertTrue(end != null && end.id() == id && end.end() == Storms.End.CONTAINED, "the storm ended " + end);
                    helper.assertTrue(storm.phase() == RecollectionStorm.Phase.GATHERING, "it raged before it was contained");
                })
                .thenSucceed();
    }

    /**
     * A Scar cannot be hurt until it is read: a punch does nothing; after the player holds the lens on it for 3 s it
     * is pinned, and the next punch hurts it.
     */
    static void scarReadThenHurt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = site(helper, 2);
        ScarEntity scar = ModEntities.SCAR.get().create(level, EntitySpawnReason.COMMAND);
        if (scar == null) {
            throw helper.assertionException(Component.literal("could not create the Scar"));
        }
        scar.snapTo(Vec3.atBottomCenterOf(site.above(2)), 0.0F, 0.0F);
        scar.setup((1 << Temper.HUSHED.id()) | (1 << Temper.PLUNGING.id()), 3, site);
        helper.assertTrue(level.addFreshEntity(scar), "the Scar was not added");
        // Six blocks off: inside the distance the Scar keeps (4 to 7), so it hovers in front of the player.
        Vec3 stand = Vec3.atBottomCenterOf(site.offset(6, 0, 0));
        ServerPlayer player = LivePlayers.join(helper, "LiveScarReader", stand);
        player.getAbilities().invulnerable = true;
        float full = scar.getHealth();
        helper.onEachTick(() -> {
            if (scar.isAlive()) {
                // Its recall may lift the player (a fall memory); a player standing their ground comes back down.
                player.snapTo(stand);
                player.setDeltaMovement(Vec3.ZERO);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, scar.getBoundingBox().getCenter());
            }
        });
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    player.attack(scar);
                    helper.assertTrue(scar.getHealth() == full, "an unread Scar was hurt: " + scar.getHealth() + "/" + full);
                    ItemStack lens = new ItemStack(ModItems.CHRONICLE_LENS.get());
                    player.setItemInHand(InteractionHand.MAIN_HAND, lens);
                    player.gameMode.useItem(player, level, lens, InteractionHand.MAIN_HAND);
                })
                .thenWaitUntil(() -> helper.assertTrue(scar.isPinned(), "not read yet: progress " + scar.readProgress() + ", focusing " + player.isUsingItem()
                        + ", distance " + Math.round(player.distanceTo(scar)) + ", sight " + player.hasLineOfSight(scar)))
                .thenExecute(() -> {
                    player.stopUsingItem();
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                    player.resetAttackStrengthTicker();
                    player.attack(scar);
                    helper.assertTrue(scar.getHealth() < full, "a read Scar was not hurt: " + scar.getHealth() + "/" + full);
                    scar.discard();
                })
                .thenSucceed();
    }

    private static void keepLoud(ServerLevel level, BlockPos pos, PressureBand target) {
        for (int i = 0; i < 80 && band(level, pos).ordinal() < target.ordinal(); i++) {
            ImprintWriter.spike(level, pos, 3);
        }
    }

    private static PressureBand band(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? PressureBand.CALM : MemoryPressure.band(memory.cachedPressure());
    }
}
