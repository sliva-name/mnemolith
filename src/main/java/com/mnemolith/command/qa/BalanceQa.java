package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mnemolith.config.CommonConfig;
import com.mnemolith.config.ServerConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.EmberBrazierBlock;
import com.mnemolith.entity.echo.ScarEntity;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith balanceqa}. The balance pass invariants (see {@code docs/design/balance.md}): the band
 * thresholds still mean what the lens says; instability cooling clears a failed-compose spike in minutes, not
 * hours; a natural storm needs sustained presence in a fracture (buildup first, warning before the rolls, a
 * passing visit never storms) with an expected time inside the target band; surplus slips sink into the ember
 * brazier at half an ember; the Scar and the Unremembered pay amethyst (the currency every stage spends);
 * echo slips stay craftable from amethyst or recollite, and the lens stays an early amethyst craft.
 */
public final class BalanceQa {
    private static final String[] NAMES = {"thresholds", "decayClearsSpike", "stormBuildupBeforeRolls", "stormWarningFirst",
            "stormExpectedBand", "brazierSlipSink", "scarPaysBroadly", "unrememberedPaysBroadly", "echoSlipCraftable",
            "lensStaysEarly", "lang"};
    private static final java.util.UUID PLAYER = java.util.UUID.fromString("88888888-6666-2222-3333-aeaeaeaeaeae");
    private static int salt;

    private BalanceQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = MemoryPressure.band(CommonConfig.SATURATED_THRESHOLD.get() - 1) == PressureBand.CALM
                && MemoryPressure.band(CommonConfig.SATURATED_THRESHOLD.get()) == PressureBand.SATURATED
                && MemoryPressure.band(CommonConfig.OVERLOADED_THRESHOLD.get()) == PressureBand.OVERLOADED
                && MemoryPressure.band(CommonConfig.FRACTURE_THRESHOLD.get()) == PressureBand.FRACTURE;

        int spike = CommonConfig.FAILURE_PRESSURE_SPIKE.get();
        long clearTicks = CommonConfig.INSTABILITY_DECAY.get() <= 0 ? Long.MAX_VALUE
                : (long) Math.ceil((double) spike / CommonConfig.INSTABILITY_DECAY.get()) * CommonConfig.INSTABILITY_DECAY_TICKS.get();
        ok[1] = clearTicks >= 600L && clearTicks <= 7200L;
        notes.add("spikeClearsTicks=" + clearTicks);

        int buildup = CommonConfig.STORM_BUILDUP_SECONDS.get();
        int warning = CommonConfig.STORM_WARNING_SECONDS.get();
        double chance = CommonConfig.STORM_ATTEMPT_CHANCE.get();
        ok[2] = buildup > 0 && chance > 0.0D && chance <= 1.0D;
        ok[3] = warning > 0 && warning < buildup;
        double expected = buildup + (chance > 0.0D ? 1.0D / chance : Double.POSITIVE_INFINITY);
        ok[4] = expected >= 60.0D && expected <= 240.0D;
        notes.add("stormExpectedSeconds=" + Math.round(expected));

        BlockPos a = column(level, (spawn.getX() >> 4) + 700 + salt * 3, (spawn.getZ() >> 4) - 690);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "BalanceQa"));
        try {
            BlockState fresh = ModBlocks.EMBER_BRAZIER.get().defaultBlockState();
            level.setBlock(a, fresh, 3);
            boolean fedSlip = EmberBrazierBlock.feedSlip(level, a, level.getBlockState(a));
            int charge = level.getBlockState(a).getValue(EmberBrazierBlock.CHARGE);
            boolean fedEmber = EmberBrazierBlock.feed(level, a, level.getBlockState(a));
            int after = level.getBlockState(a).getValue(EmberBrazierBlock.CHARGE);
            ok[5] = fedSlip && charge == EmberBrazierBlock.chargePerSlip() && EmberBrazierBlock.chargePerSlip() > 0
                    && EmberBrazierBlock.chargePerSlip() < EmberBrazierBlock.chargePerEmber()
                    && fedEmber && after == charge + EmberBrazierBlock.chargePerEmber();
            notes.add("slipCharge=" + charge + " emberCharge=" + after);
            level.setBlock(a, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        } finally {
            releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(a));
        }

        List<ItemStack> scarDrops = ScarEntity.drops(level, 3, List.of(com.mnemolith.echo.graft.Temper.GRAVE), a);
        ok[6] = scarDrops.stream().anyMatch(s -> s.is(ModItems.SCAR_FRAGMENT.get()))
                && scarDrops.stream().anyMatch(s -> s.is(Items.AMETHYST_SHARD) && s.getCount() >= 3)
                && scarDrops.stream().anyMatch(s -> s.is(com.mnemolith.content.ModItems.RESIDUAL_SHARD.get()));

        var loot = server.reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, net.minecraft.resources.Identifier.fromNamespaceAndPath("mnemolith", "entities/unremembered")));
        ok[7] = loot != net.minecraft.world.level.storage.loot.LootTable.EMPTY
                && Feel3Qa.class.getResource("/data/mnemolith/loot_table/entities/unremembered.json") != null
                && lootJsonHas("amethyst_shard") && lootJsonHas("recollite_shard");

        // echo slips must stay craftable without Memory Hollows: amethyst or recollite (memory crystals)
        ok[8] = recipeHasIngredient(server, "echo_slip", "mnemolith:memory_crystals");
        ok[9] = recipeHasIngredient(server, "chronicle_lens", "mnemolith:memory_crystals") && !recipeHasIngredient(server, "chronicle_lens", "recollite_shard");

        boolean lang = true;
        for (String key : new String[] {"stormBuildupSeconds", "stormWarningSeconds", "emberBrazierChargePerSlip"}) {
            for (String l : new String[] {"en_us", "ru_ru"}) {
                lang &= Feel3Qa.langHasPub(l, "mnemolith.configuration." + key) && Feel3Qa.langHasPub(l, "mnemolith.configuration." + key + ".tooltip");
            }
        }
        for (String l : new String[] {"en_us", "ru_ru"}) {
            lang &= Feel3Qa.langHasPub(l, "mnemolith.storm.warning");
        }
        ok[10] = lang;

        return new QaReport("balanceqa", NAMES, ok, notes);
    }

    private static boolean lootJsonHas(String needle) {
        try (var in = BalanceQa.class.getResourceAsStream("/data/mnemolith/loot_table/entities/unremembered.json")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains(needle);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** Reads the shipped recipe json: the gate is data, and data is what a datapack would replace. */
    private static boolean recipeHasIngredient(net.minecraft.server.MinecraftServer server, String recipeId, String itemId) {
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(
                ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("mnemolith", recipeId)));
        if (recipe.isEmpty()) {
            return false;
        }
        try (var in = BalanceQa.class.getResourceAsStream("/data/mnemolith/recipe/" + recipeId + ".json")) {
            return in != null && new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains(itemId);
        } catch (java.io.IOException e) {
            return false;
        }
    }
}
