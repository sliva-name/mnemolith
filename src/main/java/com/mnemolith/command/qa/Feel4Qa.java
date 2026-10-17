package com.mnemolith.command.qa;

import static com.mnemolith.command.qa.QaSupport.column;
import static com.mnemolith.command.qa.QaSupport.releaseColumn;
import static com.mnemolith.command.qa.QaSupport.tickColumn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mnemolith.common.MemoryGlyphs;
import com.mnemolith.content.ModItems;
import com.mnemolith.imprint.ChunkMemory;
import com.mnemolith.imprint.Imprint;
import com.mnemolith.imprint.ImprintTag;
import com.mnemolith.network.ChunkPressure;
import com.mnemolith.network.PressureCollector;
import com.mnemolith.world.LoadedChunkMemory;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * {@code /mnemolith feel4qa}. Memory feel, stage 4: the lens and circlet overlays' shared rules. A lens snapshot chunk
 * carries its loudest tag over the wire (and -1 for none); the loudest is the strongest imprint, not the newest; every
 * tag has a glyph colour and an out-of-range ordinal none; the circlet tint applies only to a non-spectator wearing the
 * circlet on the head; both client toggles have RU/EN lang. The live test {@code lens_reads_loudest_circlet_tints}
 * checks a real player.
 */
public final class Feel4Qa {
    private static final String[] NAMES = {"wire", "wireNone", "loudestStrongest", "loudestEmpty", "glyphColours", "glyphNone",
            "circletWorn", "circletNotWorn", "lang"};
    private static final UUID PLAYER = UUID.fromString("66666666-5555-2222-3333-aeaeaeaeaeae");
    private static int salt;

    private Feel4Qa() {}

    public static int run(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        return check(source.getLevel(), BlockPos.containing(source.getPosition())).send(source, true);
    }

    public static QaReport check(ServerLevel level, BlockPos spawn) {
        salt++;
        boolean[] ok = new boolean[NAMES.length];
        List<String> notes = new ArrayList<>();
        BlockPos a = column(level, (spawn.getX() >> 4) + 650 + salt * 3, (spawn.getZ() >> 4) - 640);
        tickColumn(level, a);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(PLAYER, "Feel4Qa"));
        try {
            ChunkPressure loud = new ChunkPressure(3, -4, 120, 2, 0, ImprintTag.SCULK.ordinal());
            ChunkPressure quiet = new ChunkPressure(3, -4, 0, 0, 0);
            ok[0] = roundTrip(level, loud).equals(loud);
            ok[1] = quiet.loudest() == -1 && roundTrip(level, quiet).equals(quiet);

            LoadedChunkMemory.clear(level.getChunkAt(a));
            ok[3] = PressureCollector.loudestOrdinal(null) == -1 && PressureCollector.loudestOrdinal(new ChunkMemory()) == -1;
            ChunkMemory memory = LoadedChunkMemory.getOrCreate(level.getChunkAt(a));
            long now = level.getGameTime();
            memory.addImprint(new Imprint(ImprintTag.DEATH, 9, a, Optional.empty(), 1, now - 100, false), 64);
            memory.addImprint(new Imprint(ImprintTag.PATH, 2, a, Optional.empty(), 2, now, false), 64);
            ok[2] = PressureCollector.loudestOrdinal(memory) == ImprintTag.DEATH.ordinal();

            Set<Integer> colours = new HashSet<>();
            boolean all = true;
            for (ImprintTag tag : ImprintTag.values()) {
                int c = MemoryGlyphs.colour(tag);
                all &= c != 0 && MemoryGlyphs.colour(tag.ordinal()) == c;
                colours.add(c);
            }
            ok[4] = all && colours.size() >= 8;
            ok[5] = MemoryGlyphs.colour(-1) == 0 && MemoryGlyphs.colour(ImprintTag.values().length) == 0;

            player.setGameMode(GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            boolean bare = MemoryGlyphs.circletTint(player);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.REMEMBERING_CIRCLET.get()));
            boolean inHand = MemoryGlyphs.circletTint(player);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.REMEMBERING_CIRCLET.get()));
            ok[6] = MemoryGlyphs.circletTint(player);
            player.setGameMode(GameType.SPECTATOR);
            boolean spectator = MemoryGlyphs.circletTint(player);
            player.setGameMode(GameType.SURVIVAL);
            ok[7] = !bare && !inHand && !spectator && !MemoryGlyphs.circletTint(null);

            boolean lang = true;
            for (String key : new String[] {"lensLoudestGlyph", "circletTint", "pressureEffectStrength"}) {
                for (String l : new String[] {"en_us", "ru_ru"}) {
                    lang &= langHas(l, "mnemolith.configuration." + key) && langHas(l, "mnemolith.configuration." + key + ".tooltip");
                }
            }
            ok[8] = lang;
            notes.add("colours=" + colours.size());
        } finally {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.setGameMode(GameType.SURVIVAL);
            LoadedChunkMemory.clear(level.getChunkAt(a));
            releaseColumn(level, ChunkPos.containing(a));
        }
        return new QaReport("feel4qa", NAMES, ok, notes);
    }

    private static ChunkPressure roundTrip(ServerLevel level, ChunkPressure value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ChunkPressure.STREAM_CODEC.encode(buf, value);
            return ChunkPressure.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static boolean langHas(String lang, String key) {
        try (var in = Feel4Qa.class.getResourceAsStream("/assets/mnemolith/lang/" + lang + ".json")) {
            return in != null && com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().has(key);
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
    }
}
