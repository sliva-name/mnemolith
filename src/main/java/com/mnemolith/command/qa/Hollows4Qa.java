package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.composition.CompositionRecipe;
import com.mnemolith.content.composition.CompositionRecipes;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.data.ImprintCast;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.echo.EchoLesson;
import com.mnemolith.echo.EchoLife;
import com.mnemolith.echo.EchoRecording;
import com.mnemolith.echo.EchoRegistry;
import com.mnemolith.echo.graft.EchoGrafts;
import com.mnemolith.echo.graft.Temper;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.echo.EchoEntity;
import com.mnemolith.entity.mob.Faded;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.network.HollowFlickerPayload;
import com.mnemolith.particle.ModParticles;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.ArchiveKeepers;
import com.mnemolith.worldgen.hollows.HollowFlickers;
import com.mnemolith.worldgen.hollows.HollowForgetting;
import com.mnemolith.worldgen.hollows.Hollows;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith hollows4qa}. Memory Hollows, stage 4: the forgotten imprint (appended tag, its scene, written only
 * in Memory Hollows, an uncaught flicker and a dying faded forgetting their memory), the forgotten temper (its work lets
 * go of the chunk's loudest imprint and writes nothing), the forgetting formula, the archive keeper (woken by an
 * unrolled sunken archive chest, once per chest, not by creative players, tougher, always drops a forgotten slip, saves
 * its post), the particle and the guide page. Works on the flat game test world.
 */
public final class Hollows4Qa {
    private static final String[] NAMES = {"tagData", "hollowsOnly", "forgetsInHollows", "flickerExpiry", "noChanceNoForget",
            "forgottenStays", "fadedDeathForgets", "temperData", "temperForgets", "temperIdle", "formula", "keeperWakes", "keeperOnce",
            "keeperCreative", "keeperDrop", "keeperSaves", "particle", "guidePage"};
    private static final UUID PLAYER = UUID.fromString("44444444-1111-2222-3333-888888888888");
    private static int salt;

    private Hollows4Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        // ---------- the tag ----------
        ImprintTag forgotten = ImprintTag.FORGOTTEN;
        ok[0] = forgotten.ordinal() == 14 && forgotten.getSerializedName().equals("forgotten") && forgotten.weight() == 1
                && HollowFlickers.scene(forgotten) == HollowFlickers.FADE && HollowFlickers.SCENES == HollowFlickers.FADE + 1
                && ImprintWriter.intensityFor(forgotten) == com.mnemolith.imprint.ImprintConstants.FORGOTTEN_INTENSITY;

