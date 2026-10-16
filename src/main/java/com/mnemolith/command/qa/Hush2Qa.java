package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.block.EmberBrazierBlock;
import com.mnemolith.content.block.HushLoomBlock;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.entity.mob.Hushling;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
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
 * {@code /mnemolith hush2qa}. Pale Chorus, stage 2: the hush loom. Recipe and loot; a filament used by hand threads it
 * with the loudest tag of its chunk and is spent; an empty chunk takes nothing; a hushling cannot thin and a brazier
 * cannot burn the kept tag, and both stop when only kept imprints are left; writes are unchanged; the mark survives a
 * save round trip; breaking the loom forgets it. A real player's hand is the live test {@code loom_keeps_thread}.
 */
public final class Hush2Qa {
    private static final String[] NAMES = {"blockData", "recipe", "loot", "threadByHand", "emptyTakesNothing", "thinSkipsKept",
            "burnSkipsKept", "writesUnchanged", "savedMark", "breakForgets", "guide"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-eeeeeeeeeeee");
    private static int salt;

    private Hush2Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        BlockState fresh = ModBlocks.HUSH_LOOM.get().defaultBlockState();
        ok[0] = fresh.getValue(HushLoomBlock.KEPT) == 0 && !fresh.getValue(HushLoomBlock.THREADED) && HushLoomBlock.kept(fresh).isEmpty()
                && new ItemStack(ModItems.HUSH_LOOM.get()).getMaxStackSize() == 64;
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("hush_loom")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack f = new ItemStack(ModItems.HUSH_FILAMENT.get());
            ItemStack s = new ItemStack(Items.END_STONE_BRICKS);
            CraftingInput input = CraftingInput.of(3, 3, List.of(f.copy(), new ItemStack(Items.PURPUR_BLOCK), f.copy(), s.copy(), f.copy(), s.copy(),
                    s.copy(), s.copy(), s.copy()));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.HUSH_LOOM.get())
                    && server.getAdvancements().get(id("recipes/hush_loom")) != null;
        }
        ok[2] = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("blocks/hush_loom"))) != LootTable.EMPTY;

        BlockPos a = column(level, (spawn.getX() >> 4) + 490 + salt * 3, (spawn.getZ() >> 4) - 480);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Hush2Qa"));
        BlockPos pos = a;
        BlockPos brazier = a.east(2);
        Hushling singer = null;
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 2.5D, 0.0F, 0.0F);
            level.setBlock(pos, fresh, 3);
            QaSupport.clear(level, pos);

            // ---------- threading ----------
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HUSH_FILAMENT.get(), 4));
            use(level, pos, player);
            BlockState empty = level.getBlockState(pos);
            ok[4] = !empty.getValue(HushLoomBlock.THREADED) && player.getMainHandItem().getCount() == 4;
            ImprintWriter.write(level, pos, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, pos, List.of(ImprintTag.EXPLOSION), null, false);
            ImprintWriter.write(level, pos, List.of(ImprintTag.BUILD), null, false);
            use(level, pos, player);
            BlockState threaded = level.getBlockState(pos);
            ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            ok[3] = threaded.getValue(HushLoomBlock.THREADED) && HushLoomBlock.kept(threaded).orElse(null) == ImprintTag.EXPLOSION
                    && memory != null && memory.keeps(ImprintTag.EXPLOSION) && !memory.keeps(ImprintTag.PATH)
                    && player.getMainHandItem().getCount() == 3;
            notes.add("kept=" + HushLoomBlock.kept(threaded).orElse(null) + " left=" + player.getMainHandItem().getCount());

            // ---------- thinning: keep the quietest ----------
            HushLoomBlock.keep(level, pos, ImprintTag.PATH);
            singer = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
            if (singer != null) {
                singer.snapTo(pos.getX() + 0.5D, pos.getY() + 1, pos.getZ() + 1.5D, 0.0F, 0.0F);
                singer.setNoAi(true);
                level.addFreshEntity(singer);
                String first = singer.thin(level);
                memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
                boolean pathStays = count(memory, ImprintTag.PATH) == 1 && count(memory, ImprintTag.BUILD) == 0 && count(memory, ImprintTag.EXPLOSION) == 1;
                String second = singer.thin(level);
                memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
                String third = singer.thin(level);
                boolean onlyKept = count(memory, ImprintTag.PATH) == 1 && count(memory, ImprintTag.EXPLOSION) == 0;
                ok[5] = "thinned".equals(first) && pathStays && "thinned".equals(second) && "empty".equals(third) && onlyKept;
                notes.add("thin " + first + "/" + second + "/" + third);
            }

            // ---------- burning: keep the loudest ----------
            QaSupport.clear(level, pos);
            HushLoomBlock.keep(level, pos, ImprintTag.EXPLOSION);
            level.setBlock(brazier, ModBlocks.EMBER_BRAZIER.get().defaultBlockState().setValue(EmberBrazierBlock.CHARGE, 8).setValue(EmberBrazierBlock.LIT, true), 3);
            ImprintWriter.write(level, pos, List.of(ImprintTag.EXPLOSION), null, false);
            ImprintWriter.write(level, pos, List.of(ImprintTag.BUILD), null, false);
            EmberBrazierBlock.Burn burn = EmberBrazierBlock.burn(level, brazier);
            memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            boolean loudStays = count(memory, ImprintTag.EXPLOSION) == 1 && count(memory, ImprintTag.BUILD) == 0;
            EmberBrazierBlock.Burn idle = EmberBrazierBlock.burn(level, brazier);
            ok[6] = burn == EmberBrazierBlock.Burn.BURNED && loudStays && idle == EmberBrazierBlock.Burn.EMPTY
                    && level.getBlockState(brazier).getValue(EmberBrazierBlock.CHARGE) == 7;
            notes.add("burn " + burn + "/" + idle);

            // ---------- writes are not the loom's business ----------
            ImprintWriter.write(level, pos, List.of(ImprintTag.FIRE), null, false);
            memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            ok[7] = count(memory, ImprintTag.FIRE) == 1;

            // ---------- the mark is saved ----------
            if (memory != null) {
                var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                Tag saved = ChunkMemory.CODEC.codec().encodeStart(ops, memory).getOrThrow();
                ChunkMemory back = ChunkMemory.CODEC.codec().parse(ops, saved).getOrThrow();
                ok[8] = back.keeps(ImprintTag.EXPLOSION) && back.loomsCopy().size() == 1 && back.loomsCopy().get(0).pos().equals(pos);
            }

            // ---------- breaking forgets ----------
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
            ok[9] = memory != null && !memory.keeps(ImprintTag.EXPLOSION) && memory.loomsCopy().isEmpty()
                    && EmberBrazierBlock.burn(level, brazier) == EmberBrazierBlock.Burn.BURNED;
        } finally {
            if (singer != null) {
                singer.discard();
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(brazier, Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, pos);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[10] = GuideBook.pageCount() == GuideBook.PAGE_COUNT && guidePage("loom");
        return new QaReport("hush2qa", NAMES, ok, notes);
    }

    private static void use(ServerLevel level, BlockPos pos, FakePlayer player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D), Direction.UP, pos, false);
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
    }

    private static int count(ChunkMemory memory, ImprintTag tag) {
        if (memory == null) {
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
