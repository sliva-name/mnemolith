package com.mnemolith.command;

import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.HollowRegions;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mnemolith hollows survey [radius]}: samples the level's biome source on a grid (every 64 blocks at Y 64) and
 * reports how much of it is Memory Hollows and how much is a host biome. {@code /mnemolith hollows flicker [scene]}
 * plays one flicker six blocks in front of the caller, in any biome (screenshots and client checks).
 */
public final class HollowsCommand {
    private static final int STEP = 64;

    private HollowsCommand() {}

    public static int survey(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        BlockPos center = BlockPos.containing(source.getPosition());
        int total = 0;
        int hollows = 0;
        int hosts = 0;
        int nearest = Integer.MAX_VALUE;
        BlockPos nearestAt = null;
        long started = System.nanoTime();
        for (int dx = -radius; dx <= radius; dx += STEP) {
            for (int dz = -radius; dz <= radius; dz += STEP) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                Holder<Biome> biome = biomes.getNoiseBiome(x >> 2, 16, z >> 2, sampler);
                total++;
                if (Hollows.is(biome)) {
                    hollows++;
                    int d = (int) Math.sqrt((double) dx * dx + (double) dz * dz);
                    if (d < nearest) {
                        nearest = d;
                        nearestAt = new BlockPos(x, 64, z);
                    }
                } else if (biome.is(Hollows.HOSTS)) {
                    hosts++;
                }
            }
        }
        long micros = (System.nanoTime() - started) / 1000L;
        String line = String.format("hollows survey r=%d samples=%d hollows=%d (%.2f%% of all, %.2f%% of host land) hosts=%d nearest=%s regions=%d took=%dus",
                radius, total, hollows, 100.0D * hollows / Math.max(1, total), 100.0D * hollows / Math.max(1, hollows + hosts), hosts,
                nearestAt == null ? "none" : nearestAt.toShortString() + " (" + nearest + " blocks)", HollowRegions.active().size(), micros);
        source.sendSuccess(() -> Component.literal(line), false);
        com.mnemolith.Mnemolith.LOGGER.info("Mnemolith {}", line);
        return hollows;
    }

    public static int flicker(CommandContext<CommandSourceStack> context, int scene) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 eye = source.getPosition();
        float yaw = source.getRotation().y;
        Vec3 ahead = eye.add(-Mth.sin(yaw * Mth.DEG_TO_RAD) * 6.0D, 0.0D, Mth.cos(yaw * Mth.DEG_TO_RAD) * 6.0D);
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead));
        // carry the spot's own imprint, as a natural flicker would, so it can be caught into a slip
        com.mnemolith.imprint.ImprintTag tag = HollowFlickers.imprintAt(
                com.mnemolith.world.LoadedChunkMemory.existing(level.getChunkAt(at)), level.getRandom());
        int shown = scene < 0 ? HollowFlickers.scene(tag) : Mth.clamp(scene, 0, HollowFlickers.SCENES - 1);
        HollowFlickers.send(level, new HollowFlickerPayload(at, yaw + 180.0F, shown, tag == null ? -1 : tag.ordinal()));
        source.sendSuccess(() -> Component.literal("hollows flicker scene=" + shown + " tag=" + tag + " at " + at.toShortString()), false);
        return 1;
    }

    public static IntegerArgumentType radius() {
        return IntegerArgumentType.integer(64, 16384);
    }
}
