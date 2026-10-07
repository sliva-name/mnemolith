package com.mnemolith.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.CompositionReelBlockEntity;
import com.mnemolith.content.composition.ComposeResult;
import com.mnemolith.content.composition.Composition;
import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.content.composition.CompositionRecipes;
import com.mnemolith.content.menu.CompositionMenu;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.imprint.ImprintConstants;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.ServerTuningPayload;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Blank slips and the reel, the server tuning packet, mute libraries only on End ground, and templates that stand up. */
final class Round2Tests {
    private Round2Tests() {}

    /** Blank slips must not go into the reel, and a blank already inside is refused without burning anything. */
    static void blankSlips(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack blank = new ItemStack(ModItems.IMPRINT_SLIP.get());
        ItemStack written = ImprintSlips.of(ImprintTag.SILENCE);

        helper.assertTrue(!Composition.composable(blank), "a blank slip counts as composable");
        helper.assertTrue(Composition.composable(written), "a written slip is not composable");

        BlockPos reelPos = new BlockPos(1, 1, 1);
        helper.setBlock(reelPos, ModBlocks.COMPOSITION_REEL.get());
        CompositionReelBlockEntity reel = helper.getBlockEntity(reelPos, CompositionReelBlockEntity.class);
        helper.assertTrue(!reel.canPlaceItem(0, blank), "hoppers can put a blank slip into the reel");
        helper.assertTrue(reel.canPlaceItem(0, written), "hoppers cannot put a written slip into the reel");

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        CompositionMenu menu = new CompositionMenu(0, player.getInventory(), reel);
        helper.assertTrue(!menu.getSlot(0).mayPlace(blank), "the reel slot accepts a blank slip");
        helper.assertTrue(menu.getSlot(0).mayPlace(written), "the reel slot refuses a written slip");

        // An older save may still hold a blank slip: Compose must refuse and keep every slip.
        SimpleContainer container = new SimpleContainer(ImprintConstants.COMPOSITION_SLOTS);
        container.setItem(0, written.copy());
        container.setItem(1, blank.copyWithCount(3));
        ComposeResult result = Composition.compose(level, helper.absolutePos(reelPos), null, container);
        helper.assertTrue(result.status() == ComposeResult.BLANK, "compose with a blank slip gave status " + result.status());
        helper.assertTrue(container.getItem(0).getCount() == 1 && container.getItem(1).getCount() == 3,
                "compose with a blank slip burned a slip");
        helper.succeed();
    }

