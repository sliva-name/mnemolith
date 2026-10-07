package com.mnemolith.gametest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.worldgen.ModFeatures;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config at its edges and past them. Every declared default sits inside its own range and the spec turns garbage
 * (a word where a number goes, a number past the range) back into the default; worldgen survives bounds that are
 * swapped or lie outside the world (vein and pocket min Y above max Y, both above the build height) without throwing.
 */
final class ConfigNegativeTests {
    private ConfigNegativeTests() {}

    private static final int LANE = 67;

    static void specRejectsGarbage(GameTestHelper helper) {
        int checked = 0;
        for (Class<?> holder : new Class<?>[] {CommonConfig.class, ServerConfig.class}) {
            for (Field field : holder.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                ModConfigSpec.ConfigValue<?> value;
                try {
                    value = (ModConfigSpec.ConfigValue<?>) field.get(null);
                } catch (IllegalAccessException ex) {
                    throw new IllegalStateException(ex);
                }
                ModConfigSpec.ValueSpec spec = value.getSpec();
                String name = holder.getSimpleName() + "." + field.getName();
                Object def = value.getDefault();
                helper.assertTrue(spec.test(def), name + ": its default " + def + " is outside its own range");
                helper.assertTrue(!spec.test("garbage"), name + " accepts the word 'garbage'");
                helper.assertTrue(def.equals(spec.correct("garbage")), name + " corrects 'garbage' to " + spec.correct("garbage") + " instead of the default " + def);
                if (value instanceof ModConfigSpec.IntValue) {
                    // Past both ends: rejected unless the range itself reaches the int limits.
                    boolean high = spec.test(Integer.MAX_VALUE);
                    boolean low = spec.test(Integer.MIN_VALUE);
                    helper.assertTrue(!high || !low, name + " accepts every int");
                    Object corrected = spec.correct(Integer.MAX_VALUE);
                    helper.assertTrue(spec.test(corrected), name + " corrects MAX_VALUE to " + corrected + ", which it does not accept");
                } else if (value instanceof ModConfigSpec.DoubleValue) {
                    helper.assertTrue(!spec.test(Double.NaN), name + " accepts NaN");
                    helper.assertTrue(spec.test(spec.correct(Double.POSITIVE_INFINITY)), name + " corrects infinity to a value it does not accept");
                }
                checked++;
            }
        }
        helper.assertTrue(checked > 100, "only " + checked + " config values found");
        helper.succeed();
    }

