package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.EmberBrazierBlock;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith ember2qa}. Embers, stage 2: the ember brazier. Recipe and loot; an ember fed by hand lights it and
 * is spent, a full brazier refuses; a burn takes the loudest imprint of the chunk, spends one charge and writes
 * nothing; an empty chunk spends nothing; the last charge puts it out; light and comparator follow the charge. The
 * scheduled burning and a real player's hand are the live test {@code brazier_burns_down}.
 */
public final class Ember2Qa {
    private static final String[] NAMES = {"blockData", "recipe", "loot", "feedByHand", "fullRefuses", "burnsLoudest", "writesNothing",
            "emptySpendsNothing", "goesCold", "lightAndComparator", "scheduled", "guide"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-dddddddddddd");
    private static int salt;

    private Ember2Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        BlockState fresh = ModBlocks.EMBER_BRAZIER.get().defaultBlockState();
        ok[0] = fresh.getValue(EmberBrazierBlock.CHARGE) == 0 && !fresh.getValue(EmberBrazierBlock.LIT)
                && new ItemStack(ModItems.EMBER_BRAZIER.get()).getMaxStackSize() == 64;
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("ember_brazier")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack i = new ItemStack(Items.IRON_INGOT);
            ItemStack b = new ItemStack(Items.POLISHED_BLACKSTONE);
            CraftingInput input = CraftingInput.of(3, 3, List.of(i.copy(), ItemStack.EMPTY, i.copy(), i.copy(), new ItemStack(ModItems.MEMORY_EMBER.get()), i.copy(),
                    b.copy(), b.copy(), b.copy()));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.EMBER_BRAZIER.get())
                    && server.getAdvancements().get(id("recipes/ember_brazier")) != null;
        }
        ok[2] = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("blocks/ember_brazier"))) != LootTable.EMPTY;

        BlockPos a = column(level, (spawn.getX() >> 4) + 410 + salt * 3, (spawn.getZ() >> 4) - 390);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Ember2Qa"));
        BlockPos pos = a;
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 2.5D, 0.0F, 0.0F);
            level.setBlock(pos, fresh, 3);
            QaSupport.clear(level, pos);

            // ---------- feeding ----------
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MEMORY_EMBER.get(), 8));
            use(level, pos, player);
            BlockState fed = level.getBlockState(pos);
            ok[3] = fed.getValue(EmberBrazierBlock.CHARGE) == EmberBrazierBlock.CHARGE_PER_EMBER && fed.getValue(EmberBrazierBlock.LIT)
                    && player.getMainHandItem().getCount() == 7;
            use(level, pos, player);
            use(level, pos, player);
            use(level, pos, player);
            use(level, pos, player);
            BlockState full = level.getBlockState(pos);
            ok[4] = full.getValue(EmberBrazierBlock.CHARGE) == EmberBrazierBlock.MAX_CHARGE && player.getMainHandItem().getCount() == 4;
            notes.add("fed=" + fed.getValue(EmberBrazierBlock.CHARGE) + " full=" + full.getValue(EmberBrazierBlock.CHARGE) + " left=" + player.getMainHandItem().getCount());
            ok[10] = level.getBlockTicks().hasScheduledTick(pos, ModBlocks.EMBER_BRAZIER.get());

            // ---------- burning ----------
            QaSupport.clear(level, pos);
            ImprintWriter.write(level, pos, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, pos, List.of(ImprintTag.EXPLOSION), null, false);
            ImprintWriter.write(level, pos, List.of(ImprintTag.BUILD), null, false);
            ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            int before = memory == null ? 0 : memory.imprintCount();
            ImprintTag loudest = memory == null ? null : memory.highest().map(Imprint::tag).orElse(null);
            int loudestBefore = count(memory, loudest);
            EmberBrazierBlock.Burn burn = EmberBrazierBlock.burn(level, pos);
            memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            int after = memory == null ? 0 : memory.imprintCount();
            ok[5] = burn == EmberBrazierBlock.Burn.BURNED && before == 3 && after == 2 && count(memory, loudest) == loudestBefore - 1
                    && level.getBlockState(pos).getValue(EmberBrazierBlock.CHARGE) == EmberBrazierBlock.MAX_CHARGE - 1;
            ok[6] = memory != null && !memory.imprintsCopy().stream().anyMatch(i -> i.tag() == ImprintTag.FIRE);
            notes.add("burn=" + burn + " before=" + before + " after=" + after + " loudest=" + loudest);

            QaSupport.clear(level, pos);
            int chargeBefore = level.getBlockState(pos).getValue(EmberBrazierBlock.CHARGE);
            EmberBrazierBlock.Burn idle = EmberBrazierBlock.burn(level, pos);
            ok[7] = idle == EmberBrazierBlock.Burn.EMPTY && level.getBlockState(pos).getValue(EmberBrazierBlock.CHARGE) == chargeBefore;

            // ---------- the last charge ----------
            level.setBlock(pos, fresh.setValue(EmberBrazierBlock.CHARGE, 1).setValue(EmberBrazierBlock.LIT, true), 3);
            int lit = level.getBlockState(pos).getLightEmission(level, pos);
            int signal = level.getBlockState(pos).getAnalogOutputSignal(level, pos, Direction.NORTH);
            ImprintWriter.write(level, pos, List.of(ImprintTag.BUILD), null, false);
            EmberBrazierBlock.Burn last = EmberBrazierBlock.burn(level, pos);
            BlockState cold = level.getBlockState(pos);
            ok[8] = last == EmberBrazierBlock.Burn.BURNED && cold.getValue(EmberBrazierBlock.CHARGE) == 0 && !cold.getValue(EmberBrazierBlock.LIT)
                    && EmberBrazierBlock.burn(level, pos) == EmberBrazierBlock.Burn.COLD;
            int dark = cold.getLightEmission(level, pos);
            level.setBlock(pos, fresh.setValue(EmberBrazierBlock.CHARGE, 12).setValue(EmberBrazierBlock.LIT, true), 3);
            int signal12 = level.getBlockState(pos).getAnalogOutputSignal(level, pos, Direction.NORTH);
            ok[9] = lit == 13 && dark == 0 && signal == 1 && signal12 == 12;
            notes.add("light lit=" + lit + " dark=" + dark + " signal=" + signal + "/" + signal12);
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, pos);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[11] = GuideBook.pageCount() == GuideBook.PAGE_COUNT && guidePage("brazier");
        return new QaReport("ember2qa", NAMES, ok, notes);
    }

    private static void use(ServerLevel level, BlockPos pos, FakePlayer player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D), Direction.UP, pos, false);
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    }

    private static int count(ChunkMemory memory, ImprintTag tag) {
        if (memory == null || tag == null) {
            return 0;
        }
        return (int) memory.imprintsCopy().stream().filter(i -> i.tag() == tag).count();
    }

    private static boolean guidePage(String page) {
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (page.equals(GuideBook.pageId(i))) {
                return true;
            }
        }
        return false;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