    /** The tuning packet carries the server formulas in order, item results included. */
    static void tuningPayload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<CompositionRecipe> formulas = CompositionRecipes.all();
        ServerTuningPayload sent = new ServerTuningPayload(48, 10, formulas);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        ServerTuningPayload.STREAM_CODEC.encode(buf, sent);
        ServerTuningPayload got = ServerTuningPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(got.possessRange() == 48 && got.mineMaxRadius() == 10, "tuning numbers changed on the wire");
        helper.assertTrue(got.formulas().equals(formulas), "formulas changed on the wire");
        helper.assertTrue(formulas.stream().anyMatch(CompositionRecipe::hasItemResult), "no formula with an item result to check");
        helper.assertTrue(buf.readableBytes() == 0, "tuning packet left unread bytes");
        helper.succeed();
    }

    /** Mute libraries: never over the void, never on the dragon island, and still found on outer islands. */
    static void muteLibraryGround(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "no End level in the test server");
        Optional<Structure> found = end.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getOptional(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "mute_library"));
        helper.assertTrue(found.isPresent(), "mute_library structure not registered");
        Structure library = found.get();
        ChunkGenerator generator = end.getChunkSource().getGenerator();
        Predicate<Holder<Biome>> valid = library.biomes()::contains;

        // The empty ring between the dragon island and the outer islands, and the dragon island itself.
        int[][] central = {{9, 41}, {4, -51}, {-53, 8}, {39, 16}, {40, 29}, {43, -56}, {-49, -55}, {0, 7}, {0, 0}, {3, -3}};
        for (int[] c : central) {
            helper.assertTrue(library.findValidGenerationPoint(context(end, generator, valid, new ChunkPos(c[0], c[1]))).isEmpty(),
                    "mute library can start in the central End at chunk " + c[0] + ", " + c[1]);
        }

        // Outer islands: every start must stand on ground at all corners, outside the_end biome; some must exist.
        int starts = 0;
        for (int i = 0; i < 160; i++) {
            double angle = i * 0.61803398875D * Math.PI * 2.0D;
            int radius = 80 + (i * 7) % 40;
            ChunkPos pos = new ChunkPos((int) Math.round(Math.cos(angle) * radius), (int) Math.round(Math.sin(angle) * radius));
            Optional<Structure.GenerationStub> stub = library.findValidGenerationPoint(context(end, generator, valid, pos));
            if (stub.isEmpty()) {
                continue;
            }
            starts++;
            BlockPos start = stub.get().position();
            helper.assertTrue(start.getY() >= 60, "mute library start below the island surface at " + start);
            BoundingBox box = stub.get().getPiecesBuilder().getBoundingBox();
            int[][] corners = {{box.minX(), box.minZ()}, {box.maxX(), box.minZ()}, {box.minX(), box.maxZ()}, {box.maxX(), box.maxZ()}};
            for (int[] corner : corners) {
                int ground = generator.getFirstOccupiedHeight(corner[0], corner[1], Heightmap.Types.WORLD_SURFACE_WG, end, end.getChunkSource().randomState());
                helper.assertTrue(ground >= 60, "mute library corner over the void at " + corner[0] + ", " + corner[1] + " (ground " + ground + ")");
            }
            Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(start.getX()), QuartPos.fromBlock(start.getY()),
                    QuartPos.fromBlock(start.getZ()), end.getChunkSource().randomState().sampler());
            helper.assertTrue(!biome.is(Biomes.THE_END), "mute library in the central the_end biome at " + start);
        }
        helper.assertTrue(starts > 0, "no mute library could start on any sampled outer-island chunk");
        helper.succeed();
    }

    /**
     * Every mod structure template must actually stand up when placed: the main wall block appears at every
     * position the template asks for. Templates with a second "air" entry per position used to place as a bare
     * hole with only the chests, walls and lanterns left.
     */
    static void templatesPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // name, main wall block, feet height of the ground floor above the template's bottom (-1: not walked through).
        Object[][] cases = {
                {"mute_library", Blocks.END_STONE_BRICKS, 1},
                {"hush_chapel", ModBlocks.MUTE_STONE_BRICKS.get(), 1},
                {"flooded_archive", Blocks.DEEPSLATE_BRICKS, 1},
                {"memory_field", ModBlocks.ARCHIVAL_STRATUM_BRICKS.get(), 1},
                // On 8 blocks of basalt piers.
                {"ashen_archive", Blocks.POLISHED_BLACKSTONE_BRICKS, 9},
                {"chronicle_observatory", Blocks.STONE_BRICKS, -1},
        };
        // High above the test lane so neighbouring tests are not touched; cleared again afterwards.
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).atY(level.getMaxY() - 24);
        StructurePlaceSettings settings = new StructurePlaceSettings();
        for (Object[] c : cases) {
            String name = (String) c[0];
            Block wall = (Block) c[1];
            Optional<StructureTemplate> found = level.getStructureManager().get(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, name));
            helper.assertTrue(found.isPresent(), "missing structure template " + name);
            StructureTemplate template = found.get();
            Set<BlockPos> wanted = new HashSet<>();
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, wall)) {
                wanted.add(info.pos());
            }
            helper.assertTrue(wanted.size() > 20, name + " has almost no " + wall);
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            BoundingBox box = template.getBoundingBox(settings, origin);
            int feet = (Integer) c[2];
            if (feet >= 0) {
                assertWalkable(helper, level, name, box, box.minY() + feet);
            }
            int placed = 0;
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (level.getBlockState(pos).is(wall)) {
                    placed++;
                }
            }
            for (BlockPos pos : BlockPos.betweenClosed(box.minX() - 1, box.minY(), box.minZ() - 1, box.maxX() + 1, box.maxY(), box.maxZ() + 1)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            helper.assertTrue(placed == wanted.size(), name + " placed " + placed + " of " + wanted.size() + " " + wall + " blocks");
        }
        helper.succeed();
    }

    /**
     * Every chest, shrine and vault of a placed template can be walked up to from outside on its ground floor: a
     * flood fill over the cells a player fits through (feet and head clear; a bottom slab is stepped onto, water
     * waded through) from the ring around the template. Rooms share walls, and doorways carved before the next room
     * was built used to be walled up again, sealing the main halls off from their entrances.
     */
    private static void assertWalkable(GameTestHelper helper, ServerLevel level, String name, BoundingBox box, int feetY) {
        int minX = box.minX() - 1;
        int maxX = box.maxX() + 1;
        int minZ = box.minZ() - 1;
        int maxZ = box.maxZ() + 1;
        Set<BlockPos> seen = new HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (x == minX || x == maxX || z == minZ || z == maxZ) {
                    BlockPos start = new BlockPos(x, feetY, z);
                    if (fits(level, start) && seen.add(start)) {
                        queue.add(start);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos next = at.relative(direction);
                if (next.getY() < feetY || next.getY() > feetY + 1 || next.getX() < minX || next.getX() > maxX
                        || next.getZ() < minZ || next.getZ() > maxZ || seen.contains(next) || !fits(level, next)) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        int targets = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.CHEST) && !state.is(ModBlocks.ARCHIVE_SHRINE.get()) && !state.is(ModBlocks.ARCHIVE_VAULT.get())) {
                continue;
            }
            targets++;
            boolean reached = false;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos beside = pos.relative(side);
                if (seen.contains(beside) || seen.contains(beside.below())) {
                    reached = true;
                }
            }
            BlockPos local = pos.subtract(new BlockPos(box.minX(), box.minY(), box.minZ()));
            helper.assertTrue(reached, name + ": the " + state.getBlock().getName().getString() + " at " + local.toShortString()
                    + " cannot be walked up to from the entrance (walled in)");
        }
        helper.assertTrue(targets > 0, name + " has no chest or shrine to reach");
    }

    /** A player fits with their feet in {@code pos}: feet and head have no collision above a bottom slab's height. */
    private static boolean fits(ServerLevel level, BlockPos pos) {
        return low(level, pos) && low(level, pos.above());
    }

    private static boolean low(ServerLevel level, BlockPos pos) {
        net.minecraft.world.phys.shapes.VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        return shape.isEmpty() || shape.max(net.minecraft.core.Direction.Axis.Y) <= 0.5D;
    }

    private static Structure.GenerationContext context(ServerLevel end, ChunkGenerator generator, Predicate<Holder<Biome>> valid, ChunkPos pos) {
        return new Structure.GenerationContext(end.registryAccess(), generator, generator.getBiomeSource(), end.getChunkSource().randomState(),
                end.getStructureManager(), end.getSeed(), pos, end, valid);
    }
}
