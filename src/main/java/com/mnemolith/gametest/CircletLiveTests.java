package com.mnemolith.gametest;

import com.mnemolith.content.ModItems;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.boss.Unremembered;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.world.LoadedChunkMemory;
import com.mnemolith.worldgen.hollows.HollowForgetting;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The remembering circlet with two real server players ({@link LivePlayers}) on a painted hollow: the Unremembered's
 * pulse darkens the bare-headed player and passes over the wearer; a hollow does not forget beside the wearer, and
 * still forgets 20 blocks away.
 */
final class CircletLiveTests {
    private CircletLiveTests() {}

    static void circletWardsAndKeeps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos site = LivePlayers.liveSite(level, 15);
        LivePlayers.force(level, site);
        LivePlayers.force(level, site.offset(24, 0, 0));
        ServerPlayer wearer = LivePlayers.join(helper, "LiveCircletWearer", Vec3.atBottomCenterOf(site));
        ServerPlayer bare = LivePlayers.join(helper, "LiveCircletBare", Vec3.atBottomCenterOf(site.offset(0, 0, 3)));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var server = level.getServer();
                    var console = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
                    try {
                        server.getCommands().getDispatcher().execute(String.format("fillbiome %d %d %d %d %d %d mnemolith:memory_hollows",
                                site.getX() - 8, site.getY() - 4, site.getZ() - 8, site.getX() + 40, site.getY() + 4, site.getZ() + 16), console);
                    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                        throw new IllegalStateException(e);
                    }
                    wearer.setGameMode(GameType.SURVIVAL);
                    bare.setGameMode(GameType.SURVIVAL);
                    wearer.removeAllEffects();
                    bare.removeAllEffects();
                    wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.REMEMBERING_CIRCLET.get()));
                    bare.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    for (Unremembered old : level.getEntitiesOfClass(Unremembered.class, new AABB(site).inflate(80.0D))) {
                        old.discard();
                    }
                    Unremembered boss = ModEntities.UNREMEMBERED.get().create(level, EntitySpawnReason.COMMAND);
                    helper.assertTrue(boss != null, "no boss");
                    boss.snapTo(site.getX() + 2.5D, site.getY(), site.getZ() + 1.5D, 0.0F, 0.0F);
                    boss.setNoAi(true);
                    level.addFreshEntity(boss);
                    int touched = boss.pulse(level);
                    boolean wearerDark = wearer.hasEffect(MobEffects.DARKNESS);
                    boolean bareDark = bare.hasEffect(MobEffects.DARKNESS);
                    boss.discard();

                    BlockPos near = site.offset(2, 0, 0);
                    BlockPos far = site.offset(24, 0, 0);
                    LoadedChunkMemory.clear(level.getChunkAt(near));
                    LoadedChunkMemory.clear(level.getChunkAt(far));
                    boolean forgotNear = HollowForgetting.forget(level, near, ImprintTag.PATH);
                    boolean forgotFar = HollowForgetting.forget(level, far, ImprintTag.PATH);
                    LoadedChunkMemory.clear(level.getChunkAt(near));
                    LoadedChunkMemory.clear(level.getChunkAt(far));
                    wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    helper.assertTrue(touched == 1 && bareDark && !wearerDark,
                            "the pulse should pass over the wearer: touched=" + touched + " wearer=" + wearerDark + " bare=" + bareDark);
                    helper.assertTrue(!forgotNear, "the hollow forgot beside the circlet");
                    helper.assertTrue(forgotFar, "the hollow should still forget away from the circlet");
                })
                .thenSucceed();
    }
}
