package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.content.ModBlocks;
import com.mnemolith.content.block.EmberBrazierBlock;
import com.mnemolith.content.block.HushLoomBlock;
import com.mnemolith.entity.ModEntities;
import com.mnemolith.entity.mob.Hush;
import com.mnemolith.entity.mob.Hushling;
import com.mnemolith.fishing.Angling;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.imprint.ImprintWriter;
import com.mnemolith.particle.MemoryFeel;
import com.mnemolith.particle.MemoryFeel.Kind;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith feelqa}. Memory feel, stage 1: the five cue kinds are registered (particle types, sound events,
 * sounds.json entries with subtitles, the client toggle's lang), the toggle matches exactly the memory sounds, and the
 * stage 1 systems send the right kind: a brazier burn and a hushling thinning are ERASED, a hush is FORGOTTEN, a loom
 * threading is KEPT, an angled memory is CAUGHT; a burn or thinning that finds nothing sends no cue. The live test
 * {@code loom_cue_reaches_player} threads a loom with a real player.
 */
public final class FeelQa {
    private static final String[] NAMES = {"registered", "soundsJson", "toggleMatches", "brazierErases", "thinErases", "hushForgets",
            "loomKeeps", "anglingCatches", "nothingNoCue"};
    private static final UUID PLAYER = UUID.fromString("66666666-1111-2222-3333-aeaeaeaeaeae");
    private static int salt;

    private FeelQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();

        boolean registered = true;
        for (Kind kind : Kind.values()) {
            String name = kind.name().toLowerCase(java.util.Locale.ROOT);
            registered &= BuiltInRegistries.PARTICLE_TYPE.containsKey(id("memory_" + name))
                    && BuiltInRegistries.SOUND_EVENT.containsKey(id("memory." + name))
                    && MemoryFeel.particle(kind) != null && MemoryFeel.sound(kind) != null;
        }
        ok[0] = registered;
        boolean json = false;
        try (var in = FeelQa.class.getResourceAsStream("/assets/mnemolith/sounds.json")) {
            var sounds = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            json = true;
            for (Kind kind : Kind.values()) {
                String name = "memory." + kind.name().toLowerCase(java.util.Locale.ROOT);
                json &= sounds.has(name) && langHas("en_us", "subtitles.mnemolith." + name) && langHas("ru_ru", "subtitles.mnemolith." + name);
            }
        } catch (java.io.IOException | RuntimeException e) {
            notes.add("sounds.json: " + e.getMessage());
        }
        ok[1] = json && langHas("en_us", "mnemolith.configuration.memoryEventSounds.tooltip") && langHas("ru_ru", "mnemolith.configuration.memoryEventSounds.tooltip");
        ok[2] = MemoryFeel.isMemorySound(id("memory.kept")) && !MemoryFeel.isMemorySound(id("imprint_write"))
                && !MemoryFeel.isMemorySound(Identifier.withDefaultNamespace("memory.kept")) && !MemoryFeel.isMemorySound(null);

        BlockPos a = column(level, (spawn.getX() >> 4) + 590 + salt * 3, (spawn.getZ() >> 4) - 580);
        tickColumn(level, a);
        BlockPos brazier = a.offset(1, 0, 1);
        BlockPos loom = a.offset(4, 0, 1);
        BlockPos water = a.offset(1, 0, 5);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "FeelQa"));
        Hushling mob = null;
        FishingHook hook = null;
        try {
            player.getInventory().clearContent();
            player.setGameMode(GameType.SURVIVAL);
            player.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() - 2.5D, 0.0F, 0.0F);
            QaSupport.clear(level, a);

            // ---------- brazier ----------
            level.setBlock(brazier, ModBlocks.EMBER_BRAZIER.get().defaultBlockState().setValue(EmberBrazierBlock.CHARGE, 8).setValue(EmberBrazierBlock.LIT, true), 3);
            int erased = MemoryFeel.sent(Kind.ERASED);
            EmberBrazierBlock.Burn empty = EmberBrazierBlock.burn(level, brazier);
            boolean emptyQuiet = empty == EmberBrazierBlock.Burn.EMPTY && MemoryFeel.sent(Kind.ERASED) == erased;
            ImprintWriter.write(level, a, List.of(ImprintTag.BUILD), null, false);
            EmberBrazierBlock.burn(level, brazier);
            ok[3] = MemoryFeel.sent(Kind.ERASED) == erased + 1;

            // ---------- hushling ----------
            mob = ModEntities.HUSHLING.get().create(level, EntitySpawnReason.COMMAND);
            if (mob != null) {
                mob.snapTo(a.getX() + 0.5D, a.getY(), a.getZ() + 0.5D, 0.0F, 0.0F);
                mob.setNoAi(true);
                level.addFreshEntity(mob);
                int before = MemoryFeel.sent(Kind.ERASED);
                String none = mob.thin(level);
                emptyQuiet &= "empty".equals(none) && MemoryFeel.sent(Kind.ERASED) == before;
                ImprintWriter.write(level, a, List.of(ImprintTag.PATH), null, false);
                mob.thin(level);
                ok[4] = MemoryFeel.sent(Kind.ERASED) == before + 1;
                int forgotten = MemoryFeel.sent(Kind.FORGOTTEN);
                Hush.forget(player.getUUID());
                ok[5] = mob.hushPlayer(level, player) && MemoryFeel.sent(Kind.FORGOTTEN) == forgotten + 1;
                Hush.forget(player.getUUID());
                player.removeAllEffects();
            }
            ok[8] = emptyQuiet;

            // ---------- loom ----------
            level.setBlock(loom, ModBlocks.HUSH_LOOM.get().defaultBlockState(), 3);
            int kept = MemoryFeel.sent(Kind.KEPT);
            ok[6] = HushLoomBlock.keep(level, loom, ImprintTag.DEATH) && MemoryFeel.sent(Kind.KEPT) == kept + 1
                    && MemoryFeel.lastPos().closerThan(loom, 2.0D);

            // ---------- angling ----------
            QaSupport.clear(level, a);
            level.setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FISHING_ROD));
            hook = new FishingHook(player, level, 0, 0);
            hook.setPos(water.getX() + 0.5D, water.getY() + 0.4D, water.getZ() + 0.5D);
            level.addFreshEntity(hook);
            ImprintWriter.write(level, water, List.of(ImprintTag.FALL), null, false);
            int caught = MemoryFeel.sent(Kind.CAUGHT);
            ok[7] = Angling.angle(level, hook, player).isPresent() && MemoryFeel.sent(Kind.CAUGHT) == caught + 1;
            notes.add("sent erased=" + MemoryFeel.sent(Kind.ERASED) + " kept=" + MemoryFeel.sent(Kind.KEPT) + " caught=" + MemoryFeel.sent(Kind.CAUGHT)
                    + " forgotten=" + MemoryFeel.sent(Kind.FORGOTTEN));
        } finally {
            if (mob != null) {
                mob.discard();
            }
            if (hook != null) {
                hook.discard();
            }
            for (var item : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(a).inflate(8.0D))) {
                item.discard();
            }
            level.setBlock(brazier, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(loom, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            QaSupport.clear(level, a);
            player.getInventory().clearContent();
            releaseColumn(level, ChunkPos.containing(a));
        }
        return new QaReport("feelqa", NAMES, ok, notes);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, path);
    }

    private static boolean langHas(String lang, String key) {
        try (var in = FeelQa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
