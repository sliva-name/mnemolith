package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModItems;
import com.mnemolith.content.guide.GuideBook;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Cinderkin;
import com.mnemolith.imprint.ImprintTag;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * {@code /mnemolith emberqa}. Embers, stage 1: the cinderkin (fire immune, hostile, its hit ignites, snowballs and
 * water hurt it) and the memory ember (fuel, loot), the fire imprint a cinderkin leaves dying in the Nether and not
 * elsewhere, its Nether spawn modifier and its guide page. Chasing a real player is the live test
 * {@code cinderkin_burns_player}.
 */
public final class EmberQa {
    private static final String[] NAMES = {"itemData", "fuel", "entityData", "snowball", "water", "ignites", "netherImprint",
            "noImprintElsewhere", "loot", "spawnModifier", "guide"};
    private static int salt;

    private EmberQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        var server = level.getServer();

        ItemStack ember = new ItemStack(ModItems.MEMORY_EMBER.get());
        ok[0] = ember.getMaxStackSize() == 64 && new ItemStack(ModItems.CINDERKIN_SPAWN_EGG.get()).getMaxStackSize() == 64;
        int burn = ember.getBurnTime(RecipeType.SMELTING, level.fuelValues());
        ok[1] = burn == 1600;
        EntityType<Cinderkin> type = ModEntities.CINDERKIN.get();
        ok[2] = type.fireImmune() && !type.isAllowedInPeaceful();
        notes.add("burn=" + burn);

        BlockPos a = column(level, (spawn.getX() >> 4) + 390 + salt * 3, (spawn.getZ() >> 4) - 360);
        tickColumn(level, a);
        List<Entity> extras = new ArrayList<>();
        ServerLevel nether = server.getLevel(Level.NETHER);
        BlockPos n = null;
        try {
            // ---------- cold ----------
            Cinderkin cold = cinderkin(level, a.offset(3, 0, 0), extras);
            Snowball snowball = net.minecraft.world.entity.EntityTypes.SNOWBALL.create(level, EntitySpawnReason.COMMAND);
            extras.add(snowball);
            float before = cold.getHealth();
            cold.hurtServer(level, level.damageSources().thrown(snowball, null), 0.0F);
            float lost = before - cold.getHealth();
            ok[3] = lost >= 4.0F;
            ok[4] = cold.isSensitiveToWater();
            notes.add("snowball=" + lost);

            // ---------- the burning hit ----------
            Cinderkin hitter = cinderkin(level, a.offset(-3, 0, 0), extras);
            Zombie victim = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            victim.snapTo(a.getX() - 2.0D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
            victim.setNoAi(true);
            level.addFreshEntity(victim);
            extras.add(victim);
            boolean hit = hitter.doHurtTarget(level, victim);
            ok[5] = hit && victim.getRemainingFireTicks() > 0;
            notes.add("hit=" + hit + " fire=" + victim.getRemainingFireTicks());

            // ---------- the imprint: overworld no, Nether yes ----------
            BlockPos d = a.offset(0, 0, 5);
            QaSupport.clear(level, d);
            Cinderkin here = cinderkin(level, d, extras);
            here.hurtServer(level, level.damageSources().generic(), 10_000.0F);
            ok[7] = here.isDeadOrDying() && !QaSupport.hasTag(level, d, ImprintTag.FIRE);
            if (nether != null) {
                n = new BlockPos((spawn.getX() >> 4 << 4) + 6408 + salt * 48, 64, (spawn.getZ() >> 4 << 4) - 5752);
                tickColumn(nether, n);
                QaSupport.clear(nether, n);
                Cinderkin there = cinderkin(nether, n, extras);
                there.hurtServer(nether, nether.damageSources().generic(), 10_000.0F);
                ok[6] = there.isDeadOrDying() && QaSupport.hasTag(nether, n, ImprintTag.FIRE);
                QaSupport.clear(nether, n);
            } else {
                notes.add("no nether level");
            }
            QaSupport.clear(level, d);
        } finally {
            for (Entity entity : extras) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(a).inflate(16.0D))) {
                item.discard();
            }
            if (n != null) {
                for (ItemEntity item : nether.getEntitiesOfClass(ItemEntity.class, new AABB(n).inflate(16.0D))) {
                    item.discard();
                }
                releaseColumn(nether, ChunkPos.containing(n));
            }
            releaseColumn(level, ChunkPos.containing(a));
        }

        ok[8] = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id("entities/cinderkin"))) != LootTable.EMPTY;
        ok[9] = server.registryAccess().lookupOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS)
                .get(ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, id("nether_cinderkin"))).isPresent();
        ok[10] = GuideBook.pageCount() == GuideBook.PAGE_COUNT && guidePage("cinderkin");
        return new QaReport("emberqa", NAMES, ok, notes);
    }

    private static Cinderkin cinderkin(ServerLevel level, BlockPos pos, List<Entity> extras) {
        Cinderkin mob = ModEntities.CINDERKIN.get().create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("cinderkin did not create");
        }
        mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        extras.add(mob);
        return mob;
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
