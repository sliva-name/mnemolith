package com.mnemolith.gametest;

import com.mnemolith.entity.MemoryMob;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.FractureStalker;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** Synced mob flags that must survive a chunk unload / restart (save to NBT, load into a fresh entity). */
final class PersistenceTests {
    private PersistenceTests() {}

    static void run(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();

        // Elite fracture stalker: skin and the scar sinew drop hang on this flag.
        FractureStalker stalker = ModEntities.FRACTURE_STALKER.get().create(level, EntitySpawnReason.LOAD);
        helper.assertTrue(stalker != null, "no stalker");
        CompoundTag tag = save(level, stalker);
        tag.putBoolean("elite", true);
        FractureStalker elite = load(level, ModEntities.FRACTURE_STALKER.get(), tag);
        helper.assertTrue(elite.elite(), "a saved elite stalker loaded as a normal one");
        helper.assertTrue(load(level, ModEntities.FRACTURE_STALKER.get(), save(level, elite)).elite(), "an elite stalker lost its flag on the second reload");
        helper.assertTrue(!load(level, ModEntities.FRACTURE_STALKER.get(), save(level, stalker)).elite(), "a normal stalker came back elite");

        // Twin memory mobs.
        twin(helper, level, ModEntities.ECHO_STRIDER.get());
        twin(helper, level, ModEntities.ARCHIVIST.get());
        twin(helper, level, ModEntities.MOMENT_REPLICANT.get());
        helper.succeed();
    }

    private static <T extends MemoryMob> void twin(GameTestHelper helper, ServerLevel level, EntityType<T> type) {
        T mob = type.create(level, EntitySpawnReason.LOAD);
        helper.assertTrue(mob != null, "no " + type);
        mob.setTwin(true);
        helper.assertTrue(load(level, type, save(level, mob)).twin(), type + " lost its twin flag on reload");
        mob.setTwin(false);
        helper.assertTrue(!load(level, type, save(level, mob)).twin(), type + " came back a twin");
    }

    private static CompoundTag save(ServerLevel level, Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static <T extends Entity> T load(ServerLevel level, EntityType<T> type, CompoundTag tag) {
        T copy = type.create(level, EntitySpawnReason.LOAD);
        if (copy == null) {
            throw new IllegalStateException("could not create " + type);
        }
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        return copy;
    }
}
