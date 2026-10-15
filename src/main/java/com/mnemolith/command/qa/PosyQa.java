package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.RemembrancePosy;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith posyqa}. The remembrance posy: item and recipe; a faded laid to rest (gone without dying, its copied
 * memory written back, nothing forgotten, a recollite shard left, one posy spent); a faded with nothing copied writes
 * nothing; an archive keeper calmed and still standing; creative keeps the posy; a dead faded takes nothing; the guide
 * page. Works on the flat game test world (plains, so no forgetting would happen anyway; the write-back is the check).
 */
public final class PosyQa {
    private static final String[] NAMES = {"itemData", "recipe", "restWritesBack", "restLeavesShard", "restSpends", "restNoCopy",
            "keeperCalmed", "creativeKeeps", "deadIgnored", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-aaaaaaaaaaaa");
    private static int salt;

    private PosyQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = new ItemStack(ModItems.REMEMBRANCE_POSY.get()).getMaxStackSize() == 16;
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("remembrance_posy")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack f = new ItemStack(ModItems.FORGET_ME_NOT.get());
            CraftingInput input = CraftingInput.of(2, 2, List.of(f.copy(), f.copy(), new ItemStack(Items.STRING), f.copy()));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.REMEMBRANCE_POSY.get())
                    && server.getAdvancements().get(id("recipes/remembrance_posy")) != null;
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 210 + salt * 3, (spawn.getZ() >> 4) - 66);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "PosyQa"));
        List<net.minecraft.world.entity.Entity> extras = new ArrayList<>();
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 1.5D, 0.0F, 0.0F);
            LevelChunk chunk = level.getChunkAt(a);
            LoadedChunkMemory.clear(chunk);
            ItemStack posies = new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 4);

            Faded copier = faded(level, a.offset(1, 0, 0));
            extras.add(copier);
            copier.copy(a, ImprintTag.FALL);
            RemembrancePosy.Result rest = RemembrancePosy.offer(level, player, copier, posies);
            ok[2] = rest == RemembrancePosy.Result.RESTED && copier.isRemoved() && !copier.isDeadOrDying()
                    && count(level, a, ImprintTag.FALL) == 1 && count(level, a, ImprintTag.FORGOTTEN) == 0;
            int shards = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(4.0D))) {
                shards += item.getItem().is(ModItems.RECOLLITE_SHARD.get()) ? item.getItem().getCount() : 0;
                extras.add(item);
            }
            ok[3] = shards == 1;
            ok[4] = posies.getCount() == 3;
            notes.add("rest=" + rest + " fall=" + count(level, a, ImprintTag.FALL) + " shards=" + shards + " posies=" + posies.getCount());

            LoadedChunkMemory.clear(chunk);
            Faded blank = faded(level, a.offset(-1, 0, 0));
            extras.add(blank);
            RemembrancePosy.Result blankRest = RemembrancePosy.offer(level, player, blank, posies);
            ChunkMemory memory = LoadedChunkMemory.existing(chunk);
            ok[5] = blankRest == RemembrancePosy.Result.RESTED && blank.isRemoved() && (memory == null || memory.imprintCount() == 0);

            Faded keeper = faded(level, a.offset(0, 0, 2));
            extras.add(keeper);
            keeper.makeKeeper(a.offset(0, 0, 3));
            keeper.provoke(player);
            int angry = keeper.angerTicks();
            RemembrancePosy.Result calm = RemembrancePosy.offer(level, player, keeper, posies);
            ok[6] = angry > 0 && calm == RemembrancePosy.Result.CALMED && keeper.isAlive() && keeper.keeper() && keeper.angerTicks() == 0
                    && keeper.getTarget() == null && posies.getCount() == 1;
            notes.add("keeper anger " + angry + "->" + keeper.angerTicks() + " posies=" + posies.getCount());

            player.setGameMode(GameType.CREATIVE);
            Faded another = faded(level, a.offset(2, 0, 1));
            extras.add(another);
            RemembrancePosy.Result creative = RemembrancePosy.offer(level, player, another, posies);
            ok[7] = creative == RemembrancePosy.Result.RESTED && posies.getCount() == 1;
            player.setGameMode(GameType.SURVIVAL);

            Faded dead = faded(level, a.offset(-2, 0, 1));
            extras.add(dead);
            dead.discard();
            ok[8] = RemembrancePosy.offer(level, player, dead, posies) == RemembrancePosy.Result.NONE && posies.getCount() == 1;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(6.0D))) {
                extras.add(item);
            }
        } finally {
            for (var entity : extras) {
                if (entity.isAlive()) {
                    entity.discard();
                }
            }
            player.setGameMode(GameType.SURVIVAL);
            releaseColumn(level, ChunkPos.containing(a));
        }

        int lanternPage = -1;
        int posyPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("lantern")) {
                lanternPage = i;
            } else if (GuideBook.pageId(i).equals("posy")) {
                posyPage = i;
            }
        }
        ok[9] = lanternPage >= 0 && posyPage == lanternPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("posyqa", NAMES, ok, notes);
    }

    private static Faded faded(ServerLevel level, BlockPos pos) {
        Faded faded = ModEntities.FADED.get().create(level, EntitySpawnReason.COMMAND);
        if (faded == null) {
            throw new IllegalStateException("faded did not create");
        }
        faded.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        faded.setNoAi(true);
        level.addFreshEntity(faded);
        return faded;
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
