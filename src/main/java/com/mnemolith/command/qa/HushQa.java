package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Hush;
import com.mnemolith.entity.mob.Hushling;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@code /mnemolith hushqa}. The Pale Chorus, stage 1: the hushling and hush filament. Thinning takes the quietest
 * imprint only; the hit hushes a survival player (not a creative one), a hushed player's writes are dropped while
 * others' are kept, and the hush ends; a note block shatters hushlings in range and not beyond, and a shattered one
 * neither thins nor hushes and takes double damage; the silence imprint in the End and not elsewhere; loot, spawn
 * modifier, guide. A hushling's AI hitting a real player is the live test {@code hushling_hushes_player}.
 */
public final class HushQa {
    private static final String[] NAMES = {"itemData", "entityData", "thinsQuietest", "thinEmpty", "hushes", "creativeNotHushed", "hushedDropsWrites",
            "othersStillWrite", "hushEnds", "noteShatters", "noteRange", "shatteredQuiet", "doubleDamage", "endImprint", "noImprintElsewhere", "loot",
            "spawnModifier", "guide"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-ffffffffffff");
    private static int salt;

    private HushQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ok[0] = new ItemStack(ModItems.HUSH_FILAMENT.get()).getMaxStackSize() == 64 && new ItemStack(ModItems.HUSHLING_SPAWN_EGG.get()).getMaxStackSize() == 64;
        EntityType<Hushling> type = ModEntities.HUSHLING.get();
        ok[1] = !type.isAllowedInPeaceful() && !type.fireImmune() && type.getHeight() < 1.6F;

        BlockPos a = column(level, (spawn.getX() >> 4) + 470 + salt * 3, (spawn.getZ() >> 4) - 460);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "HushQa"));
        List<Entity> extras = new ArrayList<>();
        ServerLevel end = server.getLevel(Level.END);
        BlockPos e = null;
        int hereSilence = 0;
        try {
            player.setGameMode(GameType.SURVIVAL);
            player.removeAllEffects();
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 2.5D, 0.0F, 0.0F);
            Hush.forget(PLAYER);

            // ---------- thinning ----------
            Hushling singer = hushling(level, a, extras);
            QaSupport.clear(level, a);
            ImprintWriter.write(level, a, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, a, List.of(ImprintTag.EXPLOSION), null, false);
            ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(a));
            ImprintTag quietest = memory == null ? null
                    : memory.imprintsCopy().stream().min(Comparator.comparingInt(Imprint::pressureContribution)).map(Imprint::tag).orElse(null);
            String thin = singer.thin(level);
            memory = LoadedChunkMemory.existing(level.getChunkAt(a));
            List<Imprint> left = memory == null ? List.of() : memory.imprintsCopy();
            ok[2] = "thinned".equals(thin) && left.size() == 1 && quietest != null && left.get(0).tag() != quietest;
            QaSupport.clear(level, a);
            ok[3] = "empty".equals(singer.thin(level));
            notes.add("thin=" + thin + " quietest=" + quietest + " left=" + left.stream().map(Imprint::tag).toList());

            // ---------- the hush ----------
            ok[4] = singer.hushPlayer(level, player) && Hush.hushed(level, PLAYER);
            Hush.forget(PLAYER);
            player.setGameMode(GameType.CREATIVE);
            ok[5] = !singer.hushPlayer(level, player) && !Hush.hushed(level, PLAYER);
            player.setGameMode(GameType.SURVIVAL);
            singer.hushPlayer(level, player);
            BlockPos w = a.offset(0, 0, 3);
            QaSupport.clear(level, w);
            boolean dropped = !ImprintWriter.write(level, w, List.of(ImprintTag.BUILD), PLAYER, false) && !QaSupport.hasTag(level, w, ImprintTag.BUILD);
            ok[6] = dropped;
            ok[7] = ImprintWriter.write(level, w, List.of(ImprintTag.BUILD), null, false) && QaSupport.hasTag(level, w, ImprintTag.BUILD);
            Hush.forget(PLAYER);
            Hush.hush(level, PLAYER, 0);
            ok[8] = !Hush.hushed(level, PLAYER);
            QaSupport.clear(level, w);

            // ---------- sound breaks it ----------
            Hushling far = hushling(level, a.offset(0, 0, 20), extras);
            int shattered = Hushling.onNote(level, a.offset(2, 0, 0));
            ok[9] = shattered == 1 && singer.shattered();
            ok[10] = !far.shattered();
            ImprintWriter.write(level, a, List.of(ImprintTag.PATH), null, false);
            Hush.forget(PLAYER);
            ok[11] = "shattered".equals(singer.thin(level)) && QaSupport.hasTag(level, a, ImprintTag.PATH) && !singer.hushPlayer(level, player)
                    && !Hush.hushed(level, PLAYER);
            float s0 = singer.getHealth();
            singer.hurtServer(level, level.damageSources().generic(), 4.0F);
            float sLost = s0 - singer.getHealth();
            float f0 = far.getHealth();
            far.hurtServer(level, level.damageSources().generic(), 4.0F);
            float fLost = f0 - far.getHealth();
            ok[12] = Math.abs(sLost - 2.0F * fLost) < 0.01F && fLost > 0.0F;
            notes.add("shattered=" + shattered + " lost shattered=" + sLost + " plain=" + fLost);
            QaSupport.clear(level, a);

            // ---------- the imprint ----------
            BlockPos d = a.offset(0, 0, 6);
            QaSupport.clear(level, d);
            Hushling dies = hushling(level, d, extras);
            dies.hurtServer(level, level.damageSources().generic(), 10_000.0F);
            // an unwitnessed death writes one silence anywhere (ImprintWriter.witnessedTags); only the End adds the hushling's own
            hereSilence = countTag(level, d, ImprintTag.SILENCE);
            ok[14] = dies.isDeadOrDying() && hereSilence <= 1;
            QaSupport.clear(level, d);
            if (end != null) {
                e = new BlockPos(1800 + salt * 48, 64, -2000);
                tickColumn(end, e);
                QaSupport.clear(end, e);
                Hushling there = hushling(end, e, extras);
                there.hurtServer(end, end.damageSources().generic(), 10_000.0F);
                int endSilence = countTag(end, e, ImprintTag.SILENCE);
                ok[13] = there.isDeadOrDying() && endSilence == hereSilence + 1;
                notes.add("silence overworld=" + hereSilence + " end=" + endSilence);
                QaSupport.clear(end, e);
            } else {
                notes.add("no end level");
            }
        } finally {
            for (Entity entity : extras) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(24.0D))) {
                item.discard();
            }
            if (e != null) {
                for (ItemEntity item : end.getEntitiesOfClass(ItemEntity.class, new AABB(e).inflate(16.0D))) {
                    item.discard();
                }
                releaseColumn(end, ChunkPos.containing(e));
            }
            Hush.forget(PLAYER);
            player.removeAllEffects();
            player.setGameMode(GameType.SURVIVAL);
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[15] = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("entities/hushling"))) != LootTable.EMPTY;
        ok[16] = server.registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id("end_hushling"))).isPresent();
        boolean page = false;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            page |= "hushling".equals(GuideBook.pageId(i));
        }
        ok[17] = page && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("hushqa", NAMES, ok, notes);
    }

    private static int countTag(ServerLevel level, BlockPos pos, ImprintTag tag) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : (int) memory.imprintsCopy().stream().filter(i -> i.tag() == tag).count();
    }

    private static Hushling hushling(ServerLevel level, BlockPos pos, List<Entity> extras) {
        Hushling mob = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("hushling did not create");
        }
        mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        extras.add(mob);
        return mob;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
