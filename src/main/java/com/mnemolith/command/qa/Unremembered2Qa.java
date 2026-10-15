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
import com.mnemolith.content.item.RememberingCirclet;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.entity.mob.Faded;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith unremembered2qa}. The Unremembered, stage 2: the Lethe tear and the remembering circlet (items,
 * recipe, head slot, repair), the tear dropped only when a player brings the boss down, the circlet revealing faded
 * and the boss within reach and not beyond, and the two advancements. The ward and the keeping need real players in
 * the level; the live test {@code circlet_wards_and_keeps} covers them.
 */
public final class Unremembered2Qa {
    private static final String[] NAMES = {"itemData", "recipe", "headSlot", "repair", "tearByPlayer", "noTearOtherwise",
            "revealNear", "revealFar", "revealBoss", "advancements"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-cccccccccccc");
    private static int salt;

    private Unremembered2Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ItemStack circlet = new ItemStack(ModItems.REMEMBERING_CIRCLET.get());
        ok[0] = new ItemStack(ModItems.LETHE_TEAR.get()).getMaxStackSize() == 16 && circlet.getMaxStackSize() == 1 && circlet.isDamageableItem();
        Optional<RecipeHolder<?>> recipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id("remembering_circlet")));
        if (recipe.isPresent() && recipe.get().value() instanceof CraftingRecipe crafting) {
            ItemStack g = new ItemStack(Items.GOLD_INGOT);
            ItemStack r = new ItemStack(ModItems.RECOLLITE_SHARD.get());
            CraftingInput input = CraftingInput.of(3, 2, List.of(g.copy(), new ItemStack(ModItems.LETHE_TEAR.get()), g.copy(), r.copy(), ItemStack.EMPTY, r.copy()));
            ok[1] = crafting.matches(input, level) && crafting.assemble(input).is(ModItems.REMEMBERING_CIRCLET.get())
                    && server.getAdvancements().get(id("recipes/remembering_circlet")) != null;
        }
        var equippable = circlet.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        ok[2] = equippable != null && equippable.slot() == EquipmentSlot.HEAD;
        ok[3] = circlet.isValidRepairItem(new ItemStack(ModItems.RECOLLITE_SHARD.get()));

        BlockPos a = column(level, (spawn.getX() >> 4) + 350 + salt * 3, (spawn.getZ() >> 4) - 300);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Unremembered2Qa"));
        List<Entity> extras = new ArrayList<>();
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);

            // ---------- the tear ----------
            Unremembered slain = boss(level, a.offset(3, 0, 0));
            extras.add(slain);
            slain.hurtServer(level, level.damageSources().playerAttack(player), 10_000.0F);
            int tears = countTears(level, a.offset(3, 0, 0), extras);
            ok[4] = slain.isDeadOrDying() && tears == 1;
            Unremembered lost = boss(level, a.offset(-3, 0, 6));
            extras.add(lost);
            lost.hurtServer(level, level.damageSources().generic(), 10_000.0F);
            int lostTears = countTears(level, a.offset(-3, 0, 6), extras);
            ok[5] = lost.isDeadOrDying() && lostTears == 0;
            notes.add("tears byPlayer=" + tears + " otherwise=" + lostTears);

            // ---------- revealing ----------
            player.setItemSlot(EquipmentSlot.HEAD, circlet.copy());
            boolean worn = RememberingCirclet.worn(player);
            Faded near = faded(level, a.offset(4, 0, 0));
            Faded far = faded(level, a.offset(0, 0, 12));
            extras.add(near);
            extras.add(far);
            Unremembered boss = boss(level, a.offset(-5, 0, -5));
            extras.add(boss);
            int revealed = RememberingCirclet.reveal(level, player);
            ok[6] = worn && near.revealed();
            ok[7] = !far.revealed();
            ok[8] = boss.revealed() && revealed == 2;
            notes.add("revealed=" + revealed + " near=" + near.revealed() + " far=" + far.revealed() + " boss=" + boss.revealed());
        } finally {
            for (Entity entity : extras) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(16.0D))) {
                item.discard();
            }
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        ok[9] = server.getAdvancements().get(id("unremembered_rest")) != null && server.getAdvancements().get(RememberingCirclet.ADVANCEMENT) != null;
        return new QaReport("unremembered2qa", NAMES, ok, notes);
    }

    private static int countTears(ServerLevel level, BlockPos pos, List<Entity> extras) {
        int n = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5.0D))) {
            n += item.getItem().is(ModItems.LETHE_TEAR.get()) ? item.getItem().getCount() : 0;
            extras.add(item);
        }
        return n;
    }

    private static Unremembered boss(ServerLevel level, BlockPos pos) {
        Unremembered boss = ModEntities.UNREMEMBERED.get().create(level, EntitySpawnReason.COMMAND);
        if (boss == null) {
            throw new IllegalStateException("unremembered did not create");
        }
        boss.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        boss.setNoAi(true);
        level.addFreshEntity(boss);
        return boss;
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }
}
