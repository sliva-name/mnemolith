package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mnemolith.Mnemolith;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.StrongMemoryPayload;
import com.mnemolith.particle.AfterImages;
import com.mnemolith.pressure.MemoryPressure;
import com.mnemolith.pressure.PressureBand;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith feel3qa}. Memory feel, stage 3: the ambient cues. The after-image particle, the strong-memory
 * payload and the two client toggles' lang are registered; a calm chunk sends nothing; stepping into a saturated chunk
 * sends the hint once (not again while there); after-images need the player to stand still for
 * {@link AfterImages#STILL_TICKS}, show at most {@link AfterImages#MAX_IMAGES}, wait out the cooldown, and stop as soon
 * as the player moves. The live test {@code strong_chunk_hints_player} walks a real player in.
 */
public final class Feel3Qa {
    private static final String[] NAMES = {"registered", "calmNothing", "hintOnEnter", "hintOnce", "notStillYet", "imagesWhenStill",
            "imagesCapped", "cooldown", "movingResets"};
    private static final UUID PLAYER = UUID.fromString("66666666-3333-2222-3333-aeaeaeaeaeae");
    private static int salt;

    private Feel3Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    /** Lowest pressure in the saturated band under the current config. */
    public static int saturatedPressure() {
        for (int p = 0; p < 100_000; p++) {
            if (MemoryPressure.band(p) == PressureBand.SATURATED) {
                return p;
            }
        }
        return 0;
    }

    /** Fills {@code memory} with {@code count} loud imprints around {@code at} and marks the chunk saturated. */
    public static void saturate(ChunkMemory memory, BlockPos at, int count, long now) {
        for (int i = 0; i < count; i++) {
            memory.addImprint(new Imprint(ImprintTag.EXPLOSION, 8 + i, at.offset(i, 0, 0), Optional.empty(), i, now, false), 64);
        }
        memory.setCachedPressure(saturatedPressure());
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos calm = column(level, (spawn.getX() >> 4) + 630 + salt * 3, (spawn.getZ() >> 4) - 620);
        BlockPos loud = column(level, (spawn.getX() >> 4) + 630 + salt * 3, (spawn.getZ() >> 4) - 616);
        tickColumn(level, calm);
        tickColumn(level, loud);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Feel3Qa"));
        try {
            ok[0] = BuiltInRegistries.PARTICLE_TYPE.containsKey(Identifier.fromNamespaceAndPath(Mnemolith.MOD_ID, "memory_afterimage"))
                    && StrongMemoryPayload.TYPE != null
                    && langHas("en_us", "mnemolith.memory.strong_hint") && langHas("ru_ru", "mnemolith.memory.strong_hint")
                    && langHas("en_us", "mnemolith.configuration.memoryAfterImages.tooltip") && langHas("ru_ru", "mnemolith.configuration.memoryAfterImages.tooltip")
                    && langHas("en_us", "mnemolith.configuration.strongMemoryHint.tooltip") && langHas("ru_ru", "mnemolith.configuration.strongMemoryHint.tooltip");

            LoadedChunkMemory.clear(level.getChunkAt(calm));
            LoadedChunkMemory.clear(level.getChunkAt(loud));
            AfterImages.forget(PLAYER);
            long t = level.getGameTime() + 1000L * salt;
            player.snapTo(calm.getX() + 0.5D, calm.getY(), calm.getZ() + 0.5D, 0.0F, 0.0F);
            AfterImages.Result c1 = AfterImages.scan(level, player, t);
            AfterImages.Result c2 = AfterImages.scan(level, player, t + AfterImages.STILL_TICKS + 1);
            ok[1] = c1.equals(AfterImages.Result.NONE) && c2.equals(AfterImages.Result.NONE);

            saturate(LoadedChunkMemory.getOrCreate(level.getChunkAt(loud)), loud, 5, t);
            int hints = AfterImages.hintsSent();
            player.snapTo(loud.getX() + 0.5D, loud.getY(), loud.getZ() + 0.5D, 0.0F, 0.0F);
            AfterImages.Result enter = AfterImages.scan(level, player, t + 100);
            ok[2] = enter.hint() && AfterImages.hintsSent() == hints + 1;
            AfterImages.Result stay = AfterImages.scan(level, player, t + 120);
            ok[3] = !stay.hint() && AfterImages.hintsSent() == hints + 1;
            ok[4] = enter.images() == 0 && stay.images() == 0;

            int images = AfterImages.imagesSent();
            AfterImages.Result still = AfterImages.scan(level, player, t + 100 + AfterImages.STILL_TICKS);
            ok[5] = still.images() > 0 && AfterImages.imagesSent() == images + still.images();
            ok[6] = still.images() == AfterImages.MAX_IMAGES;
            AfterImages.Result soon = AfterImages.scan(level, player, t + 100 + AfterImages.STILL_TICKS + 20);
            AfterImages.Result later = AfterImages.scan(level, player, t + 100 + AfterImages.STILL_TICKS * 2);
            ok[7] = soon.images() == 0 && later.images() > 0;

            player.snapTo(loud.getX() + 3.5D, loud.getY(), loud.getZ() + 0.5D, 0.0F, 0.0F);
            AfterImages.Result moved = AfterImages.scan(level, player, t + 100 + AfterImages.STILL_TICKS * 4);
            AfterImages.Result again = AfterImages.scan(level, player, t + 120 + AfterImages.STILL_TICKS * 4);
            ok[8] = moved.images() == 0 && again.images() == 0 && !moved.hint();
            notes.add("still=" + still + " soon=" + soon + " later=" + later + " moved=" + moved);
        } finally {
            AfterImages.forget(PLAYER);
            LoadedChunkMemory.clear(level.getChunkAt(calm));
            LoadedChunkMemory.clear(level.getChunkAt(loud));
            releaseColumn(level, ChunkPos.containing(calm));
            releaseColumn(level, ChunkPos.containing(loud));
        }
        return new QaReport("feel3qa", NAMES, ok, notes);
    }

    public static boolean langHasPub(String lang, String key) { return langHas(lang, key); }

    private static boolean langHas(String lang, String key) {
        try (var in = Feel3Qa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
