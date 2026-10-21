package com.mnemolith.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mnemolith.content.ModBlocks;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Regressions from the 0.7.0 bug hunt. Synchronous checks over the registries and static data.
 */
final class BugHuntTests {
    private BugHuntTests() {}

    /** Blocks whose model is not a full cube even where their outline is (open frames, legs). */
    private static final Set<String> OPEN_MODELS = Set.of("hush_loom", "ember_brazier", "recollite_lantern", "memorial_stone");

    static void run(GameTestHelper helper) {
        List<String> bad = new ArrayList<>();
        occlusion(bad);
        echoSlipFromAmethyst(helper, bad);
        brazierFlamesInBowl(bad);
        if (!bad.isEmpty()) {
            helper.fail("0.7.0 regressions: " + String.join("; ", bad));
            return;
        }
        helper.succeed();
    }

    /** Brazier flames start on the ember bed inside the bowl, not half a block above it (they used full-cube heights). */
    static void brazierFlamesInBowl(List<String> bad) {
        double rim = com.mnemolith.content.block.EmberBrazierBlock.SHAPE.max(net.minecraft.core.Direction.Axis.Y);
        double bed = com.mnemolith.content.block.EmberBrazierBlock.BED_Y;
        if (bed > rim || bed < 0.3D) {
            bad.add("brazier flame height " + bed + " outside the bowl (rim " + rim + ")");
        }
    }

    /** Echo slips (the gate to every echo craft) must not need a Memory Hollows biome: amethyst works too. */
    static void echoSlipFromAmethyst(GameTestHelper helper, List<String> bad) {
        var level = helper.getLevel();
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("mnemolith", "echo_slip"));
        var recipe = level.getServer().getRecipeManager().byKey(key);
        if (recipe.isEmpty() || !(recipe.get().value() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting)) {
            bad.add("echo_slip recipe missing");
            return;
        }
        for (var crystal : List.of(net.minecraft.world.item.Items.AMETHYST_SHARD, com.mnemolith.content.ModItems.RECOLLITE_SHARD.get())) {
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER),
                    new net.minecraft.world.item.ItemStack(crystal), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE)));
            if (!crafting.matches(input, level) || !crafting.assemble(input).is(com.mnemolith.content.ModItems.ECHO_SLIP.get())) {
                bad.add("echo_slip not craftable from " + crystal);
            }
        }
    }

    /**
     * A block whose model has open gaps but whose occlusion is a full cube makes the game skip its neighbours'
     * touching faces: the world shows through the gaps (the hush loom's open frame showed the void behind it).
     */
    static void occlusion(List<String> bad) {
        for (var holder : ModBlocks.BLOCKS.getEntries()) {
            Block block = holder.get();
            String id = holder.getId().getPath();
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                boolean fullOcclusion = Block.isShapeFullBlock(state.getOcclusionShape());
                if (OPEN_MODELS.contains(id) && state.canOcclude() && fullOcclusion) {
                    bad.add(id + " occludes neighbours but is not a full cube");
                    break;
                }
            }
        }
    }
}
