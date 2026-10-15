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
import com.mnemolith.content.item.PickaxeReading;
import com.mnemolith.content.item.RecollitePickaxeItem;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith pickaxeqa}. The recollite pickaxe: the item (iron tier, 500 uses, repaired with recollite, a
 * pickaxe for enchanting), its recipe, and the reading: the chunk's loudest imprint, hushed by a mute stone; fire
 * smelts, portal pulls the drops home, death adds experience, silence leaves the break unwritten; the line and a tag's
 * effect at most once per cooldown; a flicker surfaces in Memory Hollows only. Works on the flat game test world.
 */
public final class PickaxeQa {
    private static final String[] NAMES = {"itemData", "recipe", "readNone", "readLoudest", "muteHushes", "fireSmelts", "portalPulls",
            "deathXp", "silenceQuiet", "effects", "cooldown", "hollowFlicker", "plainNoFlicker", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("55555555-1111-2222-3333-999999999999");
    private static int salt;

    private PickaxeQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        // ---------- the item ----------
        ItemStack pick = new ItemStack(ModItems.RECOLLITE_PICKAXE.get());
        ok[0] = pick.getMaxDamage() == 500 && pick.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())
                && pick.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState()) && !pick.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())
                && pick.getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0F && pick.is(ItemTags.PICKAXES)
                && new ItemStack(ModItems.RECOLLITE_SHARD.get()).is(RecollitePickaxeItem.REPAIRS);
        notes.add("durability=" + pick.getMaxDamage() + " pickaxes=" + pick.is(ItemTags.PICKAXES));

        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("recollite_pickaxe")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack r = new ItemStack(ModItems.RECOLLITE_SHARD.get());
            ItemStack s = new ItemStack(Items.STICK);
            CraftingInput input = CraftingInput.of(3, 3, List.of(r.copy(), new ItemStack(Items.IRON_INGOT), r.copy(),
                    ItemStack.EMPTY, s.copy(), ItemStack.EMPTY, ItemStack.EMPTY, s.copy(), ItemStack.EMPTY));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.RECOLLITE_PICKAXE.get())
                    && server.getAdvancements().get(id("recollite_pickaxe")) != null && server.getAdvancements().get(id("recipes/recollite_pickaxe")) != null;
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 130 + salt * 3, (spawn.getZ() >> 4) - 66);
        BlockPos plain = a.offset(48, 0, 0);
        tickColumn(level, a);
        tickColumn(level, plain);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "PickaxeQa"));
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.removeAllEffects();
            player.setItemInHand(InteractionHand.MAIN_HAND, pick.copy());
            player.snapTo(plain.getX() + 0.5D, plain.getY(), plain.getZ() + 0.5D, 0.0F, 0.0F);
            LevelChunk plainChunk = level.getChunkAt(plain);
            LoadedChunkMemory.clear(plainChunk);

            // ---------- reading ----------
            ok[2] = PickaxeReading.read(level, plain) == null && key(PickaxeReading.line(null)).equals("item.mnemolith.recollite_pickaxe.reads_none");
            seed(level, plain, ImprintTag.PATH);
            seed(level, plain, ImprintTag.DEATH);
            ok[3] = PickaxeReading.read(level, plain) == ImprintTag.DEATH && key(PickaxeReading.line(ImprintTag.DEATH)).equals("item.mnemolith.recollite_pickaxe.reads");
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(plainChunk);
            memory.addMuteStone(plain.offset(1, 0, 1));
            ok[4] = PickaxeReading.read(level, plain) == null;
            memory.removeMuteStone(plain.offset(1, 0, 1));

            // ---------- per block ----------
            reset(level, plainChunk, plain, ImprintTag.FIRE);
            List<ItemEntity> drops = new ArrayList<>(List.of(drop(level, plain, new ItemStack(Items.RAW_IRON, 2)), drop(level, plain, new ItemStack(Items.COBBLESTONE))));
            PickaxeReading.surface(level, player, plain, drops, 0, level.getGameTime());
            ok[5] = drops.get(0).getItem().is(Items.IRON_INGOT) && drops.get(0).getItem().getCount() == 2 && drops.get(1).getItem().is(Items.STONE);
            notes.add("fire drops=" + drops.get(0).getItem() + ", " + drops.get(1).getItem());

            reset(level, plainChunk, plain, ImprintTag.PORTAL);
            player.snapTo(plain.getX() + 3.5D, plain.getY(), plain.getZ() + 0.5D, 0.0F, 0.0F);
            ItemEntity far = drop(level, plain, new ItemStack(Items.COAL));
            PickaxeReading.surface(level, player, plain, List.of(far), 0, level.getGameTime());
            ok[6] = far.distanceToSqr(player) < 1.0D && far.getItem().is(Items.COAL);
            player.snapTo(plain.getX() + 0.5D, plain.getY(), plain.getZ() + 0.5D, 0.0F, 0.0F);

            reset(level, plainChunk, plain, ImprintTag.DEATH);
            int xp = PickaxeReading.surface(level, player, plain, new ArrayList<>(), 2, level.getGameTime());
            int none = PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, level.getGameTime());
            ok[7] = xp == 2 + PickaxeReading.DEATH_XP && none == 0;

            reset(level, plainChunk, plain, ImprintTag.SILENCE);
            boolean quiet = PickaxeReading.quiet(level, plain, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            boolean loudWithIron = PickaxeReading.quiet(level, plain, player);
            player.setItemInHand(InteractionHand.MAIN_HAND, pick.copy());
            reset(level, plainChunk, plain, ImprintTag.FORGOTTEN);
            boolean forgottenQuiet = PickaxeReading.quiet(level, plain, player);
            reset(level, plainChunk, plain, ImprintTag.PATH);
            boolean pathQuiet = PickaxeReading.quiet(level, plain, player);
            ok[8] = quiet && !loudWithIron && forgottenQuiet && !pathQuiet;

            // ---------- the reading's effect, once per cooldown ----------
            player.removeAllEffects();
            reset(level, plainChunk, plain, ImprintTag.BUILD);
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, level.getGameTime());
            MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
            player.removeAllEffects();
            reset(level, plainChunk, plain, ImprintTag.BOSS);
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, level.getGameTime());
            MobEffectInstance strong = player.getEffect(MobEffects.HASTE);
            reset(level, plainChunk, plain, ImprintTag.FALL);
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, level.getGameTime());
            ok[9] = haste != null && haste.getAmplifier() == 0 && strong != null && strong.getAmplifier() == 1 && player.hasEffect(MobEffects.SLOW_FALLING);

            player.removeAllEffects();
            reset(level, plainChunk, plain, ImprintTag.FALL);
            long now = level.getGameTime();
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, now);
            long reads = PickaxeReading.reads();
            player.removeAllEffects();
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, now + 1);
            boolean held = PickaxeReading.reads() == reads && !player.hasEffect(MobEffects.SLOW_FALLING);
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, now + PickaxeReading.READ_COOLDOWN);
            ok[10] = held && PickaxeReading.reads() == reads + 1 && player.hasEffect(MobEffects.SLOW_FALLING);

            // ---------- surfacing in Memory Hollows ----------
            CommandSourceStack console = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
            for (int y0 = a.getY() - 12; y0 <= a.getY() + 12; y0 += 8) {
                try {
                    server.getCommands().getDispatcher().execute(String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows",
                            a.getX() - 20, y0, a.getZ() - 20, a.getX() + 20, Math.min(y0 + 7, a.getY() + 12), a.getZ() + 20), console);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    notes.add("fillbiome failed: " + e.getMessage());
                }
            }
            LevelChunk hollowChunk = level.getChunkAt(a);
            reset(level, hollowChunk, a, ImprintTag.TRADE);
            player.snapTo(a.getX() + 2.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            HollowFlickers.clear();
            PickaxeReading.surface(level, player, a, new ArrayList<>(), 0, level.getGameTime());
            ok[11] = Hollows.is(level.getBiome(a)) && HollowFlickers.active().stream()
                    .anyMatch(f -> f.pos().equals(a) && f.tag() == ImprintTag.TRADE.ordinal());
            notes.add("hollow=" + Hollows.is(level.getBiome(a)) + " flickers=" + HollowFlickers.active().size());
            HollowFlickers.clear();
            reset(level, plainChunk, plain, ImprintTag.TRADE);
            player.snapTo(plain.getX() + 0.5D, plain.getY(), plain.getZ() + 0.5D, 0.0F, 0.0F);
            PickaxeReading.surface(level, player, plain, new ArrayList<>(), 0, level.getGameTime());
            ok[12] = !Hollows.is(level.getBiome(plain)) && HollowFlickers.active().isEmpty();
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(plain).inflate(6.0D))) {
                item.discard();
            }
        } finally {
            HollowFlickers.clear();
            PickaxeReading.resetCooldown(PLAYER);
            player.removeAllEffects();
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
            releaseColumn(level, ChunkPos.containing(plain));
        }

        // ---------- guide ----------
        int forgottenPage = -1;
        int pickaxePage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("forgotten")) {
                forgottenPage = i;
            } else if (GuideBook.pageId(i).equals("pickaxe")) {
                pickaxePage = i;
            }
        }
        ok[13] = forgottenPage >= 0 && pickaxePage == forgottenPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("pickaxeqa", NAMES, ok, notes);
    }

    /** Leaves only one imprint of {@code tag} in the chunk and clears the reading cooldown. */
    private static void reset(ServerLevel level, LevelChunk chunk, BlockPos pos, ImprintTag tag) {
        LoadedChunkMemory.clear(chunk);
        seed(level, pos, tag);
        PickaxeReading.resetCooldown(PLAYER);
    }

    private static void seed(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(pos));
        memory.addImprint(new Imprint(tag, 3, pos, Optional.empty(), 800 + tag.ordinal() + salt, level.getGameTime()), 8);
    }

    private static ItemEntity drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        return new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
    }

    private static String key(net.minecraft.network.chat.Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