    static void worldgenBounds(GameTestHelper helper) {
        try (NegativeSupport support = new NegativeSupport(helper, LANE)) {
            ServerLevel level = support.level();
            BlockPos pad = support.pad(0);
            BlockPos origin = new BlockPos(pad.getX(), 0, pad.getZ());
            Map<BlockPos, BlockState> before = snapshot(level, origin);
            boolean veins = CommonConfig.ARCHIVAL_VEINS_ENABLED.get();
            int veinChance = CommonConfig.ARCHIVAL_VEIN_CHANCE.get();
            int veinMin = CommonConfig.ARCHIVAL_VEIN_MIN_Y.get();
            int veinMax = CommonConfig.ARCHIVAL_VEIN_MAX_Y.get();
            boolean pockets = CommonConfig.MUTE_POCKETS_ENABLED.get();
            int pocketChance = CommonConfig.MUTE_POCKET_CHANCE.get();
            int pocketMin = CommonConfig.MUTE_POCKET_MIN_Y.get();
            int pocketMax = CommonConfig.MUTE_POCKET_MAX_Y.get();
            try {
                CommonConfig.ARCHIVAL_VEINS_ENABLED.set(true);
                CommonConfig.ARCHIVAL_VEIN_CHANCE.set(100);
                CommonConfig.MUTE_POCKETS_ENABLED.set(true);
                CommonConfig.MUTE_POCKET_CHANCE.set(100);
                int[][] bounds = {{30, -30}, {320, 320}, {-64, -64}, {320, -64}};
                for (int[] b : bounds) {
                    CommonConfig.ARCHIVAL_VEIN_MIN_Y.set(b[0]);
                    CommonConfig.ARCHIVAL_VEIN_MAX_Y.set(b[1]);
                    CommonConfig.MUTE_POCKET_MIN_Y.set(b[0]);
                    CommonConfig.MUTE_POCKET_MAX_Y.set(b[1]);
                    for (long seed = 1; seed <= 8; seed++) {
                        try {
                            ModFeatures.ARCHIVAL_VEIN.get().placeVein(level, origin, RandomSource.create(seed), false);
                            ModFeatures.MUTE_POCKET.get().placePocket(level, origin, RandomSource.create(seed), false);
                        } catch (RuntimeException ex) {
                            throw helper.assertionException(net.minecraft.network.chat.Component.literal(
                                    "worldgen threw with min Y " + b[0] + " and max Y " + b[1] + " (seed " + seed + "): " + ex));
                        }
                    }
                }
                // Swapped bounds still mean the range between them: nothing lands outside [-30, 30].
                CommonConfig.ARCHIVAL_VEIN_MIN_Y.set(30);
                CommonConfig.ARCHIVAL_VEIN_MAX_Y.set(-30);
                restore(level, before);
                // A solid stone cross from Y -40 to 40 so a vein has somewhere to land on any terrain.
                for (int step = -8; step <= 8; step++) {
                    for (int y = -40; y <= 40; y++) {
                        level.setBlock(new BlockPos(origin.getX() + step, y, origin.getZ()), Blocks.STONE.defaultBlockState(), 2 | 16);
                        level.setBlock(new BlockPos(origin.getX(), y, origin.getZ() + step), Blocks.STONE.defaultBlockState(), 2 | 16);
                    }
                }
                int strata = 0;
                for (long seed = 1; seed <= 8; seed++) {
                    ModFeatures.ARCHIVAL_VEIN.get().placeVein(level, origin, RandomSource.create(seed), false);
                }
                for (BlockPos pos : before.keySet()) {
                    if (level.getBlockState(pos).is(ModBlocks.ARCHIVAL_STRATUM.get())) {
                        strata++;
                        helper.assertTrue(pos.getY() >= -31 && pos.getY() <= 30, "a vein with swapped bounds 30/-30 landed at Y " + pos.getY());
                    }
                }
                helper.assertTrue(strata > 0, "a vein with swapped bounds 30/-30 placed nothing in solid stone");
            } finally {
                CommonConfig.ARCHIVAL_VEINS_ENABLED.set(veins);
                CommonConfig.ARCHIVAL_VEIN_CHANCE.set(veinChance);
                CommonConfig.ARCHIVAL_VEIN_MIN_Y.set(veinMin);
                CommonConfig.ARCHIVAL_VEIN_MAX_Y.set(veinMax);
                CommonConfig.MUTE_POCKETS_ENABLED.set(pockets);
                CommonConfig.MUTE_POCKET_CHANCE.set(pocketChance);
                CommonConfig.MUTE_POCKET_MIN_Y.set(pocketMin);
                CommonConfig.MUTE_POCKET_MAX_Y.set(pocketMax);
                restore(level, before);
                LoadedChunkMemory.clear(level.getChunkAt(origin));
            }
            helper.succeed();
        }
    }

    private static Map<BlockPos, BlockState> snapshot(ServerLevel level, BlockPos origin) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, level.getMinY() - origin.getY(), -8), new BlockPos(origin.getX() + 8, level.getMaxY(), origin.getZ() + 8))) {
            states.put(pos.immutable(), level.getBlockState(pos));
        }
        return states;
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> states) {
        for (Map.Entry<BlockPos, BlockState> entry : states.entrySet()) {
            if (!level.getBlockState(entry.getKey()).equals(entry.getValue())) {
                // A pocket chest goes without spilling its loot.
                if (level.getBlockEntity(entry.getKey()) instanceof RandomizableContainer loot) {
                    loot.setLootTable(null);
                }
                if (level.getBlockEntity(entry.getKey()) instanceof Container container) {
                    container.clearContent();
                }
                level.setBlock(entry.getKey(), entry.getValue(), 2 | 16);
            }
        }
    }
}
