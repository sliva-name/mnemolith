package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.particle.AfterEcho;
import com.mnemolith.particle.AfterImages;
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
 * {@code /mnemolith afterechoqa}. After-echoes: every imprint kind has a distant-replay sound event
 * ({@code mnemolith:memory.<tag>}) with RU/EN subtitles, a motion kind, and the client toggle's lang; a still
 * player in a saturated chunk gets bursts whose tags are recorded server-side. Sound and motion themselves are
 * client-side and checked in the visual tour.
 */
public final class AfterEchoesQa {
    private static final String[] NAMES = {"registered", "motions", "levels", "bursts", "tagsRecorded"};
    private static final UUID PLAYER = UUID.fromString("77777777-4444-3333-4444-aeaeaeaeaeae");
    private static int salt;

    private AfterEchoesQa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos loud = column(level, (spawn.getX() >> 4) + 640 + salt * 3, (spawn.getZ() >> 4) - 610);
        tickColumn(level, loud);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "AfterEchoesQa"));
        try {
            boolean registered = com.mnemolith.audio.ModSounds.MEMORY_ECHOES.size() == ImprintTag.values().length;
            for (ImprintTag tag : ImprintTag.values()) {
                registered &= BuiltInRegistries.SOUND_EVENT.containsKey(Identifier.fromNamespaceAndPath("mnemolith", AfterEcho.soundName(tag)));
                registered &= Feel3Qa.langHasPub("en_us", "subtitles.mnemolith." + AfterEcho.soundName(tag));
                registered &= Feel3Qa.langHasPub("ru_ru", "subtitles.mnemolith." + AfterEcho.soundName(tag));
                registered &= AfterEcho.tagFor(AfterEcho.code(tag)) == tag;
            }
            registered &= Feel3Qa.langHasPub("en_us", "mnemolith.configuration.memoryAfterEchoes.tooltip");
            registered &= Feel3Qa.langHasPub("ru_ru", "mnemolith.configuration.memoryAfterEchoes.tooltip");
            ok[0] = registered;

            ok[1] = AfterEcho.motionFor(ImprintTag.EXPLOSION) == AfterEcho.Motion.RIPPLE
                    && AfterEcho.motionFor(ImprintTag.DEATH) == AfterEcho.Motion.SINK
                    && AfterEcho.motionFor(ImprintTag.PATH) == AfterEcho.Motion.GROUND
                    && AfterEcho.motionFor(ImprintTag.BUILD) == AfterEcho.Motion.OUTLINE
                    && AfterEcho.motionFor(ImprintTag.FIRE) == AfterEcho.Motion.COLUMN;

            // distant and muffled, programmatically: no echo sample louder than 0.45 or brighter than pitch 0.85
            boolean levels = true;
            try (var in = AfterEchoesQa.class.getResourceAsStream("/assets/mnemolith/sounds.json")) {
                var sounds = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (ImprintTag tag : ImprintTag.values()) {
                    var entry = sounds.getAsJsonObject(AfterEcho.soundName(tag));
                    for (var el : entry.getAsJsonArray("sounds")) {
                        var o = el.getAsJsonObject();
                        levels &= (!o.has("volume") || o.get("volume").getAsFloat() <= 0.45F) && (!o.has("pitch") || o.get("pitch").getAsFloat() <= 0.85F);
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                levels = false;
            }
            ok[2] = levels;

            LoadedChunkMemory.clear(level.getChunkAt(loud));
            AfterImages.forget(PLAYER);
            long t = level.getGameTime() + 2000L * salt;
            Feel3Qa.saturate(LoadedChunkMemory.getOrCreate(level.getChunkAt(loud)), loud, 5, t);
            int echoes = AfterImages.echoesSent();
            player.snapTo(loud.getX() + 0.5D, loud.getY(), loud.getZ() + 0.5D, 0.0F, 0.0F);
            AfterImages.scan(level, player, t);
            AfterImages.Result still = AfterImages.scan(level, player, t + AfterImages.STILL_TICKS);
            ok[3] = still.images() > 0 && AfterImages.echoesSent() == echoes + still.images();
            ok[4] = !AfterImages.lastEchoTags().isEmpty() && AfterImages.lastEchoTags().stream().allMatch(tag -> tag == ImprintTag.EXPLOSION);
            notes.add("still=" + still + " tags=" + AfterImages.lastEchoTags());
        } finally {
            AfterImages.forget(PLAYER);
            // distant and muffled, programmatically: no echo sample louder than 0.45 or brighter than pitch 0.85
            boolean levels = true;
            try (var in = AfterEchoesQa.class.getResourceAsStream("/assets/mnemolith/sounds.json")) {
                var sounds = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (ImprintTag tag : ImprintTag.values()) {
                    var entry = sounds.getAsJsonObject(AfterEcho.soundName(tag));
                    for (var el : entry.getAsJsonArray("sounds")) {
                        var o = el.getAsJsonObject();
                        levels &= (!o.has("volume") || o.get("volume").getAsFloat() <= 0.45F) && (!o.has("pitch") || o.get("pitch").getAsFloat() <= 0.85F);
                    }
                }
            } catch (java.io.IOException | RuntimeException e) {
                levels = false;
            }
            ok[2] = levels;

            LoadedChunkMemory.clear(level.getChunkAt(loud));
            releaseColumn(level, ChunkPos.containing(loud));
        }
        return new QaReport("afterechoqa", NAMES, ok, notes);
    }
}
