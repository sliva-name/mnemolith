package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.HollowForgetting;
import com.mnemolith.worldgen.hollows.HollowLanterns;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * {@code /mnemolith lanternqa}. The recollite lantern: block data (light 13, pickaxe, drops itself), recipe, the guard
 * radius (8 across, 4 up or down), forgetting refused beside it and working away from it, flickers lingering twice as
 * long beside it, an expired guarded flicker not forgotten, and the guide page. Works on the flat game test world.
 */
public final class LanternQa {
    private static final String[] NAMES = {"blockData", "recipe", "guardRadius", "forgetKept", "forgetAway", "flickerLingers",
            "flickerNormal", "expiryKept", "removedForgets", "guidePage"};
    private static int salt;

    private LanternQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        BlockState lantern = ModBlocks.RECOLLITE_LANTERN.get().defaultBlockState();
        ok[0] = lantern.getLightEmission() == 13 && lantern.is(BlockTags.MINEABLE_WITH_PICKAXE) && lantern.requiresCorrectToolForDrops()
                && ModBlocks.RECOLLITE_LANTERN.get().getLootTable().isPresent()
                && server.reloadableRegistries().getLootTable(ModBlocks.RECOLLITE_LANTERN.get().getLootTable().get()) != net.minecraft.world.level.storage.loot.LootTable.EMPTY;

        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("recollite_lantern")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack h = new ItemStack(ModItems.HOLLOWSTONE_BRICKS.get());
            ItemStack r = new ItemStack(ModItems.RECOLLITE_SHARD.get());
            CraftingInput input = CraftingInput.of(3, 3, List.of(h.copy(), r.copy(), h.copy(), r.copy(), new ItemStack(ModItems.FORGET_ME_NOT.get()), r.copy(),
                    h.copy(), r.copy(), h.copy()));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.RECOLLITE_LANTERN.get())
                    && server.getAdvancements().get(id("recipes/recollite_lantern")) != null;
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 170 + salt * 3, (spawn.getZ() >> 4) - 66);
        tickColumn(level, a);
        BlockPos lamp = a.offset(0, 1, 0);
        try {
            CommandSourceStack console = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
            for (int y0 = a.getY() - 12; y0 <= a.getY() + 12; y0 += 8) {
                try {
                    server.getCommands().getDispatcher().execute(String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows",
                            a.getX() - 24, y0, a.getZ() - 24, a.getX() + 24, Math.min(y0 + 7, a.getY() + 12), a.getZ() + 24), console);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    notes.add("fillbiome failed: " + e.getMessage());
                }
            }
            HollowFlickers.clear();
            level.setBlockAndUpdate(lamp, lantern);
            BlockPos near = a.offset(HollowLanterns.RADIUS, 0, -3);
            BlockPos above = a.offset(2, HollowLanterns.HEIGHT + 1, 0);
            BlockPos away = a.offset(22, 0, 0);
            ok[2] = HollowLanterns.guarded(level, near) && HollowLanterns.guarded(level, a) && !HollowLanterns.guarded(level, away)
                    && HollowLanterns.guarded(level, a.offset(2, HollowLanterns.HEIGHT, 0)) && !HollowLanterns.guarded(level, above.above());

            LevelChunk chunk = level.getChunkAt(a);
            LoadedChunkMemory.clear(chunk);
            seed(level, a, ImprintTag.PATH);
            long kept = HollowLanterns.keptCount();
            boolean forgot = HollowForgetting.forget(level, a, ImprintTag.PATH);
            ok[3] = Hollows.is(level.getBiome(a)) && !forgot && count(level, a, ImprintTag.PATH) == 1 && count(level, a, ImprintTag.FORGOTTEN) == 0
                    && HollowLanterns.keptCount() == kept + 1;
            notes.add("hollow=" + Hollows.is(level.getBiome(a)) + " forgot beside lantern=" + forgot);

            LevelChunk awayChunk = level.getChunkAt(away);
            boolean sameChunk = awayChunk == chunk;
            LoadedChunkMemory.clear(awayChunk);
            seed(level, away, ImprintTag.TRADE);
            boolean forgotAway = HollowForgetting.forget(level, away, ImprintTag.TRADE);
            ok[4] = !sameChunk && forgotAway && count(level, away, ImprintTag.TRADE) == 0 && count(level, away, ImprintTag.FORGOTTEN) == 1;

            long now = level.getGameTime();
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            ok[5] = HollowFlickers.active().stream().anyMatch(f -> f.pos().equals(a) && f.until() == now + 2L * HollowFlickers.CATCH_WINDOW);
            HollowFlickers.send(level, new HollowFlickerPayload(away, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            ok[6] = HollowFlickers.active().stream().anyMatch(f -> f.pos().equals(away) && f.until() == now + HollowFlickers.CATCH_WINDOW);
            HollowFlickers.clear();

            // a guarded flicker outlives the normal window, then closes without forgetting
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            int early = HollowFlickers.expire(level, now + HollowFlickers.CATCH_WINDOW + 1, 1.0D);
            boolean stillThere = !HollowFlickers.active().isEmpty();
            int late = HollowFlickers.expire(level, now + 2L * HollowFlickers.CATCH_WINDOW + 1, 1.0D);
            ok[7] = early == 0 && stillThere && late == 0 && HollowFlickers.active().isEmpty() && count(level, a, ImprintTag.PATH) == 1
                    && count(level, a, ImprintTag.FORGOTTEN) == 0;
            notes.add("guarded expiry early=" + early + " still=" + stillThere + " late=" + late);

            level.setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());
            boolean forgotAfter = HollowForgetting.forget(level, a, ImprintTag.PATH);
            ok[8] = forgotAfter && count(level, a, ImprintTag.PATH) == 0 && count(level, a, ImprintTag.FORGOTTEN) == 1;
        } finally {
            HollowFlickers.clear();
            level.setBlockAndUpdate(lamp, Blocks.AIR.defaultBlockState());
            releaseColumn(level, ChunkPos.containing(a));
        }

        int pickaxePage = -1;
        int lanternPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("pickaxe")) {
                pickaxePage = i;
            } else if (GuideBook.pageId(i).equals("lantern")) {
                lanternPage = i;
            }
        }
        ok[9] = pickaxePage >= 0 && lanternPage == pickaxePage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("lanternqa", NAMES, ok, notes);
    }

    private static void seed(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(pos));
        memory.addImprint(new Imprint(tag, 3, pos, Optional.empty(), 900 + tag.ordinal() + salt, level.getGameTime()), 8);
    }

    private static int count(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        int n = 0;
        for (int i = 0; memory != null && i < memory.imprintCount(); i++) {
            n += memory.imprintAt(i).tag() == tag ? 1 : 0;
        }
        return n;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
