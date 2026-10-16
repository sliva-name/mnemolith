package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.content.ModItems;
import com.mnemolith.data.ImprintSlips;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith lureqa}. Undertow, stage 2: the tide lure. Item data, recipe; the chance with a lure in the off
 * hand (+20%, capped at 80%); a lured angle takes the loudest imprint instead of the oldest and wears the lure by one;
 * an empty chunk does not wear it; a lure in the main hand does nothing; a worn-out lure breaks; seeded rolls. The
 * live test {@code lured_angler_lands_loudest} reels with a real player.
 */
public final class LureQa {
    private static final String[] NAMES = {"itemData", "recipe", "chance", "takesLoudest", "wearsLure", "emptyKeepsLure", "mainHandIgnored",
            "breaks", "seededRolls"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-acacacacacac");
    private static int salt;

    private LureQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ItemStack fresh = new ItemStack(ModItems.TIDE_LURE.get());
        ok[0] = fresh.getMaxStackSize() == 1 && fresh.getMaxDamage() == Angling.LURE_DURABILITY;
        var recipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "tide_lure")));
        if (recipe.isPresent() && recipe.get().value() instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting) {
            ItemStack p = new ItemStack(Items.PRISMARINE_SHARD);
            var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Items.STRING), ItemStack.EMPTY,
                    p.copy(), new ItemStack(ModItems.RECOLLITE_SHARD.get()), p.copy(), ItemStack.EMPTY, p.copy(), ItemStack.EMPTY));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.TIDE_LURE.get())
                    && server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath(com.mnemolith.Mnemolith.MOD_ID, "recipes/tide_lure")) != null;
        }

        BlockPos a = column(level, (spawn.getX() >> 4) + 550 + salt * 3, (spawn.getZ() >> 4) - 540);
        tickColumn(level, a);
        BlockPos water = a.offset(2, 0, 2);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "LureQa"));
        FishingHook hook = null;
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 3.5D, 0.0F, 0.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
            float bare = Angling.chance(level, player);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.TIDE_LURE.get()));
            float lured = Angling.chance(level, player);
            ok[2] = near(bare, 0.30F) && near(lured, 0.50F) && near(Angling.chanceFor(3, true), 0.80F) && near(Angling.chanceFor(5, true), 0.80F)
                    && near(Angling.chanceFor(1, false), 0.40F);
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            hook = new FishingHook(player, level, 0, 0);
            hook.setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);
            level.addFreshEntity(hook);
            QaSupport.clear(level, water);

            // ---------- loudest, and the wear ----------
            ImprintWriter.write(level, water, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, water, List.of(ImprintTag.EXPLOSION), null, false);
            ImprintWriter.write(level, water, List.of(ImprintTag.BUILD), null, false);
            Optional<Imprint> taken = Angling.angle(level, hook, player);
            ok[3] = taken.map(i -> i.tag() == ImprintTag.EXPLOSION).orElse(false);
            ok[4] = player.getOffhandItem().getDamageValue() == 1;
            notes.add("lured took=" + taken.map(Imprint::tag).orElse(null) + " wear=" + player.getOffhandItem().getDamageValue());
            QaSupport.clear(level, water);
            ok[5] = Angling.angle(level, hook, player).isEmpty() && player.getOffhandItem().getDamageValue() == 1;

            // ---------- the main hand does not lure ----------
            ImprintWriter.write(level, water, List.of(ImprintTag.PATH), null, false);
            ImprintWriter.write(level, water, List.of(ImprintTag.EXPLOSION), null, false);
            ItemStack lure = player.getOffhandItem().copy();
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, lure);
            Optional<Imprint> plain = Angling.angle(level, hook, player);
            ok[6] = plain.map(i -> i.tag() == ImprintTag.PATH).orElse(false) && near(Angling.chance(level, player), 0.30F)
                    && player.getMainHandItem().getDamageValue() == 1;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));

            // ---------- a worn lure breaks ----------
            ItemStack worn = new ItemStack(ModItems.TIDE_LURE.get());
            worn.setDamageValue(Angling.LURE_DURABILITY - 1);
            player.setItemInHand(InteractionHand.OFF_HAND, worn);
            Optional<Imprint> last = Angling.angle(level, hook, player);
            ok[7] = last.isPresent() && player.getOffhandItem().isEmpty();

            // ---------- seeded rolls with a lure ----------
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.TIDE_LURE.get()));
            QaSupport.clear(level, water);
            for (int i = 0; i < 20; i++) {
                ImprintWriter.write(level, water, List.of(ImprintTag.values()[i % 9]), null, false);
            }
            int stocked = count(level, water);
            RandomSource random = RandomSource.create(0x71DEL);
            int hits = 0;
            for (int i = 0; i < 20; i++) {
                if (Angling.onCatch(level, hook, player, random).isPresent()) {
                    hits++;
                }
            }
            ok[8] = hits > 0 && hits < 20 && count(level, water) == stocked - hits && player.getOffhandItem().getDamageValue() == hits;
            notes.add("seeded lured hits=" + hits + "/20");
        } finally {
            if (hook != null) {
                hook.discard();
            }
            slips(level, water).forEach(ItemEntity::discard);
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, water);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        return new QaReport("lureqa", NAMES, ok, notes);
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
