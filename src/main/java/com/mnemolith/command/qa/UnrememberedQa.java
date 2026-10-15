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
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.content.item.HollowEffigy;
import com.mnemolith.content.item.RemembrancePosy;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith unrememberedqa}. The Unremembered, stage 1: the hollow effigy (item, recipe), every call refusal
 * (outside the hollows, too little forgotten, a lantern, another one near) with nothing spent, a call (boss risen,
 * forgetting taken up, effigy spent), the lens scale, feeding and the lantern that stops it, faded called only in the
 * second half and up to the cap, the posy (refused unseen, a heavy hit revealed), death clearing forgotten imprints
 * around it and dropping a forgotten slip, sinking away, and the guide page. Paints a patch of Memory Hollows with
 * {@code fillbiome} on the flat game test world.
 */
public final class UnrememberedQa {
    private static final String[] NAMES = {"itemData", "recipe", "refuseOutside", "refuseTooFew", "refuseKept", "called",
            "refuseAnother", "lensScale", "feedHeals", "feedKept", "callFaded", "posyUnseen", "posyRevealed", "deathRemembers",
            "deathSlip", "sinkAway", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-bbbbbbbbbbbb");
    private static int salt;

    private UnrememberedQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = new ItemStack(ModItems.HOLLOW_EFFIGY.get()).getMaxStackSize() == 1;
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("hollow_effigy")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack e = ItemStack.EMPTY;
            ItemStack h = new ItemStack(ModBlocks.HOLLOWSTONE_BRICKS.get());
            ItemStack r = new ItemStack(ModItems.RECOLLITE_SHARD.get());
            ItemStack b = new ItemStack(ModBlocks.RECOLLITE_BLOCK.get());
            CraftingInput input = CraftingInput.of(3, 3, List.of(e, h.copy(), e, r.copy(), b, r.copy(), e, h.copy(), e));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.HOLLOW_EFFIGY.get())
                    && server.getAdvancements().get(id("recipes/hollow_effigy")) != null;
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 330 + salt * 4, (spawn.getZ() >> 4) - 270);
        BlockPos plain = column(level, (spawn.getX() >> 4) + 330 + salt * 4, (spawn.getZ() >> 4) - 262);
        tickColumn(level, a);
        tickColumn(level, plain);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "UnrememberedQa"));
        List<Entity> extras = new ArrayList<>();
        BlockPos lantern = a.offset(3, 0, 3);
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 3.5D, 0.0F, 0.0F);
            CommandSourceStack console = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
            for (int y0 = a.getY() - 12; y0 <= a.getY() + 12; y0 += 8) {
                try {
                    server.getCommands().getDispatcher().execute(String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows",
                            a.getX() - 24, y0, a.getZ() - 24, a.getX() + 24, Math.min(y0 + 7, a.getY() + 12), a.getZ() + 24), console);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    notes.add("fillbiome failed: " + e.getMessage());
                }
            }
            discardNear(level, a);
            LevelChunk chunk = level.getChunkAt(a);
            LevelChunk plainChunk = level.getChunkAt(plain);
            LoadedChunkMemory.clear(chunk);
            LoadedChunkMemory.clear(plainChunk);
            ItemStack effigy = new ItemStack(ModItems.HOLLOW_EFFIGY.get(), 1);

            // ---------- refusals, nothing spent ----------
            forgotten(level, plain, 4);
            HollowEffigy.Result outside = Hollows.is(level.getBiome(plain)) ? HollowEffigy.Result.FAILED : HollowEffigy.call(level, player, plain, effigy);
            ok[2] = outside == HollowEffigy.Result.NOT_HOLLOW && effigy.getCount() == 1;
            forgotten(level, a, 2);
            HollowEffigy.Result few = HollowEffigy.call(level, player, a, effigy);
            ok[3] = few == HollowEffigy.Result.TOO_FEW && effigy.getCount() == 1;
            forgotten(level, a, 2);
            level.setBlockAndUpdate(lantern, ModBlocks.RECOLLITE_LANTERN.get().defaultBlockState());
            HollowEffigy.Result kept = HollowEffigy.call(level, player, a, effigy);
            level.setBlockAndUpdate(lantern, Blocks.AIR.defaultBlockState());
            ok[4] = kept == HollowEffigy.Result.KEPT && effigy.getCount() == 1 && Unremembered.countForgotten(chunk) == 4;
            notes.add("outside=" + outside + " few=" + few + " kept=" + kept);

            // ---------- the call ----------
            HollowEffigy.Result called = HollowEffigy.call(level, player, a, effigy);
            List<Unremembered> risen = level.getEntitiesOfClass(Unremembered.class, new AABB(a).inflate(4.0D));
            extras.addAll(risen);
            ok[5] = called == HollowEffigy.Result.CALLED && risen.size() == 1 && Unremembered.countForgotten(chunk) == 0 && effigy.isEmpty();
            notes.add("called=" + called + " risen=" + risen.size() + " forgottenLeft=" + Unremembered.countForgotten(chunk));
            if (risen.isEmpty()) {
                return new QaReport("unrememberedqa", NAMES, ok, notes);
            }
            Unremembered boss = risen.get(0);
            boss.setNoAi(true);

            forgotten(level, a, 3);
            ItemStack second = new ItemStack(ModItems.HOLLOW_EFFIGY.get());
            HollowEffigy.Result another = HollowEffigy.call(level, player, a.offset(2, 0, 0), second);
            ok[6] = another == HollowEffigy.Result.ANOTHER && second.getCount() == 1;
            LoadedChunkMemory.clear(chunk);

            // ---------- lens ----------
            float unseen = boss.playerDamageScale();
            float before = boss.getHealth();
            boss.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            float unseenHit = before - boss.getHealth();
            boss.reveal();
            float revealed = boss.playerDamageScale();
            before = boss.getHealth();
            boss.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
            float revealedHit = before - boss.getHealth();
            ok[7] = unseen == Unremembered.UNSEEN_DAMAGE && revealed == Unremembered.REVEALED_DAMAGE && boss.revealed()
                    && unseenHit > 0.0F && revealedHit > unseenHit * 2.0F;
            notes.add(String.format("lens unseenHit=%.2f revealedHit=%.2f", unseenHit, revealedHit));

            // ---------- feeding ----------
            boss.setHealth(100.0F);
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(chunk);
            memory.addImprint(imprint(ImprintTag.DEATH, a), 64);
            memory.addImprint(imprint(ImprintTag.PATH, a), 64);
            String fed = boss.feed(level);
            ok[8] = "fed".equals(fed) && boss.getHealth() == 100.0F + Unremembered.FEED_HEAL && memory.imprintCount() == 1
                    && memory.imprintAt(0).tag() == ImprintTag.PATH;
            level.setBlockAndUpdate(lantern, ModBlocks.RECOLLITE_LANTERN.get().defaultBlockState());
            float health = boss.getHealth();
            String keptFeed = boss.feed(level);
            level.setBlockAndUpdate(lantern, Blocks.AIR.defaultBlockState());
            ok[9] = "kept".equals(keptFeed) && boss.getHealth() == health && memory.imprintCount() == 1;
            notes.add("feed=" + fed + " health=" + boss.getHealth() + " lantern=" + keptFeed);
            LoadedChunkMemory.clear(chunk);

            // ---------- faded in the second half ----------
            boss.setHealth(boss.getMaxHealth());
            Faded early = boss.callFaded(level);
            boss.setHealth(boss.getMaxHealth() * 0.4F);
            int calledFaded = 0;
            for (int i = 0; i < 5; i++) {
                Faded faded = boss.callFaded(level);
                if (faded != null) {
                    faded.setNoAi(true);
                    extras.add(faded);
                    calledFaded++;
                }
            }
            ok[10] = early == null && calledFaded == Unremembered.FADED_CAP;
            notes.add("faded called=" + calledFaded);
            for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(a).inflate(20.0D))) {
                faded.discard();
            }

            // ---------- posy ----------
            ItemStack posies = new ItemStack(ModItems.REMEMBRANCE_POSY.get(), 2);
            boss.setHealth(120.0F);
            reHide(boss);
            RemembrancePosy.Result unseenPosy = RemembrancePosy.offerBoss(level, player, boss, posies);
            ok[11] = unseenPosy == RemembrancePosy.Result.NONE && posies.getCount() == 2 && boss.getHealth() == 120.0F;
            boss.reveal();
            RemembrancePosy.Result seenPosy = RemembrancePosy.offerBoss(level, player, boss, posies);
            float posyHit = 120.0F - boss.getHealth();
            ok[12] = seenPosy == RemembrancePosy.Result.RESTED && posies.getCount() == 1 && posyHit >= 12.0F;
            notes.add(String.format("posy unseen=%s seen=%s hit=%.1f", unseenPosy, seenPosy, posyHit));

            // ---------- death: the hollow remembers ----------
            LevelChunk next = level.getChunk(chunk.getPos().x() + 1, chunk.getPos().z());
            LoadedChunkMemory.clear(next);
            forgotten(level, a, 2);
            forgotten(level, new BlockPos(next.getPos().getMinBlockX() + 4, a.getY(), next.getPos().getMinBlockZ() + 4), 2);
            boss.hurtServer(level, level.damageSources().playerAttack(player), 10_000.0F);
            boolean dead = boss.isDeadOrDying();
            ok[13] = dead && boss.remembered() == 4 && Unremembered.countForgotten(chunk) == 0 && Unremembered.countForgotten(next) == 0;
            int slips = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(boss.blockPosition()).inflate(6.0D))) {
                var cast = item.getItem().get(ModDataComponents.IMPRINT_CAST.get());
                slips += item.getItem().is(ModItems.IMPRINT_SLIP.get()) && cast != null && cast.tag() == ImprintTag.FORGOTTEN ? 1 : 0;
                extras.add(item);
            }
            ok[14] = slips == 1;
            notes.add("dead=" + dead + " remembered=" + boss.remembered() + " slips=" + slips);
            LoadedChunkMemory.clear(next);

            // ---------- sink away ----------
            Unremembered lonely = com.mnemolith.entity.ModEntities.UNREMEMBERED.get().create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (lonely != null) {
                lonely.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
                lonely.setNoAi(true);
                level.addFreshEntity(lonely);
                extras.add(lonely);
                int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(6.0D)).size();
                lonely.sinkAway(level);
                ok[15] = lonely.isRemoved() && !lonely.isDeadOrDying() && level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(6.0D)).size() == drops;
            }
        } finally {
            for (Entity entity : extras) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(a).inflate(20.0D))) {
                faded.discard();
            }
            level.setBlockAndUpdate(lantern, Blocks.AIR.defaultBlockState());
            LoadedChunkMemory.clear(level.getChunkAt(a));
            LoadedChunkMemory.clear(level.getChunkAt(plain));
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
            releaseColumn(level, ChunkPos.containing(plain));
        }

        int posyPage = -1;
        int page = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("posy")) {
                posyPage = i;
            } else if (GuideBook.pageId(i).equals("unremembered")) {
                page = i;
            }
        }
        ok[16] = posyPage >= 0 && page == posyPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("unrememberedqa", NAMES, ok, notes);
    }

    /** Ends a reveal at once (QA): the next lens check would set it again. */
    private static void reHide(Unremembered boss) {
        boss.hideForQa();
    }

    private static void forgotten(ServerLevel level, BlockPos pos, int n) {
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(pos));
        for (int i = 0; i < n; i++) {
            memory.addImprint(new Imprint(ImprintTag.FORGOTTEN, ImprintWriter.intensityFor(ImprintTag.FORGOTTEN), pos.offset(i, 0, 0),
                    Optional.empty(), Imprint.contextHash(ImprintTag.FORGOTTEN, pos, level.getGameTime() + i * 31L + salt), level.getGameTime()), 64);
        }
    }

    private static Imprint imprint(ImprintTag tag, BlockPos pos) {
        return new Imprint(tag, ImprintWriter.intensityFor(tag), pos, Optional.empty(), Imprint.contextHash(tag, pos, pos.asLong() + tag.ordinal()), 0L);
    }

    private static void discardNear(ServerLevel level, BlockPos pos) {
        for (Unremembered boss : level.getEntitiesOfClass(Unremembered.class, new AABB(pos).inflate(80.0D))) {
            boss.discard();
        }
        for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(pos).inflate(24.0D))) {
            faded.discard();
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