        BlockPos a = column(level, (spawn.getX() >> 4) + 90 + salt * 3, (spawn.getZ() >> 4) - 66);
        BlockPos plain = a.offset(48, 0, 0);
        tickColumn(level, a);
        tickColumn(level, plain);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Hollows4Qa"));
        List<net.minecraft.world.entity.Entity> extras = new ArrayList<>();
        EchoEntity echo = null;
        try {
            HollowFlickers.clear();
            discardNear(level, a);
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
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
            LevelChunk plainChunk = level.getChunkAt(plain);
            LoadedChunkMemory.clear(hollowChunk);
            LoadedChunkMemory.clear(plainChunk);

            // ---------- written only in the hollows ----------
            boolean plainIsPlain = !Hollows.is(level.getBiome(plain));
            boolean plainWrite = ImprintWriter.write(level, plain, List.of(ImprintTag.FORGOTTEN), null, false);
            boolean plainForget = HollowForgetting.forget(level, plain, ImprintTag.PATH);
            ok[1] = plainIsPlain && !plainWrite && !plainForget && count(level, plain, ImprintTag.FORGOTTEN) == 0;
            notes.add("plain biome=" + plainIsPlain + " write=" + plainWrite + " forget=" + plainForget);

            // ---------- forgetting in the hollows ----------
            seed(level, a, ImprintTag.PATH);
            boolean hollowIs = Hollows.is(level.getBiome(a));
            boolean forgot = HollowForgetting.forget(level, a, ImprintTag.PATH);
            ok[2] = hollowIs && forgot && count(level, a, ImprintTag.PATH) == 0 && count(level, a, ImprintTag.FORGOTTEN) == 1;
            notes.add("hollows biome=" + hollowIs + " forgot=" + forgot + " path=" + count(level, a, ImprintTag.PATH) + " forgotten="
                    + count(level, a, ImprintTag.FORGOTTEN));

            // ---------- an uncaught flicker ----------
            LoadedChunkMemory.clear(hollowChunk);
            seed(level, a, ImprintTag.TRADE);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WORK, ImprintTag.TRADE.ordinal()));
            long late = level.getGameTime() + HollowFlickers.CATCH_WINDOW + 1;
            int expired = HollowFlickers.expire(level, late, 1.0D);
            ok[3] = expired == 1 && HollowFlickers.active().isEmpty() && count(level, a, ImprintTag.TRADE) == 0
                    && count(level, a, ImprintTag.FORGOTTEN) == 1;
            notes.add("expire forgot=" + expired + " trade=" + count(level, a, ImprintTag.TRADE));
            // chance 0: the window still closes, nothing is forgotten
            seed(level, a, ImprintTag.PATH);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.WALK, ImprintTag.PATH.ordinal()));
            int none = HollowFlickers.expire(level, late + HollowFlickers.CATCH_WINDOW + 1, 0.0D);
            ok[4] = none == 0 && HollowFlickers.active().isEmpty() && count(level, a, ImprintTag.PATH) == 1;
            // a forgotten flicker is not forgotten again
            int before = count(level, a, ImprintTag.FORGOTTEN);
            HollowFlickers.send(level, new HollowFlickerPayload(a, 0.0F, HollowFlickers.FADE, ImprintTag.FORGOTTEN.ordinal()));
            int again = HollowFlickers.expire(level, late + 3L * HollowFlickers.CATCH_WINDOW, 1.0D);
            ok[5] = again == 0 && count(level, a, ImprintTag.FORGOTTEN) == before;
            HollowFlickers.clear();

            // ---------- a faded dies ----------
            LoadedChunkMemory.clear(hollowChunk);
            seed(level, a, ImprintTag.FALL);
            Faded copier = faded(level, a.offset(2, 0, 0));
            extras.add(copier);
            copier.copy(a, ImprintTag.FALL);
            copier.hurtServer(level, level.damageSources().generic(), 1000.0F);
            ok[6] = !copier.isAlive() && count(level, a, ImprintTag.FALL) == 0 && count(level, a, ImprintTag.FORGOTTEN) == 1;
            notes.add("faded death alive=" + copier.isAlive() + " fall=" + count(level, a, ImprintTag.FALL) + " forgotten="
                    + count(level, a, ImprintTag.FORGOTTEN));

            // ---------- the temper ----------
            Temper temper = Temper.of(ImprintTag.FORGOTTEN);
            ok[7] = temper == Temper.FORGOTTEN && temper.hollowsOnly() && temper.residue() == null && !Temper.DEEP.hollowsOnly();
            EchoRegistry.get(server).forget(PLAYER);
            echo = EchoLife.spawn(level, player, recording(level, plain), EchoLesson.NONE);
            if (echo != null) {
                echo.stopReplay();
                echo.snapTo(Vec3.atBottomCenterOf(plain), 0.0F, 0.0F);
                ItemStack slip = ImprintSlips.of(ImprintTag.FORGOTTEN, a);
                boolean took = EchoGrafts.graft(player, echo, slip);
                LoadedChunkMemory.clear(plainChunk);
                seed(level, plain, ImprintTag.DEATH);
                int charge = echo.graftCharge();
                ImprintTag work = EchoGrafts.workImprint(level, echo, plain);
                ok[8] = took && echo.graftTemper() == Temper.FORGOTTEN && work == null && count(level, plain, ImprintTag.DEATH) == 0
                        && count(level, plain, ImprintTag.FORGOTTEN) == 0 && echo.graftCharge() == charge - 1;
                notes.add("temper took=" + took + " work=" + work + " death=" + count(level, plain, ImprintTag.DEATH) + " charge " + charge + "->"
                        + echo.graftCharge());
                int idle = echo.graftCharge();
                ImprintTag quiet = EchoGrafts.workImprint(level, echo, plain);
                ok[9] = quiet == null && echo.graftCharge() == idle && LoadedChunkMemory.existing(plainChunk) != null
                        && LoadedChunkMemory.existing(plainChunk).imprintCount() == 0;
            } else {
                notes.add("echo did not spawn");
            }

            // ---------- the formula ----------
            for (CompositionRecipe recipe : CompositionRecipes.all()) {
                if (recipe.id().equals(id("forgetting"))) {
                    ok[10] = recipe.tags().contains(ImprintTag.FORGOTTEN) && recipe.tags().contains(ImprintTag.SILENCE) && recipe.tags().size() == 2
                            && recipe.product() == ImprintTag.FORGOTTEN;
                }
            }

            // ---------- the archive keeper ----------
            BlockPos chest = a.offset(-4, 0, 4);
            level.setBlockAndUpdate(chest.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(chest) instanceof RandomizableContainerBlockEntity box) {
                box.setLootTable(ArchiveKeepers.ARCHIVE_LOOT, 42L);
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx != 0 || dz != 0) {
                        level.setBlockAndUpdate(chest.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(chest.offset(dx, 0, dz), Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(chest.offset(dx, 1, dz), Blocks.AIR.defaultBlockState());
                    }
                }
            }
            player.snapTo(chest.getX() + 0.5D, chest.getY(), chest.getZ() - 1.5D, 0.0F, 0.0F);
            Faded keeper = ArchiveKeepers.wake(level, chest, player);
            if (keeper != null) {
                extras.add(keeper);
                boolean canTarget = keeper.canAttack(player);
                ok[11] = keeper.keeper() && keeper.getMaxHealth() == (float) Faded.KEEPER_HEALTH && keeper.hasHome()
                        && keeper.getHomePosition().equals(chest) && keeper.distanceToSqr(Vec3.atBottomCenterOf(chest)) <= 16.0D
                        && keeper.getTarget() == (canTarget ? player : null) && keeper.isPersistenceRequired();
                notes.add("keeper health=" + keeper.getMaxHealth() + " home=" + keeper.getHomePosition().toShortString() + " canTarget=" + canTarget);
                Faded second = ArchiveKeepers.wake(level, chest, player);
                int keepers = level.getEntitiesOfClass(Faded.class, new AABB(chest).inflate(16.0D), f -> f.isAlive() && f.keeper()).size();
                ok[12] = second == keeper && keepers == 1;
                player.setGameMode(GameType.CREATIVE);
                ok[13] = ArchiveKeepers.wake(level, chest.offset(0, 0, 40), player) == null;
                player.setGameMode(GameType.SURVIVAL);
                keeper.hurtServer(level, level.damageSources().playerAttack(player), 1000.0F);
                int slips = 0;
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, keeper.getBoundingBox().inflate(3.0D))) {
                    ImprintCast cast = item.getItem().get(ModDataComponents.IMPRINT_CAST.get());
                    if (cast != null && cast.tag() == ImprintTag.FORGOTTEN) {
                        slips += item.getItem().getCount();
                    }
                    extras.add(item);
                }
                ok[14] = !keeper.isAlive() && slips >= 1;
                notes.add("keeper drop alive=" + keeper.isAlive() + " slips=" + slips);
            } else {
                notes.add("no keeper woke");
            }
            Faded post = faded(level, a.offset(0, 0, -3));
            extras.add(post);
            post.makeKeeper(chest);
            Faded copy = reload(level, post);
            if (copy != null) {
                extras.add(copy);
                ok[15] = copy.keeper() && copy.hasHome() && copy.getHomePosition().equals(chest);
            }
        } finally {
            for (var entity : extras) {
                if (entity.isAlive()) {
                    entity.discard();
                }
            }
            if (echo != null && echo.isAlive()) {
                echo.discardSilently();
            }
            EchoRegistry.get(server).forget(PLAYER);
            HollowFlickers.clear();
            releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(a));
            releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(plain));
        }

        // ---------- particle, guide ----------
        ok[16] = BuiltInRegistries.PARTICLE_TYPE.containsKey(ModParticles.GRAFT_FORGOTTEN.getId())
                && EchoGrafts.particle(Temper.FORGOTTEN) == ModParticles.GRAFT_FORGOTTEN.get();
        int fadedPage = -1;
        int forgottenPage = -1;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            if (GuideBook.pageId(i).equals("faded")) {
                fadedPage = i;
            } else if (GuideBook.pageId(i).equals("forgotten")) {
                forgottenPage = i;
            }
        }
        ok[17] = fadedPage >= 0 && forgottenPage == fadedPage + 1 && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("hollows4qa", NAMES, ok, notes);
    }

    private static void seed(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(pos));
        memory.addImprint(new Imprint(tag, 3, pos, Optional.empty(), 700 + tag.ordinal() + salt, level.getGameTime()), 8);
    }

    private static int count(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        int n = 0;
        if (memory != null) {
            for (int i = 0; i < memory.imprintCount(); i++) {
                n += memory.imprintAt(i).tag() == tag ? 1 : 0;
            }
        }
        return n;
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

    private static Faded reload(ServerLevel level, Faded faded) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        faded.saveWithoutId(output);
        CompoundTag tag = output.buildResult();
        faded.discard();
        Faded copy = ModEntities.FADED.get().create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            return null;
        }
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return copy;
    }

    private static EchoRecording recording(ServerLevel level, BlockPos base) {
        Vec3 origin = Vec3.atBottomCenterOf(base);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(20 * EchoRecording.FRAME_BYTES).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 20; i++) {
            EchoRecording.writeFrame(buffer, origin, origin.x, origin.y, origin.z, 0.0F, 0.0F, 0.0F, EchoRecording.FLAG_GROUND);
        }
        return new EchoRecording(PLAYER, "Hollows4Qa", level.dimension(), origin, buffer.array(), List.of());
    }

    private static void discardNear(ServerLevel level, BlockPos pos) {
        for (Faded faded : level.getEntitiesOfClass(Faded.class, new AABB(pos).inflate(40.0D))) {
            faded.discard();
        }
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
