package com.mnemolith.command.qa;

import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mnemolith.Mnemolith;
import com.mnemolith.config.CommonConfig;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.entity.MobActions;
import com.mnemolith.entity.MobSpawns;
import com.mnemolith.entity.ai.CopiedActionKind;
import com.mnemolith.entity.mob.MomentReplicant;
import com.mnemolith.imprint.ModAttachments;
import com.mnemolith.recall.Gesture;
import com.mnemolith.recall.GestureKind;
import com.mnemolith.recall.GestureLog;
import com.mnemolith.recall.LivingMemory;
import com.mnemolith.recall.RecallSpace;
import com.mnemolith.world.LoadedChunkMemory;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith recallqa}. Gamemaster pass over the gesture log and replicant recall.
 * When a real player runs it, a replicant also enters recall beside them. The suite itself
 * uses a fake player in a far column and does not chat about what the gesture was.
 */
public final class RecallQa {
    private static final String[] NAMES = {
            "recorded", "debounced", "capped", "freshSkipped", "oldChosen", "expired", "fakeSkipped",
            "recallIdle", "recallActs", "recallVanishes", "stillCopies", "localNear", "localMuted", "distorted"
    };
    private static int salt = 1;

    private RecallQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            demonstrate(player);
        }
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        BlockPos site = QaSupport.column(level, (spawn.getX() >> 4) + 56 + salt * 3, (spawn.getZ() >> 4) + 40);
        QaSupport.tickColumn(level, site);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "RecallQa"));
        player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
        MomentReplicant recall = null;
        MomentReplicant copy = null;
        BlockPos ghost = null;
        boolean[] checks = new boolean[NAMES.length];
        java.util.List<String> notes = new java.util.ArrayList<>();
        try {
            int before = LivingMemory.size(player);
            LivingMemory.onAttack(player, null);
            checks[6] = LivingMemory.size(player) == before;

            boolean wrote = LivingMemory.remember(player, GestureKind.ATTACK, site, 10.0F, 0.0F, "minecraft:iron_sword", "", -1, "minecraft:zombie");
            checks[0] = wrote && LivingMemory.size(player) == 1 && LivingMemory.newest(player) != null && LivingMemory.newest(player).kind() == GestureKind.ATTACK;
            boolean again = LivingMemory.remember(player, GestureKind.ATTACK, site, 10.0F, 0.0F, "minecraft:iron_sword", "", -1, "minecraft:zombie");
            checks[1] = !again && LivingMemory.size(player) == 1;

            int cap = CommonConfig.RECALL_MAX_GESTURES.get();
            for (int i = 0; i < cap + 8; i++) {
                LivingMemory.remember(player, GestureKind.PLACE, site.offset(3 + i * 3, 0, 0), 0.0F, 0.0F, "", "minecraft:oak_planks", -1, "");
            }
            checks[2] = LivingMemory.size(player) == cap;

            player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
            long now = level.getGameTime();
            int minAge = CommonConfig.RECALL_MIN_AGE.get();
            int maxAge = CommonConfig.RECALL_MAX_AGE.get();
            String dimension = level.dimension().identifier().toString();
            LivingMemory.plant(player, gesture(GestureKind.ATTACK, dimension, site, now - 10L, false));
            checks[3] = LivingMemory.qualifying(player).isEmpty();
            long oldTime = now - minAge - 80L;
            LivingMemory.plant(player, gesture(GestureKind.ATTACK, dimension, site.offset(4, 0, 0), oldTime, false));
            Optional<Gesture> chosen = LivingMemory.qualifying(player);
            checks[4] = chosen.isPresent() && chosen.get().gameTime() == oldTime;
            LivingMemory.plant(player, gesture(GestureKind.PLACE, dimension, site.offset(8, 0, 0), now - maxAge - 80L, false));
            checks[5] = LivingMemory.qualifying(player).map(gesture -> gesture.gameTime() == oldTime).orElse(false)
                    && (LivingMemory.newest(player) == null || LivingMemory.newest(player).gameTime() != now - maxAge - 80L);

            recall = MobSpawns.summonReplicant(level, site);
            if (recall != null) {
                player.setPos(site.getX() - 1.2D, site.getY(), site.getZ() + 0.5D);
                player.setHealth(player.getMaxHealth());
                float health = player.getHealth();
                Gesture attack = gesture(GestureKind.ATTACK, dimension, site, 0.0F, oldTime, false, "minecraft:iron_sword", "");
                recall.beginRecall(player, attack);
                int idle = CommonConfig.RECALL_IDLE_TICKS.get();
                int match = CommonConfig.RECALL_MATCH_TICKS.get();
                for (int i = 0; i < idle; i++) {
                    recall.tickRecall(level);
                }
                checks[7] = recall.recalling() && recall.recallPhase() == 2 && player.getHealth() == health && !recall.recallPlayed()
                        && recall.getMainHandItem().is(Items.IRON_SWORD)
                        && MomentReplicant.recallHand(gesture(GestureKind.ATTACK, dimension, site, oldTime, false)).is(Items.WOODEN_SWORD)
                        && MomentReplicant.recallHand(gesture(GestureKind.FALL, dimension, site, oldTime, false)).isEmpty();
                for (int i = 0; i < match; i++) {
                    recall.tickRecall(level);
                }
                recall.tickRecall(level);
                boolean melee = recall.recallPlayed() && player.getHealth() == health;

                MomentReplicant builder = MobSpawns.summonReplicant(level, site.offset(6, 0, 0));
                boolean ghostOk = false;
                if (builder != null) {
                    builder.beginRecall(player, gesture(GestureKind.PLACE, dimension, site, 90.0F, oldTime, false, "", "minecraft:oak_planks"));
                    for (int i = 0; i < idle + match + 1; i++) {
                        builder.tickRecall(level);
                    }
                    ghost = builder.blockPosition().relative(Direction.fromYRot(90.0F));
                    boolean griefing = level.getGameRules().get(GameRules.MOB_GRIEFING);
                    ghostOk = builder.recallPlayed() && builder.getMainHandItem().is(Items.OAK_PLANKS)
                            && (!griefing || level.getBlockState(ghost).is(ModBlocks.REPLICATED_MOMENT.get()));
                    notes.add("griefing=" + griefing + " ghost=" + (ghost == null ? "-" : level.getBlockState(ghost).getBlock().toString()));
                    builder.discard();
                }
                checks[8] = melee && ghostOk;

                int guard = CommonConfig.RECALL_VANISH_TICKS.get() + 20;
                while (!recall.isRemoved() && guard-- > 0) {
                    recall.tickRecall(level);
                }
                checks[9] = recall.isRemoved();
            }

            copy = MobSpawns.summonReplicant(level, site.offset(-4, 0, 0));
            if (copy != null) {
                copy.beginTelegraph(CopiedActionKind.MELEE, null);
                checks[10] = copy.action() == MobActions.TELEGRAPH && !copy.recalling();
                copy.discard();
            }

            player.setPos(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D);
            player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
            LivingMemory.plant(player, gesture(GestureKind.PLACE, dimension, site, oldTime, false));
            checks[11] = LivingMemory.localCandidate(player).isPresent();
            QaSupport.muteStone(level, site);
            checks[12] = LivingMemory.localCandidate(player).isEmpty();

            Gesture plain = gesture(GestureKind.USE, dimension, site, 0.0F, oldTime, false);
            Gesture warped = gesture(GestureKind.USE, dimension, site, 0.0F, oldTime, true);
            checks[13] = RecallSpace.yaw(plain) == plain.yaw()
                    && Math.abs(Mth.wrapDegrees(RecallSpace.yaw(warped) - warped.yaw()) - RecallSpace.YAW_NOISE) < 0.01F
                    && !RecallSpace.place(warped).equals(warped.pos());
        } catch (RuntimeException ex) {
            Mnemolith.LOGGER.error("Mnemolith recallqa failed", ex);
            notes.add("exception " + ex);
        } finally {
            if (recall != null && !recall.isRemoved()) {
                recall.discard();
            }
            if (copy != null && !copy.isRemoved()) {
                copy.discard();
            }
            QaSupport.discardReplicants(level, site);
            if (ghost != null && level.getBlockState(ghost).is(ModBlocks.REPLICATED_MOMENT.get())) {
                level.setBlock(ghost, Blocks.AIR.defaultBlockState(), 2);
            }
            LoadedChunkMemory.removeMuteStone(level, site);
            player.setData(ModAttachments.GESTURE_LOG.get(), new GestureLog());
            QaSupport.releaseColumn(level, net.minecraft.world.level.ChunkPos.containing(site));
        }
        return new QaReport("recallqa", NAMES, checks, notes).log();
    }

    /** Plants one old attack on the player who ran the command and spawns a recalling replicant beside them. */
    private static void demonstrate(ServerPlayer player) {
        if (!LivingMemory.enabled() || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long when = level.getGameTime() - CommonConfig.RECALL_MIN_AGE.get() - 200L;
        String item = player.getMainHandItem().isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString();
        Gesture gesture = new Gesture(
                GestureKind.ATTACK,
                level.dimension().identifier().toString(),
                player.blockPosition(),
                player.getYRot(),
                player.getXRot(),
                item,
                "",
                -1,
                "",
                when,
                false,
                java.util.List.of());
        LivingMemory.plant(player, gesture);
        LivingMemory.markWow(player);
        Vec3 look = player.getLookAngle();
        BlockPos spawn = BlockPos.containing(player.getX() + look.x * 3.0D, player.getY(), player.getZ() + look.z * 3.0D);
        MomentReplicant replicant = MobSpawns.summonReplicant(level, spawn);
        if (replicant != null) {
            replicant.beginRecall(player, gesture);
        }
    }

    private static Gesture gesture(GestureKind kind, String dimension, BlockPos pos, long time, boolean distorted) {
        return gesture(kind, dimension, pos, 0.0F, time, distorted, "", "");
    }

    private static Gesture gesture(GestureKind kind, String dimension, BlockPos pos, float yaw, long time, boolean distorted) {
        return gesture(kind, dimension, pos, yaw, time, distorted, "", "");
    }

    private static Gesture gesture(GestureKind kind, String dimension, BlockPos pos, float yaw, long time, boolean distorted, String item, String block) {
        return new Gesture(kind, dimension, pos, yaw, 0.0F, item, block, -1, "", time, distorted, java.util.List.of());
    }
}
