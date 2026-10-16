package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.HushLoomBlock;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.data.ImprintSlips;
import com.mnemolith.data.ModDataComponents;
import com.mnemolith.fishing.Angling;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith anglingqa}. Undertow, stage 1: memory angling. The chance (base, Luck of the Sea, cap); a forced
 * angle takes the chunk's oldest imprint and flings one slip of its tag toward the angler, writing nothing; an empty
 * chunk and a bobber out of water take nothing; a hush loom's kept tag is skipped; a seeded run of catches hits some
 * and misses some; the guide page. A real player's catch on the event bus is the live test {@code angler_lands_memory}.
 */
public final class AnglingQa {
    private static final String[] NAMES = {"chance", "luckOfTheSea", "takesOldest", "slipFlies", "writesNothing", "emptyTakesNothing",
            "dryTakesNothing", "keptSkipped", "seededRolls", "guide"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-abababababab");
    private static int salt;

    private AnglingQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();

        ok[0] = near(Angling.chanceFor(0), 0.30F) && near(Angling.chanceFor(1), 0.40F) && near(Angling.chanceFor(3), 0.60F)
                && near(Angling.chanceFor(5), 0.60F);
        ItemStack lucky = new ItemStack(Items.FISHING_ROD);
        lucky.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUCK_OF_THE_SEA), 2);
        ok[1] = near(Angling.chance(level, lucky), 0.50F) && near(Angling.chance(level, new ItemStack(Items.FISHING_ROD)), 0.30F)
                && near(Angling.chance(level, ItemStack.EMPTY), 0.30F);

        BlockPos a = column(level, (spawn.getX() >> 4) + 530 + salt * 3, (spawn.getZ() >> 4) - 520);
        tickColumn(level, a);
        BlockPos water = a.offset(2, 0, 2);
        BlockPos dry = a.offset(2, 0, 6);
        BlockPos loom = a.offset(6, 0, 2);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "AnglingQa"));
        FishingHook hook = null;
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 3.5D, 0.0F, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            hook = new FishingHook(player, level, 0, 0);
            hook.setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);
            level.addFreshEntity(hook);
            QaSupport.clear(level, water);

            // ---------- a forced angle ----------
            ImprintWriter.write(level, water, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, water, List.of(ImprintTag.BUILD), null, false);
            ImprintWriter.write(level, water, List.of(ImprintTag.EXPLOSION), null, false);
            int before = count(level, water);
            Optional<Imprint> taken = Angling.angle(level, hook, player);
            List<ItemEntity> slips = slips(level, water);
            ok[2] = taken.map(i -> i.tag() == ImprintTag.PATH).orElse(false) && slips.size() == 1
                    && slips.get(0).getItem().get(ModDataComponents.IMPRINT_CAST.get()) != null
                    && slips.get(0).getItem().get(ModDataComponents.IMPRINT_CAST.get()).tag() == ImprintTag.PATH;
            ok[3] = slips.size() == 1 && slips.get(0).getDeltaMovement().y > 0.0D && slips.get(0).getDeltaMovement().z < 0.0D;
            ok[4] = count(level, water) == before - 1;
            notes.add("taken=" + taken.map(Imprint::tag).orElse(null) + " slips=" + slips.size() + " before=" + before + " after=" + count(level, water));
            slips.forEach(ItemEntity::discard);

            // ---------- nothing to take ----------
            QaSupport.clear(level, water);
            ok[5] = Angling.angle(level, hook, player).isEmpty() && slips(level, water).isEmpty();
            ImprintWriter.write(level, water, List.of(ImprintTag.PATH), null, false);
            hook.setPos(dry.getX() + 0.5D, dry.getY() + 0.4D, dry.getZ() + 0.5D);
            ok[6] = Angling.angle(level, hook, player).isEmpty() && count(level, water) == 1;
            hook.setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);

            // ---------- a kept tag stays ----------
            QaSupport.clear(level, water);
            level.setBlock(loom, ModBlocks.HUSH_LOOM.get().defaultBlockState(), 3);
            ImprintWriter.write(level, water, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, water, List.of(ImprintTag.BUILD), null, false);
            HushLoomBlock.keep(level, loom, ImprintTag.PATH);
            Optional<Imprint> skipped = Angling.angle(level, hook, player);
            Optional<Imprint> none = Angling.angle(level, hook, player);
            ok[7] = skipped.map(i -> i.tag() == ImprintTag.BUILD).orElse(false) && none.isEmpty() && count(level, water) == 1;
            slips(level, water).forEach(ItemEntity::discard);
            level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);

            // ---------- seeded rolls ----------
            QaSupport.clear(level, water);
            for (int i = 0; i < 20; i++) {
                ImprintWriter.write(level, water, List.of(ImprintTag.values()[i % 9]), null, false);
            }
            int stocked = count(level, water);
            RandomSource random = RandomSource.create(0xF15A1L);
            int hits = 0;
            for (int i = 0; i < 20; i++) {
                if (Angling.onCatch(level, hook, player, random).isPresent()) {
                    hits++;
                }
            }
            ok[8] = hits > 0 && hits < 20 && count(level, water) == stocked - hits && slips(level, water).size() == hits;
            notes.add("seeded hits=" + hits + "/20 stocked=" + stocked);
        } finally {
            if (hook != null) {
                hook.discard();
            }
            slips(level, water).forEach(ItemEntity::discard);
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, water);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        boolean page = false;
        for (int i = 0; i < GuideBook.pageCount(); i++) {
            page |= "undertow".equals(GuideBook.pageId(i));
        }
        ok[9] = page && GuideBook.pageCount() == GuideBook.PAGE_COUNT;
        return new QaReport("anglingqa", NAMES, ok, notes);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0E-4F;
    }

    private static int count(ServerLevel level, BlockPos pos) {
        ChunkMemory memory = LoadedChunkMemory.existing(level.getChunkAt(pos));
        return memory == null ? 0 : memory.imprintCount();
    }

    private static List<ItemEntity> slips(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4.0D), e -> ImprintSlips.isSlip(e.getItem()));
    }
}
